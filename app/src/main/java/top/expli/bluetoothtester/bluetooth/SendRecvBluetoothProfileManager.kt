package top.expli.bluetoothtester.bluetooth

import android.bluetooth.BluetoothSocket
import android.content.Context
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

data class SpeedTestResult(
    val txAvgBps: Double,
    val rxAvgBps: Double,
    val txTotalBytes: Long,
    val rxTotalBytes: Long,
    val durationMs: Long
)

abstract class SendRecvBluetoothProfileManager(context: Context) :
    BasicBluetoothProfileManager(context) {
    protected var socket: BluetoothSocket? = null
    protected var input: InputStream? = null
    protected var output: OutputStream? = null

    open fun send(bytes: ByteArray): Boolean {
        val out = output ?: return false
        return try {
            out.write(bytes)
            out.flush()
            true
        } catch (_: IOException) {
            false
        }
    }

    open fun receive(maxBytes: Int): ByteArray? {
        val inp = input ?: return null
        return try {
            if (maxBytes <= 0) return null
            val buf = ByteArray(maxBytes)
            val read = inp.read(buf)
            if (read <= 0) null else buf.copyOf(read)
        } catch (exec: IOException) {
            throw exec
        }
    }

    /**
     * Measures throughput by repeatedly sending and receiving payloads.
     * Returns null if any send/receive fails or inputs are invalid. Reports instant and running
     * average throughput via [progress] callback when provided. Data transfer runs on IO, while
     * calculations run on Default to keep them parallel.
     */
    open suspend fun speedTestWithInstantSpeed(
        testDurationMs: Long = 5000,
        payloadSize: Int = 4096,
        txEnabled: Boolean = true,
        rxEnabled: Boolean = true,
        // tx: 发送方向, rx: 接收方向
        progress: ((
            txInstantBps: Double,
            rxInstantBps: Double,
            txAvgBps: Double,
            rxAvgBps: Double,
            txTotalBytes: Long,
            rxTotalBytes: Long,
            elapsedMs: Long
        ) -> Unit)? = null
    ): SpeedTestResult? = coroutineScope {
        if (payloadSize <= 0 || testDurationMs <= 0) return@coroutineScope null
        if (!txEnabled && !rxEnabled) return@coroutineScope null
        val payload = ByteArray(payloadSize) { it.toByte() }
        val bytesSent = AtomicLong(0L)
        val bytesReceived = AtomicLong(0L)
        val txStartNs = AtomicLong(0L)
        val rxStartNs = AtomicLong(0L)
        val failed = AtomicBoolean(false)
        val isRunning = AtomicBoolean(true)
        val testStart = System.nanoTime()

        val sender =
            if (!txEnabled) {
                null
            } else {
                launch(Dispatchers.IO) {
                    val out = output ?: run {
                        failed.set(true)
                        isRunning.set(false)
                        return@launch
                    }
                    val payloadBytes = payload.size.toLong()
                    var pending = 0L
                    while (isRunning.get() && isActive) {
                        try {
                            out.write(payload)
                            txStartNs.compareAndSet(0L, System.nanoTime())
                        } catch (_: IOException) {
                            failed.set(true)
                            isRunning.set(false)
                            break
                        }
                        pending += payloadBytes
                        if (pending >= 64 * 1024) {
                            bytesSent.addAndGet(pending)
                            pending = 0L
                        }
                    }
                    if (pending > 0) bytesSent.addAndGet(pending)
                }
            }

        val receiver =
            if (!rxEnabled) {
                null
            } else {
                launch(Dispatchers.IO) {
                    val inp = input ?: run {
                        failed.set(true)
                        isRunning.set(false)
                        return@launch
                    }

                    val bufSize = minOf(maxOf(payloadSize, 32 * 1024), 256 * 1024)
                    val buf = ByteArray(bufSize)
                    var pending = 0L

                    while (isRunning.get() && isActive) {
                        val available = try {
                            inp.available()
                        } catch (_: IOException) {
                            failed.set(true)
                            isRunning.set(false)
                            break
                        }
                        if (available <= 0) {
                            yield()
                            continue
                        }
                        val toRead = minOf(available, buf.size)
                        val read = try {
                            inp.read(buf, 0, toRead)
                        } catch (_: IOException) {
                            failed.set(true)
                            isRunning.set(false)
                            break
                        }
                        if (read == -1) {
                            failed.set(true)
                            isRunning.set(false)
                            break
                        }
                        if (read > 0) {
                            rxStartNs.compareAndSet(0L, System.nanoTime())
                            pending += read.toLong()
                            if (pending >= 64 * 1024) {
                                bytesReceived.addAndGet(pending)
                                pending = 0L
                            }
                        }
                    }
                    if (pending > 0) bytesReceived.addAndGet(pending)
                }
            }

        val monitor = launch(Dispatchers.Default) {
            var lastTxBytes = 0L
            var lastRxBytes = 0L
            var lastTime = System.nanoTime()
            val intervalMs = 500L

            while (isRunning.get() && isActive) {
                delay(intervalMs)

                val currentTime = System.nanoTime()
                val currentTxBytes = bytesSent.get()
                val currentRxBytes = bytesReceived.get()

                val deltaTime = (currentTime - lastTime) / 1_000_000_000.0
                val totalTime = (currentTime - testStart) / 1_000_000_000.0

                if (deltaTime > 0) {
                    val deltaTxBytes = currentTxBytes - lastTxBytes
                    val deltaRxBytes = currentRxBytes - lastRxBytes
                    val txInstantSpeed = deltaTxBytes / deltaTime
                    val rxInstantSpeed = deltaRxBytes / deltaTime

                    val txAvgSpeed =
                        if (!txEnabled) {
                            0.0
                        } else {
                            val start = txStartNs.get()
                            val txTime =
                                if (start == 0L) 0.0 else (currentTime - start) / 1_000_000_000.0
                            if (txTime > 0) currentTxBytes / txTime else 0.0
                        }
                    val rxAvgSpeed =
                        if (!rxEnabled) {
                            0.0
                        } else {
                            val start = rxStartNs.get()
                            val rxTime =
                                if (start == 0L) 0.0 else (currentTime - start) / 1_000_000_000.0
                            if (rxTime > 0) currentRxBytes / rxTime else 0.0
                        }

                    progress?.invoke(
                        txInstantSpeed,
                        rxInstantSpeed,
                        txAvgSpeed,
                        rxAvgSpeed,
                        currentTxBytes,
                        currentRxBytes,
                        (totalTime * 1000).toLong()
                    )

                    lastTxBytes = currentTxBytes
                    lastRxBytes = currentRxBytes
                    lastTime = currentTime
                }

                if (totalTime >= testDurationMs / 1000.0) {
                    return@launch
                }
            }
        }

        monitor.join()
        isRunning.set(false)
        sender?.cancel()
        receiver?.cancel()

        if (failed.get()) return@coroutineScope null

        val endTime = System.nanoTime()
        val durationNs = endTime - testStart
        val durationSec = durationNs / 1_000_000_000.0
        if (durationSec <= 0) return@coroutineScope null
        val txBytes = bytesSent.get()
        val rxBytes = bytesReceived.get()

        val txDurationSec =
            if (txEnabled) {
                val start = txStartNs.get()
                if (start == 0L) 0.0 else (endTime - start) / 1_000_000_000.0
            } else {
                0.0
            }
        val rxDurationSec =
            if (rxEnabled) {
                val start = rxStartNs.get()
                if (start == 0L) 0.0 else (endTime - start) / 1_000_000_000.0
            } else {
                0.0
            }
        SpeedTestResult(
            txAvgBps = if (txDurationSec > 0) txBytes.toDouble() / txDurationSec else 0.0,
            rxAvgBps = if (rxDurationSec > 0) rxBytes.toDouble() / rxDurationSec else 0.0,
            txTotalBytes = txBytes,
            rxTotalBytes = rxBytes,
            durationMs = durationNs / 1_000_000
        )
    }
}

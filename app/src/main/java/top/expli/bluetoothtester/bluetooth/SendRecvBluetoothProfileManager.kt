package top.expli.bluetoothtester.bluetooth

import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

data class SpeedTestResult(
    val txAvgBps: Double,
    val rxAvgBps: Double,
    val txTotalBytes: Long,
    val rxTotalBytes: Long,
    val durationMs: Long,
    val txWriteAvgMs: Double? = null,
    val txWriteMaxMs: Double? = null,
    val rxReadAvgMs: Double? = null,
    val rxReadMaxMs: Double? = null,
    val rxReadAvgBytes: Double? = null,
    val txFirstWriteDelayMs: Long? = null,
    val rxFirstByteDelayMs: Long? = null
)

data class SpeedTestDiagnostics(
    val txWriteAvgMs: Double?,
    val txWriteMaxMs: Double?,
    val rxReadAvgMs: Double?,
    val rxReadMaxMs: Double?,
    val rxReadAvgBytes: Double?,
    val txFirstWriteDelayMs: Long?,
    val rxFirstByteDelayMs: Long?
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
        verifyData: Boolean = false,  // 是否验证接收数据的内容
        customPayload: ByteArray? = null,  // 自定义payload，null=使用默认序列
        diagnostics: ((SpeedTestDiagnostics) -> Unit)? = null,
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
        // 使用自定义payload或默认序列
        val payload = customPayload?.let { custom ->
            // 如果自定义payload小于payloadSize，循环填充
            if (custom.size >= payloadSize) {
                custom.copyOf(payloadSize)
            } else {
                ByteArray(payloadSize) { i -> custom[i % custom.size] }
            }
        } ?: ByteArray(payloadSize) { it.toByte() }
        val bytesSent = AtomicLong(0L)
        val bytesReceived = AtomicLong(0L)
        val txStartNs = AtomicLong(0L)
        val rxStartNs = AtomicLong(0L)
        val txWriteOps = AtomicLong(0L)
        val txWriteTotalNs = AtomicLong(0L)
        val txWriteMaxNs = AtomicLong(0L)
        val rxReadOps = AtomicLong(0L)
        val rxReadTotalNs = AtomicLong(0L)
        val rxReadMaxNs = AtomicLong(0L)
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
                    var localOps = 0L
                    var localTotalNs = 0L
                    var localMaxNs = 0L

                    while (isRunning.get() && isActive) {
                        val t0 = System.nanoTime()
                        try {
                            out.write(payload)
                            txStartNs.compareAndSet(0L, System.nanoTime())
                        } catch (_: IOException) {
                            failed.set(true)
                            isRunning.set(false)
                            break
                        }
                        val dt = System.nanoTime() - t0
                        localOps += 1
                        localTotalNs += dt
                        if (dt > localMaxNs) localMaxNs = dt
                        pending += payloadBytes
                        if (pending >= 64 * 1024) {
                            bytesSent.addAndGet(pending)
                            pending = 0L
                            if (localOps > 0) {
                                txWriteOps.addAndGet(localOps)
                                txWriteTotalNs.addAndGet(localTotalNs)
                                while (true) {
                                    val prev = txWriteMaxNs.get()
                                    if (localMaxNs <= prev) break
                                    if (txWriteMaxNs.compareAndSet(prev, localMaxNs)) break
                                }
                                localOps = 0L
                                localTotalNs = 0L
                                localMaxNs = 0L
                            }
                        }
                    }
                    try {
                        out.flush()
                    } catch (_: IOException) {
                    }
                    if (localOps > 0) {
                        txWriteOps.addAndGet(localOps)
                        txWriteTotalNs.addAndGet(localTotalNs)
                        while (true) {
                            val prev = txWriteMaxNs.get()
                            if (localMaxNs <= prev) break
                            if (txWriteMaxNs.compareAndSet(prev, localMaxNs)) break 
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
                    var localOps = 0L
                    var localTotalNs = 0L
                    var localMaxNs = 0L

                    while (isRunning.get() && isActive) {
                        val t0 = System.nanoTime()
                        val read = try {
                            inp.read(buf, 0, buf.size)
                        } catch (_: IOException) {
                            failed.set(true)
                            isRunning.set(false)
                            break
                        }
                        val dt = System.nanoTime() - t0
                        localOps += 1
                        localTotalNs += dt
                        if (dt > localMaxNs) localMaxNs = dt
                        if (read == -1) {
                            failed.set(true)
                            isRunning.set(false)
                            break
                        }
                        if (read > 0) {
                            // 可选的数据验证：检查是否是预期的测速payload模式
                            if (verifyData) {
                                var valid = true
                                for (i in 0 until read) {
                                    // 验证数据是否符合 payload 的模式 (it.toByte())
                                    if (buf[i] != ((bytesReceived.get() + pending + i) % 256).toByte()) {
                                        valid = false
                                        break
                                    }
                                }
                                if (!valid) {
                                    // 收到非测速数据，忽略或标记失败
                                    continue  // 忽略这批数据，不计入统计
                                }
                            }
                            
                            rxStartNs.compareAndSet(0L, System.nanoTime())
                            pending += read.toLong()
                            if (pending >= 64 * 1024) {
                                bytesReceived.addAndGet(pending)
                                pending = 0L
                                if (localOps > 0) {
                                    rxReadOps.addAndGet(localOps)
                                    rxReadTotalNs.addAndGet(localTotalNs)
                                    while (true) {
                                        val prev = rxReadMaxNs.get()
                                        if (localMaxNs <= prev) break
                                        if (rxReadMaxNs.compareAndSet(prev, localMaxNs)) break
                                    }
                                    localOps = 0L
                                    localTotalNs = 0L
                                    localMaxNs = 0L
                                }
                            }
                        }
                    }
                    if (localOps > 0) {
                        rxReadOps.addAndGet(localOps)
                        rxReadTotalNs.addAndGet(localTotalNs)
                        while (true) {
                            val prev = rxReadMaxNs.get()
                            if (localMaxNs <= prev) break
                            if (rxReadMaxNs.compareAndSet(prev, localMaxNs)) break
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

                    diagnostics?.invoke(
                        SpeedTestDiagnostics(
                            txWriteAvgMs =
                                txWriteOps.get().takeIf { it > 0 }?.let { ops ->
                                    (txWriteTotalNs.get().toDouble() / ops) / 1_000_000.0
                                },
                            txWriteMaxMs = txWriteMaxNs.get().takeIf { it > 0 }
                                ?.let { it / 1_000_000.0 },
                            rxReadAvgMs =
                                rxReadOps.get().takeIf { it > 0 }?.let { ops ->
                                    (rxReadTotalNs.get().toDouble() / ops) / 1_000_000.0
                                },
                            rxReadMaxMs = rxReadMaxNs.get().takeIf { it > 0 }
                                ?.let { it / 1_000_000.0 },
                            rxReadAvgBytes =
                                rxReadOps.get().takeIf { it > 0 }?.let { ops ->
                                    currentRxBytes.toDouble() / ops
                                },
                            txFirstWriteDelayMs =
                                txStartNs.get().takeIf { it > 0 }?.let { start ->
                                    (start - testStart) / 1_000_000
                                },
                            rxFirstByteDelayMs =
                                rxStartNs.get().takeIf { it > 0 }?.let { start ->
                                    (start - testStart) / 1_000_000
                                }
                        )
                    )

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
            durationMs = durationNs / 1_000_000,
            txWriteAvgMs =
                txWriteOps.get().takeIf { it > 0 }?.let { ops ->
                    (txWriteTotalNs.get().toDouble() / ops) / 1_000_000.0
                },
            txWriteMaxMs = txWriteMaxNs.get().takeIf { it > 0 }?.let { it / 1_000_000.0 },
            rxReadAvgMs =
                rxReadOps.get().takeIf { it > 0 }?.let { ops ->
                    (rxReadTotalNs.get().toDouble() / ops) / 1_000_000.0
                },
            rxReadMaxMs = rxReadMaxNs.get().takeIf { it > 0 }?.let { it / 1_000_000.0 },
            rxReadAvgBytes =
                rxReadOps.get().takeIf { it > 0 }?.let { ops -> rxBytes.toDouble() / ops },
            txFirstWriteDelayMs =
                txStartNs.get().takeIf { it > 0 }?.let { start -> (start - testStart) / 1_000_000 },
            rxFirstByteDelayMs =
                rxStartNs.get().takeIf { it > 0 }?.let { start -> (start - testStart) / 1_000_000 }
        )
    }

    /**
     * RX-only throughput test. Starts listening immediately, replies are managed by caller.
     * Test ends automatically after receiving [targetBytes], or returns null on failure/timeout.
     */
    open suspend fun speedTestRxUntilBytes(
        targetBytes: Long,
        maxDurationMs: Long = 30_000,
        payloadSize: Int = 4096,
        verifyData: Boolean = false,
        diagnostics: ((SpeedTestDiagnostics) -> Unit)? = null,
        onReceiverReady: (suspend () -> Boolean)? = null,
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
        if (payloadSize <= 0 || targetBytes <= 0 || maxDurationMs <= 0) return@coroutineScope null

        val bytesReceived = AtomicLong(0L)
        val rxStartNs = AtomicLong(0L)
        val rxReadOps = AtomicLong(0L)
        val rxReadTotalNs = AtomicLong(0L)
        val rxReadMaxNs = AtomicLong(0L)
        val failed = AtomicBoolean(false)
        val isRunning = AtomicBoolean(true)
        val testStart = System.nanoTime()
        val endNs = AtomicLong(0L)
        val receiverReady = CompletableDeferred<Unit>()

        val receiver = launch(Dispatchers.IO) {
            val inp = input ?: run {
                failed.set(true)
                isRunning.set(false)
                receiverReady.complete(Unit)
                return@launch
            }

            val bufSize = minOf(maxOf(payloadSize, 32 * 1024), 256 * 1024)
            val buf = ByteArray(bufSize)
            var pending = 0L
            var localOps = 0L
            var localTotalNs = 0L
            var localMaxNs = 0L
            receiverReady.complete(Unit)

            while (isRunning.get() && isActive) {
                val receivedSoFar = bytesReceived.get() + pending
                val remaining = targetBytes - receivedSoFar
                if (remaining <= 0L) break
                val toRead = minOf(buf.size.toLong(), remaining).toInt()

                val t0 = System.nanoTime()
                val read = try {
                    runInterruptible { inp.read(buf, 0, toRead) }
                } catch (_: kotlinx.coroutines.CancellationException) {
                    break
                } catch (_: IOException) {
                    failed.set(true)
                    isRunning.set(false)
                    break
                }
                val dt = System.nanoTime() - t0
                localOps += 1
                localTotalNs += dt
                if (dt > localMaxNs) localMaxNs = dt

                if (read == -1) {
                    failed.set(true)
                    isRunning.set(false)
                    break
                }
                if (read > 0) {
                    if (verifyData) {
                        var valid = true
                        for (i in 0 until read) {
                            if (buf[i] != ((bytesReceived.get() + pending + i) % 256).toByte()) {
                                valid = false
                                break
                            }
                        }
                        if (!valid) {
                            continue
                        }
                    }

                    rxStartNs.compareAndSet(0L, System.nanoTime())
                    pending += read.toLong()

                    if (pending >= 64 * 1024 || bytesReceived.get() + pending >= targetBytes) {
                        bytesReceived.addAndGet(pending)
                        pending = 0L
                        if (localOps > 0) {
                            rxReadOps.addAndGet(localOps)
                            rxReadTotalNs.addAndGet(localTotalNs)
                            while (true) {
                                val prev = rxReadMaxNs.get()
                                if (localMaxNs <= prev) break
                                if (rxReadMaxNs.compareAndSet(prev, localMaxNs)) break
                            }
                            localOps = 0L
                            localTotalNs = 0L
                            localMaxNs = 0L
                        }
                    }

                    if (bytesReceived.get() >= targetBytes) {
                        endNs.compareAndSet(0L, System.nanoTime())
                        isRunning.set(false)
                        break
                    }
                }
            }

            if (localOps > 0) {
                rxReadOps.addAndGet(localOps)
                rxReadTotalNs.addAndGet(localTotalNs)
                while (true) {
                    val prev = rxReadMaxNs.get()
                    if (localMaxNs <= prev) break
                    if (rxReadMaxNs.compareAndSet(prev, localMaxNs)) break
                }
            }
            if (pending > 0) bytesReceived.addAndGet(pending)
        }

        val monitor = launch(Dispatchers.Default) {
            var lastRxBytes = 0L
            var lastTime = System.nanoTime()
            val intervalMs = 500L

            while (isRunning.get() && isActive) {
                delay(intervalMs)

                val currentTime = System.nanoTime()
                val currentRxBytes = bytesReceived.get()
                val deltaTime = (currentTime - lastTime) / 1_000_000_000.0
                val totalTime = (currentTime - testStart) / 1_000_000_000.0

                if (deltaTime > 0) {
                    val deltaRxBytes = currentRxBytes - lastRxBytes
                    val rxInstantSpeed = deltaRxBytes / deltaTime
                    val start = rxStartNs.get()
                    val rxTime = if (start == 0L) 0.0 else (currentTime - start) / 1_000_000_000.0
                    val rxAvgSpeed = if (rxTime > 0) currentRxBytes / rxTime else 0.0

                    diagnostics?.invoke(
                        SpeedTestDiagnostics(
                            txWriteAvgMs = null,
                            txWriteMaxMs = null,
                            rxReadAvgMs =
                                rxReadOps.get().takeIf { it > 0 }?.let { ops ->
                                    (rxReadTotalNs.get().toDouble() / ops) / 1_000_000.0
                                },
                            rxReadMaxMs = rxReadMaxNs.get().takeIf { it > 0 }
                                ?.let { it / 1_000_000.0 },
                            rxReadAvgBytes =
                                rxReadOps.get().takeIf { it > 0 }?.let { ops ->
                                    currentRxBytes.toDouble() / ops
                                },
                            txFirstWriteDelayMs = null,
                            rxFirstByteDelayMs =
                                rxStartNs.get().takeIf { it > 0 }?.let { startNs ->
                                    (startNs - testStart) / 1_000_000
                                }
                        )
                    )

                    progress?.invoke(
                        0.0,
                        rxInstantSpeed,
                        0.0,
                        rxAvgSpeed,
                        0L,
                        currentRxBytes,
                        (totalTime * 1000).toLong()
                    )

                    lastRxBytes = currentRxBytes
                    lastTime = currentTime
                }

                if (totalTime >= maxDurationMs / 1000.0 || bytesReceived.get() >= targetBytes) {
                    endNs.compareAndSet(0L, System.nanoTime())
                    isRunning.set(false)
                    return@launch
                }
            }
        }

        receiverReady.await()
        if (failed.get()) {
            isRunning.set(false)
            receiver.cancel()
            monitor.cancel()
            return@coroutineScope null
        }
        if (onReceiverReady != null) {
            val ok =
                try {
                    onReceiverReady.invoke()
                } catch (_: Throwable) {
                    false
                }
            if (!ok) {
                failed.set(true)
                isRunning.set(false)
            }
        }

        monitor.join()
        isRunning.set(false)
        receiver.cancel()

        if (failed.get()) return@coroutineScope null

        val endTime = endNs.get().takeIf { it > 0 } ?: System.nanoTime()
        val durationNs = endTime - testStart
        val durationSec = durationNs / 1_000_000_000.0
        if (durationSec <= 0) return@coroutineScope null

        val rxBytes = bytesReceived.get()
        if (rxBytes < targetBytes) return@coroutineScope null

        val rxDurationSec = rxStartNs.get().takeIf { it > 0 }?.let { start ->
            (endTime - start) / 1_000_000_000.0
        } ?: 0.0

        SpeedTestResult(
            txAvgBps = 0.0,
            rxAvgBps = if (rxDurationSec > 0) rxBytes.toDouble() / rxDurationSec else 0.0,
            txTotalBytes = 0L,
            rxTotalBytes = rxBytes,
            durationMs = durationNs / 1_000_000,
            txWriteAvgMs = null,
            txWriteMaxMs = null,
            rxReadAvgMs =
                rxReadOps.get().takeIf { it > 0 }?.let { ops ->
                    (rxReadTotalNs.get().toDouble() / ops) / 1_000_000.0
                },
            rxReadMaxMs = rxReadMaxNs.get().takeIf { it > 0 }?.let { it / 1_000_000.0 },
            rxReadAvgBytes =
                rxReadOps.get().takeIf { it > 0 }?.let { ops -> rxBytes.toDouble() / ops },
            txFirstWriteDelayMs = null,
            rxFirstByteDelayMs =
                rxStartNs.get().takeIf { it > 0 }?.let { start -> (start - testStart) / 1_000_000 }
        )
    }
}

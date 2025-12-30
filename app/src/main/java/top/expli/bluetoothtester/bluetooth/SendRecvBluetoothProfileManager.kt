package top.expli.bluetoothtester.bluetooth

import android.bluetooth.BluetoothSocket
import android.content.Context
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

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
        val payload = ByteArray(payloadSize) { it.toByte() }
        val bytesSent = AtomicLong(0L)
        val bytesReceived = AtomicLong(0L)
        var failed = false
        var isRunning = true
        val testStart = System.nanoTime()

        // 1. 发送协程 (只管灌水)
        val sender = launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                if (!send(payload)) {
                    failed = true
                    isRunning = false
                    break
                }
                bytesSent.addAndGet(payload.size.toLong())
            }
        }

        // 2. 接收协程 (只管抽水)
        val receiver = launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                val inp = input ?: run {
                    failed = true
                    isRunning = false
                    break
                }
                val available = try {
                    inp.available()
                } catch (_: IOException) {
                    failed = true
                    isRunning = false
                    break
                }
                if (available <= 0) {
                    delay(5)
                    continue
                }
                val toRead = minOf(payloadSize, available)
                val buf = ByteArray(toRead)
                val read = try {
                    inp.read(buf, 0, toRead)
                } catch (_: IOException) {
                    failed = true
                    isRunning = false
                    break
                }
                if (read == -1) {
                    failed = true
                    isRunning = false
                    break
                }
                if (read > 0) {
                    bytesReceived.addAndGet(read.toLong())
                }
            }
        }

        // 3. 监控与速度计算协程
        val monitor = launch(Dispatchers.Default) {
            var lastTxBytes = 0L
            var lastRxBytes = 0L
            var lastTime = System.nanoTime()
            val intervalMs = 500L // 采样间隔：500ms

            while (isRunning && isActive) {
                delay(intervalMs)

                val currentTime = System.nanoTime()
                val currentTxBytes = bytesSent.get()
                val currentRxBytes = bytesReceived.get()

                // 计算时间差 (秒)
                val deltaTime = (currentTime - lastTime) / 1_000_000_000.0
                val totalTime = (currentTime - testStart) / 1_000_000_000.0

                if (deltaTime > 0) {
                    // --- 瞬时速度计算 ---
                    val deltaTxBytes = currentTxBytes - lastTxBytes
                    val deltaRxBytes = currentRxBytes - lastRxBytes
                    val txInstantSpeed = deltaTxBytes / deltaTime
                    val rxInstantSpeed = deltaRxBytes / deltaTime

                    // --- 总平均速度计算 ---
                    val txAvgSpeed = currentTxBytes / totalTime
                    val rxAvgSpeed = currentRxBytes / totalTime

                    progress?.invoke(
                        txInstantSpeed,
                        rxInstantSpeed,
                        txAvgSpeed,
                        rxAvgSpeed,
                        currentTxBytes,
                        currentRxBytes,
                        (totalTime * 1000).toLong()
                    )

                    // 更新上一轮快照
                    lastTxBytes = currentTxBytes
                    lastRxBytes = currentRxBytes
                    lastTime = currentTime
                }

                // 检查是否达到测试预设时长
                if (totalTime >= testDurationMs / 1000.0) {
                    return@launch
                }
            }
        }

        monitor.join()
        isRunning = false
        sender.cancel()
        receiver.cancel()

        if (failed) return@coroutineScope null

        val durationNs = System.nanoTime() - testStart
        val durationSec = durationNs / 1_000_000_000.0
        if (durationSec <= 0) return@coroutineScope null
        val txBytes = bytesSent.get()
        val rxBytes = bytesReceived.get()
        SpeedTestResult(
            txAvgBps = txBytes.toDouble() / durationSec,
            rxAvgBps = rxBytes.toDouble() / durationSec,
            txTotalBytes = txBytes,
            rxTotalBytes = rxBytes,
            durationMs = durationNs / 1_000_000
        )
    }
}

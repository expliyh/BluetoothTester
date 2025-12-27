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
     * Measures average throughput (bytes per second) by repeatedly sending and receiving payloads.
     * Returns null if any send/receive fails or inputs are invalid. Reports per-iteration and running
     * average throughput via [progress] callback when provided. Data transfer runs on IO, while
     * calculations run on Default to keep them parallel.
     */
    open suspend fun speedTestWithInstantSpeed(
        testDurationMs: Long = 5000,
        payloadSize: Int = 4096,
        // 回调增加 instantSpeedBps: 瞬时速度, avgSpeedBps: 平均速度
        progress: ((instantSpeedBps: Double, avgSpeedBps: Double) -> Unit)? = null
    ): Double? = coroutineScope {
        val payload = ByteArray(payloadSize) { it.toByte() }
        val bytesReceived = AtomicLong(0L)
        var isRunning = true
        val testStart = System.nanoTime()

        // 1. 发送协程 (只管灌水)
        val sender = launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                if (!send(payload)) {
                    isRunning = false
                    break
                }
            }
        }

        // 2. 接收协程 (只管抽水)
        val receiver = launch(Dispatchers.IO) {
            while (isRunning && isActive) {
                val received = receive(payloadSize)
                if (received != null) {
                    bytesReceived.addAndGet(received.size.toLong())
                }
            }
        }

        // 3. 监控与速度计算协程
        val monitor = launch(Dispatchers.Default) {
            var lastBytes = 0L
            var lastTime = System.nanoTime()
            val intervalMs = 500L // 采样间隔：500ms

            while (isRunning && isActive) {
                delay(intervalMs)

                val currentTime = System.nanoTime()
                val currentBytes = bytesReceived.get()

                // 计算时间差 (秒)
                val deltaTime = (currentTime - lastTime) / 1_000_000_000.0
                val totalTime = (currentTime - testStart) / 1_000_000_000.0

                if (deltaTime > 0) {
                    // --- 瞬时速度计算 ---
                    val deltaBytes = currentBytes - lastBytes
                    val instantSpeed = deltaBytes / deltaTime

                    // --- 总平均速度计算 ---
                    val avgSpeed = currentBytes / totalTime

                    // 每次采样都动态返回当前迭代的速度与平均速度
                    progress?.invoke(instantSpeed, avgSpeed)

                    // 更新上一轮快照
                    lastBytes = currentBytes
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

        // 返回最终平均速度
        val finalTotalTime = (System.nanoTime() - testStart) / 1_000_000_000.0
        if (finalTotalTime <= 0) return@coroutineScope null
        bytesReceived.get().toDouble() / finalTotalTime
    }
}

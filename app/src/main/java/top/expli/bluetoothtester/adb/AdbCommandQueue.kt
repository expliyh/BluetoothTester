package top.expli.bluetoothtester.adb

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * ADB 命令串行执行队列。
 * 使用 Mutex 确保多个 ADB 命令按接收顺序依次执行，避免并发冲突。
 */
object AdbCommandQueue {
    private val mutex = Mutex()

    suspend fun <T> execute(block: suspend () -> T): T = mutex.withLock { block() }
}

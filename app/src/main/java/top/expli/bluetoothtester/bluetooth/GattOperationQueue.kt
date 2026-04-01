package top.expli.bluetoothtester.bluetooth

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Sealed interface representing the result of a GATT operation.
 */
sealed interface GattResult<out T> {
    data class Success<T>(val value: T) : GattResult<T>
    data class Error(val status: Int, val message: String) : GattResult<Nothing>
    data class Timeout(val operationName: String) : GattResult<Nothing>
}

/**
 * Serializes GATT operations using a Mutex to ensure only one operation
 * executes at a time. Each operation has a configurable timeout.
 *
 * GATT callbacks bridge to coroutines via CompletableDeferred.
 */
internal class GattOperationQueue {
    private val mutex = Mutex()

    /**
     * Enqueues a GATT operation for serial execution.
     *
     * @param operationName descriptive name used in timeout results
     * @param timeout max wait time in milliseconds (default 5000)
     * @param operation lambda receiving a CompletableDeferred that the GATT callback should complete
     * @return the GattResult produced by the callback, or GattResult.Timeout on timeout
     */
    suspend fun <T> enqueue(
        operationName: String = "unknown",
        timeout: Long = 5000L,
        operation: (CompletableDeferred<GattResult<T>>) -> Unit
    ): GattResult<T> {
        return mutex.withLock {
            val deferred = CompletableDeferred<GattResult<T>>()
            operation(deferred)
            withTimeoutOrNull(timeout) {
                deferred.await()
            } ?: GattResult.Timeout(operationName)
        }
    }
}

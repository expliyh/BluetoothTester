package top.expli.bluetoothtester.adb

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdbCommandQueueTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Test
    fun execute_returnsBlockResult_directly() = runTest(dispatcher) {
        val result = AdbCommandQueue.execute { 42 }
        assertEquals(42, result)
    }

    @Test
    fun execute_serialOrder_preserved() = runTest(dispatcher) {
        val order = mutableListOf<Int>()

        val job1 = launch(dispatcher) {
            AdbCommandQueue.execute {
                order.add(1)
                1
            }
        }
        val job2 = launch(dispatcher) {
            AdbCommandQueue.execute {
                order.add(2)
                2
            }
        }

        job1.join()
        job2.join()

        assertEquals("执行顺序应为 FIFO", listOf(1, 2), order)
    }

    @Test
    fun execute_mutualExclusion_sequentialEvenWhenConcurrent() = runTest(dispatcher) {
        // When multiple coroutines race to acquire the mutex,
        // only one runs at a time, preserving the expected output.
        val results = mutableListOf<Int>()
        val jobs = (1..5).map { n ->
            launch(dispatcher) {
                AdbCommandQueue.execute {
                    results.add(n)
                    n
                }
            }
        }
        jobs.forEach { it.join() }

        assertEquals("5 次执行应全部完成", 5, results.size)
        // Each element appears exactly once
        assertEquals(results.sorted(), results)
    }
}

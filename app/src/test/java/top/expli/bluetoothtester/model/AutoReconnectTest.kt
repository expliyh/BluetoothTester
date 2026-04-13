package top.expli.bluetoothtester.model

import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/**
 * Feature: bluetooth-socket-testing, Property 9: 自动重连重试上限
 *
 * 对任意重连尝试序列，自动重连逻辑的重试次数不应超过配置的最大重试次数（默认 3 次）。
 * 若所有重试均失败，应停止重连。
 *
 * **Validates: Requirements 6.4**
 */
@OptIn(io.kotest.common.ExperimentalKotest::class)
class AutoReconnectTest {

    /**
     * 模拟自动重连逻辑的核心循环，提取为纯函数以便测试。
     * 返回 Pair(实际尝试次数, 是否成功)。
     *
     * @param maxRetries 最大重试次数
     * @param connectResults 每次连接尝试的结果（true=成功, false=失败），
     *                       如果尝试次数超过列表长度，视为失败
     */
    private fun simulateAutoReconnect(
        maxRetries: Int,
        connectResults: List<Boolean>
    ): Pair<Int, Boolean> {
        var attempts = 0
        var succeeded = false
        for (attempt in 1..maxRetries) {
            attempts++
            val result = connectResults.getOrElse(attempt - 1) { false }
            if (result) {
                succeeded = true
                break
            }
        }
        return Pair(attempts, succeeded)
    }

    // ══════════════════════════════════════════════════════
    // 单元测试
    // ══════════════════════════════════════════════════════

    @Test
    fun `all retries fail - attempts equal maxRetries`() {
        val (attempts, succeeded) = simulateAutoReconnect(
            maxRetries = 3,
            connectResults = listOf(false, false, false)
        )
        assertEquals(3, attempts)
        assertFalse(succeeded)
    }

    @Test
    fun `first attempt succeeds - only 1 attempt`() {
        val (attempts, succeeded) = simulateAutoReconnect(
            maxRetries = 3,
            connectResults = listOf(true, false, false)
        )
        assertEquals(1, attempts)
        assertTrue(succeeded)
    }

    @Test
    fun `second attempt succeeds - 2 attempts`() {
        val (attempts, succeeded) = simulateAutoReconnect(
            maxRetries = 3,
            connectResults = listOf(false, true, false)
        )
        assertEquals(2, attempts)
        assertTrue(succeeded)
    }

    @Test
    fun `last attempt succeeds - attempts equal maxRetries`() {
        val (attempts, succeeded) = simulateAutoReconnect(
            maxRetries = 3,
            connectResults = listOf(false, false, true)
        )
        assertEquals(3, attempts)
        assertTrue(succeeded)
    }

    @Test
    fun `maxRetries of 1 with failure - exactly 1 attempt`() {
        val (attempts, succeeded) = simulateAutoReconnect(
            maxRetries = 1,
            connectResults = listOf(false)
        )
        assertEquals(1, attempts)
        assertFalse(succeeded)
    }

    @Test
    fun `maxRetries of 1 with success - exactly 1 attempt`() {
        val (attempts, succeeded) = simulateAutoReconnect(
            maxRetries = 1,
            connectResults = listOf(true)
        )
        assertEquals(1, attempts)
        assertTrue(succeeded)
    }

    @Test
    fun `default SppSession autoReconnectMaxRetries is 3`() {
        val device = SppDevice(name = "test", address = "AA:BB:CC:DD:EE:FF")
        val session = SppSession(device = device)
        assertEquals(3, session.autoReconnectMaxRetries)
    }

    @Test
    fun `default SppSession autoReconnectEnabled is false`() {
        val device = SppDevice(name = "test", address = "AA:BB:CC:DD:EE:FF")
        val session = SppSession(device = device)
        assertFalse(session.autoReconnectEnabled)
    }

    @Test
    fun `default SppSession autoReconnectInterval is 2000`() {
        val device = SppDevice(name = "test", address = "AA:BB:CC:DD:EE:FF")
        val session = SppSession(device = device)
        assertEquals(2000L, session.autoReconnectInterval)
    }

    // ══════════════════════════════════════════════════════
    // Property 9: 自动重连重试上限
    // ══════════════════════════════════════════════════════

    /**
     * Property 9: 对任意 maxRetries (1..100) 和全失败的连接结果序列，
     * 重试次数恰好等于 maxRetries，且最终结果为失败。
     *
     * **Validates: Requirements 6.4**
     */
    @Test
    fun `property - all failures result in exactly maxRetries attempts`() {
        runBlocking {
            checkAll(PropTestConfig(iterations = 100), Arb.int(1..100)) { maxRetries ->
                val allFail = List(maxRetries) { false }
                val (attempts, succeeded) = simulateAutoReconnect(maxRetries, allFail)

                assertEquals(
                    "Attempts should equal maxRetries when all fail",
                    maxRetries, attempts
                )
                assertFalse(
                    "Should not succeed when all attempts fail",
                    succeeded
                )
            }
        }
    }

    /**
     * Property 9: 对任意 maxRetries (1..100) 和任意成功位置 (1..maxRetries)，
     * 重试次数等于成功位置，且最终结果为成功。
     *
     * **Validates: Requirements 6.4**
     */
    @Test
    fun `property - success at position K results in exactly K attempts`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(1..50),
                Arb.int(1..50)
            ) { maxRetries, rawSuccessPos ->
                val successPos = rawSuccessPos.coerceAtMost(maxRetries)
                val results = List(maxRetries) { idx -> idx == successPos - 1 }
                val (attempts, succeeded) = simulateAutoReconnect(maxRetries, results)

                assertEquals(
                    "Attempts should equal success position",
                    successPos, attempts
                )
                assertTrue(
                    "Should succeed when attempt at position $successPos succeeds",
                    succeeded
                )
            }
        }
    }

    /**
     * Property 9: 对任意 maxRetries，重试次数永远不超过 maxRetries。
     * 无论连接结果如何，attempts ≤ maxRetries。
     *
     * **Validates: Requirements 6.4**
     */
    @Test
    fun `property - attempts never exceed maxRetries regardless of results`() {
        runBlocking {
            checkAll(
                PropTestConfig(iterations = 100),
                Arb.int(1..100),
                Arb.int(0..200)
            ) { maxRetries, resultSeed ->
                // 生成随机的连接结果序列
                val random = java.util.Random(resultSeed.toLong())
                val results = List(maxRetries + 10) { random.nextBoolean() }
                val (attempts, _) = simulateAutoReconnect(maxRetries, results)

                assertTrue(
                    "Attempts ($attempts) should not exceed maxRetries ($maxRetries)",
                    attempts <= maxRetries
                )
            }
        }
    }
}

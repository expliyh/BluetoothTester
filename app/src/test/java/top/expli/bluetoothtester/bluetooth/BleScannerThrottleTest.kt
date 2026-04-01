package top.expli.bluetoothtester.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import android.util.SparseArray
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Bug condition exploration test for BLE scan list performance issue.
 *
 * **Validates: Requirements 1.1, 1.4, 2.1, 2.3**
 *
 * Bug Condition: isBugCondition(input) where input.scanState == Scanning
 * AND callbackType == onScanResult AND deviceMap.size >= 1
 * AND timeSinceLastFlush < 300ms
 *
 * This test MUST FAIL on unfixed code — failure confirms the bug exists.
 * On unfixed code, each handleScanResult() call immediately rebuilds the full
 * device list and pushes to _devices StateFlow, resulting in 50 updates
 * instead of the expected ≤2.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BleScannerThrottleTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var bleScanner: BleScanner

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val mockContext = mockk<Context>(relaxed = true)
        bleScanner = BleScanner(mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Simulate 50 rapid handleScanResult() calls within a tight loop (< 100ms).
     *
     * Expected (after fix): _devices StateFlow should be updated at most 2 times
     * (not 50), because the throttle mechanism batches updates.
     *
     * On UNFIXED code: _devices.value is reassigned on every single call,
     * so we expect 50 updates — the assertion will FAIL, confirming the bug.
     */
    @Test
    fun `rapid handleScanResult calls should be throttled to at most 2 updates`() = runTest(testDispatcher) {
        val deviceCount = 50
        val emissions = mutableListOf<List<top.expli.bluetoothtester.model.BleDeviceResult>>()

        // Collect all emissions from _devices StateFlow in background
        val collectJob = launch(UnconfinedTestDispatcher(testScheduler)) {
            bleScanner.devices.toList(emissions)
        }

        // Simulate 50 rapid scan results with unique device addresses
        for (i in 0 until deviceCount) {
            val scanResult = createMockScanResult(
                address = "AA:BB:CC:DD:EE:%02X".format(i),
                name = "Device_$i",
                rssi = -50 - (i % 30)
            )
            bleScanner.handleScanResult(scanResult)
        }

        // Allow any pending coroutines to complete
        advanceUntilIdle()

        // Cancel the collector
        collectJob.cancel()

        // The emissions list includes the initial empty list emission.
        // Subtract 1 to get the number of updates caused by handleScanResult calls.
        val updateCount = emissions.size - 1 // subtract initial empty list emission

        // BUG CONDITION ASSERTION:
        // After fix: throttle should limit updates to at most 2 in a <100ms window
        // On unfixed code: this will be 50 (one per handleScanResult call) → TEST FAILS
        assertTrue(
            "Expected at most 2 StateFlow updates in rapid succession, but got $updateCount. " +
                "This confirms the bug: each handleScanResult() triggers an immediate full list rebuild.",
            updateCount <= 2
        )

        // PRESERVATION ASSERTION:
        // All 50 devices must eventually appear in the final device list
        val finalDevices = bleScanner.devices.value
        assertEquals(
            "All $deviceCount devices should eventually appear in the device list",
            deviceCount,
            finalDevices.size
        )
    }

    /**
     * Creates a mock ScanResult with the given parameters.
     */
    private fun createMockScanResult(
        address: String,
        name: String?,
        rssi: Int
    ): ScanResult {
        val device = mockk<BluetoothDevice> {
            every { this@mockk.address } returns address
            every { this@mockk.name } returns name
        }

        val emptySparseArray = SparseArray<ByteArray>()

        val scanRecord = mockk<ScanRecord> {
            every { deviceName } returns name
            every { serviceUuids } returns emptyList<ParcelUuid>()
            every { manufacturerSpecificData } returns emptySparseArray
            every { txPowerLevel } returns Int.MIN_VALUE
            every { bytes } returns byteArrayOf(0x01, 0x02, 0x03)
        }

        return mockk<ScanResult> {
            every { this@mockk.device } returns device
            every { this@mockk.rssi } returns rssi
            every { this@mockk.scanRecord } returns scanRecord
        }
    }
}

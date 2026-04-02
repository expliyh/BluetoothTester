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
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import top.expli.bluetoothtester.model.BleDeviceResult
import top.expli.bluetoothtester.model.BleScanFilter
import top.expli.bluetoothtester.model.BleScanRecordData
import top.expli.bluetoothtester.model.ClassicDeviceResult
import top.expli.bluetoothtester.model.DeviceType
import top.expli.bluetoothtester.model.ScanViewModel
import top.expli.bluetoothtester.model.UnifiedDeviceResult
import java.util.UUID
import kotlin.random.Random

/**
 * Preservation property tests for BLE scan list performance bugfix.
 *
 * **Validates: Requirements 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7, 3.9, 3.10**
 *
 * These tests capture the CURRENT correct behavior of device discovery,
 * filtering, merging, and hex conversion logic BEFORE the fix is applied.
 * All tests MUST PASS on unfixed code to establish a baseline.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BleScannerPreservationTest {

    private val testDispatcher = UnconfinedTestDispatcher()
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

    // ─── Random data generation helpers ───

    private fun randomAddress(index: Int): String =
        "AA:BB:CC:DD:%02X:%02X".format(index / 256, index % 256)

    private fun randomByteArray(size: Int, seed: Long = Random.nextLong()): ByteArray {
        val rng = Random(seed)
        return ByteArray(size) { rng.nextInt(256).toByte() }
    }

    private fun createBleDeviceEntry(
        address: String,
        name: String? = "Device_$address",
        rssi: Int = -50,
        serviceUuids: List<UUID> = emptyList(),
        manufacturerData: Map<Int, ByteArray> = emptyMap(),
        rawBytes: ByteArray = byteArrayOf(0x01, 0x02, 0x03),
        txPowerLevel: Int? = null,
        lastSeenMs: Long = System.currentTimeMillis()
    ): BleScanner.BleDeviceEntry {
        val device = mockk<BluetoothDevice> {
            every { this@mockk.address } returns address
            every { this@mockk.name } returns name
        }
        return BleScanner.BleDeviceEntry(
            device = device,
            name = name,
            rssi = rssi,
            scanRecord = BleScanRecordData(
                serviceUuids = serviceUuids,
                manufacturerData = manufacturerData,
                txPowerLevel = txPowerLevel,
                rawBytes = rawBytes
            ),
            lastSeenMs = lastSeenMs,
            cachedRawHex = rawBytes.joinToString("") { "%02X".format(it) },
            cachedManufacturerHex = manufacturerData.mapValues { (_, bytes) ->
                bytes.joinToString("") { "%02X".format(it) }
            }
        )
    }

    private fun createMockScanResult(
        address: String,
        name: String?,
        rssi: Int,
        rawBytes: ByteArray = byteArrayOf(0x01, 0x02, 0x03),
        manufacturerData: Map<Int, ByteArray> = emptyMap()
    ): ScanResult {
        val device = mockk<BluetoothDevice> {
            every { this@mockk.address } returns address
            every { this@mockk.name } returns name
        }

        // SparseArray is an Android framework class; mock its iteration methods
        val keys = manufacturerData.keys.toList()
        val values = manufacturerData.values.toList()
        val sparseArray = mockk<SparseArray<ByteArray>> {
            every { size() } returns keys.size
            for (i in keys.indices) {
                every { keyAt(i) } returns keys[i]
                every { valueAt(i) } returns values[i]
            }
        }

        val scanRecord = mockk<ScanRecord> {
            every { deviceName } returns name
            every { serviceUuids } returns emptyList<ParcelUuid>()
            every { manufacturerSpecificData } returns sparseArray
            every { txPowerLevel } returns Int.MIN_VALUE
            every { bytes } returns rawBytes
        }

        return mockk<ScanResult> {
            every { this@mockk.device } returns device
            every { this@mockk.rssi } returns rssi
            every { this@mockk.scanRecord } returns scanRecord
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 1: buildFilteredDeviceList with no filter returns all devices
    // Validates: Requirements 3.1, 3.2
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `buildFilteredDeviceList with no filter returns all devices`() {
        // Populate deviceMap via handleScanResult with varying device counts
        val deviceCounts = listOf(1, 5, 10, 20)

        for (count in deviceCounts) {
            setUp() // fresh scanner
            for (i in 0 until count) {
                val result = createMockScanResult(
                    address = randomAddress(i),
                    name = "Device_$i",
                    rssi = -40 - i
                )
                bleScanner.handleScanResult(result)
            }

            val filteredList = bleScanner.buildFilteredDeviceList()
            assertEquals(
                "With $count devices and no filter, all should be returned",
                count,
                filteredList.size
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 2: buildFilteredDeviceList with name filter correctly filters
    // Validates: Requirements 3.3
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `buildFilteredDeviceList with name filter correctly filters`() {
        // Add devices with various names
        val names = listOf("TestDevice_A", "MyPhone", "TestDevice_B", "Speaker", "TestDevice_C")
        for ((i, name) in names.withIndex()) {
            val result = createMockScanResult(
                address = randomAddress(i),
                name = name,
                rssi = -50
            )
            bleScanner.handleScanResult(result)
        }

        // Set filter with name keyword "Test" via reflection on currentFilter
        val filterField = BleScanner::class.java.getDeclaredField("currentFilter")
        filterField.isAccessible = true
        filterField.set(bleScanner, BleScanFilter(nameKeyword = "Test"))

        val filteredList = bleScanner.buildFilteredDeviceList()
        assertEquals("Only devices with 'Test' in name should pass", 3, filteredList.size)
        assertTrue(
            "All returned devices should contain 'Test' in name",
            filteredList.all { it.name?.contains("Test", ignoreCase = true) == true }
        )
    }

    @Test
    fun `buildFilteredDeviceList with blank name filter returns all devices`() {
        for (i in 0 until 5) {
            val result = createMockScanResult(
                address = randomAddress(i),
                name = "Device_$i",
                rssi = -50
            )
            bleScanner.handleScanResult(result)
        }

        val filterField = BleScanner::class.java.getDeclaredField("currentFilter")
        filterField.isAccessible = true
        filterField.set(bleScanner, BleScanFilter(nameKeyword = "  "))

        val filteredList = bleScanner.buildFilteredDeviceList()
        assertEquals("Blank name filter should return all devices", 5, filteredList.size)
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 3: buildFilteredDeviceList with RSSI threshold correctly filters
    // Validates: Requirements 3.3
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `buildFilteredDeviceList with RSSI threshold correctly filters`() {
        // Add devices with varying RSSI values
        val rssiValues = listOf(-30, -50, -60, -70, -80, -90)
        for ((i, rssi) in rssiValues.withIndex()) {
            val result = createMockScanResult(
                address = randomAddress(i),
                name = "Device_$i",
                rssi = rssi
            )
            bleScanner.handleScanResult(result)
        }

        val filterField = BleScanner::class.java.getDeclaredField("currentFilter")
        filterField.isAccessible = true
        filterField.set(bleScanner, BleScanFilter(rssiThreshold = -70))

        val filteredList = bleScanner.buildFilteredDeviceList()
        // Devices with RSSI >= -70: -30, -50, -60, -70 (4 devices)
        assertEquals("Only devices with RSSI >= -70 should pass", 4, filteredList.size)
        assertTrue(
            "All returned devices should have RSSI >= -70",
            filteredList.all { it.rssi >= -70 }
        )
    }

    @Test
    fun `buildFilteredDeviceList with RSSI threshold - parameterized random data`() {
        val rng = Random(42)
        repeat(10) { trial ->
            setUp() // fresh scanner each trial
            val deviceCount = rng.nextInt(5, 20)
            val threshold = rng.nextInt(-100, -20)
            val rssiValues = List(deviceCount) { rng.nextInt(-100, 0) }

            for ((i, rssi) in rssiValues.withIndex()) {
                val result = createMockScanResult(
                    address = randomAddress(i),
                    name = "Device_$i",
                    rssi = rssi
                )
                bleScanner.handleScanResult(result)
            }

            val filterField = BleScanner::class.java.getDeclaredField("currentFilter")
            filterField.isAccessible = true
            filterField.set(bleScanner, BleScanFilter(rssiThreshold = threshold))

            val filteredList = bleScanner.buildFilteredDeviceList()
            val expectedCount = rssiValues.count { it >= threshold }
            assertEquals(
                "Trial $trial: threshold=$threshold, expected $expectedCount devices",
                expectedCount,
                filteredList.size
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 4: toBleDeviceResult produces correct hex strings
    // Validates: Requirements 3.1, 3.2
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `toBleDeviceResult produces correct hex strings for rawScanRecord`() {
        val rawBytes = byteArrayOf(0x0A, 0xFF.toByte(), 0x00, 0x7F, 0x80.toByte())
        val result = createMockScanResult(
            address = "AA:BB:CC:DD:00:01",
            name = "HexTest",
            rssi = -50,
            rawBytes = rawBytes
        )
        bleScanner.handleScanResult(result)

        val deviceList = bleScanner.buildFilteredDeviceList()
        assertEquals(1, deviceList.size)

        val bleResult = deviceList[0]
        val expectedHex = rawBytes.joinToString("") { "%02X".format(it) }
        assertEquals("rawScanRecord hex should match", expectedHex, bleResult.rawScanRecord)
        assertEquals("0AFF007F80", bleResult.rawScanRecord)
    }

    @Test
    fun `toBleDeviceResult produces correct hex strings for manufacturerData`() {
        val mfgData = mapOf(
            0x004C to byteArrayOf(0x02, 0x15, 0xAA.toByte()),
            0x0059 to byteArrayOf(0xFF.toByte(), 0x00)
        )
        val result = createMockScanResult(
            address = "AA:BB:CC:DD:00:02",
            name = "MfgTest",
            rssi = -60,
            manufacturerData = mfgData
        )
        bleScanner.handleScanResult(result)

        val deviceList = bleScanner.buildFilteredDeviceList()
        assertEquals(1, deviceList.size)

        val bleResult = deviceList[0]
        assertEquals("0215AA", bleResult.manufacturerData[0x004C])
        assertEquals("FF00", bleResult.manufacturerData[0x0059])
    }

    @Test
    fun `toBleDeviceResult hex conversion with empty byte arrays`() {
        val result = createMockScanResult(
            address = "AA:BB:CC:DD:00:03",
            name = "EmptyTest",
            rssi = -50,
            rawBytes = byteArrayOf()
        )
        bleScanner.handleScanResult(result)

        val deviceList = bleScanner.buildFilteredDeviceList()
        assertEquals(1, deviceList.size)
        assertEquals("Empty rawBytes should produce empty hex string", "", deviceList[0].rawScanRecord)
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 5: buildCombinedDevices correctly merges BLE + Classic into Dual
    // Validates: Requirements 3.4, 3.7
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `buildCombinedDevices merges BLE and Classic with same address into Dual`() {
        val app = mockk<android.app.Application>(relaxed = true)
        every { app.applicationContext } returns mockk(relaxed = true)
        val viewModel = ScanViewModel(app)

        val sharedAddress = "AA:BB:CC:DD:00:01"
        val bleOnly = "AA:BB:CC:DD:00:02"
        val classicOnly = "AA:BB:CC:DD:00:03"

        val bleDevices = listOf(
            BleDeviceResult(
                address = sharedAddress, name = "DualDevice", rssi = -50,
                serviceUuids = emptyList(), manufacturerData = emptyMap(),
                txPowerLevel = null, rawScanRecord = "0102", lastSeenTimestamp = 1000L
            ),
            BleDeviceResult(
                address = bleOnly, name = "BleOnly", rssi = -60,
                serviceUuids = emptyList(), manufacturerData = emptyMap(),
                txPowerLevel = null, rawScanRecord = "0304", lastSeenTimestamp = 1000L
            )
        )

        val classicDevices = listOf(
            ClassicDeviceResult(
                address = sharedAddress, name = "DualDevice_Classic",
                majorDeviceClass = 0, minorDeviceClass = 0, deviceType = DeviceType.Classic
            ),
            ClassicDeviceResult(
                address = classicOnly, name = "ClassicOnly",
                majorDeviceClass = 0, minorDeviceClass = 0, deviceType = DeviceType.Classic
            )
        )

        val combined = viewModel.buildCombinedDevices(bleDevices, classicDevices)

        assertEquals("Should have 3 unique devices", 3, combined.size)

        val dualDevice = combined.find { it.address == sharedAddress }
        assertNotNull("Shared address device should exist", dualDevice)
        assertEquals("Shared address should be Dual type", DeviceType.Dual, dualDevice!!.deviceType)
        assertNotNull("Dual device should have bleData", dualDevice.bleData)
        assertNotNull("Dual device should have classicData", dualDevice.classicData)

        val bleOnlyDevice = combined.find { it.address == bleOnly }
        assertNotNull("BLE-only device should exist", bleOnlyDevice)
        assertEquals("BLE-only should be BLE type", DeviceType.BLE, bleOnlyDevice!!.deviceType)

        val classicOnlyDevice = combined.find { it.address == classicOnly }
        assertNotNull("Classic-only device should exist", classicOnlyDevice)
        assertEquals("Classic-only should be Classic type", DeviceType.Classic, classicOnlyDevice!!.deviceType)
    }

    @Test
    fun `buildCombinedDevices preserves BLE name when Classic name is null`() {
        val app = mockk<android.app.Application>(relaxed = true)
        every { app.applicationContext } returns mockk(relaxed = true)
        val viewModel = ScanViewModel(app)

        val address = "AA:BB:CC:DD:00:01"
        val bleDevices = listOf(
            BleDeviceResult(
                address = address, name = "BleName", rssi = -50,
                serviceUuids = emptyList(), manufacturerData = emptyMap(),
                txPowerLevel = null, rawScanRecord = "", lastSeenTimestamp = 1000L
            )
        )
        val classicDevices = listOf(
            ClassicDeviceResult(
                address = address, name = null,
                majorDeviceClass = 0, minorDeviceClass = 0, deviceType = DeviceType.Classic
            )
        )

        val combined = viewModel.buildCombinedDevices(bleDevices, classicDevices)
        assertEquals(1, combined.size)
        assertEquals("BleName", combined[0].name)
        assertEquals(DeviceType.Dual, combined[0].deviceType)
    }

    // ═══════════════════════════════════════════════════════════════
    // Test 6: Hex string conversion consistency (random ByteArrays)
    // Validates: Requirements 3.1, 3.2
    // Property: For all randomly generated ByteArrays, hex string conversion
    // bytes.joinToString("") { "%02X".format(it) } produces consistent results
    // ═══════════════════════════════════════════════════════════════

    @Test
    fun `hex string conversion is consistent across random byte arrays`() {
        val rng = Random(12345)
        repeat(50) { trial ->
            setUp() // fresh scanner
            val byteSize = rng.nextInt(0, 200)
            val rawBytes = randomByteArray(byteSize, seed = rng.nextLong())

            val result = createMockScanResult(
                address = randomAddress(trial),
                name = "RandDevice_$trial",
                rssi = -50,
                rawBytes = rawBytes
            )
            bleScanner.handleScanResult(result)

            val deviceList = bleScanner.buildFilteredDeviceList()
            assertEquals(1, deviceList.size)

            // Verify hex matches direct conversion
            val expectedHex = rawBytes.joinToString("") { "%02X".format(it) }
            assertEquals(
                "Trial $trial: hex conversion should be consistent for ${byteSize}-byte array",
                expectedHex,
                deviceList[0].rawScanRecord
            )
        }
    }

    @Test
    fun `hex string conversion for manufacturer data is consistent across random data`() {
        val rng = Random(67890)
        repeat(20) { trial ->
            setUp() // fresh scanner
            val numEntries = rng.nextInt(1, 5)
            val mfgData = mutableMapOf<Int, ByteArray>()
            val expectedHex = mutableMapOf<Int, String>()

            for (j in 0 until numEntries) {
                val companyId = rng.nextInt(0, 0xFFFF)
                val dataSize = rng.nextInt(1, 50)
                val data = randomByteArray(dataSize, seed = rng.nextLong())
                mfgData[companyId] = data
                expectedHex[companyId] = data.joinToString("") { "%02X".format(it) }
            }

            val result = createMockScanResult(
                address = randomAddress(trial),
                name = "MfgRand_$trial",
                rssi = -50,
                manufacturerData = mfgData
            )
            bleScanner.handleScanResult(result)

            val deviceList = bleScanner.buildFilteredDeviceList()
            assertEquals(1, deviceList.size)

            for ((companyId, hex) in expectedHex) {
                assertEquals(
                    "Trial $trial: manufacturer data hex for company $companyId should match",
                    hex,
                    deviceList[0].manufacturerData[companyId]
                )
            }
        }
    }
}

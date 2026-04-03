package top.expli.bluetoothtester.ui.common

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.expli.bluetoothtester.model.DeviceType

// Feature: gatt-client-address-autofill, Property 7: Within each region, MAC addresses are deduplicated

/**
 * Property test verifying that within each region (bonded devices / scanned devices),
 * there are no duplicate MAC addresses after applying the dedup logic.
 *
 * **Validates: Requirements 8.1**
 *
 * For any list of BondedDeviceItem that may contain duplicate MAC addresses,
 * applying `distinctBy { it.address }` should produce a list with all unique addresses,
 * size <= original, and every address in the result was present in the original.
 */
class MacAddressDedupPropertyTest {

    /** Small pool of MAC addresses to increase collision probability. */
    private val macAddressPool = listOf(
        "AA:BB:CC:DD:EE:01",
        "AA:BB:CC:DD:EE:02",
        "AA:BB:CC:DD:EE:03",
        "AA:BB:CC:DD:EE:04",
        "AA:BB:CC:DD:EE:05"
    )

    private val arbDeviceType: Arb<DeviceType> =
        Arb.element(DeviceType.BLE, DeviceType.Classic, DeviceType.Dual)

    /** Generates a BondedDeviceItem with an address drawn from a small pool. */
    private val arbBondedDeviceItem: Arb<BondedDeviceItem> = arbitrary {
        val name = Arb.string(1..15).bind()
        val address = Arb.element(macAddressPool).bind()
        val deviceType = arbDeviceType.bind()
        BondedDeviceItem(name = name, address = address, deviceType = deviceType)
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `distinctBy address removes all duplicate MAC addresses`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(arbBondedDeviceItem, 0..30)
        ) { devices ->
            // Apply the same dedup logic used by loadBondedDeviceItems
            val deduped = devices.distinctBy { it.address }

            // 1. No duplicate addresses remain
            val addresses = deduped.map { it.address }
            assertEquals(
                "Deduped list should have all unique addresses, but found duplicates: " +
                    addresses.groupBy { it }.filter { it.value.size > 1 }.keys,
                addresses.size,
                addresses.toSet().size
            )

            // 2. Result size <= original list size
            assertTrue(
                "Deduped size (${deduped.size}) should be <= original size (${devices.size})",
                deduped.size <= devices.size
            )

            // 3. Every address in the result was present in the original list
            val originalAddresses = devices.map { it.address }.toSet()
            assertTrue(
                "All deduped addresses should be present in the original list",
                deduped.all { it.address in originalAddresses }
            )
        }
    }
}

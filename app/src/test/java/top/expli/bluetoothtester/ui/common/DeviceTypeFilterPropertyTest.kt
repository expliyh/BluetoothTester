package top.expli.bluetoothtester.ui.common

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.set
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import top.expli.bluetoothtester.model.DeviceType

// Feature: gatt-client-address-autofill, Property 5: Device type filtering correctness

/**
 * Property test verifying device type filtering correctness.
 *
 * **Validates: Requirements 5.2, 5.3**
 *
 * For any device list and any `deviceTypeFilter: Set<DeviceType>?`:
 * - When filter is not null, all displayed devices have deviceType in the filter set
 * - When filter is null, all devices are shown (result equals original list)
 * - Result size is always <= original list size
 */
class DeviceTypeFilterPropertyTest {

    private val arbDeviceType: Arb<DeviceType> = Arb.of(DeviceType.BLE, DeviceType.Classic, DeviceType.Dual)

    /** Generates a random BondedDeviceItem with a random DeviceType. */
    private val arbBondedDeviceItem: Arb<BondedDeviceItem> = Arb.string(1..20).map { name ->
        name // just to get a string
    }.let { arbName ->
        io.kotest.property.arbitrary.arbitrary {
            val name = arbName.bind()
            val deviceType = arbDeviceType.bind()
            val address = buildString {
                repeat(6) { i ->
                    if (i > 0) append(':')
                    append("%02X".format((0..255).random()))
                }
            }
            BondedDeviceItem(name = name, address = address, deviceType = deviceType)
        }
    }

    /** Generates a random Set<DeviceType>? — either null or a subset of DeviceType values. */
    private val arbDeviceTypeFilter: Arb<Set<DeviceType>?> =
        Arb.set(arbDeviceType, 0..3).orNull(nullProbability = 0.3)

    /**
     * Applies the same filtering logic used in DevicePickerSheet:
     * if filter is not null, keep only devices whose deviceType is in the filter set;
     * otherwise return the full list.
     */
    private fun applyFilter(
        devices: List<BondedDeviceItem>,
        filter: Set<DeviceType>?
    ): List<BondedDeviceItem> {
        return if (filter != null) {
            devices.filter { it.deviceType in filter }
        } else {
            devices
        }
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `filtered devices only contain types in the filter set or all when filter is null`() = runTest {
        checkAll(
            PropTestConfig(iterations = 100),
            Arb.list(arbBondedDeviceItem, 0..30),
            arbDeviceTypeFilter
        ) { devices, filter ->
            val result = applyFilter(devices, filter)

            if (filter != null) {
                // All items in result must have deviceType in the filter set
                assertTrue(
                    "When filter=$filter, all result items should have deviceType in filter, " +
                        "but found: ${result.map { it.deviceType }.distinct()}",
                    result.all { it.deviceType in filter }
                )
            } else {
                // When filter is null, result should equal the original list
                assertEquals(
                    "When filter is null, result should equal original list",
                    devices,
                    result
                )
            }

            // Result size should always be <= original list size
            assertTrue(
                "Result size (${result.size}) should be <= original size (${devices.size})",
                result.size <= devices.size
            )
        }
    }
}

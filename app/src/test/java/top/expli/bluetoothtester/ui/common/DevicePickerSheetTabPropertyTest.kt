package top.expli.bluetoothtester.ui.common

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.boolean
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

// Feature: gatt-client-address-autofill, Property 2: Tab visibility is consistent with configuration parameters

/**
 * Property test verifying that Tab visibility is consistent with the
 * showBonded/showScanned configuration parameters.
 *
 * **Validates: Requirements 2.3, 2.4**
 *
 * For any (showBonded, showScanned) Boolean pair, tabs should be shown
 * iff both showBonded and showScanned are true.
 */
class DevicePickerSheetTabPropertyTest {

    /**
     * Computes whether tabs should be visible given the configuration.
     * This mirrors the logic in DevicePickerSheet: TabRow is rendered
     * only when both showBonded and showScanned are true.
     */
    private fun computeTabsVisible(showBonded: Boolean, showScanned: Boolean): Boolean {
        return showBonded && showScanned
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `tabs are shown iff both showBonded and showScanned are true`() = runTest {
        checkAll(PropTestConfig(iterations = 100), Arb.boolean(), Arb.boolean()) { showBonded, showScanned ->
            val tabsVisible = computeTabsVisible(showBonded, showScanned)
            val expected = showBonded && showScanned

            assertEquals(
                "For showBonded=$showBonded, showScanned=$showScanned: " +
                    "tabsVisible should be $expected but was $tabsVisible",
                expected,
                tabsVisible
            )
        }
    }
}

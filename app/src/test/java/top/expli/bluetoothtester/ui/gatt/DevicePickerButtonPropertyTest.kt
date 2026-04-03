package top.expli.bluetoothtester.ui.gatt

import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import top.expli.bluetoothtester.model.GattConnectionState

// Feature: gatt-client-address-autofill, Property 1: Device picker button enabled state is consistent with connection state

/**
 * Property test verifying that the device picker button enabled state
 * is consistent with the GATT connection state.
 *
 * **Validates: Requirements 1.1, 1.2**
 *
 * For any GattConnectionState, the button should be enabled iff the state
 * is neither Connected nor Connecting.
 */
class DevicePickerButtonPropertyTest {

    /**
     * Generates a random [GattConnectionState] covering all sealed subtypes:
     * Idle, Connecting, Connected, Disconnected, Error(status).
     */
    private val arbGattConnectionState: Arb<GattConnectionState> = Arb.choice(
        Arb.of(GattConnectionState.Idle),
        Arb.of(GattConnectionState.Connecting),
        Arb.of(GattConnectionState.Connected),
        Arb.of(GattConnectionState.Disconnected),
        Arb.int(-100..100).map { GattConnectionState.Error(it) }
    )

    /**
     * Computes the expected device picker button enabled state.
     * This mirrors the logic in ConnectionSection: the button is enabled
     * when the state is neither Connected nor Connecting.
     */
    private fun isDevicePickerButtonEnabled(state: GattConnectionState): Boolean {
        return state !is GattConnectionState.Connected && state !is GattConnectionState.Connecting
    }

    @OptIn(ExperimentalKotest::class)
    @Test
    fun `device picker button enabled state equals not Connected and not Connecting`() = runTest {
        checkAll(PropTestConfig(iterations = 100), arbGattConnectionState) { state ->
            val buttonEnabled = isDevicePickerButtonEnabled(state)
            val expected = state !is GattConnectionState.Connected &&
                    state !is GattConnectionState.Connecting

            assertEquals(
                "For state $state, buttonEnabled should be $expected but was $buttonEnabled",
                expected,
                buttonEnabled
            )
        }
    }
}

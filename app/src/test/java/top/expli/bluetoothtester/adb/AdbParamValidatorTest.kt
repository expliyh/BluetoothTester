package top.expli.bluetoothtester.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbParamValidatorTest {

    // ─── Unknown command ───

    @Test
    fun validate_unknownCommand_returnsError() {
        val result = AdbParamValidator.validate("nonexistent.command", emptyMap())
        assertNotNull("未知命令应返回错误", result)
        assertEquals("unknown_command", result!!.error)
    }

    // ─── Missing required params ───

    @Test
    fun validate_sppConnect_missingAddress_returnsError() {
        val result = AdbParamValidator.validate("spp.connect", emptyMap())
        assertNotNull("缺少必填参数应返回错误", result)
        assertEquals("missing_param", result!!.error)
    }

    @Test
    fun validate_sppRegister_missingNameAndAddress_returnsError() {
        val result = AdbParamValidator.validate("spp.register", emptyMap())
        assertNotNull("缺少 name 和 address 应返回错误", result)
        assertEquals("missing_param", result!!.error)
    }

    @Test
    fun validate_sppSend_missingData_returnsError() {
        val result = AdbParamValidator.validate("spp.send", mapOf("address" to "AA:BB:CC:DD:EE:FF"))
        assertNotNull("缺少 data 应返回错误", result)
        assertEquals("missing_param", result!!.error)
    }

    @Test
    fun validate_status_missingModule_returnsError() {
        val result = AdbParamValidator.validate("status", emptyMap())
        assertNotNull("缺少 module 应返回错误", result)
        assertEquals("missing_param", result!!.error)
    }

    // ─── Valid commands (no required params) ───

    @Test
    fun validate_ping_noParams_passes() {
        val result = AdbParamValidator.validate("ping", emptyMap())
        assertNull("ping 无必填参数应通过", result)
    }

    @Test
    fun validate_devices_noParams_passes() {
        val result = AdbParamValidator.validate("devices", emptyMap())
        assertNull("devices 无必填参数应通过", result)
    }

    @Test
    fun validate_scanBleStart_noParams_passes() {
        val result = AdbParamValidator.validate("scan.ble.start", emptyMap())
        assertNull("scan.ble.start 无必填参数应通过", result)
    }

    @Test
    fun validate_validSppConnect_allRequired_passes() {
        val result = AdbParamValidator.validate(
            "spp.connect",
            mapOf("address" to "AA:BB:CC:DD:EE:FF")
        )
        assertNull("spp.connect 提供必填 address 应通过", result)
    }

    @Test
    fun validate_blankParam_treatedAsMissing() {
        val result = AdbParamValidator.validate(
            "spp.connect",
            mapOf("address" to "  ")
        )
        assertNotNull("空白参数应视为缺失", result)
        assertEquals("missing_param", result!!.error)
    }

    // ─── MAC address validation ───

    @Test
    fun validate_invalidMac_returnsError() {
        val result = AdbParamValidator.validate(
            "spp.connect",
            mapOf("address" to "not-a-mac-address")
        )
        assertNotNull("无效 MAC 应返回错误", result)
        assertEquals("invalid_mac", result!!.error)
    }

    @Test
    fun validate_validMac_passes() {
        val result = AdbParamValidator.validate(
            "spp.connect",
            mapOf("address" to "00:11:22:33:44:FF")
        )
        assertNull("有效 MAC 地址应通过", result)
    }

    @Test
    fun validate_macLowercase_passes() {
        val result = AdbParamValidator.validate(
            "spp.connect",
            mapOf("address" to "aa:bb:cc:dd:ee:ff")
        )
        assertNull("小写 MAC 地址应通过", result)
    }

    @Test
    fun validate_macMixedCase_passes() {
        val result = AdbParamValidator.validate(
            "spp.connect",
            mapOf("address" to "Aa:bB:Cc:Dd:Ee:Ff")
        )
        assertNull("混合大小写 MAC 应通过", result)
    }

    // ─── UUID validation ───

    @Test
    fun validate_invalidUuid_returnsError() {
        val result = AdbParamValidator.validate(
            "gatt.read",
            mapOf(
                "address" to "AA:BB:CC:DD:EE:FF",
                "service_uuid" to "not-a-uuid",
                "char_uuid" to "00002a29-0000-1000-8000-00805f9b34fb"
            )
        )
        assertNotNull("无效 UUID 应返回错误", result)
        assertEquals("invalid_uuid", result!!.error)
    }

    @Test
    fun validate_validUuid_passes() {
        val result = AdbParamValidator.validate(
            "gatt.read",
            mapOf(
                "address" to "AA:BB:CC:DD:EE:FF",
                "service_uuid" to "0000180a-0000-1000-8000-00805f9b34fb",
                "char_uuid" to "00002a29-0000-1000-8000-00805f9b34fb"
            )
        )
        assertNull("有效 UUID 应通过", result)
    }

    @Test
    fun validate_uuidNotRequired_whenMissing_passes() {
        val result = AdbParamValidator.validate(
            "spp.register",
            mapOf(
                "name" to "TestDevice",
                "address" to "AA:BB:CC:DD:EE:FF"
            )
        )
        assertNull("uuid 非必填，缺失应通过", result)
    }

    // ─── MTU validation ───

    @Test
    fun validate_mtuValid_passes() {
        val result = AdbParamValidator.validate(
            "gatt.mtu",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "mtu" to "512")
        )
        assertNull("有效 MTU 应通过", result)
    }

    @Test
    fun validate_mtuTooLow_returnsError() {
        val result = AdbParamValidator.validate(
            "gatt.mtu",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "mtu" to "10")
        )
        assertNotNull("MTU 低于 23 应返回错误", result)
        assertEquals("invalid_mtu", result!!.error)
    }

    @Test
    fun validate_mtuTooHigh_returnsError() {
        val result = AdbParamValidator.validate(
            "gatt.mtu",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "mtu" to "600")
        )
        assertNotNull("MTU 高于 517 应返回错误", result)
        assertEquals("invalid_mtu", result!!.error)
    }

    @Test
    fun validate_mtuNotNumeric_returnsError() {
        val result = AdbParamValidator.validate(
            "gatt.mtu",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "mtu" to "abc")
        )
        assertNotNull("MTU 非数字应返回错误", result)
        assertEquals("invalid_mtu", result!!.error)
    }

    // ─── PSM validation ───

    @Test
    fun validate_psmValid_passes() {
        val result = AdbParamValidator.validate(
            "l2cap.connect",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "psm" to "128")
        )
        assertNull("有效 PSM 应通过", result)
    }

    @Test
    fun validate_psmZero_returnsError() {
        val result = AdbParamValidator.validate(
            "l2cap.connect",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "psm" to "0")
        )
        assertNotNull("PSM 为 0 应返回错误", result)
        assertEquals("invalid_psm", result!!.error)
    }

    @Test
    fun validate_psmTooHigh_returnsError() {
        val result = AdbParamValidator.validate(
            "l2cap.connect",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "psm" to "99999")
        )
        assertNotNull("PSM 超高应返回错误", result)
        assertEquals("invalid_psm", result!!.error)
    }

    // ─── Duration validation ───

    @Test
    fun validate_durationValid_passes() {
        val result = AdbParamValidator.validate(
            "spp.speed_test.start",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "duration" to "10")
        )
        assertNull("有效 duration 应通过", result)
    }

    @Test
    fun validate_durationZero_returnsError() {
        val result = AdbParamValidator.validate(
            "spp.speed_test.start",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "duration" to "0")
        )
        assertNotNull("duration 为 0 应返回错误", result)
        assertEquals("invalid_duration", result!!.error)
    }

    @Test
    fun validate_durationNegative_returnsError() {
        val result = AdbParamValidator.validate(
            "spp.speed_test.start",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "duration" to "-5")
        )
        assertNotNull("duration 为负应返回错误", result)
        assertEquals("invalid_duration", result!!.error)
    }

    // ─── Payload size validation ───

    @Test
    fun validate_payloadSizeValid_passes() {
        val result = AdbParamValidator.validate(
            "spp.speed_test.start",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "payload_size" to "1024")
        )
        assertNull("有效 payload_size 应通过", result)
    }

    @Test
    fun validate_payloadSizeZero_returnsError() {
        val result = AdbParamValidator.validate(
            "spp.speed_test.start",
            mapOf("address" to "AA:BB:CC:DD:EE:FF", "payload_size" to "0")
        )
        assertNotNull("payload_size 为 0 应返回错误", result)
        assertEquals("invalid_payload_size", result!!.error)
    }

    // ─── RSSI validation ───

    @Test
    fun validate_rssiValid_passes() {
        val result = AdbParamValidator.validate(
            "scan.ble.start",
            mapOf("rssi" to "-65")
        )
        assertNull("有效 RSSI 应通过", result)
    }

    @Test
    fun validate_rssiTooLow_returnsError() {
        val result = AdbParamValidator.validate(
            "scan.ble.start",
            mapOf("rssi" to "-200")
        )
        assertNotNull("RSSI 低于 -127 应返回错误", result)
        assertEquals("invalid_rssi", result!!.error)
    }

    @Test
    fun validate_rssiPositive_returnsError() {
        val result = AdbParamValidator.validate(
            "scan.ble.start",
            mapOf("rssi" to "5")
        )
        assertNotNull("RSSI 为正数应返回错误", result)
        assertEquals("invalid_rssi", result!!.error)
    }

    // ─── Enable validation ───

    @Test
    fun validate_enableTrue_passes() {
        val result = AdbParamValidator.validate(
            "gatt.notify",
            mapOf(
                "address" to "AA:BB:CC:DD:EE:FF",
                "service_uuid" to "0000180a-0000-1000-8000-00805f9b34fb",
                "char_uuid" to "00002a29-0000-1000-8000-00805f9b34fb",
                "enable" to "true"
            )
        )
        assertNull("enable=true 应通过", result)
    }

    @Test
    fun validate_enableOne_passes() {
        val result = AdbParamValidator.validate(
            "gatt.notify",
            mapOf(
                "address" to "AA:BB:CC:DD:EE:FF",
                "service_uuid" to "0000180a-0000-1000-8000-00805f9b34fb",
                "char_uuid" to "00002a29-0000-1000-8000-00805f9b34fb",
                "enable" to "1"
            )
        )
        assertNull("enable=1 应通过", result)
    }

    @Test
    fun validate_enableInvalid_returnsError() {
        val result = AdbParamValidator.validate(
            "gatt.notify",
            mapOf(
                "address" to "AA:BB:CC:DD:EE:FF",
                "service_uuid" to "0000180a-0000-1000-8000-00805f9b34fb",
                "char_uuid" to "00002a29-0000-1000-8000-00805f9b34fb",
                "enable" to "yes"
            )
        )
        assertNotNull("enable=yes 应返回错误", result)
        assertEquals("invalid_enable", result!!.error)
    }

    // ─── Commands with no required params ───

    @Test
    fun validate_l2capListen_passes() {
        val result = AdbParamValidator.validate("l2cap.listen", emptyMap())
        assertNull("l2cap.listen 无必填参数应通过", result)
    }

    @Test
    fun validate_advertiseStart_passes() {
        val result = AdbParamValidator.validate("advertise.start", emptyMap())
        assertNull("advertise.start 无必填参数应通过", result)
    }

    @Test
    fun validate_scanResults_passes() {
        val result = AdbParamValidator.validate("scan.results", mapOf("type" to "ble"))
        assertNull("scan.results 无必填参数应通过", result)
    }
}

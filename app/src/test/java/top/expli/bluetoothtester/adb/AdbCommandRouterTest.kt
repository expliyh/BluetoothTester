package top.expli.bluetoothtester.adb

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import top.expli.bluetoothtester.model.AdbResponse

@OptIn(ExperimentalCoroutinesApi::class)
class AdbCommandRouterTest {

    @After
    fun tearDown() {
        AdbCommandRouter.handler = null
    }

    // ─── Ping ───

    @Test
    fun route_ping_returnsPong() = runTest {
        val response = AdbCommandRouter.route("ping", emptyMap())
        assertTrue("ping 应返回 success", response.success)
        assertEquals("pong", response.message)
        val data = response.data as? JsonObject
        assertEquals(true, data?.get("pong")?.let {
            (it as? JsonPrimitive)?.content?.toBoolean()
        })
        assertNotNull("ping 响应应包含 timestamp", data?.get("timestamp"))
    }

    // ─── Unknown command ───

    @Test
    fun route_unknownCommand_returnsError() = runTest {
        val response = AdbCommandRouter.route("nonexistent", emptyMap())
        assertFalse("未知命令 success 应为 false", response.success)
        assertEquals("unknown_command", response.error)
    }

    // ─── Handler not ready (no handler registered) ───

    @Test
    fun route_sppConnect_noHandler_returnsError() = runTest {
        val response = AdbCommandRouter.route("spp.connect", mapOf("address" to "AA:BB:CC:DD:EE:FF"))
        assertFalse("无 handler 时 success 应为 false", response.success)
        assertEquals("handler_not_ready", response.error)
    }

    @Test
    fun route_sppRegister_noHandler_returnsError() = runTest {
        val response = AdbCommandRouter.route("spp.register", mapOf("name" to "Test", "address" to "AA:BB:CC:DD:EE:FF"))
        assertFalse("无 handler 时 success 应为 false", response.success)
        assertEquals("handler_not_ready", response.error)
    }

    @Test
    fun route_status_noHandler_returnsError() = runTest {
        val response = AdbCommandRouter.route("status", mapOf("module" to "spp"))
        assertFalse("无 handler 时 success 应为 false", response.success)
        assertEquals("handler_not_ready", response.error)
    }

    // ─── SPP commands delegate to handler ───

    @Test
    fun route_sppConnect_withHandler_delegatesToHandler() = runTest {
        val expected = AdbResponse(success = true, data = null, message = "connected from handler")
        AdbCommandRouter.handler = FakeHandler(connectResponse = expected)

        val response = AdbCommandRouter.route("spp.connect", mapOf("address" to "AA:BB:CC:DD:EE:FF"))
        assertEquals(expected, response)
    }

    @Test
    fun route_sppDisconnect_withHandler_delegatesToHandler() = runTest {
        val expected = AdbResponse(success = true)
        AdbCommandRouter.handler = FakeHandler(disconnectResponse = expected)

        val response = AdbCommandRouter.route("spp.disconnect", mapOf("address" to "AA:BB:CC:DD:EE:FF"))
        assertEquals(expected, response)
    }

    @Test
    fun route_sppSend_withHandler_delegatesToHandler() = runTest {
        val expected = AdbResponse(success = true)
        AdbCommandRouter.handler = FakeHandler(sendResponse = expected)

        val response = AdbCommandRouter.route("spp.send", mapOf("address" to "AA:BB:CC:DD:EE:FF", "data" to "hello"))
        assertEquals(expected, response)
    }

    @Test
    fun route_sppSpeedTestStart_withHandler_delegatesToHandler() = runTest {
        val expected = AdbResponse(success = true)
        AdbCommandRouter.handler = FakeHandler(speedTestStartResponse = expected)

        val response = AdbCommandRouter.route("spp.speed_test.start", mapOf("address" to "AA:BB:CC:DD:EE:FF"))
        assertEquals(expected, response)
    }

    @Test
    fun route_sppSpeedTestStop_withHandler_delegatesToHandler() = runTest {
        val expected = AdbResponse(success = true)
        AdbCommandRouter.handler = FakeHandler(speedTestStopResponse = expected)

        val response = AdbCommandRouter.route("spp.speed_test.stop", mapOf("address" to "AA:BB:CC:DD:EE:FF"))
        assertEquals(expected, response)
    }

    // ─── Status / Devices / ChatClear delegate to handler ───

    @Test
    fun route_devices_withHandler_delegatesToHandler() = runTest {
        val expected = AdbResponse(success = true, message = "3 devices")
        AdbCommandRouter.handler = FakeHandler(devicesResponse = expected)

        val response = AdbCommandRouter.route("devices", emptyMap())
        assertEquals(expected, response)
    }

    @Test
    fun route_chatClear_withHandler_delegatesToHandler() = runTest {
        val expected = AdbResponse(success = true)
        AdbCommandRouter.handler = FakeHandler(chatClearResponse = expected)

        val response = AdbCommandRouter.route("chat.clear", mapOf("address" to "AA:BB:CC:DD:EE:FF"))
        assertEquals(expected, response)
    }

    // ─── Stub commands (GATT) ───

    @Test
    fun route_gattConnect_returnsStub() = runTest {
        val response = AdbCommandRouter.route("gatt.connect", mapOf("address" to "AA:BB:CC:DD:EE:FF"))
        assertTrue("stub 命令 success 应为 true", response.success)
        assertEquals("GATT 连接 - 命令已接收 (stub)", response.message)
        val data = response.data as? JsonObject
        assertEquals(true, data?.get("stub")?.let {
            (it as? JsonPrimitive)?.content?.toBoolean()
        })
        assertEquals("AA:BB:CC:DD:EE:FF", data?.get("address")?.let {
            (it as? JsonPrimitive)?.content
        })
    }

    @Test
    fun route_gattRead_returnsStubWithParams() = runTest {
        val response = AdbCommandRouter.route("gatt.read", mapOf(
            "address" to "AA:BB:CC:DD:EE:FF",
            "service_uuid" to "0000180a-0000-1000-8000-00805f9b34fb",
            "char_uuid" to "00002a29-0000-1000-8000-00805f9b34fb"
        ))
        assertTrue("stub 命令 success 应为 true", response.success)
        assertTrue(response.message!!.contains("GATT 读取特征值"))
        val data = response.data as? JsonObject
        assertEquals(true, data?.get("stub")?.let {
            (it as? JsonPrimitive)?.content?.toBoolean()
        })
    }

    @Test
    fun route_gattWrite_returnsStub() = runTest {
        val response = AdbCommandRouter.route("gatt.write", mapOf("address" to "AA:BB:CC:DD:EE:FF",
            "service_uuid" to "0000180a-0000-1000-8000-00805f9b34fb",
            "char_uuid" to "00002a29-0000-1000-8000-00805f9b34fb",
            "data" to "hello"))
        assertTrue(response.success)
        assertTrue(response.message!!.contains("GATT 写入特征值"))
    }

    @Test
    fun route_gattNotify_returnsStub() = runTest {
        val response = AdbCommandRouter.route("gatt.notify", mapOf(
            "address" to "AA:BB:CC:DD:EE:FF",
            "service_uuid" to "0000180a-0000-1000-8000-00805f9b34fb",
            "char_uuid" to "00002a29-0000-1000-8000-00805f9b34fb",
            "enable" to "true"))
        assertTrue(response.success)
        assertTrue(response.message!!.contains("GATT 通知设置"))
    }

    @Test
    fun route_gattMtu_returnsStub() = runTest {
        val response = AdbCommandRouter.route("gatt.mtu", mapOf("address" to "AA:BB:CC:DD:EE:FF", "mtu" to "512"))
        assertTrue(response.success)
        assertTrue(response.message!!.contains("GATT MTU 协商"))
    }

    // ─── Stub commands (L2CAP) ───

    @Test
    fun route_l2capConnect_returnsStub() = runTest {
        val response = AdbCommandRouter.route("l2cap.connect", mapOf("address" to "AA:BB:CC:DD:EE:FF", "psm" to "128"))
        assertTrue(response.success)
        assertTrue(response.message!!.contains("L2CAP 连接"))
    }

    @Test
    fun route_l2capListen_returnsStub() = runTest {
        val response = AdbCommandRouter.route("l2cap.listen", emptyMap())
        assertTrue(response.success)
        assertTrue(response.message!!.contains("L2CAP 监听"))
    }

    // ─── Stub commands (Scan/Advertise) ───

    @Test
    fun route_scanBleStart_returnsStub() = runTest {
        val response = AdbCommandRouter.route("scan.ble.start", emptyMap())
        assertTrue(response.success)
        assertTrue(response.message!!.contains("BLE 扫描启动"))
    }

    @Test
    fun route_advertiseStop_returnsStub() = runTest {
        val response = AdbCommandRouter.route("advertise.stop", emptyMap())
        assertTrue(response.success)
        assertTrue(response.message!!.contains("BLE 广播停止"))
    }

    @Test
    fun route_scanResults_returnsStub() = runTest {
        val response = AdbCommandRouter.route("scan.results", mapOf("type" to "ble"))
        assertTrue(response.success)
        assertTrue(response.message!!.contains("扫描结果查询"))
    }

    // ─── parseRequest ───

    @Test
    fun parseRequest_validJson_returnsRequest() {
        val json = """{"command":"ping","params":{}}"""
        val request = AdbCommandRouter.parseRequest(json)
        assertNotNull("有效 JSON 应返回请求对象", request)
        assertEquals("ping", request!!.command)
        assertTrue(request.params.isEmpty())
    }

    @Test
    fun parseRequest_withParams_parsesCorrectly() {
        val json = """{"command":"spp.connect","params":{"address":"AA:BB:CC:DD:EE:FF"}}"""
        val request = AdbCommandRouter.parseRequest(json)
        assertNotNull(request)
        assertEquals("spp.connect", request!!.command)
        assertEquals("AA:BB:CC:DD:EE:FF", request.params["address"])
    }

    @Test
    fun parseRequest_invalidJson_returnsNull() {
        val result = AdbCommandRouter.parseRequest("not json at all")
        assertNull("无效 JSON 应返回 null", result)
    }

    @Test
    fun parseRequest_missingCommandField_returnsNull() {
        // command is required (non-nullable String, no default) — deserialization throws
        val json = """{"params":{"key":"value"}}"""
        val request = AdbCommandRouter.parseRequest(json)
        assertNull("缺少必填 command 字段应返回 null", request)
    }

    @Test
    fun parseRequest_emptyJson_returnsNull() {
        val result = AdbCommandRouter.parseRequest("")
        assertNull("空字符串应返回 null", result)
    }

    // ─── toJson ───

    @Test
    fun toJson_successResponse_serializesCorrectly() {
        val response = AdbResponse(success = true, message = "ok")
        val json = AdbCommandRouter.toJson(response)
        assertTrue("JSON 应包含 success", json.contains("\"success\":true"))
        assertTrue("JSON 应包含 message", json.contains("\"ok\""))
    }

    @Test
    fun toJson_errorResponse_serializesCorrectly() {
        val response = AdbResponse(success = false, error = "test_error", message = "fail")
        val json = AdbCommandRouter.toJson(response)
        assertTrue(json.contains("\"success\":false"))
        assertTrue(json.contains("\"test_error\""))
    }

    // ─── Fake handler for testing delegation ───

    private class FakeHandler(
        private val connectResponse: AdbResponse? = null,
        private val disconnectResponse: AdbResponse? = null,
        private val sendResponse: AdbResponse? = null,
        private val speedTestStartResponse: AdbResponse? = null,
        private val speedTestStopResponse: AdbResponse? = null,
        private val devicesResponse: AdbResponse? = null,
        private val chatClearResponse: AdbResponse? = null,
    ) : AdbCommandHandler {

        override suspend fun handleSppRegister(params: Map<String, String>) =
            AdbResponse(success = true, message = "registered")

        override suspend fun handleSppConnect(params: Map<String, String>) =
            connectResponse ?: AdbResponse(success = true)

        override suspend fun handleSppDisconnect(params: Map<String, String>) =
            disconnectResponse ?: AdbResponse(success = true)

        override suspend fun handleSppSend(params: Map<String, String>) =
            sendResponse ?: AdbResponse(success = true)

        override suspend fun handleSppSpeedTestStart(params: Map<String, String>) =
            speedTestStartResponse ?: AdbResponse(success = true)

        override suspend fun handleSppSpeedTestStop(params: Map<String, String>) =
            speedTestStopResponse ?: AdbResponse(success = true)

        override suspend fun handleStatus(params: Map<String, String>) =
            AdbResponse(success = true, message = "status ok")

        override suspend fun handleDevices() =
            devicesResponse ?: AdbResponse(success = true)

        override suspend fun handleChatClear(params: Map<String, String>) =
            chatClearResponse ?: AdbResponse(success = true)
    }
}

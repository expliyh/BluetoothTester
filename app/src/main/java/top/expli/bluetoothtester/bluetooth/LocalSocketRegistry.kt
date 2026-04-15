package top.expli.bluetoothtester.bluetooth

import java.util.concurrent.ConcurrentHashMap

/**
 * LocalSocket 回环模式的全局注册表。
 * Server 端 register 时注册 name，Client 端 connect 时通过 UUID 查找。
 * name 格式: "bt_loopback_${uuid}"
 */
object LocalSocketRegistry {
    // uuid -> LocalServerSocket name
    private val registry = ConcurrentHashMap<String, String>()
    private val counter = java.util.concurrent.atomic.AtomicLong(0)

    fun register(uuid: String): String {
        val name = "bt_loopback_${uuid}_${counter.incrementAndGet()}"
        registry[uuid] = name
        return name
    }

    fun find(uuid: String): String? = registry[uuid]

    fun unregister(uuid: String) {
        registry.remove(uuid)
    }

    fun clear() {
        registry.clear()
    }
}

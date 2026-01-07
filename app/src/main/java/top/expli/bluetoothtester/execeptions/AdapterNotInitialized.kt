package top.expli.bluetoothtester.execeptions

// 适配器未初始化异常：在蓝牙适配器或管理器未就绪时抛出
// 提供常用构造方法，便于传入消息与根因
@Suppress("unused")
class AdapterNotInitialized : Exception {
    constructor() : super("Bluetooth adapter is not initialized.")
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
    constructor(cause: Throwable) : super(cause)
}
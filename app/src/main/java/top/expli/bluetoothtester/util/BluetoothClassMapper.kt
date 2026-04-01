package top.expli.bluetoothtester.util

import android.bluetooth.BluetoothClass

/**
 * 将 BluetoothClass 整数常量映射为中文可读描述。
 */
object BluetoothClassMapper {

    fun majorClassName(majorClass: Int): String = when (majorClass) {
        BluetoothClass.Device.Major.COMPUTER -> "计算机"
        BluetoothClass.Device.Major.PHONE -> "手机"
        BluetoothClass.Device.Major.AUDIO_VIDEO -> "音频/视频"
        BluetoothClass.Device.Major.PERIPHERAL -> "外设"
        BluetoothClass.Device.Major.IMAGING -> "图像设备"
        BluetoothClass.Device.Major.WEARABLE -> "可穿戴设备"
        BluetoothClass.Device.Major.TOY -> "玩具"
        BluetoothClass.Device.Major.HEALTH -> "健康设备"
        BluetoothClass.Device.Major.NETWORKING -> "网络设备"
        BluetoothClass.Device.Major.UNCATEGORIZED -> "未分类"
        else -> "未知 (0x${majorClass.toString(16)})"
    }

    fun minorClassName(deviceClass: BluetoothClass): String {
        val dc = deviceClass.deviceClass
        return when {
            // 计算机
            dc == BluetoothClass.Device.COMPUTER_DESKTOP -> "台式电脑"
            dc == BluetoothClass.Device.COMPUTER_SERVER -> "服务器"
            dc == BluetoothClass.Device.COMPUTER_LAPTOP -> "笔记本电脑"
            dc == BluetoothClass.Device.COMPUTER_HANDHELD_PC_PDA -> "掌上电脑"
            dc == BluetoothClass.Device.COMPUTER_PALM_SIZE_PC_PDA -> "PDA"
            dc == BluetoothClass.Device.COMPUTER_WEARABLE -> "可穿戴计算机"
            dc == BluetoothClass.Device.COMPUTER_UNCATEGORIZED -> "未分类计算机"
            // 手机
            dc == BluetoothClass.Device.PHONE_CELLULAR -> "手机"
            dc == BluetoothClass.Device.PHONE_CORDLESS -> "无绳电话"
            dc == BluetoothClass.Device.PHONE_SMART -> "智能手机"
            dc == BluetoothClass.Device.PHONE_MODEM_OR_GATEWAY -> "调制解调器"
            dc == BluetoothClass.Device.PHONE_ISDN -> "ISDN"
            dc == BluetoothClass.Device.PHONE_UNCATEGORIZED -> "未分类手机"
            // 音频/视频
            dc == BluetoothClass.Device.AUDIO_VIDEO_HEADPHONES -> "耳机"
            dc == BluetoothClass.Device.AUDIO_VIDEO_LOUDSPEAKER -> "扬声器"
            dc == BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE -> "麦克风"
            dc == BluetoothClass.Device.AUDIO_VIDEO_CAR_AUDIO -> "车载音频"
            dc == BluetoothClass.Device.AUDIO_VIDEO_HIFI_AUDIO -> "HiFi 音频"
            dc == BluetoothClass.Device.AUDIO_VIDEO_HANDSFREE -> "免提设备"
            dc == BluetoothClass.Device.AUDIO_VIDEO_PORTABLE_AUDIO -> "便携音频"
            dc == BluetoothClass.Device.AUDIO_VIDEO_SET_TOP_BOX -> "机顶盒"
            dc == BluetoothClass.Device.AUDIO_VIDEO_VCR -> "录像机"
            dc == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA -> "摄像机"
            dc == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_MONITOR -> "显示器"
            dc == BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER -> "摄录一体机"
            dc == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CONFERENCING -> "视频会议"
            dc == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_GAMING_TOY -> "游戏玩具"
            dc == BluetoothClass.Device.AUDIO_VIDEO_VIDEO_DISPLAY_AND_LOUDSPEAKER -> "显示器+扬声器"
            dc == BluetoothClass.Device.AUDIO_VIDEO_WEARABLE_HEADSET -> "可穿戴耳机"
            dc == BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED -> "未分类音频/视频"
            // 外设
            dc == BluetoothClass.Device.Major.PERIPHERAL -> "未分类外设"
            // 玩具
            dc == BluetoothClass.Device.TOY_ROBOT -> "机器人"
            dc == BluetoothClass.Device.TOY_VEHICLE -> "遥控车"
            dc == BluetoothClass.Device.TOY_DOLL_ACTION_FIGURE -> "玩偶"
            dc == BluetoothClass.Device.TOY_CONTROLLER -> "控制器"
            dc == BluetoothClass.Device.TOY_GAME -> "游戏"
            dc == BluetoothClass.Device.TOY_UNCATEGORIZED -> "未分类玩具"
            // 健康
            dc == BluetoothClass.Device.HEALTH_BLOOD_PRESSURE -> "血压计"
            dc == BluetoothClass.Device.HEALTH_THERMOMETER -> "体温计"
            dc == BluetoothClass.Device.HEALTH_WEIGHING -> "体重秤"
            dc == BluetoothClass.Device.HEALTH_GLUCOSE -> "血糖仪"
            dc == BluetoothClass.Device.HEALTH_PULSE_OXIMETER -> "脉搏血氧仪"
            dc == BluetoothClass.Device.HEALTH_PULSE_RATE -> "心率计"
            dc == BluetoothClass.Device.HEALTH_DATA_DISPLAY -> "健康数据显示"
            dc == BluetoothClass.Device.HEALTH_UNCATEGORIZED -> "未分类健康设备"
            // 可穿戴
            dc == BluetoothClass.Device.WEARABLE_WRIST_WATCH -> "手表"
            dc == BluetoothClass.Device.WEARABLE_PAGER -> "寻呼机"
            dc == BluetoothClass.Device.WEARABLE_JACKET -> "夹克"
            dc == BluetoothClass.Device.WEARABLE_HELMET -> "头盔"
            dc == BluetoothClass.Device.WEARABLE_GLASSES -> "眼镜"
            dc == BluetoothClass.Device.WEARABLE_UNCATEGORIZED -> "未分类可穿戴"
            else -> "未知"
        }
    }
}

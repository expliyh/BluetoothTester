package top.expli.bluetoothtester.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemePreset(val label: String, val seedColor: Color) {
    Default("默认", Color(0xFF1976D2)),
    Purple("紫色", Color(0xFF6750A4)),
    Green("绿色", Color(0xFF388E3C)),
    Red("红色", Color(0xFFD32F2F)),
    Orange("橙色", Color(0xFFF57C00)),
    Pink("粉色", Color(0xFFC2185B)),
    Yellow("黄色", Color(0xFFFBC02D)),
    Teal("青色", Color(0xFF00796B));
}

# BluetoothTester

一个现代化的 Android 蓝牙测试工具，采用 Jetpack Compose 构建，使用 Material Design 3 设计规范。

## ✨ 特性

- 🎨 **扁平化设计** - 简洁现代的 UI 界面，符合 Material Design 3 规范
- 🔄 **流畅动画** - 优雅的页面切换动画效果（滑动 + 淡入淡出）
- 📱 **响应式布局** - 适配不同屏幕尺寸
- 🧭 **导航系统** - 使用 Navigation Compose 实现类型安全的导航

## 📋 功能模块

### 已实现

- ✅ 主菜单界面
- ✅ 设置界面
    - 通知开关
    - 自动重连开关
    - 主题选择（占位）
    - 权限管理（占位）
    - 关于应用（占位）
- ✅ 导航系统
- ✅ 页面切换动画

### 开发中

- 🚧 扫描设备
- 🚧 已配对设备
- 🚧 BLE 扫描器
- 🚧 经典蓝牙测试

## 🛠️ 技术栈

- **Kotlin** - 编程语言
- **Jetpack Compose** - UI 框架
- **Material Design 3** - 设计规范
- **Navigation Compose** - 导航组件
- **Kotlin Serialization** - 路由序列化

## 📦 构建

```bash
# 构建 Debug 版本
.\gradlew.bat assembleDebug

# 构建 Release 版本
.\gradlew.bat assembleRelease
```

## 📱 最低要求

- Android API 33 (Android 13.0)
- 目标 SDK: Android API 36

## 📄 许可证

AGPL-3.0



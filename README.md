# BluetoothTester

一个现代化的 Android 蓝牙测试工具，采用 Jetpack Compose 构建，使用 Material Design 3 设计规范。

## 📦 构建

```bash
# 构建 Debug 版本
.\gradlew.bat assembleDebug

# 构建 Release 版本
.\gradlew.bat assembleRelease
```

## 🛠️ 环境初始化与维护（Linux/macOS）

> 推荐 Java 25，并确保 `java -version` 输出为 25.x。
> `init-android-env.sh` 会根据系统自动选择 Linux/macOS 对应的 Android Command-line Tools 安装包。

```bash
# 1) 初始化 Android SDK + 写入 local.properties
./scripts/init-android-env.sh

# 2) 维护环境（更新 SDK 元数据 + 校验必需组件 + Gradle 健康检查）
./scripts/maintain-android-env.sh
```

## 📱 最低要求

- Android API 33 (Android 13.0)
- 目标 SDK: Android API 36

## 📄 许可证

AGPL-3.0



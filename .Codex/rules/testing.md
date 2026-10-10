# 测试规则

- 领域逻辑优先写 `domain/src/test` 的纯 Kotlin 测试。
- Room 行为、迁移和备份导入使用 Android/instrumentation 测试覆盖真实数据库边界。
- Compose 测试验证用户可见路径和语义标签，不重复测试纯领域算法。
- 执行 `./gradlew test`；涉及 Android 时再执行对应 `connectedDebugAndroidTest` 或 CI 等价任务。
- 报告测试结果时区分代码失败、设备/SDK/模拟器限制和构建工具链限制。

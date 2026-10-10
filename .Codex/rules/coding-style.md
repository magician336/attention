# Kotlin 与 Gradle 规则

- 使用 Kotlin 风格和显式、可读的领域命令；避免把 Android 类型泄漏到 `:domain`。
- 依赖通过 `gradle/libs.versions.toml` 管理，不在模块脚本中散落版本号。
- 共享规则放在领域层，Compose 层只负责呈现状态和发送用户意图。
- Room entity/DAO 变更必须同步 schema、迁移和对应测试。
- 协程作用域由生命周期或明确的 service scope 管理，避免全局作用域。

# 贡献指南

Attention 当前是 v0.1.0 早期预览项目。欢迎围绕可复现的问题、领域行为和 Android 适配提出贡献。

## 提交 Issue

请尽量包含：

- 使用场景和复现步骤
- 预期行为与实际行为
- Android 版本、设备或模拟器信息
- 相关日志或最小复现说明

不要上传真实时间记录、JSON 备份或其他个人敏感数据。

## 提交 Pull Request

1. 先通过 Issue 讨论较大的行为变化。
2. 保持领域术语与 [`CONTEXT.md`](CONTEXT.md) 一致。
3. 在 PR 描述中说明修改内容、影响范围和验证命令。
4. 如果改变备份、数据库或迁移行为，请明确说明兼容性影响。

## 本地验证

```bash
./gradlew test lintDebug assembleDebug
```

项目采用 Apache-2.0 协议。提交代码即表示你同意按该协议授权贡献内容。

---

## English

Attention is an early v0.1.0 preview. Please open an Issue before substantial behavioral changes, keep terminology aligned with [`CONTEXT.md`](CONTEXT.md), and describe the affected behavior and validation commands in your Pull Request.

Do not upload real time records, JSON backups, or other sensitive personal data. Contributions are released under Apache-2.0.

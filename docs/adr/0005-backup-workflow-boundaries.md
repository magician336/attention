---
status: accepted
date: 2026-10-14
---

# 备份 workflow、状态提交与 SAF 的职责边界

## 决策

备份恢复沿四个边界协作：

- `AttentionBackupCodec` 是 version 1 协议的唯一权威。它负责 JSON 编解码、跨实体校验、稳定 ID 合并、里程碑 `instanceKey` 去重和经验规范化；它不读写 Room、DataStore 或文件 URI。
- `BackupWorkflow` 是纯 Kotlin 的动作编排边界。它从一次状态快照创建不可变 `ImportPlan`，生成 typed 导出文档，并在提交时检查状态指纹。它不依赖 Android，也不直接操作持久化实现。
- `BackupStateSubmissionPort` 是持久化提交边界。生产实现以互斥区串行读取、保存导入前快照、替换 Room 与 DataStore 状态并同步提醒；任一环节失败时执行补偿回滚，并保留可恢复的最近快照。恢复操作复用同一提交路径，以保持单步快照交换。
- `Workspace` 是 Android 文件交付边界。它只处理 SAF 的 `OpenDocument`/`CreateDocument`、URI 字节读写、用户确认和 typed 结果展示。取消、读取失败或写入失败只影响交付提示，不改变领域状态。

`AttentionViewModel` 只提交 typed workflow 命令并映射结果/错误。兼容旧 API 的方法也必须转发到 workflow，不能再次 decode、merge 或实现导入事务。

## 不变量

- version 1 JSON 的字段外形和稳定 ID 语义不变；设置覆盖、活动计时器校验、里程碑历史和经验规范化继续由 codec/domain 规则决定。
- 预览、候选状态和提交使用同一 `ImportPlan`。状态指纹变化必须返回 typed stale 结果，不写入也不旋转快照。
- 导入前快照在替换运行时状态前保存。Room、DataStore 或提醒失败时，运行时状态和最近快照都必须可恢复。
- CSV bundle 的两个文档来自同一个不可变状态快照；SAF 交付失败不回滚或修改领域状态。

## 回归护栏

删除 workflow 或提交端口时，边界测试应失败；删除页面/ViewModel 的旧 codec 调用或重复导入编排后，源代码边界测试应继续阻止这些路径回归。测试只检查公共调用边界和用户可观察结果，不锁定内部实现细节。

## 后果

新增备份格式或存储实现必须先接入对应边界。Android 文件选择器、`ContentResolver` 和权限处理不得进入纯 Kotlin workflow；Room/DataStore/提醒的补偿逻辑不得复制到页面或 ViewModel。

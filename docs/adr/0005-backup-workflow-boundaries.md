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

## 备份协议的边界（票 202–206）

前一节定的是「备份这件事由哪些边界协作」。本节定的是**备份文档的形状由谁拥有**，两者不重叠：
上一节的 `AttentionBackupCodec` 是行为的权威，本节讲的是这个文档里出现哪些字段、由谁命名。

### 协议承载什么

version 1 备份文档由 `app` 的备份协议包自带模型承载，领域类型一概不参与：

- `BackupProtocolModels` 是文档的持久化载体，字段集合必须与对应领域类型一一对应；
- `BackupProtocolEnums` 是文档里那四个按名出现的枚举，连同「名字认不出来时读成什么」这条规则（它们在文档里一共占五个字段：启动入口两处，加上节奏、来源、频率各一处）；
- `BackupProtocolPayloads` 是承载点清单，登记每个协议模型在文档里的位置；
- `AttentionBackup` 用协议模型组装文档，codec 只在协议模型上编解码。

清单由 `BackupProtocolPayloadTest` 守（协议描述符与清单一致），协议与领域的形状一致性由
`BackupDomainMappingTest` 守。两侧各自可演进：领域可以自由加字段而不改变备份格式，备份要加字段
必须在协议包里显式加，并说明理由。

### 与领域如何转换

`BackupDomainMapping` 是领域 ↔ 协议之间**唯一**的转换处。它是显式的逐字段搬运，不是序列化：

- 转换同时提供 `toProtocol` 与 `toDomain`，往返必须相等；领域字段被压到非默认值上走一遍，防止
  「默认值恰好相同」掩盖漏搬。
- 协议模型的默认值决定缺字段的历史备份读成什么，因此必须与领域类型的构造默认值一致。
- 协议相对领域唯一的可选性差异是「行 ID 必须自带」：领域类型靠 `newId()` 默认值把 ID 视为可选，
  协议模型没有这个默认值，一行没有 ID 的记录是结构损坏，应当整份被拒。

### 线上名字由谁拥有

`BackupProtocolPayloads` 里的 `serialName` 来自协议模型自己的序列化标识。领域类型不再为备份而
可序列化，因此领域侧的 `@Serializable` 标注已经全部摘除——它们曾让「加一个领域字段就悄悄改变备份
格式」成为可能，且标注的唯一实际用途只是让字段对照测试能读到描述符。

例外只有一处，且理由与备份无关：`TimerSegment` 仍然可序列化，因为 Room 的 `ActiveTimerEntity` 把计时
片段整列存成 JSON，经 `BusinessMappers` 的 `roomJson` 编解码，属于运行时计时现场的持久化路径。

另外两条按名持久化的路径继续使用 `:domain` 的 `PersistedEnumSerialization`：Room 的枚举列与 DataStore
的启动入口偏好。它们与备份协议的那份按名枚举是**两套各自拥有的规则**，对同一个名字必须给出同一个值，
由 `BackupDomainMappingTest` 与 `UnknownEnumValueToleranceTest` 对照，不允许其中一侧借道另一侧。

### 回归护栏

给进入备份的领域类型加一个字段而不改协议包（或反之），`BackupDomainMappingTest` 的形状对照会变红；
映射函数漏搬字段，逐字段 round-trip 会变红；协议模型凭空多出一个「必填字段」，
`the_only_optionality_difference_is_the_row_id_the_protocol_requires` 会变红。领域类型重新被标上
`@Serializable` 属于回归：它意味着备份格式又在跟着领域字段走。

## 后果

新增备份格式或存储实现必须先接入对应边界。Android 文件选择器、`ContentResolver` 和权限处理不得进入纯 Kotlin workflow；Room/DataStore/提醒的补偿逻辑不得复制到页面或 ViewModel。

---
status: accepted
date: 2026-10-09
---

# Room 与 Preferences DataStore 的运行时职责边界

## 决策

Attention 的运行时主数据使用 Room，少量应用设置使用 Preferences DataStore：

- Room 保存目标树、目标阶段、时间记录、日程条目、重复日程、目标迁移、未来目标规则、周期快照、目标移动、里程碑、经验和活动计时。
- 周期快照是历史事实，保留 `targetId` 文本作为历史引用但不建立活目标外键；删除目标不会删除快照。
- Preferences DataStore 保存规划日界线、周起始日、每日容量、首屏、上次打开页面、引导状态和通知偏好。
- JSON 只作为版本化备份与恢复格式，不作为运行时主存储。
- `:domain` 继续只包含纯 Kotlin 的领域类型和规则；Room、DataStore、Compose 和系统服务属于 Android 适配层。

## 范围取舍

本次架构切换采用一次性实现，目标是新安装直接使用 Room 与设置 DataStore。已有版本的 `attention_state_json` 不在本期自动迁移范围内，也不承诺升级后保留旧本地数据。发布说明必须明确这一点；后续若需要兼容，应单独设计一次性导入工具和回滚方案。

## 背景

当前实现把完整的 `AttentionState` 编码成一个 Preferences DataStore 字符串。它让设置和业务数据共享了一个来源，但也使 DataStore 承担了实体关系、历史记录、计时状态和备份协议的职责，违反 ADR-0001 的存储分工，并导致整份状态反复序列化。

## 结果

- 业务数据可以按实体查询、建立索引、使用外键和 Room 事务。
- 设置可以独立观察和更新，不再与业务实体更新耦合。
- `AttentionState` 作为领域读模型和备份 DTO 的输入继续保留，但不再直接作为数据库文件。
- JSON 导入导出必须通过显式 DTO 映射 Room 与 DataStore，不能依赖数据库内部结构。
- 新安装的默认状态由 Room 空数据库和 DataStore 默认值共同构成。

## 代价与风险

- 需要重构当前 `AttentionStateRepository.update(transform)` 的通用写入接口，改为设置仓库、业务数据仓库和明确的领域命令。
- Room 表结构、迁移、外键语义和导入事务需要额外测试。
- 本期不保留旧安装数据，发布前必须确认产品接受这一点。
- 因此 v1/v2 仅作为预发布 Room 基础版本处理：数据库 builder 对这两个已知版本使用显式 destructive fallback，且通过 instrumentation fixture 验证；未知未来版本不自动清空。

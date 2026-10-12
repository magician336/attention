# T14 日周月统计规格

## Problem Statement

用户需要在一个统一的统计页面中按日、周或自然月回顾时间投入。统计口径、目标树去重、未归属活动、目标迁移和设置影响需要形成稳定且可验证的产品行为。

## Solution

在统计页面提供“日 / 周 / 月”周期切换，并以当前选定日期计算对应范围。统计由纯 Kotlin 领域层统一计算，页面只负责展示目标投入、实际投入、目标缺口、超额投入、迁移目标、未归属活动和按目标分组的投入。

日统计、周统计和月统计均使用规划日口径；周统计使用设置中的周起始日；自然月按月份边界切换。规划日界线或周起始日变更后，未来查看的统计重新计算，历史时间记录不被改写。

## User Stories

1. As a 用户, I want to 查看某个规划日、周或自然月的统计, so that I can compare target and actual investment.
2. As a 用户, I want to 在周期之间切换并看到当前统计范围, so that I can compare不同周期。
3. As a 用户, I want to 看到目标投入、实际投入、目标缺口和超额投入, so that I can review commitment and result.
4. As a 用户, I want to 单独看到迁移目标和未归属活动, so that I can distinguish planned migration from actual investment.
5. As a 用户, I want to 按目标查看投入并获得父目标子树汇总, so that I can understand the target tree without double counting.
6. As a 用户, I want to 周起始日和规划日界线设置影响统计范围, so that reports match my planning habit.
7. As a 用户, I want to 设置变化不改写历史时间记录, so that historical facts remain stable.
8. As a 用户, I want to 统计在记录编辑、删除、归属变更、计时器结算和应用重启后保持一致, so that it reflects the persisted time ledger.
9. As a 用户, I want to 一次性、重复和无记录周期得到正确的零值或目标值, so that empty periods are understandable.
10. As a 用户, I want to 已取消迁移不计入统计, so that only active arrangements are shown.

## Implementation Decisions

- 保留一个最高层领域统计 seam：输入日期和周期类型，输出范围、目标投入、实际投入、未归属活动、迁移目标、超额投入和按目标分钟数。
- 使用 `LocalDate` 闭区间；周范围由 `weekStartDay` 决定；规划日归属由 `planningDayBoundaryMinutes` 决定；月范围为自然月。
- 实际投入只来自时间记录。未归属活动计入实际总量但不进入目标分组；日程预计时长和完成标记不计入实际投入。
- 目标树汇总递归包含后代投入，同一时间记录在总计中只计算一次。
- 目标投入按范围内适用的目标阶段计算；一次性阶段按截止日期和范围交集处理，周期阶段沿用现有领域规则。
- 迁移目标按目的日期范围相交计算，已取消迁移排除；迁移不伪造时间记录。
- 缺口和超额投入是非负派生值，不改变账本、目标阶段或周期快照。
- Compose 页面只消费领域结果；Room、DataStore 和 UI 不复制统计算法。
- 保留现有统计成长信息和导航；本 Issue 增加周期选择与投入摘要，不重设计奖励等级。

## Testing Decisions

- 主要 seam 为纯 Kotlin 领域统计函数，只断言外部可见统计值和范围。
- 领域测试覆盖日/周/月、自定义周起始日、规划日界线跨午夜、无记录、未归属活动、父子去重、缺口、超额、有效/取消迁移、一次性/重复阶段和设置变化。
- Compose 集成测试覆盖周期切换、范围文案、各摘要字段、未归属活动和父子目标不重复。
- 复用现有 Room 业务仓库测试验证重启后统计输入一致；不在 DAO 中复制统计算法。
- 固定时区、日期和规划日界线，避免设备当前时间导致不稳定。

## Out of Scope

- 自动目标迁移、自动结转、缺口补偿、时间记录编辑边界和归档目标规则。
- 周期快照保存/结算协议、投入经验、里程碑、等级和奖励规则。
- 云端同步、导出格式、图表可视化和跨设备报表。
- 改变自然月定义或新增数据库表（除非现有读模型无法承载输入）。

## Further Notes

现有代码已有 `periodStats` 领域 seam、统计页面和目标树/统计集成测试基础，应优先扩展这些 seam。迁移目标表示目的范围内的主动迁移量，不是实际投入。实现中若目标阶段自然月或未来规则存在歧义，应先补领域测试并记录决策。

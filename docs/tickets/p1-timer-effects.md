## Parent

[#216](https://github.com/magician336/attention/issues/216)

## What to build

让日程条目和独立计时入口通过同一个计时平台效果边界工作。用户启动计时后，领域状态先成功保存，再由唯一的 Android adapter 处理恢复提示、通知状态反馈和前台计时服务；暂停、恢复和结束也保持同一条生命周期路径。页面只发送开始、暂停、恢复和结束 intent，不接触 Android 服务或权限 API。该路径不包含 Progress Quest 游戏会话。

## Acceptance criteria

- [ ] 从日程条目启动计时仍生成带日程来源的时间记录，从独立入口启动计时仍生成没有日程来源的时间记录。
- [ ] 两个入口在计时成功后只经过同一个平台效果路径，恢复提示清理、通知状态处理和前台服务启动不会重复或分叉。
- [ ] 领域命令失败时不会启动或重启前台服务；平台提示失败时不会撤销已经保存的时间记录，现有反馈仍可见。
- [ ] 暂停、恢复、结束和应用重建后的同步保持计时状态、前台服务和恢复提示一致；重复回调不会留下残余服务。
- [ ] 日程页面与其 actions seam 不依赖 Android 服务、权限、系统时钟或完整 AttentionViewModel。
- [ ] 记录型 fake platform effects、页面 actions 测试和必要的宿主级服务/权限测试覆盖上述用户可观察行为。

## Blocked by

None (can start immediately).

# 1b · 浮光全画板静态 UI

## 范围

本切片实现设计稿“浮光 · 日程与学习伙伴 App”13 张画板对应的静态 Compose UI：

- 01 今日日程
- 02 本周安排
- 03 对话首页
- 04 浮光主 Agent 对话
- 05 雅思助手详情
- 06 输入区方案（A 为正式方案，B/C 为对比展示）
- 07 学习总览
- 08 变更授权
- 09 排程规则
- 10 建立小助手
- 11 小助手自动排
- 12 回主 Agent 确认
- 13 谁定什么 · 责任边界

本切片只提供静态 UI、Mock 数据和动作回调，不包含真实 AI、ViewModel、持久化、业务算法和主工作区导航接线。

## 统一接口

- `FuguangAppState`：13 张画板需要的展示状态
- `FuguangAppActions`：导航、输入、发送、创建助手、选择方案、确认写入、撤回等动作
- `FuguangNoOpActions`：预览和测试用空实现

任务 2 实现 `FuguangAppActions` 并把 ViewModel 状态映射为 `FuguangAppState` 即可，不需要修改页面组件。

## 代码

- `FuguangChatTheme.kt`：颜色、8 档字阶、行高、间距与圆角令牌
- `ChatModels.kt` / `FuguangAppContract.kt`：展示模型与动作契约
- `ChatComponents.kt`：消息气泡、通知卡、快捷建议、输入栏、底部导航
- `ChatScreens.kt`：对话首页、主 Agent、雅思助手详情
- `ScheduleAndLearningScreens.kt`：今日日程、本周安排、学习总览
- `InfoScreens.kt`：变更授权、排程规则、责任边界
- `AssistantFlowScreens.kt`：建立小助手、助手排计划、主 Agent 确认
- `InputSpecScreen.kt`：输入区 A/B/C 对比板
- `FuguangAppPreviewData.kt`：全画板 Mock 数据
- `ChatPreviews.kt`：Android Studio Preview
- `app/src/debug/java/.../ChatPreviewActivity.kt`：Debug 预览入口

## Debug 预览

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.attention.app/.chat.ChatPreviewActivity --es screen day
```

可用 `screen`：

```text
day / week / hub / main / assistant / input / learning /
authorization / rules / create / plan / confirm / responsibility
```

## 验证

```powershell
.\gradlew.bat test lintDebug assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.attention.app.chat.FuguangChatScreenTest'
```

结果：

- JVM 测试、lint、Debug APK：通过
- `FuguangChatScreenTest`：7/7 通过
- API 36 模拟器：13 张画板均完成视觉检查

## 已知边界

- 未提供 Noto Sans SC / Inter 字体文件，当前使用 Android 系统 SansSerif 回退。
- 06 的 B/C 仅为输入区对比展示，正式 UI 使用设计指定的方案 A。
- 跨页面导航、真实 AI、业务状态、Room 持久化和自动排程算法由任务 2 / 组合根负责。

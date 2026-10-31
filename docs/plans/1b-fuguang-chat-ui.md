# 1b · 浮光对话页静态 UI

## 范围

本切片实现设计稿 `浮光 · 日程与学习伙伴 App.pdf` 中与对话相关的静态界面：

- 画板 03：对话首页
- 画板 04：浮光主 Agent 对话
- 画板 05：雅思助手详情
- 画板 06：输入区方案 A（已采用方案）

不包含真实 AI 请求、ViewModel、持久化、后端接口和主工作区导航接入。页面通过 `FuguangChatUiState`、`FuguangConversationHubState`、`FuguangAssistantDetailState` 接收数据，通过回调发出输入、发送、快捷建议和卡片操作。

## 代码

- `app/src/main/java/com/attention/app/chat/FuguangChatTheme.kt`：颜色、8 档字阶、间距与圆角令牌
- `ChatModels.kt`：静态展示模型
- `ChatComponents.kt`：消息气泡、调整卡、飞书通知卡、快捷建议、输入栏、底部导航
- `ChatScreens.kt`：对话首页、主 Agent 对话、雅思助手详情
- `ChatPreviewData.kt`：Preview / Debug Mock 数据
- `ChatPreviews.kt`：Android Studio Preview
- `app/src/debug/java/.../ChatPreviewActivity.kt`：Debug 预览入口

## Debug 预览

在模拟器或设备上运行：

```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.attention.app/.chat.ChatPreviewActivity --es screen hub
adb shell am start -n com.attention.app/.chat.ChatPreviewActivity --es screen main
adb shell am start -n com.attention.app/.chat.ChatPreviewActivity --es screen assistant
```

## 交互契约

页面当前只发出以下 intent：

- `onInputChange(String)`
- `onSend()`
- `onSuggestionClick(FuguangChatSuggestion)`
- `onPrimaryAction()` / `onSecondaryAction()`
- `onOpenMainAgent()` / `onOpenAssistant(String)`
- `onBottomDestination(FuguangBottomDestination)`

任务 2 可以在不修改 UI 组件的情况下，把 ViewModel 状态和 Mock AI 接入这些回调。

## 验证

```powershell
.\gradlew.bat test lintDebug assembleDebug
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.attention.app.chat.FuguangChatScreenTest'
```

结果：

- JVM 测试、lint、Debug APK：通过
- `FuguangChatScreenTest`：3/3 通过
- API 36 模拟器人工检查：通过

## 已知边界

- 设计稿使用 Noto Sans SC / Inter；当前未新增字体资源，使用 Android 系统 SansSerif 回退，因此字形和行宽与设计软件会有轻微差异。
- 画板 06 的 B/C 两种输入方案未进入生产 UI，按设计落地规则只实现方案 A。
- 全局底部导航和主工作区接线留给组合根/任务 2，当前 Debug 预览中的底栏仅用于静态验收。

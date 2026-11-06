package com.attention.app.fuguang.week.preview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.attention.app.fuguang.week.components.FuguangWeekTheme
import com.attention.app.fuguang.week.components.fuguangWeekColors
import com.attention.app.ui.theme.AttentionTheme

/**
 * 屏 01「她先开口」的 debug 独立入口：纯文字对话页。
 *
 * 只在 debug 变体注册，发布包不含此类。带 LAUNCHER filter，装 debug 包后桌面会多一个
 * 图标，用来在真机上验收组件拼装结果（发送 → 思考块 → 答复 → 建议胶囊 → 填入）。
 */
class FuguangChatPageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 浮光那套有自己的视觉令牌，与 AttentionTheme 并列挂载而不是互相覆盖：
            // 外层保留应用主题（对话框、系统栏回退色），内层接管浮光自己的颜色与字号。
            AttentionTheme(darkTheme = false) {
                FuguangWeekTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = fuguangWeekColors().canvas,
                    ) {
                        FuguangChatPage()
                    }
                }
            }
        }
    }
}

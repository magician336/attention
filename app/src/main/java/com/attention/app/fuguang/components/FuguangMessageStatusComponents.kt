package com.attention.app.fuguang.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FuguangC4ThinkingBlock(
    text: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    Surface(modifier = modifier.height(28.dp), shape = RoundedCornerShape(10.dp), color = roles.line2) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 1.5.dp, color = roles.ink3)
            } else {
                Icon(Icons.Filled.Check, null, tint = roles.mainDeep, modifier = Modifier.size(10.dp))
            }
            Text(text, style = type.t11, color = roles.ink3, maxLines = 1)
        }
    }
}

@Composable
fun FuguangC5ReceiptRow(
    text: String,
    relativeTime: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    details: List<String> = emptyList(),
    onToggle: (() -> Unit)? = null,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    Column {
        Surface(
            onClick = { onToggle?.invoke() },
            enabled = onToggle != null,
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = roles.mainTint,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(13.dp).background(roles.mainDeep, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Check, null, tint = roles.surface, modifier = Modifier.size(9.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(text, style = type.t11.copy(fontWeight = FontWeight.Medium), color = roles.mainDeep, modifier = Modifier.weight(1f))
                Text(relativeTime, style = type.t95, color = roles.ink3)
            }
        }
        if (expanded) {
            Column(modifier = Modifier.padding(start = 20.dp, top = 5.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                details.take(4).forEach { detail -> Text("↳ $detail", style = type.t95, color = roles.ink3) }
            }
        }
    }
}

@Composable
fun FuguangC6SuggestionCapsules(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        suggestions.take(3).forEach { suggestion ->
            Surface(
                onClick = { onSelect(suggestion) },
                modifier = Modifier.height(28.dp).testTag("suggestion-$suggestion"),
                shape = CircleShape,
                color = roles.surface,
                border = BorderStroke(1.dp, roles.main),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = suggestion.take(14),
                        style = type.t125,
                        color = roles.mainDeep,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun FuguangC7TimestampDivider(
    label: String,
    modifier: Modifier = Modifier,
    withLines: Boolean = false,
) {
    val roles = fuguangRoles()
    val type = fuguangType()
    Row(modifier = modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
        if (withLines) {
            Box(Modifier.weight(1f).height(1.dp).background(roles.line))
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = type.t95.copy(fontSize = 9.5.sp, letterSpacing = 0.3.sp),
            color = roles.ink3,
            maxLines = 1,
        )
        if (withLines) {
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f).height(1.dp).background(roles.line))
        }
    }
}

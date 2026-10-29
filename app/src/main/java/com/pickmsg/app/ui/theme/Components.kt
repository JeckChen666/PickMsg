package com.pickmsg.app.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

val LocalSecondaryPage = staticCompositionLocalOf<(Boolean) -> Unit> { {} }

@Composable
fun PickMsgScaffold(
    topBar: @Composable () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    secondaryPage: Boolean = false,
    content: @Composable (PaddingValues) -> Unit,
) {
    val setSecondaryPage = LocalSecondaryPage.current
    DisposableEffect(secondaryPage, setSecondaryPage) {
        if (secondaryPage) setSecondaryPage(true)
        onDispose { if (secondaryPage) setSecondaryPage(false) }
    }
    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = topBar,
        floatingActionButton = floatingActionButton,
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickMsgTopBar(
    title: String,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = PickMsgOpacity.field),
            titleContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}

@Composable
fun PickMsgCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    JadeGlass(modifier = modifier) { Column(content = content) }
}

@Composable
fun PickMsgFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    leadingIcon: (@Composable () -> Unit)? = null,
    prominent: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Box(Modifier.padding(vertical = PickMsgSpace.xs)) { label() } },
        leadingIcon = leadingIcon,
        shape = PickMsgShape.pill,
        border = BorderStroke(PickMsgSize.border, colors.outlineVariant),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = colors.surface.copy(alpha = PickMsgOpacity.subtle),
            selectedContainerColor = if (prominent) colors.primary else colors.secondaryContainer,
            selectedLabelColor = if (prominent) colors.onPrimary else colors.onSecondaryContainer,
        ),
    )
}

@Composable
fun PickMsgBadge(text: String, warning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = PickMsgShape.badge,
        color = if (warning) colors.tertiaryContainer else colors.secondaryContainer,
        contentColor = if (warning) colors.onTertiaryContainer else colors.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = PickMsgSpace.sm, vertical = PickMsgSpace.micro))
    }
}

@Composable
fun PickMsgButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(onClick = onClick, modifier = modifier.heightIn(min = PickMsgSize.touchTarget),
        enabled = enabled, shape = PickMsgShape.control, content = content)
}

@Composable
fun PickMsgOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = PickMsgSize.touchTarget),
        enabled = enabled, shape = PickMsgShape.control, content = content)
}

@Composable
fun PickMsgFloatingActionButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: String,
) {
    ExtendedFloatingActionButton(
        onClick = onClick, icon = icon, text = { Text(label) },
        modifier = Modifier.semantics { contentDescription = label },
        shape = PickMsgShape.card,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = PickMsgElevation.floating,
            pressedElevation = PickMsgElevation.floating,
            focusedElevation = PickMsgElevation.floating,
            hoveredElevation = PickMsgElevation.overlay,
        ),
    )
}

@Composable
fun PickMsgDeleteAction(label: String, message: String, onConfirm: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    IconButton(onClick = { confirming = true }) {
        Icon(Icons.Default.Delete, contentDescription = label, tint = MaterialTheme.colorScheme.error)
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("$label？") },
            text = { Text(message, modifier = Modifier.verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = { confirming = false; onConfirm() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } },
        )
    }
}

@Composable
fun PickMsgManagementCard(
    title: String,
    summary: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    JadeGlass(onClick = onEdit, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "编辑$title" }) {
        Row(Modifier.padding(PickMsgSpace.card), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.md)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.xs)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = enabled, onCheckedChange = onToggle,
                modifier = Modifier.semantics { contentDescription = "启用$title" })
        }
    }
}

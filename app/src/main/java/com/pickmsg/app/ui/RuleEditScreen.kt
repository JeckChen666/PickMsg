@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.ActionSpec
import com.pickmsg.app.data.ActionType
import com.pickmsg.app.data.AppMatchMode
import com.pickmsg.app.data.FormatOp
import com.pickmsg.app.data.FormatOpType
import com.pickmsg.app.data.FormatTarget
import com.pickmsg.app.data.MatchMode
import com.pickmsg.app.data.RuleInfo
import com.pickmsg.app.data.SceneMatchMode
import com.pickmsg.app.service.RuleMatcher
import com.pickmsg.app.ui.theme.PickMsgDeleteAction
import com.pickmsg.app.ui.theme.PickMsgFilterChip
import com.pickmsg.app.ui.theme.PickMsgOutlinedButton
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgShape
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgTopBar

fun matchModeLabel(mode: MatchMode): String = when (mode) {
    MatchMode.ALL -> "无条件"
    MatchMode.CONTAINS_ANY -> "包含任一"
    MatchMode.CONTAINS_ALL -> "全部包含"
    MatchMode.NOT_CONTAINS_ANY -> "不包含任一"
    MatchMode.CONTAINS_NONE -> "均不包含"
    MatchMode.A_AND_B -> "A 且 B"
    MatchMode.REGEX_MATCH -> "正则匹配"
    MatchMode.REGEX_EXTRACT -> "正则提取"
}

private val WEBHOOK_PRESETS = listOf(
    "自定义" to "",
    "企业微信" to "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=",
    "飞书" to "https://open.feishu.cn/open-apis/bot/v2/hook/",
    "钉钉" to "https://oapi.dingtalk.com/robot/send?access_token=",
    "Slack" to "https://hooks.slack.com/services/",
    "Telegram" to "https://api.telegram.org/bot",
)

private val WEBHOOK_DEFAULT_BODIES = mapOf(
    "企业微信" to """{"msgtype":"text","text":{"content":"【{app}】{title}\n{content}"}}""",
    "飞书" to """{"msg_type":"text","content":{"text":"【{app}】{title}\n{content}"}}""",
    "钉钉" to """{"msgtype":"text","text":{"content":"【{app}】{title}\n{content}"}}""",
)

// ---------------------------------------------------------------------------
// 草稿状态
// ---------------------------------------------------------------------------

private data class DraftState(
    val name: String = "",
    val nameError: Boolean = false,
    val enable: Boolean = true,
    val appMode: AppMatchMode = AppMatchMode.ALL,
    val apps: List<String> = emptyList(),
    val titleMode: MatchMode = MatchMode.ALL,
    val titleKeywords: String = "",
    val titleA: String = "",
    val titleB: String = "",
    val textMode: MatchMode = MatchMode.ALL,
    val textKeywords: String = "",
    val textA: String = "",
    val textB: String = "",
    val titleOrText: Boolean = false,
    val formatOps: List<FormatOp> = emptyList(),
    // 整理类
    val needRemove: Boolean = false,
    val removeDelaySec: String = "0",
    val needRepost: Boolean = false,
    val repostTitleTemplate: String = "",
    val repostTextTemplate: String = "",
    val needCopy: Boolean = false,
    val needOpen: Boolean = false,
    // 提醒类
    val needSound: Boolean = false,
    val soundUri: String = "",
    val soundForce: Boolean = false,
    val needVibrate: Boolean = false,
    val vibratePattern: String = "",
    val needTts: Boolean = false,
    val ttsTemplate: String = "",
    // 转发类
    val needWebhook: Boolean = false,
    val webhookUrl: String = "",
    val webhookMethod: String = "POST",
    val webhookHeaders: String = "",
    val webhookBody: String = "",
    val needEmail: Boolean = false,
    val emailTo: String = "",
    val needSms: Boolean = false,
    val smsNumbers: String = "",
    val needWristband: Boolean = false,
    val wristbandTimes: String = "3",
    val wristbandInterval: String = "2",
    // 场景
    val scenesIds: List<Long> = emptyList(),
    val sceneMatchMode: SceneMatchMode = SceneMatchMode.ANY,
) {
    fun toRuleInfo(existing: RuleInfo?): RuleInfo {
        fun parseKeywords(s: String) = s.split(',', '，', '、', ';', '；', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val actions = buildList {
            if (needRemove) add(ActionSpec(type = ActionType.REMOVE, delaySec = removeDelaySec.trim().toIntOrNull() ?: 0))
            if (needRepost) {
                add(
                    ActionSpec(
                        type = ActionType.REPOST,
                        titleTemplate = repostTitleTemplate,
                        textTemplate = repostTextTemplate,
                    ),
                )
            }
            if (needCopy) add(ActionSpec(type = ActionType.COPY))
            if (needOpen) add(ActionSpec(type = ActionType.OPEN))
            if (needSound) add(ActionSpec(type = ActionType.SOUND, soundUri = soundUri, force = soundForce))
            if (needVibrate) add(ActionSpec(type = ActionType.VIBRATE, vibratePattern = vibratePattern))
            if (needTts) add(ActionSpec(type = ActionType.TTS, ttsTemplate = ttsTemplate))
            if (needWebhook) {
                add(
                    ActionSpec(
                        type = ActionType.WEBHOOK,
                        url = webhookUrl.trim(),
                        method = webhookMethod,
                        headers = webhookHeaders,
                        body = webhookBody,
                    ),
                )
            }
            if (needEmail) add(ActionSpec(type = ActionType.EMAIL, emailTo = emailTo))
            if (needSms) add(ActionSpec(type = ActionType.SMS, phoneNumbers = parseKeywords(smsNumbers)))
            if (needWristband) {
                add(
                    ActionSpec(
                        type = ActionType.WRISTBAND,
                        repeatTimes = wristbandTimes.trim().toIntOrNull() ?: 3,
                        intervalSec = wristbandInterval.trim().toIntOrNull() ?: 2,
                    ),
                )
            }
        }
        return RuleInfo(
            id = existing?.id ?: 0,
            name = name.trim(),
            enable = enable,
            timestamp = existing?.timestamp ?: System.currentTimeMillis(),
            matchAppMode = appMode,
            matchApps = apps,
            matchTitleMode = titleMode,
            matchTitleKeywords = parseKeywords(titleKeywords),
            matchTitleKeywordsA = parseKeywords(titleA),
            matchTitleKeywordsB = parseKeywords(titleB),
            matchTextMode = textMode,
            matchTextKeywords = parseKeywords(textKeywords),
            matchTextKeywordsA = parseKeywords(textA),
            matchTextKeywordsB = parseKeywords(textB),
            titleOrText = titleOrText,
            scenesIds = scenesIds,
            sceneMatchMode = sceneMatchMode,
            formatOps = formatOps,
            actions = actions,
        )
    }

    companion object {
        fun from(rule: RuleInfo?): DraftState = rule?.let { r ->
            fun join(list: List<String>) = list.joinToString(", ")
            val remove = r.actions.firstOrNull { it.type == ActionType.REMOVE }
            val repost = r.actions.firstOrNull { it.type == ActionType.REPOST }
            val sound = r.actions.firstOrNull { it.type == ActionType.SOUND }
            val vibrate = r.actions.firstOrNull { it.type == ActionType.VIBRATE }
            val tts = r.actions.firstOrNull { it.type == ActionType.TTS }
            val webhook = r.actions.firstOrNull { it.type == ActionType.WEBHOOK }
            val email = r.actions.firstOrNull { it.type == ActionType.EMAIL }
            val sms = r.actions.firstOrNull { it.type == ActionType.SMS }
            val wristband = r.actions.firstOrNull { it.type == ActionType.WRISTBAND }
            DraftState(
                name = r.name,
                enable = r.enable,
                appMode = r.matchAppMode,
                apps = r.matchApps,
                titleMode = r.matchTitleMode,
                titleKeywords = join(r.matchTitleKeywords),
                titleA = join(r.matchTitleKeywordsA),
                titleB = join(r.matchTitleKeywordsB),
                textMode = r.matchTextMode,
                textKeywords = join(r.matchTextKeywords),
                textA = join(r.matchTextKeywordsA),
                textB = join(r.matchTextKeywordsB),
                titleOrText = r.titleOrText,
                formatOps = r.formatOps,
                needRemove = remove != null,
                removeDelaySec = (remove?.delaySec ?: 0).toString(),
                needRepost = repost != null,
                repostTitleTemplate = repost?.titleTemplate.orEmpty(),
                repostTextTemplate = repost?.textTemplate.orEmpty(),
                needCopy = r.actions.any { it.type == ActionType.COPY },
                needOpen = r.actions.any { it.type == ActionType.OPEN },
                needSound = sound != null,
                soundUri = sound?.soundUri.orEmpty(),
                soundForce = sound?.force ?: false,
                needVibrate = vibrate != null,
                vibratePattern = vibrate?.vibratePattern.orEmpty(),
                needTts = tts != null,
                ttsTemplate = tts?.ttsTemplate.orEmpty(),
                needWebhook = webhook != null,
                webhookUrl = webhook?.url.orEmpty(),
                webhookMethod = webhook?.method?.ifBlank { "POST" } ?: "POST",
                webhookHeaders = webhook?.headers.orEmpty(),
                webhookBody = webhook?.body.orEmpty(),
                needEmail = email != null,
                emailTo = email?.emailTo.orEmpty(),
                needSms = sms != null,
                smsNumbers = sms?.phoneNumbers?.joinToString(", ").orEmpty(),
                needWristband = wristband != null,
                wristbandTimes = (wristband?.repeatTimes ?: 3).toString(),
                wristbandInterval = (wristband?.intervalSec ?: 2).toString(),
                scenesIds = r.scenesIds,
                sceneMatchMode = r.sceneMatchMode,
            )
        } ?: DraftState()
    }
}

// ---------------------------------------------------------------------------
// 编辑页
// ---------------------------------------------------------------------------

@Composable
fun RuleEditScreen(existing: RuleInfo?, viewModel: RulesViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var draft by remember { mutableStateOf(DraftState.from(existing)) }
    var showFormatDialog by remember { mutableStateOf(false) }
    val appCounts by viewModel.appCounts.collectAsStateWithLifecycle()
    val scenes by viewModel.scenes.collectAsStateWithLifecycle()
    val webhookTestResult by viewModel.webhookTestResult.collectAsStateWithLifecycle()

    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            @Suppress("DEPRECATION")
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) draft = draft.copy(soundUri = uri.toString())
        }
    }

    PickMsgScaffold(
        secondaryPage = true,
        topBar = {
            PickMsgTopBar(
                title = if (existing == null) "新建规则" else "编辑规则",
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "放弃")
                    }
                },
                actions = {
                    if (existing != null) {
                        PickMsgDeleteAction(
                            label = "删除规则",
                            message = "删除后无法恢复，请确认不再需要此规则。",
                            onConfirm = { viewModel.delete(existing); onClose() },
                        )
                    }
                    IconButton(onClick = {
                        if (draft.name.isBlank()) {
                            draft = draft.copy(nameError = true)
                        } else {
                            viewModel.save(draft.toRuleInfo(existing))
                            onClose()
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "保存")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PickMsgSpace.pagePadding),
            verticalArrangement = Arrangement.spacedBy(PickMsgSpace.fieldGap),
        ) {
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it, nameError = false) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("规则名称") },
                isError = draft.nameError,
                supportingText = if (draft.nameError) {
                    { Text("名称不能为空") }
                } else {
                    null
                },
                singleLine = true,
            )

            SectionTitle("应用匹配")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                AppMatchMode.entries.forEach { mode ->
                    PickMsgFilterChip(
                        selected = draft.appMode == mode,
                        onClick = { draft = draft.copy(appMode = mode) },
                        label = {
                            Text(
                                when (mode) {
                                    AppMatchMode.ALL -> "全部应用"
                                    AppMatchMode.WHITELIST -> "仅选中应用"
                                    AppMatchMode.BLACKLIST -> "排除选中应用"
                                },
                            )
                        },
                    )
                }
            }
            if (draft.appMode != AppMatchMode.ALL) {
                if (draft.apps.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                        draft.apps.forEach { pkg ->
                            PickMsgFilterChip(
                                selected = true,
                                onClick = { draft = draft.copy(apps = draft.apps - pkg) },
                                label = { Text(pkg) },
                                leadingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "移除", Modifier.width(16.dp))
                                },
                            )
                        }
                    }
                }
                if (appCounts.isNotEmpty()) {
                    Text("从已采集的应用中选择：", style = MaterialTheme.typography.bodySmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                        appCounts.forEach { app ->
                            PickMsgFilterChip(
                                selected = app.packageName in draft.apps,
                                onClick = {
                                    draft = if (app.packageName in draft.apps) {
                                        draft.copy(apps = draft.apps - app.packageName)
                                    } else {
                                        draft.copy(apps = draft.apps + app.packageName)
                                    }
                                },
                                label = { Text(app.appLabel) },
                            )
                        }
                    }
                }
                ManualPackageInput(
                    onAdd = { pkg ->
                        if (pkg.isNotEmpty() && pkg !in draft.apps) {
                            draft = draft.copy(apps = draft.apps + pkg)
                        }
                    },
                )
            }

            MatchSection(
                title = "标题匹配",
                mode = draft.titleMode,
                keywords = draft.titleKeywords,
                groupA = draft.titleA,
                groupB = draft.titleB,
                onChange = { mode, kw, a, b ->
                    draft = draft.copy(titleMode = mode, titleKeywords = kw, titleA = a, titleB = b)
                },
            )
            MatchSection(
                title = "内容匹配",
                mode = draft.textMode,
                keywords = draft.textKeywords,
                groupA = draft.textA,
                groupB = draft.textB,
                onChange = { mode, kw, a, b ->
                    draft = draft.copy(textMode = mode, textKeywords = kw, textA = a, textB = b)
                },
            )

            SectionTitle("组合方式")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                PickMsgFilterChip(
                    selected = !draft.titleOrText,
                    onClick = { draft = draft.copy(titleOrText = false) },
                    label = { Text("标题且内容") },
                )
                PickMsgFilterChip(
                    selected = draft.titleOrText,
                    onClick = { draft = draft.copy(titleOrText = true) },
                    label = { Text("标题或内容") },
                )
            }

            SectionTitle("格式化（先于动作执行，结果供所有动作使用）")
            if (draft.formatOps.isEmpty()) {
                Text("无", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                draft.formatOps.forEachIndexed { index, op ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatOpLabel(op),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            draft = draft.copy(formatOps = draft.formatOps.filterIndexed { i, _ -> i != index })
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "删除此格式化", Modifier.width(20.dp))
                        }
                    }
                }
            }
            PickMsgOutlinedButton(onClick = { showFormatDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.width(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加格式化")
            }

            // ---------------- 整理类动作 ----------------
            SectionTitle("动作 · 整理")
            ActionRow("移除通知", draft.needRemove) { draft = draft.copy(needRemove = it) }
            if (draft.needRemove) {
                NumberField("延迟秒（0=立即）", draft.removeDelaySec) {
                    draft = draft.copy(removeDelaySec = it)
                }
            }
            ActionRow("替换重发（保留原通知点击跳转）", draft.needRepost) { draft = draft.copy(needRepost = it) }
            if (draft.needRepost) {
                TemplateField("重发标题模板（留空用原标题）", draft.repostTitleTemplate) {
                    draft = draft.copy(repostTitleTemplate = it)
                }
                TemplateField("重发内容模板（留空用原内容）", draft.repostTextTemplate) {
                    draft = draft.copy(repostTextTemplate = it)
                }
                PlaceholderHint()
            }
            ActionRow("复制内容到剪贴板", draft.needCopy) { draft = draft.copy(needCopy = it) }
            ActionRow("打开原通知目标（代替用户点击）", draft.needOpen) { draft = draft.copy(needOpen = it) }

            // ---------------- 提醒类动作 ----------------
            SectionTitle("动作 · 提醒")
            ActionRow("播放铃声", draft.needSound) { draft = draft.copy(needSound = it) }
            if (draft.needSound) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    PickMsgOutlinedButton(onClick = {
                        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        }
                        ringtonePicker.launch(intent)
                    }) {
                        Text(if (draft.soundUri.isBlank()) "选择铃声（默认通知音）" else "已选铃声，点击更换")
                    }
                    if (draft.soundUri.isNotBlank()) {
                        TextButton(onClick = { draft = draft.copy(soundUri = "") }) { Text("恢复默认") }
                    }
                }
                ActionRow("无视音量强制播放（闹钟通道）", draft.soundForce) { draft = draft.copy(soundForce = it) }
            }
            ActionRow("震动", draft.needVibrate) { draft = draft.copy(needVibrate = it) }
            if (draft.needVibrate) {
                OutlinedTextField(
                    shape = PickMsgShape.control,
                    value = draft.vibratePattern,
                    onValueChange = { draft = draft.copy(vibratePattern = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("波形（毫秒序列）") },
                    placeholder = { Text("0:200:100:200") },
                    singleLine = true,
                )
            }
            ActionRow("TTS 语音播报", draft.needTts) { draft = draft.copy(needTts = it) }
            if (draft.needTts) {
                TemplateField("播报模板（留空用「应用名，标题」）", draft.ttsTemplate) {
                    draft = draft.copy(ttsTemplate = it)
                }
                PlaceholderHint()
            }

            // ---------------- 转发类动作 ----------------
            SectionTitle("动作 · 转发")
            ActionRow("Webhook", draft.needWebhook) { draft = draft.copy(needWebhook = it) }
            if (draft.needWebhook) {
                Text("预设：", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    WEBHOOK_PRESETS.forEach { (label, prefix) ->
                        PickMsgFilterChip(
                            selected = draft.webhookUrl.startsWith(prefix) && prefix.isNotEmpty(),
                            onClick = {
                                draft = draft.copy(
                                    webhookUrl = prefix,
                                    webhookBody = WEBHOOK_DEFAULT_BODIES[label] ?: draft.webhookBody,
                                    webhookMethod = "POST",
                                )
                            },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    shape = PickMsgShape.control,
                    value = draft.webhookUrl,
                    onValueChange = { draft = draft.copy(webhookUrl = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Webhook URL") },
                    singleLine = true,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    listOf("GET", "POST").forEach { m ->
                        PickMsgFilterChip(
                            selected = draft.webhookMethod == m,
                            onClick = { draft = draft.copy(webhookMethod = m) },
                            label = { Text(m) },
                        )
                    }
                }
                OutlinedTextField(
                    shape = PickMsgShape.control,
                    value = draft.webhookHeaders,
                    onValueChange = { draft = draft.copy(webhookHeaders = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义 Headers（每行 Key: Value）") },
                )
                OutlinedTextField(
                    shape = PickMsgShape.control,
                    value = draft.webhookBody,
                    onValueChange = { draft = draft.copy(webhookBody = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Body 模板") },
                )
                PlaceholderHint()
                PickMsgOutlinedButton(onClick = {
                    viewModel.testWebhook(
                        ActionSpec(
                            type = ActionType.WEBHOOK,
                            url = draft.webhookUrl,
                            method = draft.webhookMethod,
                            headers = draft.webhookHeaders,
                            body = draft.webhookBody,
                        ),
                    )
                }, enabled = draft.webhookUrl.isNotBlank()) {
                    Text("发送测试请求")
                }
                webhookTestResult?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            ActionRow("邮件转发（SMTP 账号在「我的」里配置）", draft.needEmail) { draft = draft.copy(needEmail = it) }
            if (draft.needEmail) {
                OutlinedTextField(
                    shape = PickMsgShape.control,
                    value = draft.emailTo,
                    onValueChange = { draft = draft.copy(emailTo = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("收件人（逗号分隔）") },
                    singleLine = true,
                )
            }
            ActionRow("短信转发", draft.needSms) { draft = draft.copy(needSms = it) }
            if (draft.needSms) {
                OutlinedTextField(
                    shape = PickMsgShape.control,
                    value = draft.smsNumbers,
                    onValueChange = { draft = draft.copy(smsNumbers = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("目标号码（逗号分隔）") },
                    supportingText = { Text("将申请短信发送权限；产生运营商费用") },
                )
            }
            ActionRow("手环转发（重复重发提醒）", draft.needWristband) { draft = draft.copy(needWristband = it) }
            if (draft.needWristband) {
                NumberField("重发次数", draft.wristbandTimes) { draft = draft.copy(wristbandTimes = it) }
                NumberField("间隔秒", draft.wristbandInterval) { draft = draft.copy(wristbandInterval = it) }
            }

            // ---------------- 场景 ----------------
            SectionTitle("生效场景（不选 = 全时段生效）")
            if (scenes.isEmpty()) {
                Text(
                    "还没有场景，先在「场景」页创建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    scenes.forEach { scene ->
                        PickMsgFilterChip(
                            selected = scene.id in draft.scenesIds,
                            onClick = {
                                draft = if (scene.id in draft.scenesIds) {
                                    draft.copy(scenesIds = draft.scenesIds - scene.id)
                                } else {
                                    draft.copy(scenesIds = draft.scenesIds + scene.id)
                                }
                            },
                            label = { Text(scene.name) },
                        )
                    }
                }
                if (draft.scenesIds.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                        SceneMatchMode.entries.forEach { mode ->
                            PickMsgFilterChip(
                                selected = draft.sceneMatchMode == mode,
                                onClick = { draft = draft.copy(sceneMatchMode = mode) },
                                label = { Text(if (mode == SceneMatchMode.ANY) "任一场景满足" else "全部场景满足") },
                            )
                        }
                    }
                }
            }

            // ---------------- 内嵌测试 ----------------
            SectionTitle("内嵌测试（输入样例即时验证匹配结果）")
            var testTitle by remember { mutableStateOf("") }
            var testText by remember { mutableStateOf("") }
            var testPkg by remember { mutableStateOf("") }
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = testTitle,
                onValueChange = { testTitle = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("测试标题") },
                singleLine = true,
            )
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = testText,
                onValueChange = { testText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("测试内容") },
            )
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = testPkg,
                onValueChange = { testPkg = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("测试包名（可选，用于验证应用匹配）") },
                singleLine = true,
            )
            val draftRule = draft.toRuleInfo(null)
            val (titleOk, textOk) = RuleMatcher.matchSides(draftRule, testTitle, testText)
            val appOk = testPkg.isBlank() || RuleMatcher.matchApp(draftRule, testPkg.trim())
            val matched = appOk && (if (draft.titleOrText) titleOk || textOk else titleOk && textOk)
            Text(
                buildString {
                    append(if (matched) "✅ 匹配" else "❌ 不匹配")
                    if (testTitle.isNotEmpty() || testText.isNotEmpty()) {
                        append("  （标题侧:${if (titleOk) "命中" else "未命中"} · ")
                        append("内容侧:${if (textOk) "命中" else "未命中"}")
                        if (testPkg.isNotBlank()) append(" · 应用侧:${if (appOk) "命中" else "未命中"}")
                        append("）")
                    }
                },
                style = MaterialTheme.typography.titleSmall,
                color = if (matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
    }

    if (showFormatDialog) {
        FormatOpDialog(
            onConfirm = { op ->
                draft = draft.copy(formatOps = draft.formatOps + op)
                showFormatDialog = false
            },
            onDismiss = { showFormatDialog = false },
        )
    }
}

// ---------------------------------------------------------------------------
// 小组件
// ---------------------------------------------------------------------------

@Composable
private fun ActionRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = onToggle)
        Text(
            label,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle(!checked) },
        )
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() }.take(4)) },
        modifier = Modifier.width(180.dp),
        label = { Text(label) },
        singleLine = true,
    )
}

@Composable
private fun TemplateField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
    )
}

@Composable
private fun PlaceholderHint() {
    Text(
        "可用占位符：{app} {title} {content} {time} {date} {week} {mobile}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = PickMsgSpace.sectionGap - PickMsgSpace.fieldGap),
    )
}

@Composable
private fun ManualPackageInput(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
        OutlinedTextField(
            shape = PickMsgShape.control,
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            label = { Text("手动输入包名") },
            singleLine = true,
        )
        PickMsgOutlinedButton(
            onClick = {
                val pkg = text.trim()
                if (pkg.isNotEmpty()) {
                    onAdd(pkg)
                    text = ""
                }
            },
            enabled = text.isNotBlank(),
        ) {
            Text("添加")
        }
    }
}

@Composable
private fun MatchSection(
    title: String,
    mode: MatchMode,
    keywords: String,
    groupA: String,
    groupB: String,
    onChange: (MatchMode, String, String, String) -> Unit,
) {
    SectionTitle(title)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
        MatchMode.entries.forEach { m ->
            PickMsgFilterChip(
                selected = mode == m,
                onClick = { onChange(m, keywords, groupA, groupB) },
                label = { Text(matchModeLabel(m)) },
            )
        }
    }
    when (mode) {
        MatchMode.ALL -> {}
        MatchMode.A_AND_B -> {
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = groupA,
                onValueChange = { onChange(mode, keywords, it, groupB) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("A 组关键词") },
                supportingText = { Text("多个用逗号分隔，A 组任一命中且 B 组任一命中") },
            )
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = groupB,
                onValueChange = { onChange(mode, keywords, groupA, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("B 组关键词") },
            )
        }
        MatchMode.REGEX_MATCH -> KeywordField(keywords, "正则表达式（整串匹配），多个用逗号分隔", mode, onChange)
        MatchMode.REGEX_EXTRACT -> KeywordField(keywords, "正则表达式（提取片段），多个用逗号分隔", mode, onChange)
        else -> KeywordField(keywords, "多个关键词用逗号分隔", mode, onChange)
    }
}

@Composable
private fun KeywordField(
    value: String,
    hint: String,
    mode: MatchMode,
    onChange: (MatchMode, String, String, String) -> Unit,
) {
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = value,
        onValueChange = { onChange(mode, it, "", "") },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("关键词") },
        supportingText = { Text(hint) },
    )
}

private fun formatOpLabel(op: FormatOp): String = buildString {
    append(if (op.target == FormatTarget.TITLE) "标题" else "内容")
    append(" · ")
    when (op.op) {
        FormatOpType.REGEX_REPLACE -> append("正则替换 [${op.from}]→[${op.to}]")
        FormatOpType.PREFIX -> append("添加前缀 [${op.to}]")
        FormatOpType.SUFFIX -> append("添加后缀 [${op.to}]")
        FormatOpType.REGEX_EXTRACT -> append("正则提取 [${op.from}]")
    }
}

@Composable
private fun FormatOpDialog(onConfirm: (FormatOp) -> Unit, onDismiss: () -> Unit) {
    var target by remember { mutableStateOf(FormatTarget.TITLE) }
    var op by remember { mutableStateOf(FormatOpType.REGEX_REPLACE) }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加格式化操作") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    FormatTarget.entries.forEach { t ->
                        PickMsgFilterChip(
                            selected = target == t,
                            onClick = { target = t },
                            label = { Text(if (t == FormatTarget.TITLE) "作用于标题" else "作用于内容") },
                        )
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    FormatOpType.entries.forEach { o ->
                        PickMsgFilterChip(
                            selected = op == o,
                            onClick = { op = o },
                            label = {
                                Text(
                                    when (o) {
                                        FormatOpType.REGEX_REPLACE -> "正则替换"
                                        FormatOpType.PREFIX -> "前缀"
                                        FormatOpType.SUFFIX -> "后缀"
                                        FormatOpType.REGEX_EXTRACT -> "正则提取"
                                    },
                                )
                            },
                        )
                    }
                }
                if (op == FormatOpType.REGEX_REPLACE || op == FormatOpType.REGEX_EXTRACT) {
                    OutlinedTextField(
                        shape = PickMsgShape.control,
                        value = from,
                        onValueChange = { from = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("正则表达式") },
                        singleLine = true,
                    )
                }
                if (op == FormatOpType.REGEX_REPLACE) {
                    OutlinedTextField(
                        shape = PickMsgShape.control,
                        value = to,
                        onValueChange = { to = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("替换为") },
                        singleLine = true,
                    )
                } else if (op == FormatOpType.PREFIX || op == FormatOpType.SUFFIX) {
                    OutlinedTextField(
                        shape = PickMsgShape.control,
                        value = to,
                        onValueChange = { to = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (op == FormatOpType.PREFIX) "前缀文本" else "后缀文本") },
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(FormatOp(op = op, target = target, from = from, to = to)) }) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

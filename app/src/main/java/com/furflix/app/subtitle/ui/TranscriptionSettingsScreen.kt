package com.furflix.app.subtitle.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furflix.app.R
import com.furflix.app.subtitle.StylePreset
import com.furflix.app.subtitle.TranscriptionException
import com.furflix.app.subtitle.TranscriptionPrompt
import com.furflix.app.subtitle.TranscriptionService
import com.furflix.app.subtitle.TranscriptionSettings
import com.furflix.app.ui.components.HazeTopAppBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch

/**
 * 字幕生成设置：OpenAI 兼容服务配置 + 风格预设管理 + 提示词模板。
 *
 * 与翻译设置页同构（DataStore 直读直写、无 ViewModel），字段改动即时落盘；
 * 模板较长，故用本地草稿 + 显式保存，避免每敲一键写一次 DataStore。
 */
@Composable
fun TranscriptionSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { TranscriptionSettings.getInstance(context) }
    val scope = rememberCoroutineScope()

    val config by settings.config.collectAsState(initial = null)
    val presets by settings.presets.collectAsState(initial = TranscriptionPrompt.DEFAULT_PRESETS)
    val activeIds by settings.activePresetIds.collectAsState(initial = emptySet())

    var fetching by remember { mutableStateOf(false) }
    var models by remember { mutableStateOf<List<String>>(emptyList()) }
    var fetchError by remember { mutableStateOf<TranscriptionException.Kind?>(null) }
    var presetDraft by remember { mutableStateOf<StylePreset?>(null) }
    var showModelDialog by remember { mutableStateOf(false) }

    val hazeState = remember { HazeState() }
    val current = config

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HazeTopAppBar(
                title = { Text(stringResource(R.string.subtitle_title)) },
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .hazeSource(state = hazeState)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle(stringResource(R.string.subtitle_section_service))

            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.subtitle_enabled),
                    subtitle = stringResource(R.string.subtitle_enabled_desc),
                    checked = current?.enabled ?: false,
                    onChecked = { value -> scope.launch { settings.saveEnabled(value) } }
                )
                CardDivider()
                FieldRow(
                    label = stringResource(R.string.subtitle_base_url),
                    value = current?.baseUrl.orEmpty(),
                    onValueChange = { scope.launch { settings.saveBaseUrl(it) } }
                )
                CardDivider()
                FieldRow(
                    label = stringResource(R.string.subtitle_api_key),
                    value = current?.apiKey.orEmpty(),
                    onValueChange = { scope.launch { settings.saveApiKey(it) } },
                    password = true
                )
                CardDivider()
                FieldRow(
                    label = stringResource(R.string.subtitle_model),
                    value = current?.model.orEmpty(),
                    onValueChange = { scope.launch { settings.saveModel(it) } }
                )
                CardDivider()
                // 模型列表来自 /v1/models：填好 Base URL 与 Key 后一键拉取再选
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    TextButton(
                        enabled = !fetching && current?.canFetchModels == true,
                        onClick = {
                            val snapshot = current ?: return@TextButton
                            fetching = true
                            fetchError = null
                            scope.launch {
                                try {
                                    models = TranscriptionService.getInstance().listModels(snapshot)
                                    showModelDialog = models.isNotEmpty()
                                } catch (e: TranscriptionException) {
                                    fetchError = e.kind
                                } catch (e: Exception) {
                                    fetchError = TranscriptionException.Kind.NETWORK
                                } finally {
                                    fetching = false
                                }
                            }
                        }
                    ) {
                        if (fetching) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.subtitle_fetch_models), fontSize = 12.sp)
                    }
                    val errorKind = fetchError
                    if (errorKind != null) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = transcriptionErrorText(errorKind),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            SectionTitle(stringResource(R.string.subtitle_section_presets))

            SettingsCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.subtitle_presets_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = {
                            presetDraft = StylePreset(
                                id = "custom_" + System.currentTimeMillis(),
                                name = "",
                                prompt = ""
                            )
                        }
                    ) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.subtitle_preset_add))
                    }
                }
                CardDivider()
                presets.forEachIndexed { index, preset ->
                    if (index > 0) CardDivider()
                    PresetRow(
                        preset = preset,
                        active = preset.id in activeIds,
                        onToggle = { enabled ->
                            val next = if (enabled) activeIds + preset.id else activeIds - preset.id
                            scope.launch { settings.saveActivePresetIds(next) }
                        },
                        onEdit = { presetDraft = preset },
                        onDelete = {
                            scope.launch {
                                settings.savePresets(presets.filterNot { it.id == preset.id })
                                settings.saveActivePresetIds(activeIds - preset.id)
                            }
                        }
                    )
                }
                CardDivider()
                TextButton(
                    onClick = {
                        scope.launch {
                            settings.savePresets(TranscriptionPrompt.DEFAULT_PRESETS)
                            settings.saveActivePresetIds(emptySet())
                        }
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Text(stringResource(R.string.subtitle_preset_reset), fontSize = 12.sp)
                }
            }

            SectionTitle(stringResource(R.string.subtitle_section_template))

            SettingsCard {
                TemplateEditor(
                    template = current?.template ?: TranscriptionPrompt.DEFAULT_TEMPLATE,
                    onSave = { scope.launch { settings.saveTemplate(it) } },
                    onReset = { scope.launch { settings.saveTemplate(TranscriptionPrompt.DEFAULT_TEMPLATE) } }
                )
            }
        }
    }

    // ── 模型选择对话框 ─────────────────────────────────────────────────
    if (showModelDialog) {
        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text(stringResource(R.string.subtitle_pick_model)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    models.forEach { id ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RadioButton(
                                selected = current?.model == id,
                                onClick = {
                                    scope.launch { settings.saveModel(id) }
                                    showModelDialog = false
                                }
                            )
                            Text(id, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModelDialog = false }) {
                    Text(stringResource(R.string.subtitle_action_cancel))
                }
            }
        )
    }

    // ── 预设编辑对话框（新增与编辑共用）───────────────────────────────
    presetDraft?.let { draft ->
        PresetEditorDialog(
            draft = draft,
            onDismiss = { presetDraft = null },
            onConfirm = { edited ->
                val next = presets.filterNot { it.id == edited.id } + edited
                scope.launch { settings.savePresets(next) }
                presetDraft = null
            }
        )
    }
}

@Composable
private fun PresetRow(
    preset: StylePreset,
    active: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            val untitledLabel = stringResource(R.string.subtitle_preset_untitled)
            Text(
                text = preset.name.ifEmpty { untitledLabel },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (preset.builtIn) {
                    stringResource(R.string.subtitle_preset_builtin)
                } else {
                    preset.prompt.take(80)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.subtitle_preset_edit))
        }
        IconButton(onClick = onDelete, enabled = !preset.builtIn) {
            Icon(
                Icons.Default.Delete,
                contentDescription = stringResource(R.string.subtitle_preset_delete),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = active, onCheckedChange = onToggle)
    }
}

@Composable
private fun PresetEditorDialog(
    draft: StylePreset,
    onDismiss: () -> Unit,
    onConfirm: (StylePreset) -> Unit
) {
    var name by remember(draft.id) { mutableStateOf(draft.name) }
    var prompt by remember(draft.id) { mutableStateOf(draft.prompt) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.subtitle_preset_edit)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.subtitle_preset_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.subtitle_preset_prompt)) },
                    minLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && prompt.isNotBlank(),
                onClick = { onConfirm(draft.copy(name = name.trim(), prompt = prompt.trim())) }
            ) {
                Text(stringResource(R.string.subtitle_action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.subtitle_action_cancel)) }
        }
    )
}

/** 模板编辑：本地草稿 + 显式保存（模板很长，不宜逐键写盘）。 */
@Composable
private fun TemplateEditor(
    template: String,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var draft by remember(template) { mutableStateOf(template) }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        // 把三个键名直接摊开写清楚：它们不是"示例文本"，而是会被软件替换的占位符
        Text(
            text = stringResource(
                R.string.subtitle_template_keys_hint,
                TranscriptionPrompt.VAR_EXTRA_STYLE,
                TranscriptionPrompt.VAR_TITLE,
                TranscriptionPrompt.VAR_DESCRIPTION
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(stringResource(R.string.subtitle_template)) },
            minLines = 8,
            modifier = Modifier.fillMaxWidth()
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onSave(draft) }, enabled = draft != template) {
                Text(stringResource(R.string.subtitle_template_save), fontSize = 12.sp)
            }
            TextButton(
                onClick = {
                    onReset()
                    draft = TranscriptionPrompt.DEFAULT_TEMPLATE
                }
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.subtitle_template_reset), fontSize = 12.sp)
            }
        }
    }
}

// ── 本地小部件（与翻译设置页同款，避免跨包暴露）─────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column(content = content)
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 54.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

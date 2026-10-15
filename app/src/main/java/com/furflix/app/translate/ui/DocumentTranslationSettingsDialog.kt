package com.furflix.app.translate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.KeyboardOptions
import com.furflix.app.R
import com.furflix.app.translate.data.TRANSLATION_LANGUAGES
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationConfig
import com.furflix.app.translate.data.TranslationDisplayMode
import com.furflix.app.translate.data.TranslationProvider
import com.furflix.app.translate.data.TranslationSettings
import com.furflix.app.translate.data.TranslationUiPrefs
import com.furflix.app.translate.data.languageNativeName
import kotlinx.coroutines.launch

/**
 * 文档查看器专用翻译服务配置：设置页与查看器内共用同一组件（用户要求在两处
 * 都可配置）。开启后用独立的服务商/目标语言/密钥翻译文档与 PDF 正文，
 * 与标签/评论等短文本的全局配置互不干扰——长文常用大模型，短文本用免密钥
 * 服务，两者成本与质量诉求不同。
 *
 * 未填的字段逐字段回落全局配置（见 TranslationSettings.documentConfig），
 * 因此"开启但只改服务商"也能立刻工作，不会开箱即缺密钥。
 */
@Composable
fun DocumentTranslationSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { TranslationSettings.getInstance(context) }
    val scope = rememberCoroutineScope()

    val enabled by settings.docOverrideFlow.collectAsState(initial = false)
    val snapshot by settings.docConfigSnapshot.collectAsState(initial = null)
    val global by settings.config.collectAsState(initial = TranslationConfig())
    // 文档与小说阅读器共用 DESCRIPTION 类别，故这里改的显示模式同时作用于两者
    val prefs by settings.uiPrefs.collectAsState(initial = TranslationUiPrefs.DEFAULT)
    val docDisplayMode = prefs.displayMode(TranslationCategory.DESCRIPTION)

    // 展示值：独立配置留空时呈现全局值，避免面板看起来"没配置"
    val provider = snapshot?.provider ?: global.provider
    val targetLanguage = snapshot?.targetLanguage?.ifBlank { global.targetLanguage } ?: global.targetLanguage
    val azureKey = snapshot?.azureKey.orEmpty()
    val apiKey = snapshot?.apiKey.orEmpty()
    val baseUrl = snapshot?.baseUrl.orEmpty()
    val model = snapshot?.model.orEmpty()

    var showProviderMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showLanguageMenu by remember { androidx.compose.runtime.mutableStateOf(false) }
    var showModeMenu by remember { androidx.compose.runtime.mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.document_translation_service),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.document_translation_service_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))

                // 独立服务总开关
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { settings.saveDocOverride(!enabled) } }
                        .padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.document_translation_separate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.document_translation_separate_desc),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { scope.launch { settings.saveDocOverride(it) } }
                    )
                }

                if (enabled) {
                    Spacer(Modifier.height(8.dp))

                    // 服务商选择
                    Column {
                        Text(
                            stringResource(R.string.translation_provider),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showProviderMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = providerLabel(provider),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showProviderMenu,
                            onDismissRequest = { showProviderMenu = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            TranslationProvider.entries.forEach { p ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(providerLabel(p)) },
                                    onClick = {
                                        scope.launch { settings.saveDocProvider(p) }
                                        showProviderMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 目标语言
                    Column {
                        Text(
                            stringResource(R.string.translation_target_language),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showLanguageMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = languageNativeName(targetLanguage),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            TRANSLATION_LANGUAGES.forEach { (code, name) ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(name) },
                                    onClick = {
                                        scope.launch { settings.saveDocTargetLanguage(code) }
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 译文显示模式：双语对照（原文 + 译文）/ 仅显示译文（译文就绪后隐藏原文）。
                    // 与设置页"显示方式"里的 DESCRIPTION 项是同一个持久化设置。
                    Column {
                        Text(
                            stringResource(R.string.translation_display_modes),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable { showModeMenu = true }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                Icon(
                                    Icons.Default.Translate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = stringResource(
                                        if (docDisplayMode == TranslationDisplayMode.TRANSLATION_ONLY) {
                                            R.string.translation_mode_translation_only
                                        } else {
                                            R.string.translation_mode_bilingual
                                        }
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        androidx.compose.material3.DropdownMenu(
                            expanded = showModeMenu,
                            onDismissRequest = { showModeMenu = false },
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            listOf(
                                TranslationDisplayMode.BILINGUAL to R.string.translation_mode_bilingual,
                                TranslationDisplayMode.TRANSLATION_ONLY to R.string.translation_mode_translation_only
                            ).forEach { (mode, labelRes) ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(stringResource(labelRes)) },
                                    onClick = {
                                        scope.launch {
                                            settings.saveDisplayMode(TranslationCategory.DESCRIPTION, mode)
                                        }
                                        showModeMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    when (provider) {
                        TranslationProvider.AZURE -> {
                            DocField(
                                label = stringResource(R.string.translation_azure_key),
                                value = azureKey,
                                hint = global.azureKey.ifBlank { null }?.let { "global" },
                                password = true
                            ) { scope.launch { settings.saveDocAzureKey(it) } }
                        }
                        TranslationProvider.OPENAI -> {
                            DocField(
                                label = stringResource(R.string.translation_api_key),
                                value = apiKey,
                                password = true
                            ) { scope.launch { settings.saveDocApiKey(it) } }
                            Spacer(Modifier.height(8.dp))
                            DocField(
                                label = stringResource(R.string.translation_base_url),
                                value = baseUrl,
                                hint = global.baseUrl
                            ) { scope.launch { settings.saveDocBaseUrl(it) } }
                            Spacer(Modifier.height(8.dp))
                            DocField(
                                label = stringResource(R.string.translation_model),
                                value = model,
                                hint = global.model
                            ) { scope.launch { settings.saveDocModel(it) } }
                        }
                        else -> Unit
                    }

                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.document_translation_fallback_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cd_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun DocField(
    label: String,
    value: String,
    hint: String? = null,
    password: Boolean = false,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            placeholder = hint?.let { { Text(it, fontSize = 12.sp) } },
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall
        )
    }
}

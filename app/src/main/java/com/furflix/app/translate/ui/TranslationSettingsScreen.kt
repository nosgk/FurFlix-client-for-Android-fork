package com.furflix.app.translate.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Title
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.Dialog
import com.furflix.app.R
import com.furflix.app.translate.data.TranslationCache
import com.furflix.app.translate.data.TranslationCategory
import com.furflix.app.translate.data.TranslationConfig
import com.furflix.app.translate.data.TranslationDisplayMode
import com.furflix.app.translate.data.TranslationProvider
import com.furflix.app.translate.data.TranslationSettings
import com.furflix.app.translate.data.TranslationUiPrefs
import com.furflix.app.translate.data.StoryGranularity
import com.furflix.app.translate.data.TRANSLATION_LANGUAGES
import com.furflix.app.ui.components.HazeTopAppBar
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun TranslationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { TranslationSettings.getInstance(context) }
    val cache = remember { TranslationCache.getInstance(context) }
    val scope = rememberCoroutineScope()

    val prefs by settings.uiPrefs.collectAsState(initial = TranslationUiPrefs.DEFAULT)
    val config by settings.config.collectAsState(initial = TranslationConfig())

    var cacheEpoch by remember { mutableIntStateOf(0) }
    var cacheStats by remember { mutableStateOf(0 to 0) }
    LaunchedEffect(cacheEpoch) { cacheStats = cache.stats() }

    var showLanguageDialog by remember { mutableStateOf(false) }
    var showProviderDialog by remember { mutableStateOf(false) }
    var showConcurrencyDialog by remember { mutableStateOf(false) }
    var showGranularityDialog by remember { mutableStateOf(false) }
    var modeDialogCategory by remember { mutableStateOf<TranslationCategory?>(null) }

    val hazeState = remember { HazeState() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HazeTopAppBar(
                title = { Text(stringResource(R.string.translation_title)) },
                hazeState = hazeState,
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            SectionTitle(stringResource(R.string.settings_general))

            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.translation_enabled),
                    subtitle = stringResource(R.string.translation_enabled_desc),
                    checked = prefs.enabled,
                    onChecked = { scope.launch { settings.saveEnabled(it) } }
                )
            }

            // 自动翻译按内容类型逐项开关（原全局开关拆分）
            SectionTitle(stringResource(R.string.translation_auto_section))
            SettingsCard {
                SwitchRow(
                    title = stringResource(R.string.translation_auto_cat_title),
                    subtitle = stringResource(R.string.translation_auto_cat_subtitle),
                    checked = prefs.titleAuto,
                    onChecked = { scope.launch { settings.saveTitleAuto(it) } }
                )
                CardDivider()
                SwitchRow(
                    title = stringResource(R.string.translation_auto_cat_description),
                    subtitle = stringResource(R.string.translation_auto_cat_subtitle),
                    checked = prefs.descriptionAuto,
                    onChecked = { scope.launch { settings.saveDescriptionAuto(it) } }
                )
                CardDivider()
                SwitchRow(
                    title = stringResource(R.string.translation_auto_cat_tag),
                    subtitle = stringResource(R.string.translation_auto_cat_subtitle),
                    checked = prefs.tagsAuto,
                    onChecked = { scope.launch { settings.saveTagsAuto(it) } }
                )
                CardDivider()
                SwitchRow(
                    title = stringResource(R.string.translation_auto_cat_comment),
                    subtitle = stringResource(R.string.translation_auto_cat_subtitle),
                    checked = prefs.commentAuto,
                    onChecked = { scope.launch { settings.saveCommentAuto(it) } }
                )
                CardDivider()
                SwitchRow(
                    title = stringResource(R.string.translation_auto_cat_profile),
                    subtitle = stringResource(R.string.translation_auto_cat_subtitle),
                    checked = prefs.profileAuto,
                    onChecked = { scope.launch { settings.saveProfileAuto(it) } }
                )
                CardDivider()
                SwitchRow(
                    title = stringResource(R.string.translation_auto_cat_journal),
                    subtitle = stringResource(R.string.translation_auto_cat_subtitle),
                    checked = prefs.journalAuto,
                    onChecked = { scope.launch { settings.saveJournalAuto(it) } }
                )
            }

            SectionTitle(stringResource(R.string.translation_target_language))
            SettingsCard {
                SelectRow(
                    icon = Icons.Default.Language,
                    title = stringResource(R.string.translation_target_language),
                    current = languageNativeName(config.targetLanguage),
                    onClick = { showLanguageDialog = true }
                )
                CardDivider()
                SelectRow(
                    icon = Icons.Default.Translate,
                    title = stringResource(R.string.translation_provider),
                    current = providerLabel(config.provider),
                    onClick = { showProviderDialog = true }
                )
            }

            if (config.provider == TranslationProvider.AZURE) {
                SectionTitle(stringResource(R.string.translation_provider_azure))
                SettingsCard {
                    FieldRow(
                        label = stringResource(R.string.translation_azure_key),
                        value = config.azureKey,
                        password = true,
                        onValueChange = { scope.launch { settings.saveAzureKey(it) } }
                    )
                }
            }

            if (config.provider == TranslationProvider.OPENAI) {
                SectionTitle(stringResource(R.string.translation_provider_openai))
                SettingsCard {
                    FieldRow(
                        label = stringResource(R.string.translation_api_key),
                        value = config.apiKey,
                        password = true,
                        onValueChange = { scope.launch { settings.saveApiKey(it) } }
                    )
                    CardDivider()
                    FieldRow(
                        label = stringResource(R.string.translation_base_url),
                        value = config.baseUrl,
                        onValueChange = { scope.launch { settings.saveBaseUrl(it) } },
                        hint = stringResource(R.string.translation_base_url_https_hint),
                        uriKeyboard = true
                    )
                    CardDivider()
                    FieldRow(
                        label = stringResource(R.string.translation_model),
                        value = config.model,
                        onValueChange = { scope.launch { settings.saveModel(it) } }
                    )
                    CardDivider()
                    FieldRow(
                        label = stringResource(R.string.translation_system_prompt),
                        value = config.systemPrompt,
                        minLines = 2,
                        onValueChange = { scope.launch { settings.saveSystemPrompt(it) } },
                        hint = stringResource(R.string.translation_prompts_reset) + " · " +
                            stringResource(R.string.translation_prompt_default_hint)
                    )
                    CardDivider()
                    FieldRow(
                        label = stringResource(R.string.translation_user_prompt),
                        value = config.userPrompt,
                        minLines = 3,
                        onValueChange = { scope.launch { settings.saveUserPrompt(it) } },
                        hint = stringResource(R.string.translation_prompt_tokens_hint)
                    )
                    TextButton(
                        onClick = {
                            scope.launch {
                                settings.saveSystemPrompt("")
                                settings.saveUserPrompt("")
                            }
                        },
                        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                    ) {
                        Text(stringResource(R.string.translation_prompts_reset))
                    }
                }
            }

            SectionTitle(stringResource(R.string.translation_display_modes))
            SettingsCard {
                TranslationCategory.entries.forEachIndexed { index, category ->
                    if (index > 0) CardDivider()
                    SelectRow(
                        icon = category.icon,
                        title = stringResource(category.titleRes()),
                        current = stringResource(
                            if (prefs.displayMode(category) == TranslationDisplayMode.TRANSLATION_ONLY) {
                                R.string.translation_mode_translation_only
                            } else {
                                R.string.translation_mode_bilingual
                            }
                        ),
                        onClick = { modeDialogCategory = category }
                    )
                }
            }

            SectionTitle(stringResource(R.string.translation_engine))
            SettingsCard {
                SelectRow(
                    icon = Icons.Default.Speed,
                    title = stringResource(R.string.translation_concurrency),
                    current = stringResource(R.string.translation_concurrency_value, config.maxConcurrency),
                    onClick = { showConcurrencyDialog = true }
                )
                CardDivider()
                SelectRow(
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    title = stringResource(R.string.translation_story_granularity),
                    current = stringResource(
                        if (prefs.storyGranularity == StoryGranularity.SENTENCE) {
                            R.string.story_granularity_sentence
                        } else {
                            R.string.story_granularity_paragraph
                        }
                    ),
                    onClick = { showGranularityDialog = true }
                )
            }

            SectionTitle(stringResource(R.string.translation_cache))
            SettingsCard {
                SelectRow(
                    icon = Icons.Default.Cached,
                    title = stringResource(R.string.translation_cache),
                    current = stringResource(
                        R.string.translation_cache_stats,
                        cacheStats.first,
                        formatBytes(cacheStats.second)
                    ),
                    onClick = {}
                )
                CardDivider()
                // 组合内解析文案（配置感知），lambda 只捕获结果值
                val clearedMessage = stringResource(R.string.translation_cache_cleared)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch {
                                cache.clear()
                                cacheEpoch++
                                Toast.makeText(context, clearedMessage, Toast.LENGTH_SHORT).show()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.translation_cache_clear),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showLanguageDialog) {
        SelectionDialog(
            title = stringResource(R.string.translation_target_language),
            icon = Icons.Default.Language,
            options = TRANSLATION_LANGUAGES.map { DialogOption(it.first, it.second) },
            selected = config.targetLanguage,
            onSelect = { scope.launch { settings.saveTargetLanguage(it) } },
            onDismiss = { showLanguageDialog = false }
        )
    }

    if (showProviderDialog) {
        SelectionDialog(
            title = stringResource(R.string.translation_provider),
            icon = Icons.Default.Translate,
            options = TranslationProvider.entries.map { DialogOption(it, providerLabel(it)) },
            selected = config.provider,
            onSelect = { scope.launch { settings.saveProvider(it) } },
            onDismiss = { showProviderDialog = false }
        )
    }

    if (showConcurrencyDialog) {
        SelectionDialog(
            title = stringResource(R.string.translation_concurrency),
            icon = Icons.Default.Speed,
            options = (1..8).map { DialogOption(it, it.toString()) },
            selected = config.maxConcurrency,
            onSelect = { scope.launch { settings.saveConcurrency(it) } },
            onDismiss = { showConcurrencyDialog = false }
        )
    }

    if (showGranularityDialog) {
        SelectionDialog(
            title = stringResource(R.string.translation_story_granularity),
            icon = Icons.AutoMirrored.Filled.MenuBook,
            options = listOf(
                DialogOption(StoryGranularity.PARAGRAPH, stringResource(R.string.story_granularity_paragraph)),
                DialogOption(StoryGranularity.SENTENCE, stringResource(R.string.story_granularity_sentence)),
            ),
            selected = prefs.storyGranularity,
            onSelect = { scope.launch { settings.saveStoryGranularity(it) } },
            onDismiss = { showGranularityDialog = false }
        )
    }

    modeDialogCategory?.let { category ->
        SelectionDialog(
            title = stringResource(category.titleRes()),
            icon = category.icon,
            options = listOf(
                DialogOption(TranslationDisplayMode.BILINGUAL, stringResource(R.string.translation_mode_bilingual)),
                DialogOption(TranslationDisplayMode.TRANSLATION_ONLY, stringResource(R.string.translation_mode_translation_only)),
            ),
            selected = prefs.displayMode(category),
            onSelect = { scope.launch { settings.saveDisplayMode(category, it) } },
            onDismiss = { modeDialogCategory = null }
        )
    }
}

// -----------------------------------------------------------------------------------------
// Helpers
// -----------------------------------------------------------------------------------------

private data class DialogOption<T>(val value: T, val label: String)

private fun TranslationCategory.titleRes(): Int = when (this) {
    TranslationCategory.DESCRIPTION -> R.string.translation_content_description
    TranslationCategory.TITLE -> R.string.translation_content_title
    TranslationCategory.TAG -> R.string.translation_content_tags
    TranslationCategory.COMMENT -> R.string.translation_content_comments
    TranslationCategory.USER_PROFILE -> R.string.translation_content_profile
    TranslationCategory.JOURNAL -> R.string.translation_content_journals
}

private val TranslationCategory.icon: ImageVector
    get() = when (this) {
        TranslationCategory.DESCRIPTION -> Icons.Default.Folder
        TranslationCategory.TITLE -> Icons.Default.Title
        TranslationCategory.TAG -> Icons.Default.Pets
        TranslationCategory.COMMENT -> Icons.Default.GTranslate
        TranslationCategory.USER_PROFILE -> Icons.Default.Public
        TranslationCategory.JOURNAL -> Icons.AutoMirrored.Filled.MenuBook
    }

@Composable
private fun providerLabel(provider: TranslationProvider): String = stringResource(
    when (provider) {
        TranslationProvider.GOOGLE -> R.string.translation_provider_google
        TranslationProvider.CHROME -> R.string.translation_provider_chrome
        TranslationProvider.EDGE -> R.string.translation_provider_edge
        TranslationProvider.AZURE -> R.string.translation_provider_azure
        TranslationProvider.OPENAI -> R.string.translation_provider_openai
    }
)

// 语言名恒显母语名（与设置页语言选择器原则一致）
private fun languageNativeName(code: String): String =
    com.furflix.app.translate.data.languageNativeName(code)

private fun formatBytes(bytes: Int): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.ROOT, "%.1f MB", bytes / 1048576f)
    bytes >= 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}

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
    androidx.compose.material3.HorizontalDivider(
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
private fun SelectRow(
    icon: ImageVector,
    title: String,
    current: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Box32(icon)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                current,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FieldRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    minLines: Int = 1,
    hint: String? = null,
    uriKeyboard: Boolean = false
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = minLines <= 1,
            minLines = minLines,
            shape = RoundedCornerShape(16.dp),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = when {
                    password -> KeyboardType.Password
                    uriKeyboard -> KeyboardType.Uri
                    else -> KeyboardType.Text
                }
            )
        )
        if (hint != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Box32(icon: ImageVector) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun <T> SelectionDialog(
    title: String,
    icon: ImageVector,
    options: List<DialogOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(16.dp))
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    options.forEach { option ->
                        val isSelected = option.value == selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else androidx.compose.ui.graphics.Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                    else androidx.compose.ui.graphics.Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    onSelect(option.value)
                                    onDismiss()
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            RadioButton(selected = isSelected, onClick = {
                                onSelect(option.value)
                                onDismiss()
                            })
                            Text(
                                option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

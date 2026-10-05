package com.furflix.app.ui.screens

import androidx.compose.material3.MaterialTheme

import android.os.Environment
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.res.stringResource
import com.furflix.app.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.furflix.app.ui.components.DefaultHazeStyle
import com.furflix.app.ui.components.HazeTopAppBar
import com.furflix.app.ui.theme.*
import com.furflix.app.viewmodel.MainViewModel
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onNavigateToAppearance: () -> Unit,
    onNavigateToFilters: () -> Unit,
    onNavigateToTranslation: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToStorage: () -> Unit = {},
    viewModel: MainViewModel = viewModel()
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val username by viewModel.username.collectAsState()

    // 剪贴板链接识别开关（回前台检测 FA 链接并询问跳转）
    val context2 = androidx.compose.ui.platform.LocalContext.current
    val repository2 = remember { com.furflix.app.data.repository.FurRepository.getInstance(context2) }
    val scope2 = rememberCoroutineScope()
    val clipboardLinkEnabled by repository2.clipboardLinkEnabledFlow.collectAsState(initial = true)

    val downloadPath by viewModel.downloadFolder.collectAsState()
    val displayPath = downloadPath.ifEmpty { "Pictures/FurFlix" }
    var showDownloadFolderDialog by remember { mutableStateOf(false) }

    var showLanguageDialog by remember { mutableStateOf(false) }
    val appLocales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
    val currentLanguageCode = appLocales[0]?.language
    // 语言锚点恒显母语名(不参与翻译);"zh" 仅作比对键,应用侧实际写入 zh-CN
    val languageNameMap = mapOf("en" to "English", "ru" to "Русский", "uk" to "Українська", "zh" to "中文")
    val currentLanguageName = currentLanguageCode?.let { languageNameMap[it] }
        ?: stringResource(R.string.settings_language_system)

    val hazeState = remember { HazeState() }


    Scaffold(
        containerColor = Color.Black,
        topBar = {
            HazeTopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                hazeState = hazeState,
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
                .hazeSource(state = hazeState)
        ) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {

                // --- Account Hero Card ---
                AccountHeroCard(
                    isLoggedIn = isLoggedIn,
                    username = username,
                    onLoginClick = onNavigateToLogin,
                    onLogoutClick = { viewModel.logout() }
                )

                // --- General Group ---
                SettingsSectionHeader(stringResource(R.string.settings_general))
                SettingsGroupCard {
                    SettingsRow(
                        icon = Icons.Default.ColorLens,
                        iconColor = Color(0xFF9C27B0),
                        title = stringResource(R.string.settings_appearance),
                        subtitle = stringResource(R.string.settings_appearance_desc),
                        onClick = onNavigateToAppearance
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.FilterAlt,
                        iconColor = Color(0xFFFF9800),
                        title = stringResource(R.string.settings_filters_content),
                        subtitle = stringResource(R.string.settings_filters_desc),
                        onClick = onNavigateToFilters
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Translate,
                        iconColor = Color(0xFF00BCD4),
                        title = stringResource(R.string.settings_translation),
                        subtitle = stringResource(R.string.settings_translation_desc),
                        onClick = onNavigateToTranslation
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.Language,
                        iconColor = Color(0xFF2196F3),
                        title = stringResource(R.string.settings_language),
                        subtitle = stringResource(R.string.settings_language_desc),
                        onClick = { showLanguageDialog = true },
                        trailing = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = currentLanguageName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            }
                        }
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.ContentPaste,
                        iconColor = Color(0xFF8BC34A),
                        title = stringResource(R.string.settings_clipboard_link),
                        subtitle = stringResource(R.string.settings_clipboard_link_desc),
                        onClick = { scope2.launch { repository.saveClipboardLinkEnabled(!clipboardLinkEnabled) } },
                        trailing = {
                            Switch(
                                checked = clipboardLinkEnabled,
                                onCheckedChange = { checked ->
                                    scope2.launch { repository.saveClipboardLinkEnabled(checked) }
                                }
                            )
                        }
                    )
                }

                // --- Storage Group ---
                SettingsSectionHeader(stringResource(R.string.settings_storage))
                SettingsGroupCard {
                    SettingsRow(
                        icon = Icons.Default.DeleteSweep,
                        iconColor = Color(0xFFFF5252),
                        title = stringResource(R.string.settings_storage_management),
                        subtitle = stringResource(R.string.settings_storage_management_desc),
                        onClick = onNavigateToStorage
                    )
                    SettingsDivider()
                    SettingsRow(
                        icon = Icons.Default.FolderOpen,
                        iconColor = Color(0xFF4CAF50),
                        title = stringResource(R.string.settings_downloads_folder),
                        subtitle = displayPath,
                        onClick = { showDownloadFolderDialog = true }
                    )
                }

                // --- About Group ---
                SettingsSectionHeader(stringResource(R.string.settings_about))
                AboutHeroCard(onClick = onNavigateToAbout)

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showLanguageDialog) {
        Dialog(onDismissRequest = { showLanguageDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DialogIconTile(icon = Icons.Default.Language, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isSystemSelected = appLocales.isEmpty
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isSystemSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (isSystemSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                        androidx.core.os.LocaleListCompat.getEmptyLocaleList()
                                    )
                                    showLanguageDialog = false
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.settings_language_system),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSystemSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSystemSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSystemSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        languageNameMap.forEach { (code, name) ->
                            val isSelected = currentLanguageCode == code
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                                    .border(
                                        1.dp,
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else Color.Transparent,
                                        RoundedCornerShape(14.dp)
                                    )
                                    .clickable {
                                        val tag = if (code == "zh") "zh-CN" else code
                                        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                            androidx.core.os.LocaleListCompat.forLanguageTags(tag)
                                        )
                                        showLanguageDialog = false
                                    }
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isSelected) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadFolderDialog) {
        var selectedBaseDir by remember { mutableStateOf(if (displayPath.startsWith("Downloads")) "Downloads" else if (displayPath.startsWith("DCIM")) "DCIM" else "Pictures") }
        var subfolderName by remember { mutableStateOf(if (displayPath.contains("/")) displayPath.substringAfter("/") else "FurFlix") }

        Dialog(onDismissRequest = { showDownloadFolderDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DialogIconTile(icon = Icons.Default.FolderSpecial, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(stringResource(R.string.settings_download_dialog_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(stringResource(R.string.settings_download_base_dir), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Pictures", "Downloads", "DCIM").forEach { dir ->
                            val isSelected = selectedBaseDir == dir
                            val bgColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent)
                            val borderColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            val textColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(bgColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                    .clickable { selectedBaseDir = dir }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon = when(dir) {
                                    "Pictures" -> Icons.Default.Image
                                    "Downloads" -> Icons.Default.Download
                                    else -> Icons.Default.CameraAlt
                                }
                                Icon(icon, contentDescription = null, tint = textColor, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    when(dir) {
                                        "Pictures" -> stringResource(R.string.settings_download_pictures)
                                        "Downloads" -> stringResource(R.string.settings_download_downloads)
                                        else -> stringResource(R.string.settings_download_dcim)
                                    },
                                    color = textColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (isSelected) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(stringResource(R.string.settings_download_subfolder), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = subfolderName,
                        onValueChange = { subfolderName = it.replace(Regex("[^a-zA-Z0-9_-]"), "") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.settings_download_path_preview, "$selectedBaseDir/${subfolderName.ifEmpty { "..." }}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDownloadFolderDialog = false }) {
                            Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val newFolder = if (subfolderName.isEmpty()) selectedBaseDir else "$selectedBaseDir/$subfolderName"
                                viewModel.setDownloadFolder(newFolder)
                                showDownloadFolderDialog = false
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.action_save))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountHeroCard(
    isLoggedIn: Boolean,
    username: String,
    onLoginClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(24.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (!isLoggedIn) Modifier.clickable(onClick = onLoginClick) else Modifier),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .border(
                            width = 1.5.dp,
                            color = primary.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.25f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = primary, modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLoggedIn) username else stringResource(R.string.settings_not_logged_in),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (isLoggedIn) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF2EA043)))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.settings_account_connected),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF2EA043)
                        )
                    }
                } else {
                    Text(
                        text = stringResource(R.string.settings_login_to_furaffinity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isLoggedIn) {
                IconButton(
                    onClick = onLogoutClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF5252).copy(alpha = 0.08f))
                        .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.22f), CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.cd_logout), tint = Color(0xFFFF5252), modifier = Modifier.size(18.dp))
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = primary, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
internal fun SettingsSectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp)
    )
}

@Composable
internal fun SettingsGroupCard(content: @Composable ColumnScope.() -> Unit) {
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
internal fun SettingsDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 76.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    )
}

@Composable
private fun DialogIconTile(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AboutHeroCard(onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(primary.copy(alpha = 0.12f))
                    .border(1.dp, primary.copy(alpha = 0.30f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Pets, contentDescription = null, tint = primary, modifier = Modifier.size(28.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "FurFlix",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    stringResource(R.string.settings_about_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CtaChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
internal fun SettingsRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(30.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        if (trailing != null) {
            trailing()
        } else {
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        }
    }
}

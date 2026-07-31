package me.rerere.rikkahub.ui.pages.translator

import android.content.ClipData
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clipboard
import me.rerere.hugeicons.stroke.Cancel01
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.BottomAppBarDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.ModelType
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ai.ModelSelector
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape
import me.rerere.rikkahub.utils.getText
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

@Composable
fun TranslatorPage(vm: TranslatorVM = koinViewModel()) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val inputText by vm.inputText.collectAsStateWithLifecycle()
    val translatedText by vm.translatedText.collectAsStateWithLifecycle()
    val targetLanguage by vm.targetLanguage.collectAsStateWithLifecycle()
    val translating by vm.translating.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val toaster = LocalToaster.current
    val scope = rememberCoroutineScope()
    val fallbackError = stringResource(R.string.translator_page_error)

    // 处理错误
    LaunchedEffect(Unit) {
        vm.errorFlow.collect { error ->
            toaster.show(error.message ?: fallbackError, type = ToastType.Error)
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.translator_page_title))
                },
                navigationIcon = {
                    BackButton()
                },
                actions = {
                    ModelSelector(
                        modelId = settings.translateModeId,
                        onSelect = {
                            vm.updateSettings(settings.copy(translateModeId = it.id))
                        },
                        providers = settings.providers,
                        type = ModelType.CHAT,
                        onlyIcon = true,
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        bottomBar = {
            BottomBar(
                translating = translating,
                onTranslate = {
                    vm.translate()
                },
                onCancelTranslation = {
                    vm.cancelTranslation()
                },
                onLanguageSelected = {
                    vm.updateTargetLanguage(it)
                },
                targetLanguage = targetLanguage
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CustomColors.cardColorsOnSurfaceContainer,
                shape = rememberScreenEdgeCornerShape(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { vm.updateInputText(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.translator_page_input_placeholder)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent
                    ),
                    maxLines = 10,
                    textStyle = MaterialTheme.typography.headlineSmall,
                )

                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            clipboard.getClipEntry()?.clipData?.getText()?.let {
                                vm.updateInputText(it)
                            }
                        }
                    }
                ) {
                    Icon(HugeIcons.Clipboard, null)
                    Text(stringResource(R.string.translator_page_paste), modifier = Modifier.padding(start = 4.dp))
                }
                }
            }

            // 翻译进度条
            Crossfade(translating) { isTranslating ->
                if (isTranslating) {
                    LinearWavyProgressIndicator(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                    )
                } else {
                    HorizontalDivider()
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CustomColors.cardColorsOnSurfaceContainer,
                shape = rememberScreenEdgeCornerShape(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = translatedText,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.translator_page_result_placeholder)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            disabledBorderColor = Color.Transparent,
                        ),
                        maxLines = 10,
                        textStyle = MaterialTheme.typography.headlineSmall,
                    )

                    AnimatedVisibility(translatedText.isNotBlank()) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    clipboard.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(null, translatedText)
                                        )
                                    )
                                }
                            }
                        ) {
                            Icon(HugeIcons.Clipboard, null)
                            Text(stringResource(R.string.translator_page_copy_result), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

private val Locales by lazy {
    listOf(
        Locale.SIMPLIFIED_CHINESE,
        Locale.ENGLISH,
        Locale.TRADITIONAL_CHINESE,
        Locale.JAPANESE,
        Locale.KOREAN,
        Locale.FRENCH,
        Locale.GERMAN,
        Locale.ITALIAN,
        Locale.Builder().setLanguage("es").setRegion("ES").build()
    )
}

@Composable
private fun LanguageSelector(
    targetLanguage: Locale,
    onLanguageSelected: (Locale) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    @Composable
    fun getLanguageDisplayName(locale: Locale): String {
        return when (locale) {
            Locale.SIMPLIFIED_CHINESE -> stringResource(R.string.language_simplified_chinese)
            Locale.ENGLISH -> stringResource(R.string.language_english)
            Locale.TRADITIONAL_CHINESE -> stringResource(R.string.language_traditional_chinese)
            Locale.JAPANESE -> stringResource(R.string.language_japanese)
            Locale.KOREAN -> stringResource(R.string.language_korean)
            Locale.FRENCH -> stringResource(R.string.language_french)
            Locale.GERMAN -> stringResource(R.string.language_german)
            Locale.ITALIAN -> stringResource(R.string.language_italian)
            Locale.Builder().setLanguage("es").setRegion("ES").build() -> stringResource(R.string.language_spanish)
            else -> locale.getDisplayLanguage(Locale.getDefault())
        }
    }

    Box(
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            OutlinedTextField(
                value = getLanguageDisplayName(targetLanguage),
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                    .fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent
                )
            )

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Locales.forEach { language ->
                    DropdownMenuItem(
                        text = { Text(getLanguageDisplayName(language)) },
                        onClick = {
                            onLanguageSelected(language)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomBar(
    targetLanguage: Locale,
    onLanguageSelected: (Locale) -> Unit,
    translating: Boolean,
    onTranslate: () -> Unit,
    onCancelTranslation: () -> Unit
) {
    BottomAppBar(
        actions = {
            // 目标语言选择
            LanguageSelector(
                targetLanguage = targetLanguage,
                onLanguageSelected = { onLanguageSelected(it) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (translating) {
                        onCancelTranslation()
                    } else {
                        onTranslate()
                    }
                },
                containerColor = BottomAppBarDefaults.bottomAppBarFabColor,
                elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
            ) {
                if (!translating) {
                    Text(
                        stringResource(R.string.translator_page_translate),
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else {
                    Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.common_cancel))
                }
            }
        }
    )
}

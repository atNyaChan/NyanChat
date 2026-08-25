package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.AiSearch02
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.pages.setting.SearchAbilityTagLine

enum class SearchMode {
    OFF,
    LOCAL,
    BUILT_IN,
}

@Composable
fun SearchPickerIcon(
    enableSearch: Boolean,
    useBuiltInSearch: Boolean,
    settings: Settings,
    modifier: Modifier = Modifier,
    model: Model?,
) {
    val currentService = settings.searchServices.getOrNull(settings.searchServiceSelected)
    val builtInSearchActive = useBuiltInSearch && model?.tools?.contains(BuiltInTools.Search) == true
    Box(
        modifier = modifier.size(24.dp),
        contentAlignment = Alignment.Center
    ) {
        if (builtInSearchActive) {
            Icon(
                imageVector = HugeIcons.AiSearch02,
                contentDescription = stringResource(R.string.use_web_search),
            )
        } else if (enableSearch && currentService != null) {
            AutoAIIcon(
                name = currentService.displayName,
                color = Color.Transparent
            )
        } else {
            Icon(
                imageVector = HugeIcons.Search01,
                contentDescription = stringResource(R.string.use_web_search),
            )
        }
    }
}

@Composable
fun SearchPickerSheet(
    show: Boolean,
    enableSearch: Boolean,
    useBuiltInSearch: Boolean,
    settings: Settings,
    onSelectSearch: (mode: SearchMode, serviceIndex: Int?) -> Unit,
    model: Model?,
    onDismiss: () -> Unit,
) {
    if (show) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            )
        ) {
            SearchPicker(
                enableSearch = enableSearch,
                useBuiltInSearch = useBuiltInSearch,
                settings = settings,
                onSelectSearch = onSelectSearch,
                model = model,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun SearchPicker(
    enableSearch: Boolean,
    useBuiltInSearch: Boolean,
    settings: Settings,
    model: Model?,
    onSelectSearch: (mode: SearchMode, serviceIndex: Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val navBackStack = LocalNavController.current
    // 模型内置搜索开关（模型设置里的内置工具）开着才会显示“模型内置搜索”项
    val hasBuiltInSearchEnabled = model?.tools?.contains(BuiltInTools.Search) == true
    // 内置搜索仅在模型支持时生效，否则回退到本地搜索
    val isBuiltInSearchSelected = useBuiltInSearch && hasBuiltInSearchEnabled
    val isLocalSearchSelected = enableSearch && !isBuiltInSearchSelected
    val selectedServiceIndex = settings.searchServiceSelected

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.search_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    onDismiss()
                    navBackStack.navigate(Screen.SettingSearch)
                }
            ) {
                Icon(HugeIcons.Settings03, contentDescription = null)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 400.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            settings.searchServices.forEachIndexed { index, service ->
                SearchPickerOptionRow(
                    selected = isLocalSearchSelected && index == selectedServiceIndex,
                    onClick = {
                        if (isLocalSearchSelected && index == selectedServiceIndex) {
                            onSelectSearch(SearchMode.OFF, index)
                        } else {
                            onSelectSearch(SearchMode.LOCAL, index)
                        }
                    },
                    leading = {
                        AutoAIIcon(
                            name = service.displayName,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                ) {
                    Text(
                        text = service.displayName,
                        style = MaterialTheme.typography.titleMedium
                    )
                    SearchAbilityTagLine(options = service)
                }
            }

            if (hasBuiltInSearchEnabled) {
                SearchPickerOptionRow(
                    selected = isBuiltInSearchSelected,
                    onClick = {
                        onSelectSearch(
                            if (isBuiltInSearchSelected) SearchMode.OFF else SearchMode.BUILT_IN,
                            null,
                        )
                    },
                    leading = {
                        Icon(
                            imageVector = HugeIcons.AiSearch02,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                ) {
                    Text(
                        text = stringResource(R.string.built_in_search_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPickerOptionRow(
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    OutlinedItemCard(
        onClick = onClick,
        colors = if (selected) {
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        } else {
            CardDefaults.outlinedCardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            )
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center
            ) {
                leading()
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                content()
            }
            RadioButton(selected = selected, onClick = onClick)
        }
    }
}

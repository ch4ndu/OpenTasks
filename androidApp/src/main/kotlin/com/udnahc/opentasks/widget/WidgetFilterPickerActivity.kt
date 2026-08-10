package com.udnahc.opentasks.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.udnahc.opentasks.data.auth.WidgetAccountGate
import com.udnahc.opentasks.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.widget_filter_picker_title
import opentasks.composeapp.generated.resources.widget_filter_all
import opentasks.composeapp.generated.resources.widget_filter_next_7_days
import opentasks.composeapp.generated.resources.widget_filter_today
import opentasks.composeapp.generated.resources.widget_filter_tomorrow
import org.jetbrains.compose.resources.stringResource
import org.lighthousegames.logging.logging
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val log = logging("WidgetFilterPicker")

private data class FilterOption(
    val key: String,
    val label: String,
    val type: WidgetFilterType,
    val categoryId: String? = null,
)

class WidgetFilterPickerActivity : ComponentActivity(), KoinComponent {

    private val widgetAccountGate: WidgetAccountGate by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)

        val appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        )
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val prefs = widgetAccountGate.withAuthenticatedBoundary {
                WidgetPreferences.load(this@WidgetFilterPickerActivity, appWidgetId)
            }
            withContext(Dispatchers.Main) {
                if (prefs == null) {
                    finish()
                    return@withContext
                }
                setContent {
            var categories by remember { mutableStateOf(emptyList<Category>()) }
            LaunchedEffect(Unit) {
                categories = withContext(Dispatchers.IO) {
                    WidgetDataProvider().getCategories()
                }
            }

            val selectedFilterType = prefs.filterType
            val selectedCategoryId = prefs.filterCategoryId
            val staticOptions = listOf(
                FilterOption(
                    key = "filter:${WidgetFilterType.ALL.name}",
                    label = stringResource(Res.string.widget_filter_all),
                    type = WidgetFilterType.ALL,
                ),
                FilterOption(
                    key = "filter:${WidgetFilterType.TODAY.name}",
                    label = stringResource(Res.string.widget_filter_today),
                    type = WidgetFilterType.TODAY,
                ),
                FilterOption(
                    key = "filter:${WidgetFilterType.TOMORROW.name}",
                    label = stringResource(Res.string.widget_filter_tomorrow),
                    type = WidgetFilterType.TOMORROW,
                ),
                FilterOption(
                    key = "filter:${WidgetFilterType.NEXT_7_DAYS.name}",
                    label = stringResource(Res.string.widget_filter_next_7_days),
                    type = WidgetFilterType.NEXT_7_DAYS,
                ),
            )
            val allOptions = staticOptions + categories.map { cat ->
                FilterOption(
                    key = "category:${cat.id}",
                    label = cat.name,
                    type = WidgetFilterType.CATEGORY,
                    categoryId = cat.id,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
//                    .background(Color.Black.copy(alpha = 0.5f))
                    .background(Color.Transparent)
                    .clickable { finish() },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                        .clickable(enabled = false) { /* block clicks through */ },
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF1E1E1E),
                    tonalElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.padding(vertical = 16.dp)) {
                        Text(
                            text = stringResource(Res.string.widget_filter_picker_title),
                            color = Color.White,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )

                        HorizontalDivider(color = Color(0xFF333333))

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .fillMaxWidth(),
                        ) {
                            items(allOptions, key = { it.key }) { option ->
                                val isSelected = option.type == selectedFilterType &&
                                    (option.type != WidgetFilterType.CATEGORY || option.categoryId == selectedCategoryId)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            log.d { "Filter selected: ${option.type} category=${option.categoryId}" }
                                            val updated = prefs.copy(
                                                filterType = option.type,
                                                filterCategoryId = option.categoryId,
                                            )
                                            saveSelectionAndFinish(updated, appWidgetId)
                                        }
                                        .padding(horizontal = 20.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = option.label,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isSelected) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "\u2713",
                                            color = Color(0xFF4CAF50),
                                            fontSize = 18.sp,
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF333333))

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { finish() },
                            modifier = Modifier
                                .align(Alignment.End)
                                .padding(horizontal = 16.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.cancel),
                                color = Color(0xFFBBBBBB),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
                }
            }
        }
    }

    private fun saveSelectionAndFinish(prefs: WidgetPreferences, appWidgetId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            val saved = widgetAccountGate.withAuthenticatedBoundary { boundary ->
                WidgetPreferences.save(this@WidgetFilterPickerActivity, prefs)
                log.d { "Prefs saved, updating widget $appWidgetId" }
                log.d { "Refreshing widget $appWidgetId" }
                TaskWidget.refreshWidgetWithinBoundary(this@WidgetFilterPickerActivity, appWidgetId, boundary)
                log.d { "Widget $appWidgetId refreshed" }
            }
            if (saved == null) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

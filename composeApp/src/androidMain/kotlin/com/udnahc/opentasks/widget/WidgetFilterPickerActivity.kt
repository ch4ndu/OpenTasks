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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.udnahc.opentasks.data.dao.CategoryDao
import com.udnahc.opentasks.data.model.Category
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lighthousegames.logging.logging

private val log = logging("WidgetFilterPicker")

class WidgetFilterPickerActivity : ComponentActivity(), KoinComponent {

    private val categoryDao: CategoryDao by inject()

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

        val prefs = WidgetPreferences.load(this, appWidgetId)

        setContent {
            var categories by remember { mutableStateOf(emptyList<Category>()) }
            LaunchedEffect(Unit) {
                categories = withContext(Dispatchers.IO) {
                    categoryDao.getAllCategoriesOnce()
                }
            }

            val selectedFilterType = prefs.filterType
            val selectedCategoryId = prefs.filterCategoryId

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
                            text = "Choose List",
                            color = Color.White,
                            fontSize = 18.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )

                        HorizontalDivider(color = Color(0xFF333333))

                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                        ) {
                            data class FilterOption(
                                val label: String,
                                val type: WidgetFilterType,
                                val categoryId: String? = null,
                            )

                            val staticOptions = listOf(
                                FilterOption("All", WidgetFilterType.ALL),
                                FilterOption("Today", WidgetFilterType.TODAY),
                                FilterOption("Tomorrow", WidgetFilterType.TOMORROW),
                                FilterOption("Next 7 Days", WidgetFilterType.NEXT_7_DAYS),
                            )

                            val allOptions = staticOptions + categories.map { cat ->
                                FilterOption(cat.name, WidgetFilterType.CATEGORY, cat.id)
                            }

                            allOptions.forEach { option ->
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
                                            WidgetPreferences.save(
                                                this@WidgetFilterPickerActivity,
                                                updated,
                                            )
                                            log.d { "Prefs saved, updating widget $appWidgetId" }
                                            updateWidgetAndFinish(appWidgetId)
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
                                text = "Cancel",
                                color = Color(0xFFBBBBBB),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun updateWidgetAndFinish(appWidgetId: Int) {
        lifecycleScope.launch(Dispatchers.IO) {
            log.d { "Refreshing widget $appWidgetId" }
            TaskWidget.refreshWidget(this@WidgetFilterPickerActivity, appWidgetId)
            log.d { "Widget $appWidgetId refreshed" }
            withContext(Dispatchers.Main) { finish() }
        }
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}

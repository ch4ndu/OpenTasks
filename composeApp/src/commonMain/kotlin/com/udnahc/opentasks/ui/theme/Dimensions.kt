package com.udnahc.opentasks.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window size category derived from window width.
 * Compact = phones, Medium = small tablets/foldables, Expanded = large tablets/desktop.
 */
enum class WindowSizeCategory { COMPACT, MEDIUM, EXPANDED }

val LocalWindowSizeCategory = staticCompositionLocalOf { WindowSizeCategory.COMPACT }

data class OpenTasksDimensions(
    // ── Padding ──────────────────────────────────────────────────────────
    val paddingTiny: Dp,        // 2.dp baseline
    val paddingSmall: Dp,       // 4.dp
    val paddingMedium: Dp,      // 8.dp
    val paddingLarge: Dp,       // 12.dp
    val paddingXLarge: Dp,      // 16.dp
    val paddingXXLarge: Dp,     // 24.dp

    // ── Spacers (gaps between elements in Row/Column) ────────────────────
    val spacerTiny: Dp,         // 2.dp
    val spacerSmall: Dp,        // 4.dp
    val spacerMedium: Dp,       // 6.dp
    val spacerLarge: Dp,        // 8.dp
    val spacerXLarge: Dp,       // 12.dp
    val spacerXXLarge: Dp,      // 16.dp

    // ── Icon sizes ───────────────────────────────────────────────────────
    val iconTiny: Dp,           // 12.dp
    val iconSmall: Dp,          // 14.dp
    val iconMedium: Dp,         // 16.dp
    val iconDefault: Dp,        // 18.dp
    val iconLarge: Dp,          // 22.dp
    val iconXLarge: Dp,         // 24.dp
    val iconNavBar: Dp,         // 28.dp

    // ── Touch targets / button sizes ─────────────────────────────────────
    val touchTargetSmall: Dp,   // 20.dp (priority badge, small indicators)
    val touchTargetMedium: Dp,  // 32.dp (icon buttons, checkboxes)
    val touchTargetLarge: Dp,   // 36.dp (calendar day circles, large buttons)

    // ── Corner radii ─────────────────────────────────────────────────────
    val cornerTiny: Dp,         // 2.dp
    val cornerSmall: Dp,        // 3.dp
    val cornerMedium: Dp,       // 4.dp
    val cornerLarge: Dp,        // 8.dp
    val cornerXLarge: Dp,       // 12.dp

    // ── Dividers / borders ───────────────────────────────────────────────
    val dividerThin: Dp,        // 0.5.dp
    val dividerMedium: Dp,      // 1.5.dp
    val dividerThick: Dp,       // 2.dp

    // ── Component sizes ──────────────────────────────────────────────────
    val topBarHeight: Dp,       // 64.dp (TopAppBar default height)
    val fabAreaBottom: Dp,      // 80.dp (space reserved for FAB + nav)
    val fabBottomPadding: Dp,   // 88.dp (FAB bottom offset from nav bar)
    val minPagerHeight: Dp,     // 256.dp (date/time picker min height)
    val listRowVerticalPadding: Dp,      // 12.dp
    val listRowCompletedVerticalPadding: Dp, // 10.dp

    // ── Calendar-specific ────────────────────────────────────────────────
    val calendarDaySize: Dp,            // 32.dp (day number circle in week strip)
    val calendarDayHeaderHeight: Dp,    // 24.dp (S M T W T F S row)
    val calendarEventBarHeight: Dp,     // 15.dp (event bar in month grid)
    val calendarEventOverflowHeight: Dp,// 12.dp ("+N more" text height)
    val calendarTimeColumnWidth: Dp,    // 64.dp (time labels column in day view)
    val calendarTimelineHeight: Dp,     // 80.dp (one-hour slot height)
    val calendarDotSize: Dp,            // 4.dp (task indicator dot)
    val calendarEmptyPadding: Dp,       // 48.dp (empty state vertical padding)
    val calendarTimelineMarkerSize: Dp, // 16.dp (current time marker circle)
    val calendarTimelineDividerWidth: Dp, // 1.5.dp
    val calendarTimelineDividerHeight: Dp, // 16.dp (short divider segments)
    val calendarTimeIndicatorWidth: Dp, // 24.dp (time indicator column width)
    val calendarMonthGridEventHeight: Dp, // 16.dp (event bar in month grid cell)
    val calendarWeekDayCircle: Dp,      // 28.dp (day circle in week/3-day header)
    val miniCalTodayCircle: Dp,        // 18.dp (today circle in mini calendar)
    val calendarCollapsedWeekHeight: Dp, // 48.dp
    val calendarStackedEventsHeight: Dp, // 100.dp
    val threeDayAllDayHeight: Dp,       // 48.dp (fixed height for all-day section)
    val threeDayEventMinHeight: Dp,     // 20.dp (min height for a timed event bar)

    // ── Checkbox / badge (Eisenhower matrix) ─────────────────────────────
    val checkboxSize: Dp,       // 18.dp (quadrant task checkbox)
    val checkboxIconSize: Dp,   // 14.dp
    val checkboxBorder: Dp,     // 1.5.dp
    val checkboxCorner: Dp,     // 3.dp
    val badgeSize: Dp,          // 20.dp (quadrant numeral badge)

    // ── Create task screen ───────────────────────────────────────────────
    val priorityIndicatorSize: Dp,      // 20.dp
    val priorityIndicatorBorder: Dp,    // 2.dp
    val reminderRowButtonHeight: Dp,    // 40.dp
    val reminderDayButtonSize: Dp,      // 36.dp

    // ── Kanban board ─────────────────────────────────────────────────────
    val kanbanColumnMinWidth: Dp,       // 200.dp (min phone column width)
    val kanbanAutoScrollAmount: Dp,     // 150.dp (edge-drag auto-scroll step)
)

fun compactDimensions() = OpenTasksDimensions(
    // Padding
    paddingTiny = 2.dp,
    paddingSmall = 4.dp,
    paddingMedium = 8.dp,
    paddingLarge = 12.dp,
    paddingXLarge = 16.dp,
    paddingXXLarge = 24.dp,
    // Spacers
    spacerTiny = 2.dp,
    spacerSmall = 4.dp,
    spacerMedium = 6.dp,
    spacerLarge = 8.dp,
    spacerXLarge = 12.dp,
    spacerXXLarge = 16.dp,
    // Icons
    iconTiny = 12.dp,
    iconSmall = 14.dp,
    iconMedium = 16.dp,
    iconDefault = 18.dp,
    iconLarge = 22.dp,
    iconXLarge = 24.dp,
    iconNavBar = 28.dp,
    // Touch targets
    touchTargetSmall = 20.dp,
    touchTargetMedium = 32.dp,
    touchTargetLarge = 36.dp,
    // Corners
    cornerTiny = 2.dp,
    cornerSmall = 3.dp,
    cornerMedium = 4.dp,
    cornerLarge = 8.dp,
    cornerXLarge = 12.dp,
    // Dividers
    dividerThin = 0.5.dp,
    dividerMedium = 1.5.dp,
    dividerThick = 2.dp,
    // Component sizes
    topBarHeight = 64.dp,
    fabAreaBottom = 80.dp,
    fabBottomPadding = 88.dp,
    minPagerHeight = 256.dp,
    listRowVerticalPadding = 12.dp,
    listRowCompletedVerticalPadding = 10.dp,
    // Calendar
    calendarDaySize = 32.dp,
    calendarDayHeaderHeight = 24.dp,
    calendarEventBarHeight = 15.dp,
    calendarEventOverflowHeight = 12.dp,
    calendarTimeColumnWidth = 64.dp,
    calendarTimelineHeight = 80.dp,
    calendarDotSize = 4.dp,
    calendarEmptyPadding = 48.dp,
    calendarTimelineMarkerSize = 16.dp,
    calendarTimelineDividerWidth = 1.5.dp,
    calendarTimelineDividerHeight = 16.dp,
    calendarTimeIndicatorWidth = 24.dp,
    calendarMonthGridEventHeight = 16.dp,
    calendarWeekDayCircle = 28.dp,
    miniCalTodayCircle = 18.dp,
    calendarCollapsedWeekHeight = 48.dp,
    calendarStackedEventsHeight = 100.dp,
    threeDayAllDayHeight = 48.dp,
    threeDayEventMinHeight = 20.dp,
    // Checkbox / badge
    checkboxSize = 18.dp,
    checkboxIconSize = 14.dp,
    checkboxBorder = 1.5.dp,
    checkboxCorner = 3.dp,
    badgeSize = 20.dp,
    // Create task
    priorityIndicatorSize = 20.dp,
    priorityIndicatorBorder = 2.dp,
    reminderRowButtonHeight = 40.dp,
    reminderDayButtonSize = 36.dp,
    // Kanban
    kanbanColumnMinWidth = 200.dp,
    kanbanAutoScrollAmount = 150.dp,
)

fun mediumDimensions() = OpenTasksDimensions(
    // Padding
    paddingTiny = 3.dp,
    paddingSmall = 5.dp,
    paddingMedium = 10.dp,
    paddingLarge = 14.dp,
    paddingXLarge = 20.dp,
    paddingXXLarge = 28.dp,
    // Spacers
    spacerTiny = 3.dp,
    spacerSmall = 5.dp,
    spacerMedium = 7.dp,
    spacerLarge = 10.dp,
    spacerXLarge = 14.dp,
    spacerXXLarge = 20.dp,
    // Icons
    iconTiny = 14.dp,
    iconSmall = 16.dp,
    iconMedium = 18.dp,
    iconDefault = 20.dp,
    iconLarge = 25.dp,
    iconXLarge = 28.dp,
    iconNavBar = 32.dp,
    // Touch targets
    touchTargetSmall = 23.dp,
    touchTargetMedium = 37.dp,
    touchTargetLarge = 41.dp,
    // Corners
    cornerTiny = 3.dp,
    cornerSmall = 4.dp,
    cornerMedium = 5.dp,
    cornerLarge = 10.dp,
    cornerXLarge = 14.dp,
    // Dividers
    dividerThin = 0.5.dp,
    dividerMedium = 1.5.dp,
    dividerThick = 2.dp,
    // Component sizes
    topBarHeight = 64.dp,
    fabAreaBottom = 88.dp,
    fabBottomPadding = 96.dp,
    minPagerHeight = 300.dp,
    listRowVerticalPadding = 14.dp,
    listRowCompletedVerticalPadding = 12.dp,
    // Calendar
    calendarDaySize = 36.dp,
    calendarDayHeaderHeight = 28.dp,
    calendarEventBarHeight = 17.dp,
    calendarEventOverflowHeight = 14.dp,
    calendarTimeColumnWidth = 72.dp,
    calendarTimelineHeight = 88.dp,
    calendarDotSize = 5.dp,
    calendarEmptyPadding = 56.dp,
    calendarTimelineMarkerSize = 18.dp,
    calendarTimelineDividerWidth = 1.5.dp,
    calendarTimelineDividerHeight = 18.dp,
    calendarTimeIndicatorWidth = 28.dp,
    calendarMonthGridEventHeight = 18.dp,
    calendarWeekDayCircle = 32.dp,
    miniCalTodayCircle = 20.dp,
    calendarCollapsedWeekHeight = 56.dp,
    calendarStackedEventsHeight = 112.dp,
    threeDayAllDayHeight = 56.dp,
    threeDayEventMinHeight = 22.dp,
    // Checkbox / badge
    checkboxSize = 20.dp,
    checkboxIconSize = 16.dp,
    checkboxBorder = 1.5.dp,
    checkboxCorner = 4.dp,
    badgeSize = 23.dp,
    // Create task
    priorityIndicatorSize = 23.dp,
    priorityIndicatorBorder = 2.dp,
    reminderRowButtonHeight = 44.dp,
    reminderDayButtonSize = 40.dp,
    // Kanban
    kanbanColumnMinWidth = 240.dp,
    kanbanAutoScrollAmount = 180.dp,
)

fun expandedDimensions() = OpenTasksDimensions(
    // Padding
    paddingTiny = 4.dp,
    paddingSmall = 6.dp,
    paddingMedium = 12.dp,
    paddingLarge = 16.dp,
    paddingXLarge = 24.dp,
    paddingXXLarge = 32.dp,
    // Spacers
    spacerTiny = 4.dp,
    spacerSmall = 6.dp,
    spacerMedium = 8.dp,
    spacerLarge = 12.dp,
    spacerXLarge = 16.dp,
    spacerXXLarge = 24.dp,
    // Icons
    iconTiny = 16.dp,
    iconSmall = 18.dp,
    iconMedium = 20.dp,
    iconDefault = 22.dp,
    iconLarge = 28.dp,
    iconXLarge = 32.dp,
    iconNavBar = 36.dp,
    // Touch targets
    touchTargetSmall = 26.dp,
    touchTargetMedium = 42.dp,
    touchTargetLarge = 46.dp,
    // Corners
    cornerTiny = 4.dp,
    cornerSmall = 5.dp,
    cornerMedium = 6.dp,
    cornerLarge = 12.dp,
    cornerXLarge = 16.dp,
    // Dividers
    dividerThin = 1.dp,
    dividerMedium = 2.dp,
    dividerThick = 3.dp,
    // Component sizes
    topBarHeight = 72.dp,
    fabAreaBottom = 96.dp,
    fabBottomPadding = 104.dp,
    minPagerHeight = 360.dp,
    listRowVerticalPadding = 16.dp,
    listRowCompletedVerticalPadding = 14.dp,
    // Calendar
    calendarDaySize = 40.dp,
    calendarDayHeaderHeight = 32.dp,
    calendarEventBarHeight = 19.dp,
    calendarEventOverflowHeight = 16.dp,
    calendarTimeColumnWidth = 80.dp,
    calendarTimelineHeight = 96.dp,
    calendarDotSize = 6.dp,
    calendarEmptyPadding = 64.dp,
    calendarTimelineMarkerSize = 20.dp,
    calendarTimelineDividerWidth = 2.dp,
    calendarTimelineDividerHeight = 20.dp,
    calendarTimeIndicatorWidth = 32.dp,
    calendarMonthGridEventHeight = 20.dp,
    calendarWeekDayCircle = 36.dp,
    miniCalTodayCircle = 22.dp,
    calendarCollapsedWeekHeight = 64.dp,
    calendarStackedEventsHeight = 128.dp,
    threeDayAllDayHeight = 64.dp,
    threeDayEventMinHeight = 24.dp,
    // Checkbox / badge
    checkboxSize = 22.dp,
    checkboxIconSize = 18.dp,
    checkboxBorder = 2.dp,
    checkboxCorner = 4.dp,
    badgeSize = 26.dp,
    // Create task
    priorityIndicatorSize = 26.dp,
    priorityIndicatorBorder = 3.dp,
    reminderRowButtonHeight = 48.dp,
    reminderDayButtonSize = 44.dp,
    // Kanban
    kanbanColumnMinWidth = 280.dp,
    kanbanAutoScrollAmount = 200.dp,
)

val LocalOpenTasksDimensions = staticCompositionLocalOf { compactDimensions() }

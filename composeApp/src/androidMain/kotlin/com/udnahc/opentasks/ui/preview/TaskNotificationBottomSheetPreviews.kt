package com.udnahc.opentasks.ui.preview

import androidx.compose.runtime.Composable
import com.udnahc.opentasks.NotificationDeepLinkEvent
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.ui.screens.TaskNotificationBottomSheetContent
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.TaskNotificationUiState

@Composable
@LightDarkPreview
private fun TaskNotificationBottomSheetContentPreview() {
    OpenTasksTheme {
        TaskNotificationBottomSheetContent(
            uiState = TaskNotificationUiState(
                event = NotificationDeepLinkEvent(
                    eventId = "preview-task",
                    notificationAtUtcMillis = 1773619200000L,
                ),
                task = Task(
                    id = "preview-task",
                    title = "Prepare release notes",
                    content = "",
                ),
                taskTitle = "Prepare release notes",
                notificationTimeText = "Mar 15, 9:00 AM",
                dueText = "Mar 15, 5:00 PM",
            ),
            onMarkDone = {},
            onGotIt = {},
            onEdit = {},
        )
    }
}

package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.domain.action.category.AddCategoryAction
import com.udnahc.opentasks.domain.action.note.AddNoteAction
import com.udnahc.opentasks.domain.action.note.DeleteNoteAction
import com.udnahc.opentasks.domain.action.note.UpdateNoteAction
import com.udnahc.opentasks.domain.action.task.AddTaskAction
import com.udnahc.opentasks.domain.action.task.RescheduleAllRemindersAction
import com.udnahc.opentasks.domain.action.task.ScheduleTaskRemindersAction
import com.udnahc.opentasks.domain.action.task.UpdateTaskAction
import com.udnahc.opentasks.domain.action.settings.InitializeSyncAction
import com.udnahc.opentasks.domain.usecase.category.ObserveAllCategoriesUseCase
import com.udnahc.opentasks.domain.usecase.note.ObserveAllNotesUseCase
import com.udnahc.opentasks.domain.usecase.task.ObserveAllTasksUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(
    observeAllTasks: ObserveAllTasksUseCase,
    observeAllCategories: ObserveAllCategoriesUseCase,
    observeAllNotes: ObserveAllNotesUseCase,
    private val addTaskAction: AddTaskAction,
    private val updateTaskAction: UpdateTaskAction,
    private val scheduleTaskRemindersAction: ScheduleTaskRemindersAction,
    private val addCategoryAction: AddCategoryAction,
    private val addNoteAction: AddNoteAction,
    private val updateNoteAction: UpdateNoteAction,
    private val deleteNoteAction: DeleteNoteAction,
    private val initializeSyncAction: InitializeSyncAction,
    private val rescheduleAllRemindersAction: RescheduleAllRemindersAction,
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = observeAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<Category>> = observeAllCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = observeAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(
        title: String,
        content: String,
        priority: TaskPriority = TaskPriority.NONE,
        deadline: Long? = null,
        endDeadline: Long? = null,
        isAllDay: Boolean = false,
        notifyBeforeValue: Int = 0,
        notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        categoryId: String = "00000000-0000-0000-0000-000000000001",
        location: String = "",
        url: String = "",
        organizer: String = "",
        eventStatus: String = "",
        attendees: String = "",
        durationReminders: String = "",
        dateReminders: String = "",
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = addTaskAction(
                title = title,
                content = content,
                priority = priority,
                deadline = deadline,
                endDeadline = endDeadline,
                isAllDay = isAllDay,
                notifyBeforeValue = notifyBeforeValue,
                notifyBeforeUnit = notifyBeforeUnit,
                recurrenceType = recurrenceType,
                categoryId = categoryId,
                location = location,
                url = url,
                organizer = organizer,
                eventStatus = eventStatus,
                attendees = attendees,
                durationReminders = durationReminders,
                dateReminders = dateReminders,
            )
            scheduleTaskRemindersAction(task)
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            updateTaskAction(task)
            scheduleTaskRemindersAction(task)
        }
    }

    fun addCategory(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            addCategoryAction(name)
        }
    }

    fun addNote(title: String, content: String) {
        viewModelScope.launch(Dispatchers.IO) {
            addNoteAction(title, content)
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            updateNoteAction(note)
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteNoteAction(note)
        }
    }

    fun sync() {
        viewModelScope.launch(Dispatchers.IO) {
            initializeSyncAction()
            rescheduleAllRemindersAction()
        }
    }
}

package com.udnahc.opentasks.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.udnahc.opentasks.data.extensions.dayKey
import com.udnahc.opentasks.data.extensions.utcNow
import com.udnahc.opentasks.data.model.NotifyBeforeUnit
import com.udnahc.opentasks.data.model.RecurrenceType
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskList
import com.udnahc.opentasks.data.model.TaskPriority
import com.udnahc.opentasks.data.repository.NoteRepository
import com.udnahc.opentasks.data.repository.TaskListRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(
    private val repository: TaskRepository,
    private val taskListRepository: TaskListRepository,
    private val noteRepository: NoteRepository,
) : ViewModel() {

    val tasks: StateFlow<List<Task>> = repository.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val notes: StateFlow<List<Note>> = noteRepository.getAllNotes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val taskLists: StateFlow<List<TaskList>> = taskListRepository.getAllLists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Pre-filtered flows for screens — avoid filtering in composables
    val tasksByPriority: StateFlow<Map<TaskPriority, List<Task>>> = tasks
        .map { list -> list.groupBy { it.priority } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val tasksByDay: StateFlow<Map<Long, List<Task>>> = tasks
        .map { list -> list.filter { it.deadline != null }.groupBy { dayKey(it.deadline!!) } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val _selectedListId = MutableStateFlow(1L)
    val selectedListId: StateFlow<Long> = _selectedListId
    fun selectList(listId: Long) { _selectedListId.value = listId }

    val tasksForSelectedList: StateFlow<List<Task>> = combine(tasks, _selectedListId) { allTasks, listId ->
        allTasks.filter { it.listId == listId }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeTasksForSelectedList: StateFlow<List<Task>> = tasksForSelectedList
        .map { tasks -> tasks.filter { !it.isCompleted } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasksForSelectedList: StateFlow<List<Task>> = tasksForSelectedList
        .map { tasks -> tasks.filter { it.isCompleted } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPriority = MutableStateFlow(TaskPriority.HIGH)
    fun selectPriority(priority: TaskPriority) { _selectedPriority.value = priority }

    val tasksForSelectedPriority: StateFlow<List<Task>> = combine(tasks, _selectedPriority) { allTasks, priority ->
        allTasks.filter { it.priority == priority }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addTask(
        title: String,
        content: String,
        priority: TaskPriority = TaskPriority.NONE,
        deadline: Long? = null,
        notifyBeforeValue: Int = 0,
        notifyBeforeUnit: NotifyBeforeUnit = NotifyBeforeUnit.NONE,
        recurrenceType: RecurrenceType = RecurrenceType.NONE,
        recurrenceInterval: Int = 0,
        isUrgent: Boolean = false,
        isImportant: Boolean = false,
        listId: Long = 1L,
    ) {
        val now = utcNow()
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(
                Task(
                    title = title,
                    content = content,
                    priority = priority,
                    deadline = deadline,
                    notifyBeforeValue = notifyBeforeValue,
                    notifyBeforeUnit = notifyBeforeUnit,
                    recurrenceType = recurrenceType,
                    recurrenceInterval = recurrenceInterval,
                    isUrgent = isUrgent,
                    isImportant = isImportant,
                    listId = listId,
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.update(task.copy(updatedAt = utcNow()))
        }
    }

    fun toggleComplete(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.update(
                task.copy(
                    isCompleted = !task.isCompleted,
                    updatedAt = utcNow()
                )
            )
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(task)
        }
    }

    fun addList(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            taskListRepository.insert(
                TaskList(
                    name = name,
                    createdAt = utcNow(),
                )
            )
        }
    }

    fun updateList(taskList: TaskList) {
        viewModelScope.launch(Dispatchers.IO) {
            taskListRepository.update(taskList)
        }
    }

    fun deleteList(taskList: TaskList) {
        viewModelScope.launch(Dispatchers.IO) {
            taskListRepository.delete(taskList)
        }
    }

    fun addNote(title: String, content: String) {
        val now = utcNow()
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.insert(
                Note(
                    title = title,
                    content = content,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        }
    }

    fun updateNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.update(note.copy(updatedAt = utcNow()))
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            noteRepository.delete(note)
        }
    }
}

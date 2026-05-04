package com.udnahc.opentasks.testutil

import com.udnahc.opentasks.data.model.AppSettings
import com.udnahc.opentasks.data.model.Category
import com.udnahc.opentasks.data.model.Countdown
import com.udnahc.opentasks.data.model.Note
import com.udnahc.opentasks.data.model.Tag
import com.udnahc.opentasks.data.model.Task
import com.udnahc.opentasks.data.model.TaskTag
import com.udnahc.opentasks.data.repository.AppSettingsRepository
import com.udnahc.opentasks.data.repository.CategoryRepository
import com.udnahc.opentasks.data.repository.CountdownRepository
import com.udnahc.opentasks.data.repository.NoteRepository
import com.udnahc.opentasks.data.repository.TagRepository
import com.udnahc.opentasks.data.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeTaskRepository(initialTasks: List<Task> = emptyList()) : TaskRepository {
    private val tasksFlow = MutableStateFlow(initialTasks)
    val inserted = mutableListOf<Task>()
    val updated = mutableListOf<Task>()
    val deleted = mutableListOf<Task>()

    val tasks: List<Task>
        get() = tasksFlow.value

    fun replaceTasks(tasks: List<Task>) {
        tasksFlow.value = tasks
    }

    override fun getAllTasks(): Flow<List<Task>> = tasksFlow

    override suspend fun getTaskById(id: String): Task? =
        tasksFlow.value.firstOrNull { it.id == id && !it.isDeleted }

    override fun observeTaskById(id: String): Flow<Task?> =
        tasksFlow.map { tasks -> tasks.firstOrNull { it.id == id && !it.isDeleted } }

    override suspend fun getTaskByExternalId(externalId: String): Task? =
        tasksFlow.value.firstOrNull { it.sourceExternalId == externalId && !it.isDeleted }

    override suspend fun insert(task: Task): Long {
        inserted.add(task)
        tasksFlow.update { tasks -> tasks.filterNot { it.id == task.id } + task }
        return inserted.size.toLong()
    }

    override suspend fun update(task: Task) {
        updated.add(task)
        tasksFlow.update { tasks -> tasks.filterNot { it.id == task.id } + task }
    }

    override suspend fun delete(task: Task) {
        deleted.add(task)
        tasksFlow.update { tasks -> tasks.filterNot { it.id == task.id } }
    }

    override suspend fun getTasksWithDeadlines(): List<Task> =
        tasksFlow.value.filter { !it.isDeleted && it.deadline != null }

    override suspend fun getTaskByIdUtc(id: String): Task? = getTaskById(id)

    override suspend fun getAllTasksOnce(): List<Task> =
        tasksFlow.value.filterNot { it.isDeleted }

    override suspend fun getAllTasksOnceUtc(): List<Task> =
        tasksFlow.value.filterNot { it.isDeleted }
}

class FakeCategoryRepository(initialCategories: List<Category> = emptyList()) : CategoryRepository {
    private val categoriesFlow = MutableStateFlow(initialCategories)
    val inserted = mutableListOf<Category>()
    val updated = mutableListOf<Category>()
    val deleted = mutableListOf<Category>()

    val categories: List<Category>
        get() = categoriesFlow.value

    override fun getAllCategories(): Flow<List<Category>> = categoriesFlow

    override suspend fun getCategoryById(id: String): Category? =
        categoriesFlow.value.firstOrNull { it.id == id && !it.isDeleted }

    override suspend fun getCategoryByName(name: String): Category? =
        categoriesFlow.value.firstOrNull { it.name == name && !it.isDeleted }

    override suspend fun insert(category: Category): Long {
        inserted.add(category)
        categoriesFlow.update { categories -> categories.filterNot { it.id == category.id } + category }
        return inserted.size.toLong()
    }

    override suspend fun update(category: Category) {
        updated.add(category)
        categoriesFlow.update { categories -> categories.filterNot { it.id == category.id } + category }
    }

    override suspend fun delete(category: Category) {
        deleted.add(category)
        categoriesFlow.update { categories -> categories.filterNot { it.id == category.id } }
    }
}

class FakeNoteRepository(initialNotes: List<Note> = emptyList()) : NoteRepository {
    private val notesFlow = MutableStateFlow(initialNotes)
    val inserted = mutableListOf<Note>()
    val updated = mutableListOf<Note>()
    val deleted = mutableListOf<Note>()

    val notes: List<Note>
        get() = notesFlow.value

    override fun getAllNotes(): Flow<List<Note>> = notesFlow

    override suspend fun getNoteById(id: String): Note? =
        notesFlow.value.firstOrNull { it.id == id && !it.isDeleted }

    override fun observeNoteById(id: String): Flow<Note?> =
        notesFlow.map { notes -> notes.firstOrNull { it.id == id && !it.isDeleted } }

    override suspend fun insert(note: Note) {
        inserted.add(note)
        notesFlow.update { notes -> notes.filterNot { it.id == note.id } + note }
    }

    override suspend fun update(note: Note) {
        updated.add(note)
        notesFlow.update { notes -> notes.filterNot { it.id == note.id } + note }
    }

    override suspend fun delete(note: Note) {
        deleted.add(note)
        notesFlow.update { notes -> notes.filterNot { it.id == note.id } }
    }
}

class FakeTagRepository(initialTags: List<Tag> = emptyList()) : TagRepository {
    private val tagsFlow = MutableStateFlow(initialTags)
    private val taskTagsFlow = MutableStateFlow<List<TaskTag>>(emptyList())
    val insertedTags = mutableListOf<Tag>()
    val insertedTaskTags = mutableListOf<TaskTag>()
    val deletedTags = mutableListOf<Tag>()
    val deletedTaskTags = mutableListOf<TaskTag>()

    val taskTags: List<TaskTag>
        get() = taskTagsFlow.value

    override fun getAllTags(): Flow<List<Tag>> = tagsFlow

    override suspend fun getTagById(id: String): Tag? =
        tagsFlow.value.firstOrNull { it.id == id && !it.isDeleted }

    override suspend fun getTagByName(name: String): Tag? =
        tagsFlow.value.firstOrNull { it.name == name && !it.isDeleted }

    override fun getTagsForTask(taskId: String): Flow<List<Tag>> =
        taskTagsFlow.map { taskTags ->
            val tagIds = taskTags.filter { it.taskId == taskId && !it.isDeleted }.map { it.tagId }.toSet()
            tagsFlow.value.filter { it.id in tagIds && !it.isDeleted }
        }

    override suspend fun insertTag(tag: Tag): Long {
        insertedTags.add(tag)
        tagsFlow.update { tags -> tags.filterNot { it.id == tag.id } + tag }
        return insertedTags.size.toLong()
    }

    override suspend fun deleteTag(tag: Tag) {
        deletedTags.add(tag)
        tagsFlow.update { tags -> tags.filterNot { it.id == tag.id } }
    }

    override suspend fun insertTaskTag(taskTag: TaskTag) {
        insertedTaskTags.add(taskTag)
        taskTagsFlow.update { taskTags ->
            taskTags.filterNot { it.taskId == taskTag.taskId && it.tagId == taskTag.tagId } + taskTag
        }
    }

    override suspend fun deleteTaskTag(taskTag: TaskTag) {
        deletedTaskTags.add(taskTag)
        taskTagsFlow.update { taskTags ->
            taskTags.filterNot { it.taskId == taskTag.taskId && it.tagId == taskTag.tagId }
        }
    }
}

class FakeCountdownRepository(initialCountdowns: List<Countdown> = emptyList()) : CountdownRepository {
    private val countdownsFlow = MutableStateFlow(initialCountdowns)
    val inserted = mutableListOf<Countdown>()
    val updated = mutableListOf<Countdown>()
    val deleted = mutableListOf<Countdown>()

    val countdowns: List<Countdown>
        get() = countdownsFlow.value

    override fun getAllCountdowns(): Flow<List<Countdown>> = countdownsFlow

    override fun observeCountdownById(id: String): Flow<Countdown?> =
        countdownsFlow.map { countdowns -> countdowns.firstOrNull { it.id == id && !it.isDeleted } }

    override suspend fun getCountdownById(id: String): Countdown? =
        countdownsFlow.value.firstOrNull { it.id == id && !it.isDeleted }

    override suspend fun getCountdownByIdUtc(id: String): Countdown? = getCountdownById(id)

    override suspend fun getCountdownsWithTargetsUtc(): List<Countdown> =
        countdownsFlow.value.filterNot { it.isDeleted }

    override suspend fun insert(countdown: Countdown) {
        inserted.add(countdown)
        countdownsFlow.update { countdowns -> countdowns.filterNot { it.id == countdown.id } + countdown }
    }

    override suspend fun update(countdown: Countdown) {
        updated.add(countdown)
        countdownsFlow.update { countdowns -> countdowns.filterNot { it.id == countdown.id } + countdown }
    }

    override suspend fun delete(countdown: Countdown) {
        deleted.add(countdown)
        countdownsFlow.update { countdowns -> countdowns.filterNot { it.id == countdown.id } }
    }
}

class FakeAppSettingsRepository(initialSettings: Map<String, String> = emptyMap()) : AppSettingsRepository {
    private val settingsFlow = MutableStateFlow(initialSettings)
    val saved = mutableListOf<AppSettings>()
    val removed = mutableListOf<String>()
    var deleteAllCount = 0

    override suspend fun getValue(key: String): String? = settingsFlow.value[key]

    override fun observeValue(key: String): Flow<String?> =
        settingsFlow.map { settings -> settings[key] }

    override suspend fun setValue(key: String, value: String) {
        saved.add(AppSettings(key, value))
        settingsFlow.update { settings -> settings + (key to value) }
    }

    override suspend fun removeValue(key: String) {
        removed.add(key)
        settingsFlow.update { settings -> settings - key }
    }

    override suspend fun deleteAll() {
        deleteAllCount += 1
        settingsFlow.value = emptyMap()
    }
}

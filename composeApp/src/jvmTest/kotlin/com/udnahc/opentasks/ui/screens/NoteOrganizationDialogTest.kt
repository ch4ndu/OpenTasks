package com.udnahc.opentasks.ui.screens

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import com.udnahc.opentasks.data.model.*
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.viewmodel.NoteReference
import com.udnahc.opentasks.viewmodel.NoteRelations
import kotlin.test.*

/** Offscreen layout and semantics checks; no app window or device is opened. */
class NoteOrganizationDialogTest {
    @Test
    fun organizationHasLabeledToggleActionsAndLongListsFitCompactDialogs() {
        var changed = NoteOrganization()
        ImageComposeScene(width = 360, height = 640) {
            OpenTasksTheme(themeMode = ThemeMode.LIGHT) {
                NoteOrganizationDialog(
                    value = NoteOrganization(),
                    tags = List(100) { Tag(id = "tag-$it", name = "Tag $it") },
                    onChange = { changed = it }, createTag = { null }, onDismiss = {},
                )
            }
        }.use { scene ->
            scene.render().close()
            val toggles = scene.semanticsOwners.flatMap { it.rootSemanticsNode.descendants() }
                .filter { it.config.contains(SemanticsProperties.ToggleableState) && it.config.contains(SemanticsActions.OnClick) }
            assertTrue(toggles.isNotEmpty())
            assertTrue(toggles.first().config.getOrNull(SemanticsProperties.Text).orEmpty().isNotEmpty())
            toggles.first().config[SemanticsActions.OnClick].action?.invoke()
            assertTrue(changed.isPinned)
        }
    }

    @Test
    fun linkPickerSupportsUnavailableTargetsAndManyCandidatesWithoutUnboundedLayout() {
        ImageComposeScene(width = 360, height = 640) {
            OpenTasksTheme(themeMode = ThemeMode.LIGHT) {
                NoteLinksDialog(
                    value = NoteOrganization(linkedNoteIds = listOf("missing")),
                    relations = NoteRelations(
                        linked = listOf(NoteReference("missing", "", null)),
                        available = List(100) { NoteReference("note-$it", "Note $it", Note(id = "note-$it")) },
                    ),
                    onChange = {}, onDismiss = {}, onOpenNote = {},
                )
            }
        }.use { it.render().close() }
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }
}

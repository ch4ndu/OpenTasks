package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.use
import com.udnahc.opentasks.data.model.ThemeMode
import com.udnahc.opentasks.data.model.UiExperience
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import kotlinx.coroutines.flow.MutableStateFlow
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.back
import opentasks.composeapp.generated.resources.note_discard_title
import opentasks.composeapp.generated.resources.save
import org.jetbrains.compose.resources.stringResource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Exercises real workspace pointer dispatch in an offscreen scene; no window or device is opened. */
class NotesWorkspacePointerTest {
    @Test
    fun saveAcceptsStationaryAndSlightlyMovedPressesWithoutClickingTheCoveredScreen() {
        listOf(false, true).forEach { move ->
            var saves = 0
            var coveredClicks = 0
            var saveLabel = ""
            ImageComposeScene(width = 400, height = 800) {
                OpenTasksTheme(themeMode = ThemeMode.LIGHT, uiExperience = UiExperience.REIMAGINED) {
                    saveLabel = stringResource(Res.string.save)
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().clickable { coveredClicks++ })
                        Workspace(loading = false, onSave = { saves++ })
                    }
                }
            }.use { scene ->
                scene.render().close()
                // Blank workspace area must not deliver input to the obscured screen.
                scene.tap(Offset(390f, 790f), move)
                scene.tap(scene.buttonCenter(saveLabel), move)
                assertEquals(1, saves, "Save should fire once (move=$move)")
                assertEquals(0, coveredClicks)
            }
        }
    }

    @Test
    fun loadingBackAcceptsStationaryAndSlightlyMovedPressesAndOpensDiscardDecision() {
        listOf(false, true).forEach { move ->
            var coveredClicks = 0
            var backLabel = ""
            var discardTitle = ""
            ImageComposeScene(width = 400, height = 800) {
                OpenTasksTheme(themeMode = ThemeMode.LIGHT, uiExperience = UiExperience.REIMAGINED) {
                    backLabel = stringResource(Res.string.back)
                    discardTitle = stringResource(Res.string.note_discard_title)
                    Box(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxSize().clickable { coveredClicks++ })
                        Workspace(loading = true)
                    }
                }
            }.use { scene ->
                scene.render().close()
                scene.tap(Offset(390f, 790f), move)
                scene.tap(scene.buttonCenter(backLabel), move)
                scene.render(100_000_000L).close()
                assertTrue(scene.nodes().any { it.hasLabel(discardTitle) }, "Back should open discard decision (move=$move)")
                assertEquals(0, coveredClicks)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Workspace(loading: Boolean, onSave: () -> Unit = {}) {
        ReimaginedNotesWorkspace(
            active = true,
            loading = loading,
            requestToken = 1L,
            note = null,
            initialCapture = "",
            savedRevision = 0L,
            isBusy = false,
            isMissing = false,
            errorMessage = null,
            library = MutableStateFlow(emptyList()),
            linkedTasks = MutableStateFlow(emptyList()),
            linkedTaskPreview = MutableStateFlow(emptyList()),
            onSelectNote = {},
            onClose = {},
            onSaveAsNew = { _, _, _ -> },
            onSave = { _, _, _ -> onSave() },
            onSaveAndOpenTasks = null,
            onDelete = null,
            onLinkedTaskComplete = {},
            onBackHandler = { _, _ -> },
            navigation = {},
        )
    }

    private fun ImageComposeScene.tap(position: Offset, move: Boolean) {
        sendPointerEvent(PointerEventType.Press, position, type = PointerType.Touch)
        val release = if (move) position + Offset(1f, 1f) else position
        if (move) sendPointerEvent(PointerEventType.Move, release, type = PointerType.Touch)
        sendPointerEvent(PointerEventType.Release, release, type = PointerType.Touch)
    }

    private fun ImageComposeScene.buttonCenter(label: String): Offset = nodes().single {
        it.config.contains(SemanticsActions.OnClick) && it.hasLabel(label)
    }.boundsInRoot.center

    private fun ImageComposeScene.nodes(): List<SemanticsNode> = semanticsOwners.flatMap {
        it.rootSemanticsNode.descendants()
    }

    private fun SemanticsNode.descendants(): List<SemanticsNode> = listOf(this) + children.flatMap { it.descendants() }

    private fun SemanticsNode.hasLabel(label: String): Boolean =
        config.getOrNull(SemanticsProperties.Text)?.any { it.text == label } == true ||
            config.getOrNull(SemanticsProperties.ContentDescription)?.contains(label) == true
}

package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.account_welcome_headline
import opentasks.composeapp.generated.resources.account_welcome_subtitle
import opentasks.composeapp.generated.resources.app_name
import opentasks.composeapp.generated.resources.ic_grid_view
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** Layout only: the caller retains credentials across width and theme changes. */
@Composable
internal fun AccountWelcomeLayout(showWelcome: Boolean, form: @Composable () -> Unit) {
    val dimens = OpenTasksTheme.dimens
    val split = showWelcome &&
        accountSessionLayoutFor(OpenTasksTheme.windowSizeCategory) == AccountSessionLayout.LARGER
    Box(
        Modifier.fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(dimens.paddingXXLarge),
        contentAlignment = Alignment.Center,
    ) {
        if (split) {
            Surface(
                modifier = Modifier.widthIn(max = dimens.authWelcomeMaxWidth).fillMaxWidth(),
                shape = RoundedCornerShape(dimens.cornerXLarge),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Row(Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.weight(1f).fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(dimens.paddingXXLarge),
                        verticalArrangement = Arrangement.spacedBy(dimens.paddingXXLarge, Alignment.CenterVertically),
                    ) {
                        WelcomeBrand()
                        WelcomeMessage(centered = false)
                        WelcomeIllustration(Modifier.fillMaxWidth().height(dimens.authIllustrationExpandedHeight))
                    }
                    Column(
                        Modifier.weight(1f).padding(dimens.paddingXXLarge),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        form()
                    }
                }
            }
        } else {
            Column(
                Modifier.widthIn(max = dimens.authPanelMaxWidth).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimens.paddingXXLarge),
            ) {
                if (showWelcome) {
                    WelcomeBrand()
                    WelcomeIllustration(
                        Modifier.fillMaxWidth().height(dimens.authIllustrationCompactHeight),
                    )
                    WelcomeMessage(centered = true)
                }
                form()
            }
        }
    }
}

@Composable
private fun WelcomeBrand() {
    val dimens = OpenTasksTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.spacerLarge),
    ) {
        Icon(
            painterResource(Res.drawable.ic_grid_view),
            contentDescription = null,
            modifier = Modifier.size(dimens.touchTargetMedium),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            stringResource(Res.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun WelcomeMessage(centered: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(OpenTasksTheme.dimens.spacerLarge)) {
        Text(
            stringResource(Res.string.account_welcome_headline),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Medium,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            stringResource(Res.string.account_welcome_subtitle),
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Decorative artwork in a fixed vector coordinate space; all colors follow the active theme. */
@Composable
private fun WelcomeIllustration(modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Canvas(modifier) {
        val artworkScale = minOf(size.width / 320f, size.height / 240f)
        val offset = Offset((size.width - 320f * artworkScale) / 2f, (size.height - 240f * artworkScale) / 2f)
        withTransform({
            translate(offset.x, offset.y)
            scale(artworkScale, artworkScale, Offset.Zero)
        }) {
            drawOval(colors.primary.copy(alpha = 0.10f), Offset(28f, 209f), Size(278f, 20f))
            rotate(-10f, Offset(134f, 130f)) {
                drawRoundRect(colors.primary.copy(alpha = 0.65f), Offset(76f, 39f), Size(119f, 165f), CornerRadius(9f))
            }
            rotate(12f, Offset(220f, 150f)) {
                drawRoundRect(colors.primary.copy(alpha = 0.22f), Offset(180f, 79f), Size(91f, 126f), CornerRadius(8f))
                repeat(4) { row ->
                    drawLine(colors.primary.copy(alpha = 0.4f), Offset(200f, 103f + row * 21f), Offset(250f, 103f + row * 21f), 5f, StrokeCap.Round)
                }
            }
            // A restrained sprig balances the papers without adding interactive semantics.
            drawLine(colors.onSurfaceVariant, Offset(77f, 207f), Offset(42f, 127f), 2f, StrokeCap.Round)
            val leaf = Path().apply {
                moveTo(59f, 170f)
                cubicTo(32f, 166f, 24f, 143f, 29f, 133f)
                cubicTo(48f, 135f, 59f, 151f, 59f, 170f)
                moveTo(66f, 187f)
                cubicTo(74f, 159f, 87f, 155f, 91f, 155f)
                cubicTo(98f, 173f, 82f, 187f, 66f, 187f)
                moveTo(72f, 200f)
                cubicTo(43f, 204f, 30f, 189f, 30f, 180f)
                cubicTo(50f, 177f, 65f, 188f, 72f, 200f)
            }
            drawPath(leaf, colors.onSurfaceVariant.copy(alpha = 0.75f))
            rotate(6f, Offset(157f, 130f)) {
                drawRoundRect(colors.surface, Offset(101f, 45f), Size(119f, 165f), CornerRadius(8f))
                drawRoundRect(colors.onSurfaceVariant.copy(alpha = 0.35f), Offset(101f, 45f), Size(119f, 165f), CornerRadius(8f), style = Stroke(1.5f))
                repeat(3) { row ->
                    val y = 79f + row * 43f
                    val check = Path().apply {
                        moveTo(119f, y)
                        lineTo(126f, y + 7f)
                        lineTo(139f, y - 7f)
                    }
                    drawPath(check, colors.primary, style = Stroke(4f, cap = StrokeCap.Round))
                    drawLine(colors.onSurfaceVariant.copy(alpha = 0.55f), Offset(153f, y), Offset(199f, y), 4f, StrokeCap.Round)
                    drawLine(colors.onSurfaceVariant.copy(alpha = 0.25f), Offset(153f, y + 10f), Offset(185f, y + 10f), 3f, StrokeCap.Round)
                }
            }
            drawLine(colors.primary, Offset(223f, 31f), Offset(226f, 16f), 4f, StrokeCap.Round)
            drawLine(colors.primary, Offset(237f, 39f), Offset(247f, 27f), 4f, StrokeCap.Round)
            drawLine(colors.primary, Offset(245f, 52f), Offset(261f, 47f), 4f, StrokeCap.Round)
        }
    }
}

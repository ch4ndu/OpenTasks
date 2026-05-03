package com.udnahc.opentasks.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.cancel
import opentasks.composeapp.generated.resources.done
import opentasks.composeapp.generated.resources.importing
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ImportLoadingRow() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.width(OpenTasksTheme.dimens.spacerXLarge))
        Text(stringResource(Res.string.importing))
    }
}

@Composable
internal fun ImportSuccessText(text: String) {
    Text(
        text = text,
        color = PrimaryBlue,
    )
}

@Composable
internal fun ImportErrorText(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
internal fun ImportDoneButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(stringResource(Res.string.done), color = PrimaryBlue)
    }
}

@Composable
internal fun ImportCancelButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            stringResource(Res.string.cancel),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

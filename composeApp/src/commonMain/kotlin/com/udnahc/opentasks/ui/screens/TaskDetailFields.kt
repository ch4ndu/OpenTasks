package com.udnahc.opentasks.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.udnahc.opentasks.ui.theme.OpenTasksTheme
import com.udnahc.opentasks.ui.theme.PrimaryBlue
import opentasks.composeapp.generated.resources.Res
import opentasks.composeapp.generated.resources.attendees_hint
import opentasks.composeapp.generated.resources.event_status_hint
import opentasks.composeapp.generated.resources.ic_chevron_right
import opentasks.composeapp.generated.resources.ic_dropdown
import opentasks.composeapp.generated.resources.ic_group
import opentasks.composeapp.generated.resources.ic_info
import opentasks.composeapp.generated.resources.ic_link
import opentasks.composeapp.generated.resources.ic_location_on
import opentasks.composeapp.generated.resources.ic_open_in_new
import opentasks.composeapp.generated.resources.ic_person
import opentasks.composeapp.generated.resources.location_hint
import opentasks.composeapp.generated.resources.more_details
import opentasks.composeapp.generated.resources.open_in_maps
import opentasks.composeapp.generated.resources.organizer_hint
import opentasks.composeapp.generated.resources.section_hint
import opentasks.composeapp.generated.resources.url_hint
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun TaskDetailFields(
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    section: String,
    onSectionChange: (String) -> Unit,
    location: String,
    onLocationChange: (String) -> Unit,
    onOpenInMaps: () -> Unit,
    taskUrl: String,
    onUrlChange: (String) -> Unit,
    organizer: String,
    onOrganizerChange: (String) -> Unit,
    eventStatus: String,
    onStatusChange: (String) -> Unit,
    attendees: String,
    onAttendeesChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = OpenTasksTheme.dimens

    Column(modifier = modifier.fillMaxWidth()) {
        // Toggle button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimens.paddingMedium)
                .clickable(onClick = onToggleDetails),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(
                    if (showDetails) Res.drawable.ic_dropdown else Res.drawable.ic_chevron_right
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimens.iconDefault),
            )
            Spacer(Modifier.width(dimens.spacerSmall))
            Text(
                text = stringResource(Res.string.more_details),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedVisibility(visible = showDetails) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(dimens.spacerMedium),
            ) {
                // Section
                OutlinedTextField(
                    value = section,
                    onValueChange = onSectionChange,
                    placeholder = { Text(stringResource(Res.string.section_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Location
                OutlinedTextField(
                    value = location,
                    onValueChange = onLocationChange,
                    placeholder = { Text(stringResource(Res.string.location_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_location_on),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    trailingIcon = {
                        if (location.isNotBlank()) {
                            IconButton(onClick = onOpenInMaps) {
                                Icon(
                                    painter = painterResource(Res.drawable.ic_open_in_new),
                                    contentDescription = stringResource(Res.string.open_in_maps),
                                    tint = PrimaryBlue,
                                    modifier = Modifier.size(dimens.iconDefault),
                                )
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // URL
                OutlinedTextField(
                    value = taskUrl,
                    onValueChange = onUrlChange,
                    placeholder = { Text(stringResource(Res.string.url_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_link),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Organizer
                OutlinedTextField(
                    value = organizer,
                    onValueChange = onOrganizerChange,
                    placeholder = { Text(stringResource(Res.string.organizer_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_person),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Status
                OutlinedTextField(
                    value = eventStatus,
                    onValueChange = onStatusChange,
                    placeholder = { Text(stringResource(Res.string.event_status_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_info),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // Attendees
                OutlinedTextField(
                    value = attendees,
                    onValueChange = onAttendeesChange,
                    placeholder = { Text(stringResource(Res.string.attendees_hint)) },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.ic_group),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconDefault),
                        )
                    },
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(dimens.spacerSmall))
            }
        }
    }
}

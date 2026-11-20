package org.un1box.android

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * The device library, as an iOS 6 sheet: a blue title bar with Done, a lacquered white table of
 * devices, and the Add device row at the bottom. Renaming and deleting are iOS 6 alert sheets.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun DevicesSheet(host: GuestHost, onDismiss: () -> Unit) {
    var renaming by remember { mutableStateOf<DeviceSummary?>(null) }
    var deleting by remember { mutableStateOf<DeviceSummary?>(null) }
    BackHandler(onBack = onDismiss)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }.background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { onDismiss() },
            contentAlignment = Alignment.BottomCenter) {
            Column(Modifier.fillMaxWidth().fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                .background(Ios6.page(Ios6.isDark()))
                .ios6Pinstripe(Ios6.isDark())
                .clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) {}
                .testTag("devices_sheet")) {
                Ios6NavBar("Devices", end = { Ios6BarButton(label = "Done", onClick = onDismiss) })
                if (host.active) Text("Stop ${host.selected?.name ?: "the running device"} to switch to or add another.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                LazyColumn(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    items(host.devices, key = { it.id }) { device ->
                        // A running device's record is marked in use, which is not a crash to recover from.
                        DeviceRow(device, switchable = !host.active && !host.busy, editable = !(host.active && device.selected),
                            needsRecovery = device.dirty && !(host.active && device.selected),
                            onSelect = { host.selectDevice(device.id); onDismiss() },
                            onDetails = { host.openDetails(device) }, onRename = { renaming = device }, onDelete = { deleting = device })
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)
                            .ios6Lacquer(RoundedCornerShape(10.dp), Ios6.GrayButtonTop, Ios6.GrayButtonBottom, Ios6.GrayButtonEdge)
                            .clickable(enabled = !host.busy && !host.active) { onDismiss(); host.addDevice() }
                            .padding(12.dp).testTag("add_device").alpha(if (host.active) 0.4f else 1f),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).ios6Lacquer(RoundedCornerShape(9.dp), Ios6.ButtonTop, Ios6.ButtonBottom, Ios6.ButtonEdge),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Add, null, tint = Color.White)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                // The Add row is silver lacquer in both themes, so both labels are the dark ink.
                                Text("Add device", style = MaterialTheme.typography.titleSmall, color = Ios6.Ink)
                                Text("From an IPSW, or a device image you have", style = MaterialTheme.typography.bodySmall,
                                    color = Ios6.Ink.copy(alpha = 0.66f))
                            }
                            Ios6Chevron()
                        }
                    }
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
    renaming?.let { device -> RenameDialog(device, { renaming = null }) { host.renameDevice(device.id, it); renaming = null } }
    deleting?.let { device ->
        AlertDialog(onDismissRequest = { deleting = null }, modifier = DialogTags, icon = { Icon(Icons.Filled.Delete, null) },
            title = { Text("Delete ${device.name}?", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center) },
            text = { Text("Its apps, settings and data are removed from this phone. This can't be undone.") },
            confirmButton = { Button(onClick = { host.deleteDevice(device.id); deleting = null },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.testTag("confirm_delete")) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } })
    }
}

/** One white iOS 6 table row for a device, with its selection mark and its options menu. */
@Composable
private fun DeviceRow(device: DeviceSummary, switchable: Boolean, editable: Boolean, needsRecovery: Boolean,
                      onSelect: () -> Unit, onDetails: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column {
        Row(Modifier.fillMaxWidth()
            .ios6CellLacquer(selected = device.selected)
            .clickable(enabled = switchable && !device.selected, onClick = onSelect)
            .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp).testTag("device_row"),
            verticalAlignment = Alignment.CenterVertically) {
            Box {
                Box(Modifier.size(40.dp).ios6Lacquer(RoundedCornerShape(9.dp), Ios6.ButtonTop, Ios6.ButtonBottom, Ios6.ButtonEdge),
                    contentAlignment = Alignment.Center) {
                    Icon(LtIcons.Phone, null, Modifier.size(20.dp), tint = Color.White)
                }
                // It didn't shut down cleanly: the home screen offers Recover and start.
                if (needsRecovery) Box(Modifier.align(Alignment.BottomEnd).size(18.dp)
                    .background(Color(0xFFD93B30), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Warning, "Needs recovery", Modifier.size(12.dp), tint = Color.White)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(device.subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (device.selected) Box(Modifier.size(22.dp).ios6Lacquer(CircleShape, Ios6.ButtonTop, Ios6.ButtonBottom, Ios6.ButtonEdge),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, "Selected", Modifier.size(14.dp), tint = Color.White)
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Options for ${device.name}") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Details") }, leadingIcon = { Icon(LtIcons.Info, null) }, onClick = { menu = false; onDetails() },
                        modifier = Modifier.testTag("device_details_item"))
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Filled.Edit, null) }, onClick = { menu = false; onRename() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.Delete, null) }, enabled = editable,
                        onClick = { menu = false; onDelete() })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
internal fun RenameDialog(device: DeviceSummary, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    // Opens with the whole name selected, so typing replaces it and a tap places the cursor.
    var name by remember { mutableStateOf(TextFieldValue(device.name, TextRange(0, device.name.length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = { if (name.text.isNotBlank()) onRename(name.text.trim()) }
    AlertDialog(onDismissRequest = onDismiss, modifier = DialogTags, title = { Text("Rename device", style = MaterialTheme.typography.titleMedium) },
        text = {
            OutlinedTextField(name, { if (fitsDeviceName(it.text)) name = it }, singleLine = true, label = { Text("Name") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { save() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("rename_field"))
        },
        confirmButton = { TextButton(onClick = save, enabled = name.text.isNotBlank(), modifier = Modifier.testTag("rename_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

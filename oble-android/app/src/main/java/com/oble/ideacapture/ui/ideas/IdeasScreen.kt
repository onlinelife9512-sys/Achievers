package com.oble.ideacapture.ui.ideas

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.oble.ideacapture.data.IdeaEntity
import com.oble.ideacapture.ui.components.IdeaActions
import com.oble.ideacapture.ui.components.IdeaCard
import com.oble.ideacapture.ui.theme.Oble

@Composable
fun obleFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Oble.GreenLine,
    unfocusedBorderColor = Oble.Line,
    focusedContainerColor = Oble.Charcoal,
    unfocusedContainerColor = Oble.Charcoal,
    cursorColor = Oble.Green,
    focusedLabelColor = Oble.Green,
)

@Composable
fun IdeasScreen(vm: IdeasViewModel) {
    val context = LocalContext.current
    val ideas by vm.ideas.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<IdeaEntity?>(null) }
    var deleting by remember { mutableStateOf<IdeaEntity?>(null) }

    val actions = IdeaActions(
        onCopy = { vm.copy(context, it); Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() },
        onShare = { vm.share(context, it) },
        onWhatsApp = { idea -> vm.sendToWhatsApp(context, idea) { msg -> msg?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } } },
        onEdit = { editing = it },
        onDelete = { deleting = it },
        onMarkSent = { vm.markSent(it) },
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        Text("Ideas", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 28.dp, bottom = 16.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { vm.query.value = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search ideas, categories, tasks", color = Oble.TextFaint) },
            leadingIcon = { Icon(Icons.Outlined.Search, null, tint = Oble.TextFaint) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Outlined.Close, "Clear", tint = Oble.TextFaint) }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = obleFieldColors(),
        )
        Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IdeaFilter.entries.forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { vm.filter.value = f },
                    label = { Text(f.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Oble.GreenDeep, selectedLabelColor = Oble.Green,
                        containerColor = Oble.Black, labelColor = Oble.TextMuted,
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true, selected = filter == f,
                        borderColor = Oble.Line, selectedBorderColor = Oble.GreenLine,
                    ),
                )
            }
        }
        if (ideas.isEmpty()) {
            Spacer(Modifier.height(40.dp))
            Text(
                if (query.isBlank()) "No ideas yet. Start Idea Mode and talk naturally." else "Nothing matches “$query”.",
                style = MaterialTheme.typography.bodyMedium, color = Oble.TextFaint,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ideas, key = { it.id }) { IdeaCard(it, actions) }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editing?.let { idea -> EditIdeaDialog(idea, onSave = { vm.save(it); editing = null }, onDismiss = { editing = null }) }
    deleting?.let { idea ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Oble.Charcoal,
            title = { Text("Delete idea?") },
            text = { Text("“${idea.title}” will be removed from this phone.", color = Oble.TextMuted) },
            confirmButton = { TextButton(onClick = { vm.delete(idea); deleting = null }) { Text("Delete", color = Oble.Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel", color = Oble.TextMuted) } },
        )
    }
}

@Composable
private fun EditIdeaDialog(idea: IdeaEntity, onSave: (IdeaEntity) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(idea.title) }
    var summary by remember { mutableStateOf(idea.summary) }
    var category by remember { mutableStateOf(idea.category) }
    var status by remember { mutableStateOf(idea.status) }
    var actions by remember { mutableStateOf(idea.actionItems.joinToString("\n")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Oble.Charcoal,
        title = { Text("Edit idea") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title") }, singleLine = true, colors = obleFieldColors())
                OutlinedTextField(summary, { summary = it }, label = { Text("Summary") }, minLines = 2, colors = obleFieldColors())
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true, colors = obleFieldColors())
                OutlinedTextField(status, { status = it }, label = { Text("Status") }, singleLine = true, colors = obleFieldColors())
                OutlinedTextField(actions, { actions = it }, label = { Text("Next steps (one per line)") }, minLines = 2, colors = obleFieldColors())
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        idea.copy(
                            title = title.trim(), summary = summary.trim(), category = category.trim().ifEmpty { "General" },
                            status = status.trim().ifEmpty { "New Idea" },
                            actionItems = actions.lines().map { it.trim().removePrefix("•").trim() }.filter { it.isNotEmpty() },
                        )
                    )
                },
            ) { Text("Save", color = Oble.Green) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Oble.TextMuted) } },
    )
}

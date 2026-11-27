package com.tulipskun.aixodia.ui.files

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.tulipskun.aixodia.data.remote.FilesApi
import com.tulipskun.aixodia.data.remote.RemoteContent
import com.tulipskun.aixodia.data.remote.RemoteFile

/**
 * A file opened from the manager. Text and images are shown here from a bounded
 * read; other types get a clear "use Open with" message. The full download and
 * the system viewer stay with the caller, which already owns the SAF picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileViewer(
    file: RemoteFile,
    api: FilesApi,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onOpenWith: () -> Unit,
    onCopyPath: () -> Unit,
) {
    var content by remember(file.path) { mutableStateOf<RemoteContent?>(null) }
    var error by remember(file.path) { mutableStateOf("") }
    val kind = remember(file.name) { previewKind(file.name) }
    LaunchedEffect(file.path) {
        val limit = if (kind == PreviewKind.Image) 8 shl 20 else 1 shl 20
        runCatching { api.readLimited(file.path, limit) }
            .onSuccess { content = it }
            .onFailure { error = it.message ?: "Cannot read this file" }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        file.name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onCopyPath) {
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy path")
                    }
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = "Download")
                    }
                    IconButton(onClick = onOpenWith) {
                        Icon(Icons.Default.OpenInNew, contentDescription = "Open with")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Text(
                file.path,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                humanSize(file.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val failed = error
            val loaded = content
            when {
                failed.isNotBlank() -> Text(failed, color = MaterialTheme.colorScheme.error)
                loaded == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> {
                    if (loaded.truncated) {
                        Text(
                            "Preview shows the first " + humanSize(loaded.bytes.size.toLong()) +
                                " of " + humanSize(loaded.total),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    when (kind) {
                        PreviewKind.Text -> {
                            val text = remember(loaded) { String(loaded.bytes, Charsets.UTF_8) }
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                            )
                        }
                        PreviewKind.Image -> {
                            val bitmap = remember(loaded) {
                                BitmapFactory.decodeByteArray(loaded.bytes, 0, loaded.bytes.size)
                            }
                            if (bitmap == null) Text("This picture could not be decoded.")
                            else Image(
                                bitmap.asImageBitmap(),
                                contentDescription = file.name,
                                modifier = Modifier.fillMaxWidth(),
                                contentScale = ContentScale.Fit,
                            )
                        }
                        PreviewKind.Other -> Text(
                            "No in-app preview for this file type. Use Download or Open with.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

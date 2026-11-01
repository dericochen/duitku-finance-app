package com.duitku.app.presentation.screens.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.duitku.app.di.IoDispatcher
import com.duitku.app.domain.repository.ExportRecord
import com.duitku.app.domain.repository.ExportRepository
import com.duitku.app.presentation.components.DuitKuTopBar
import com.duitku.app.presentation.components.ErrorState
import com.duitku.app.presentation.components.LoadingBox
import com.duitku.app.presentation.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class PreviewUiState(
    val isLoading: Boolean = true,
    val record: ExportRecord? = null,
    val pageCount: Int = 0,
    val error: String? = null,
)

/** Menampilkan PDF hasil ekspor di dalam aplikasi memakai PdfRenderer (satu halaman terbuka pada satu waktu). */
@HiltViewModel
class PdfPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val export: ExportRepository,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    private val exportId = savedStateHandle.get<Long>(Screen.PdfPreview.ARG_ID) ?: -1L
    private val _state = MutableStateFlow(PreviewUiState())
    val state: StateFlow<PreviewUiState> = _state.asStateFlow()

    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val lock = Mutex()
    private val cache = object : android.util.LruCache<Int, ImageBitmap>(6) {}

    init {
        viewModelScope.launch {
            val record = export.getRecord(exportId)
            if (record == null || !record.fileExists) {
                _state.value = PreviewUiState(isLoading = false, error = "Berkas tidak ditemukan")
                return@launch
            }
            try {
                val count = withContext(io) {
                    val file = File(File(context.filesDir, "exports"), record.fileName)
                    val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                    descriptor = pfd
                    PdfRenderer(pfd).also { renderer = it }.pageCount
                }
                _state.value = PreviewUiState(isLoading = false, record = record, pageCount = count)
            } catch (e: Exception) {
                _state.value = PreviewUiState(isLoading = false, record = record, error = "PDF tidak dapat dibuka: ${e.message}")
            }
        }
    }

    suspend fun page(index: Int, widthPx: Int): ImageBitmap? {
        cache.get(index)?.let { return it }
        return withContext(io) {
            lock.withLock {
                val r = renderer ?: return@withLock null
                r.openPage(index).use { page ->
                    val height = (widthPx * page.height.toFloat() / page.width).toInt()
                    val bmp = Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp.asImageBitmap().also { cache.put(index, it) }
                }
            }
        }
    }

    override fun onCleared() {
        renderer?.close()
        descriptor?.close()
        super.onCleared()
    }
}

@Composable
fun PdfPreviewScreen(onBack: () -> Unit, viewModel: PdfPreviewViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Scaffold(
        topBar = {
            DuitKuTopBar(title = state.record?.fileName ?: "Pratinjau PDF", onBack = onBack, actions = {
                state.record?.let { r ->
                    IconButton(onClick = { ExportActions.print(context, r.fileName) }) { Icon(Icons.Rounded.Print, contentDescription = "Cetak") }
                    IconButton(onClick = { ExportActions.share(context, r.fileName, r.format) }) { Icon(Icons.Rounded.Share, contentDescription = "Bagikan") }
                }
            })
        },
    ) { padding ->
        when {
            state.isLoading -> LoadingBox(Modifier.padding(padding))
            state.error != null -> ErrorState(state.error!!, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(List(state.pageCount) { it }, key = { _, i -> i }) { _, index ->
                    PdfPage(index, viewModel)
                }
            }
        }
    }
}

@Composable
private fun PdfPage(index: Int, viewModel: PdfPreviewViewModel) {
    var image by remember(index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(index) { image = viewModel.page(index, widthPx = 1080) }
    Surface(shadowElevation = 3.dp, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f)) {
        val bitmap = image
        if (bitmap == null) {
            Box(contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            Image(bitmap = bitmap, contentDescription = "Halaman ${index + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth())
        }
    }
    Text("Halaman ${index + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(start = 4.dp))
}

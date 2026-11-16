package com.adischat.app.feature.shop.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.adischat.app.feature.shop.data.ShopBenefitEvidenceDto
import com.adischat.app.feature.shop.data.ShopBenefitsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class EvidencePage(val image: Bitmap, val totalPages: Int)

private fun renderEvidence(bytes: ByteArray, mime: String, pageIndex: Int, cacheDir: File): EvidencePage {
    if (mime != "application/pdf") {
        require(pageIndex == 0 && mime in setOf("image/jpeg", "image/png"))
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0)
        var sample = 1
        while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample }) ?: error("Image could not be opened.")
        return EvidencePage(bitmap, 1)
    }
    val file = File.createTempFile("benefit-preview-", ".pdf", cacheDir)
    try {
        file.writeBytes(bytes)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer ->
                require(pageIndex in 0 until renderer.pageCount)
                renderer.openPage(pageIndex).use { page ->
                    require(page.width > 0 && page.height > 0)
                    val scale = minOf(1600f / page.width, 2000f / page.height, 2f)
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt().coerceAtLeast(1),
                        (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888,
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return EvidencePage(bitmap, renderer.pageCount)
                }
            }
        }
    } finally {
        file.delete()
    }
}

@Composable
internal fun ShopBenefitEvidenceViewer(
    evidence: ShopBenefitEvidenceDto,
    onDismiss: () -> Unit,
    onReviewed: (String) -> Unit,
) {
    val context = LocalContext.current
    val repository = remember { ShopBenefitsRepository() }
    var bytes by remember(evidence.id) { mutableStateOf<ByteArray?>(null) }
    var page by remember(evidence.id) { mutableIntStateOf(0) }
    var rendered by remember(evidence.id) { mutableStateOf<EvidencePage?>(null) }
    var viewed by remember(evidence.id) { mutableStateOf<Set<Int>>(emptySet()) }
    var error by remember(evidence.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(evidence.id) {
        repository.adminPreview(evidence.id)
            .onSuccess { bytes = it }
            .onFailure { error = "Evidence is unavailable or access has expired." }
    }
    LaunchedEffect(bytes, page) {
        val source = bytes ?: return@LaunchedEffect
        rendered = null
        runCatching { withContext(Dispatchers.IO) { renderEvidence(source, evidence.mimeType, page, context.cacheDir) } }
            .onSuccess { result ->
                rendered = result
                viewed = viewed + page
                if (viewed.size == result.totalPages) onReviewed(evidence.id)
            }
            .onFailure { error = "Evidence could not be displayed." }
    }

    Dialog(onDismissRequest = onDismiss) {
      Surface(color = Color.White, shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(evidence.evidenceType.replace('_', ' '), color = BenefitInk)
            when {
                error != null -> Text(error.orEmpty(), color = BenefitInk)
                rendered == null -> CircularProgressIndicator(color = BenefitInk)
                else -> Image(
                    bitmap = rendered!!.image.asImageBitmap(), contentDescription = "Evidence page ${page + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp),
                )
            }
            if ((rendered?.totalPages ?: 0) > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { page-- }, enabled = page > 0) { Text("Previous") }
                    Text("${page + 1} / ${rendered?.totalPages ?: 0}", modifier = Modifier.padding(top = 12.dp))
                    OutlinedButton(onClick = { page++ }, enabled = page + 1 < (rendered?.totalPages ?: 0)) { Text("Next") }
                }
            }
            TextButton(onClick = onDismiss) { Text("Close") }
        }
      }
    }
}

package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalMuallimunPalette
import java.io.File
import java.io.FileOutputStream

object MediaHelper {

    val DESKTOP_VISUAL_ICON_MAP = mapOf(
        // Hayvanlar (15)
        "ari" to "🐝", "aslan" to "🦁", "fil" to "🐘", "horoz" to "🐓", "inek" to "🐄",
        "kaplumbaga" to "🐢", "karinca" to "🐜", "kedi" to "🐱", "kelebek" to "🦋", "kopek" to "🐕",
        "koyun" to "🐑", "kus" to "🐦", "tavsan" to "🐇", "tavuk" to "🐔", "zurafa" to "🦒",
        // Meyveler & Sebzeler (50)
        "ananas" to "🍍", "armut" to "🍐", "avakado" to "🥑", "avokado" to "🥑", "ayva" to "🍏", "bamya" to "🥬",
        "bezelye" to "🫛", "biber" to "🌶️", "burokoli" to "🥦", "brokoli" to "🥦", "cilek" to "🍓", "domates" to "🍅",
        "dut" to "🫐", "elma" to "🍎", "erik" to "🍑", "fasulye" to "🫘", "greyfurt" to "🍊",
        "havuc" to "🥕", "hindistan_cevizi" to "🥥", "hurma" to "🌴", "ispanak" to "🥬", "incir" to "🟣",
        "kabak" to "🎃", "karnibahar" to "🥦", "karnabahar" to "🥦", "karpuz" to "🍉", "kavun" to "🍈", "kayisi" to "🍑",
        "kereviz" to "🥬", "kiraz" to "🍒", "kivi" to "🥝", "lahana" to "🥬", "limon" to "🍋",
        "mandalina" to "🍊", "mantar" to "🍄", "marul" to "🥗", "maydonoz" to "🌿", "maydanoz" to "🌿", "misir" to "🌽",
        "muz" to "🍌", "nane" to "🌿", "nar" to "🍎", "patates" to "🥔", "patlican" to "🍆",
        "pirasa" to "🥬", "portakal" to "🍊", "salatalik" to "🥒", "sarimsak" to "🧄", "sogan" to "🧅",
        "salgam" to "🫜", "seftali" to "🍑", "turp" to "🫜", "uzum" to "🍇", "zeytin" to "🫒"
    )

    fun isLikelyFilePath(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        val v = value.trim().lowercase()
        return v.contains("/") || v.contains("\\") ||
            v.endsWith(".png") || v.endsWith(".jpg") || v.endsWith(".jpeg") || v.endsWith(".webp")
    }

    fun resolveEmojiFallback(wordId: String, paketId: String, gorselField: String?): String {
        if (!gorselField.isNullOrBlank() && !isLikelyFilePath(gorselField) && gorselField.trim().length <= 6) {
            return gorselField.trim()
        }
        val cleanId = wordId.lowercase().trim().removePrefix("w_")
        DESKTOP_VISUAL_ICON_MAP[cleanId]?.let { return it }

        if (!gorselField.isNullOrBlank()) {
            val stem = gorselField.replace("\\", "/")
                .substringAfterLast("/")
                .substringBeforeLast(".")
                .lowercase()
                .trim()
            DESKTOP_VISUAL_ICON_MAP[stem]?.let { return it }
        }

        return when {
            paketId.contains("hayvan", ignoreCase = true) -> "🐾"
            paketId.contains("meyve", ignoreCase = true) || paketId.contains("sebze", ignoreCase = true) -> "🍎"
            else -> "🖼️"
        }
    }

    /**
     * Önce filesDir (kullanıcının eklediği veya ZIP'ten çıkan custom_media) içinde,
     * ardından assets/ (ve assets/categories/...) altında görsel dosyasını (.png/.jpg/.webp) arar.
     */
    fun loadWordBitmap(
        context: Context,
        wordId: String,
        paketId: String,
        gorselField: String?
    ): Bitmap? {
        val cleanPath = gorselField?.trim()?.replace("\\", "/")?.trimStart('/') ?: ""
        val cleanWordId = wordId.trim().removePrefix("w_")
        val extensions = listOf("png", "jpg", "jpeg", "webp")

        // 1. Yerel Dosya Sistemi (filesDir / custom_media) adayları
        val fileCandidates = mutableListOf<File>()
        if (cleanPath.isNotEmpty() && isLikelyFilePath(cleanPath)) {
            val direct = File(cleanPath)
            if (direct.isAbsolute) fileCandidates.add(direct)
            fileCandidates.add(File(context.filesDir, cleanPath))
            fileCandidates.add(File(context.filesDir, "custom_media/$cleanPath"))
            fileCandidates.add(File(context.filesDir, "custom_media/images/${File(cleanPath).name}"))
        }
        for (ext in extensions) {
            if (paketId.isNotBlank()) {
                fileCandidates.add(File(context.filesDir, "custom_media/categories/$paketId/$cleanWordId.$ext"))
                fileCandidates.add(File(context.filesDir, "categories/$paketId/$cleanWordId.$ext"))
            }
            fileCandidates.add(File(context.filesDir, "custom_media/images/$cleanWordId.$ext"))
        }

        for (candidate in fileCandidates) {
            if (candidate.exists() && candidate.isFile && candidate.length() > 32) {
                try {
                    val bmp = BitmapFactory.decodeFile(candidate.absolutePath)
                    if (bmp != null) return bmp
                } catch (_: Exception) {
                }
            }
        }

        // 2. APK İçi Assets (assets/ ve assets/categories/...) adayları
        val assetCandidates = mutableListOf<String>()
        if (cleanPath.isNotEmpty() && isLikelyFilePath(cleanPath)) {
            assetCandidates.add(cleanPath)
            if (!cleanPath.startsWith("categories/")) {
                assetCandidates.add("categories/$cleanPath")
            }
            if (paketId.isNotBlank()) {
                assetCandidates.add("categories/$paketId/${File(cleanPath).name}")
            }
        }
        for (ext in extensions) {
            if (paketId.isNotBlank()) {
                assetCandidates.add("categories/$paketId/$cleanWordId.$ext")
                if (wordId != cleanWordId) {
                    assetCandidates.add("categories/$paketId/$wordId.$ext")
                }
            }
            assetCandidates.add("categories/$cleanWordId.$ext")
        }

        for (assetPath in assetCandidates.distinct()) {
            try {
                context.assets.open(assetPath).use { inputStream ->
                    val bmp = BitmapFactory.decodeStream(inputStream)
                    if (bmp != null) return bmp
                }
            } catch (_: Exception) {
            }
        }

        return null
    }

    /**
     * Kullanıcının telefondan seçtiği görsel (.png/.jpg) veya ses (.mp3/.wav) dosyasını
     * uygulama içi kalıcı `filesDir/custom_media/<subfolder>/` dizinine kopyalar.
     */
    fun copyPickedMediaToCustomStorage(
        context: Context,
        uri: Uri,
        subfolder: String, // "images" veya "audio"
        itemPrefix: String,
        defaultExtension: String
    ): String? {
        return try {
            val mime = context.contentResolver.getType(uri)?.lowercase() ?: ""
            val ext = when {
                mime.contains("png") -> "png"
                mime.contains("webp") -> "webp"
                mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
                mime.contains("wav") -> "wav"
                mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
                mime.contains("ogg") -> "ogg"
                else -> defaultExtension
            }
            val safePrefix = itemPrefix.lowercase().replace(Regex("[^a-z0-9_]"), "_").ifBlank { "media" }
            val targetDir = File(context.filesDir, "custom_media/$subfolder").apply { mkdirs() }
            val fileName = "${safePrefix}_${System.currentTimeMillis()}.$ext"
            val outFile = File(targetDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            "custom_media/$subfolder/$fileName"
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun HybridWordVisualBox(
    wordId: String,
    paketId: String,
    gorsel: String?,
    sizeDp: Dp = 80.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalMuallimunPalette.current
    val context = LocalContext.current

    val bitmap = remember(wordId, paketId, gorsel) {
        MediaHelper.loadWordBitmap(context, wordId, paketId, gorsel)
    }
    val fallbackEmoji = remember(wordId, paketId, gorsel) {
        MediaHelper.resolveEmojiFallback(wordId, paketId, gorsel)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.panelColor,
        border = BorderStroke(1.dp, palette.border),
        modifier = modifier.size(sizeDp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = wordId,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Text(
                    text = fallbackEmoji,
                    fontSize = (sizeDp.value * 0.5f).sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

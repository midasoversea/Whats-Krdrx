package com.whats.krdrx

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.ImageDecoder
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class VideoProfile(val label: String, val maxShortSide: Int, val maxLongSide: Int) {
    HD720("720p", 720, 1280),
    FHD1080("1080p", 1080, 1920)
}

enum class OptimizeKind { IMAGE, VIDEO }

data class OptimizeResult(
    val outputFile: File,
    val kind: OptimizeKind,
    val inputBytes: Long,
    val outputBytes: Long,
    val outputWidth: Int,
    val outputHeight: Int,
    val note: String? = null
)

/**
 * Pra-kompresi media sebelum dikirim ke WhatsApp.
 *
 * Catatan: WhatsApp tetap memproses ulang media Status di sisi mereka. Tujuan kelas ini
 * adalah mengirim berkas yang sudah mendekati target WhatsApp (resolusi, codec, ukuran)
 * sehingga kompresi ulang oleh WhatsApp lebih ringan.
 */
@OptIn(UnstableApi::class)
object StatusOptimizer {

    const val PACKAGE_WHATSAPP = "com.whatsapp"
    const val PACKAGE_WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    // Gambar
    private const val IMAGE_MAX_SIDE = 1600
    private const val JPEG_QUALITY = 90

    // Video
    // Batas ukuran konservatif (belum terverifikasi sebagai batas resmi Status WhatsApp).
    private const val MAX_OUTPUT_BYTES = 16L * 1024 * 1024
    private const val TARGET_FPS = 30f
    private const val AUDIO_BITRATE = 128_000
    private const val MIN_VIDEO_BITRATE = 600_000
    private const val MAX_VIDEO_BITRATE = 8_000_000
    private const val BITS_PER_PIXEL = 0.14
    private const val MUXING_OVERHEAD = 0.93

    private const val CACHE_DIR = "status_optimized"

    // ------------------------------------------------------------------ umum

    fun outputDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    fun clearCache(context: Context) {
        File(context.cacheDir, CACHE_DIR).deleteRecursively()
    }

    fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.let { return it }
            }
        }
        return uri.lastPathSegment ?: "media"
    }

    private fun querySize(context: Context, uri: Uri): Long {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
            }
        }
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 } ?: 0L
    }

    private fun safeBaseName(name: String): String =
        name.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(40)
            .ifBlank { "media" }

    private fun uniqueFile(dir: File, base: String, ext: String): File {
        var file = File(dir, "${base}_status.$ext")
        var n = 1
        while (file.exists()) {
            file = File(dir, "${base}_status_$n.$ext")
            n++
        }
        return file
    }

    /** Titik masuk: memproses satu media. Panggil dari coroutine pada thread utama. */
    suspend fun optimize(
        context: Context,
        uri: Uri,
        isVideo: Boolean,
        profile: VideoProfile,
        onProgress: (Int) -> Unit
    ): OptimizeResult {
        val dir = outputDir(context)
        val base = safeBaseName(displayName(context, uri))
        return if (isVideo) {
            optimizeVideo(context, uri, uniqueFile(dir, base, "mp4"), profile, onProgress)
        } else {
            optimizeImage(context, uri, uniqueFile(dir, base, "jpg")).also { onProgress(100) }
        }
    }

    // ---------------------------------------------------------------- gambar

    private suspend fun optimizeImage(context: Context, uri: Uri, out: File): OptimizeResult =
        withContext(Dispatchers.IO) {
            val inputBytes = querySize(context, uri)
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val longSide = max(w, h)
                    if (longSide > IMAGE_MAX_SIDE) {
                        val scale = IMAGE_MAX_SIDE.toFloat() / longSide
                        decoder.setTargetSize(
                            (w * scale).roundToInt().coerceAtLeast(1),
                            (h * scale).roundToInt().coerceAtLeast(1)
                        )
                    }
                    // Konversi ke sRGB agar warna tetap akurat di semua aplikasi
                    // (mis. foto Display P3 dari kamera/ponsel flagship).
                    decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }

                // JPEG tidak mendukung transparansi: ratakan di atas latar putih.
                val bitmap = if (decoded.hasAlpha()) {
                    Bitmap.createBitmap(
                        decoded.width, decoded.height, Bitmap.Config.ARGB_8888, false,
                        ColorSpace.get(ColorSpace.Named.SRGB)
                    ).also { flat ->
                        Canvas(flat).apply {
                            drawColor(Color.WHITE)
                            drawBitmap(decoded, 0f, 0f, null)
                        }
                        decoded.recycle()
                    }
                } else decoded

                FileOutputStream(out).use { stream ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) {
                        "Gagal menyimpan JPEG."
                    }
                }
                val w = bitmap.width
                val h = bitmap.height
                bitmap.recycle()

                OptimizeResult(
                    outputFile = out,
                    kind = OptimizeKind.IMAGE,
                    inputBytes = inputBytes,
                    outputBytes = out.length(),
                    outputWidth = w,
                    outputHeight = h,
                    note = "JPEG ${JPEG_QUALITY}%, sRGB, metadata (termasuk lokasi GPS) dihapus."
                )
            } catch (e: Throwable) {
                out.delete()
                throw e
            }
        }

    // ----------------------------------------------------------------- video

    private data class VideoInfo(
        val width: Int,
        val height: Int,
        val rotation: Int,
        val durationMs: Long,
        val fps: Float
    )

    private fun readVideoInfo(context: Context, uri: Uri): VideoInfo {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            fun meta(key: Int) = retriever.extractMetadata(key)
            val width = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val duration = meta(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val frames = meta(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toLongOrNull()
            val fps = if (frames != null && frames > 0 && duration > 0) frames * 1000f / duration else 0f
            return VideoInfo(width, height, rotation, duration, fps)
        } finally {
            retriever.release()
        }
    }

    private fun videoTrackMime(context: Context, uri: Uri): String? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            (0 until extractor.trackCount).firstNotNullOfOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.takeIf { it.startsWith("video/") }
            }
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun even(value: Int): Int = (value / 2 * 2).coerceAtLeast(2)

    /** Hanya memperkecil (tidak pernah memperbesar), menjaga rasio asli. */
    private fun fitWithin(width: Int, height: Int, profile: VideoProfile): Pair<Int, Int> {
        val shortSide = min(width, height)
        val longSide = max(width, height)
        val scale = min(
            1f,
            min(profile.maxShortSide.toFloat() / shortSide, profile.maxLongSide.toFloat() / longSide)
        )
        if (scale >= 1f) return width to height
        return even((width * scale).roundToInt()) to even((height * scale).roundToInt())
    }

    private data class BitratePlan(val bitrate: Int, val limitedByBudget: Boolean)

    private fun planBitrate(width: Int, height: Int, fps: Float, durationMs: Long): BitratePlan {
        val byQuality = (width.toLong() * height * fps * BITS_PER_PIXEL).toLong()
            .coerceIn(MIN_VIDEO_BITRATE.toLong(), MAX_VIDEO_BITRATE.toLong())
        if (durationMs <= 0) return BitratePlan(byQuality.toInt(), false)
        val budget = (MAX_OUTPUT_BYTES * 8 * MUXING_OVERHEAD / (durationMs / 1000.0) - AUDIO_BITRATE).toLong()
        val limited = budget < byQuality
        val final = min(byQuality, budget).coerceAtLeast(MIN_VIDEO_BITRATE.toLong())
        return BitratePlan(final.toInt(), limited)
    }

    private suspend fun optimizeVideo(
        context: Context,
        uri: Uri,
        out: File,
        profile: VideoProfile,
        onProgress: (Int) -> Unit
    ): OptimizeResult {
        val info = withContext(Dispatchers.IO) { readVideoInfo(context, uri) }
        check(info.width > 0 && info.height > 0) { "Metadata video tidak dapat dibaca." }

        val rotated = info.rotation == 90 || info.rotation == 270
        val displayW = if (rotated) info.height else info.width
        val displayH = if (rotated) info.width else info.height
        val (targetW, targetH) = fitWithin(displayW, displayH, profile)
        val needScale = targetW != displayW || targetH != displayH

        val inputBytes = querySize(context, uri)
        val container = context.contentResolver.getType(uri)
        val codec = withContext(Dispatchers.IO) { videoTrackMime(context, uri) }
        val fpsOk = info.fps in 0.1f..(TARGET_FPS + 0.5f)

        // Sudah sesuai: salin apa adanya. Re-encode tanpa perlu hanya menambah penurunan kualitas.
        val alreadyOptimal = !needScale && fpsOk &&
            codec == MimeTypes.VIDEO_H264 &&
            container == "video/mp4" &&
            inputBytes in 1..MAX_OUTPUT_BYTES
        if (alreadyOptimal) {
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                }
            }
            onProgress(100)
            return OptimizeResult(
                outputFile = out,
                kind = OptimizeKind.VIDEO,
                inputBytes = inputBytes,
                outputBytes = out.length(),
                outputWidth = displayW,
                outputHeight = displayH,
                note = "Sudah sesuai batas, tidak dikompresi ulang."
            )
        }

        val outFps = if (info.fps > 0f) min(info.fps, TARGET_FPS) else TARGET_FPS
        val plan = planBitrate(targetW, targetH, outFps, info.durationMs)

        // FrameDropEffect selalu dipasang: membatasi ke 30 fps sekaligus memastikan video
        // benar-benar ditranskode (efek "no-op" bisa membuat Transformer hanya menyalin stream
        // sehingga pengaturan bitrate tidak berlaku).
        val effects = buildList<Effect> {
            add(FrameDropEffect.createDefaultFrameDropEffect(TARGET_FPS))
            if (needScale) {
                add(Presentation.createForWidthAndHeight(targetW, targetH, Presentation.LAYOUT_SCALE_TO_FIT))
            }
        }

        try {
            runTransformer(context, uri, out, effects, plan.bitrate, onProgress)
        } catch (e: ExportException) {
            out.delete()
            throw IllegalStateException("Gagal memproses video (kode ${e.errorCode}). Format mungkin tidak didukung perangkat.", e)
        } catch (e: Throwable) {
            out.delete()
            throw e
        }

        val outBytes = out.length()
        val notes = buildList {
            add("H.264, maks ${TARGET_FPS.toInt()} fps, ${plan.bitrate / 1000} kbps")
            if (plan.limitedByBudget) add("bitrate diturunkan agar ≤ ${MAX_OUTPUT_BYTES / 1024 / 1024} MB")
            if (outBytes > MAX_OUTPUT_BYTES) add("ukuran masih di atas ${MAX_OUTPUT_BYTES / 1024 / 1024} MB, pertimbangkan memotong video")
        }
        return OptimizeResult(
            outputFile = out,
            kind = OptimizeKind.VIDEO,
            inputBytes = inputBytes,
            outputBytes = outBytes,
            outputWidth = targetW,
            outputHeight = targetH,
            note = notes.joinToString("; ") + "."
        )
    }

    /** Transformer wajib dijalankan di thread yang memiliki Looper (thread utama). */
    private suspend fun runTransformer(
        context: Context,
        input: Uri,
        output: File,
        videoEffects: List<Effect>,
        bitrate: Int,
        onProgress: (Int) -> Unit
    ): ExportResult = withContext(Dispatchers.Main.immediate) {
        coroutineScope {
            var transformerRef: Transformer? = null
            val progressJob = launch {
                val holder = ProgressHolder()
                while (isActive) {
                    val t = transformerRef
                    if (t != null && t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress)
                    }
                    delay(300)
                }
            }
            try {
                suspendCancellableCoroutine<ExportResult> { cont ->
                    val encoderFactory = DefaultEncoderFactory.Builder(context.applicationContext)
                        .setRequestedVideoEncoderSettings(
                            VideoEncoderSettings.Builder()
                                .setBitrate(bitrate)
                                .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
                                .build()
                        )
                        .build()

                    val transformer = Transformer.Builder(context.applicationContext)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)
                        .setEncoderFactory(encoderFactory)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(exportResult)
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                if (cont.isActive) cont.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformerRef = transformer

                    val edited = EditedMediaItem.Builder(MediaItem.fromUri(input))
                        .setEffects(Effects(emptyList(), videoEffects))
                        .build()

                    cont.invokeOnCancellation {
                        Handler(Looper.getMainLooper()).post { transformer.cancel() }
                    }
                    transformer.start(edited, output.absolutePath)
                }
            } finally {
                progressJob.cancel()
            }
        }
    }

    // --------------------------------------------------------------- berbagi

    fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }

    private fun mimeOf(file: File): String =
        when (file.extension.lowercase()) {
            "mp4" -> "video/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            else -> "*/*"
        }

    /** Bagikan hasil optimasi ke WhatsApp / WhatsApp Business lewat FileProvider. */
    fun shareToWhatsApp(context: Context, files: List<File>, packageName: String): Boolean {
        if (files.isEmpty()) return false
        val authority = "${context.packageName}.fileprovider"
        val uris = ArrayList(files.map { FileProvider.getUriForFile(context, authority, it) })
        val types = files.map { mimeOf(it) }.distinct()
        val mime = when {
            types.size == 1 -> types.first()
            types.all { it.startsWith("image/") } -> "image/*"
            types.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, uris[0]) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply { putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris) }
        }
        intent.type = mime
        intent.setPackage(packageName)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.clipData = ClipData.newUri(context.contentResolver, "status", uris[0]).also { clip ->
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

package com.example.skyflowtracker.utils

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class DemoVideoPlayer(
    private val assets: AssetManager,
    private val fileName: String,
    private val onFrame: (ByteArray) -> Unit,
    private val onComplete: () -> Unit
) {
    private val TAG = "DemoVideoPlayer"
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null

    fun start() {
        if (running.get()) return
        running.set(true)
        thread = Thread { decode() }
        thread?.start()
    }

    fun stop() {
        running.set(false)
        thread?.interrupt()
        thread = null
    }

    private fun decode() {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        try {
            extractor = MediaExtractor()
            val afd = assets.openFd(fileName)
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()

            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("video/")) {
                    trackIndex = i
                    break
                }
            }
            if (trackIndex < 0) {
                Log.e(TAG, "未找到视频轨道")
                onComplete()
                return
            }

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            Log.i(TAG, "视频: ${width}x${height}, mime=$mime")

            codec = MediaCodec.createDecoderByType(mime)
            val outputFormat = MediaFormat.createVideoFormat(mime, width, height)
            outputFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            codec.configure(format, null, null, 0)
            codec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            val frameIntervalMs = 80L

            while (running.get()) {
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize,
                                extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)
                if (outputIndex >= 0) {
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        codec.releaseOutputBuffer(outputIndex, false)
                        break
                    }
                    val image = codec.getOutputImage(outputIndex)
                    if (image != null) {
                        val jpeg = imageToJpeg(image, width, height)
                        if (jpeg != null && running.get()) {
                            onFrame(jpeg)
                        }
                        image.close()
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    Thread.sleep(frameIntervalMs)
                }
            }
        } catch (e: InterruptedException) {
            Log.i(TAG, "演示播放被中断")
        } catch (e: Exception) {
            Log.e(TAG, "演示视频解码失败: ${e.message}", e)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor?.release() } catch (_: Exception) {}
            running.set(false)
            onComplete()
        }
    }

    private fun imageToJpeg(image: Image, width: Int, height: Int): ByteArray? {
        return try {
            val yuvBytes = yuv420ToNv21(image)
            val yuvImage = android.graphics.YuvImage(yuvBytes, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()
            // 缩放到最大960px宽度
            val scale = if (width > 960) 960.0 / width else 1.0
            val scaledW = (width * scale).toInt()
            val scaledH = (height * scale).toInt()
            if (scale < 1.0) {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val rect = android.graphics.Rect(0, 0, width, height)
                yuvImage.compressToJpeg(rect, 85, out)
                val fullJpeg = out.toByteArray()
                val fullBitmap = android.graphics.BitmapFactory.decodeByteArray(fullJpeg, 0, fullJpeg.size)
                val scaled = Bitmap.createScaledBitmap(fullBitmap, scaledW, scaledH, true)
                fullBitmap.recycle()
                val scaledOut = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, scaledOut)
                scaled.recycle()
                scaledOut.toByteArray()
            } else {
                yuvImage.compressToJpeg(android.graphics.Rect(0, 0, width, height), 75, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.e(TAG, "YUV转JPEG失败: ${e.message}")
            null
        }
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = width * height
        val nv21 = ByteArray(ySize * 3 / 2)

        // 复制 Y 平面
        val yBuffer = yPlane.buffer
        yBuffer.rewind()
        val yRowStride = yPlane.rowStride
        if (yRowStride == width) {
            yBuffer.get(nv21, 0, ySize)
        } else {
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                yBuffer.get(nv21, row * width, width)
            }
        }

        // NV21: Y 之后是 VU 交错排列
        val vBuffer = vPlane.buffer
        val uBuffer = uPlane.buffer
        vBuffer.rewind()
        uBuffer.rewind()
        val vPixelStride = vPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val uRowStride = uPlane.rowStride

        var pos = ySize
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                nv21[pos++] = vBuffer.get(row * vRowStride + col * vPixelStride)
                nv21[pos++] = uBuffer.get(row * uRowStride + col * uPixelStride)
            }
        }

        return nv21
    }
}
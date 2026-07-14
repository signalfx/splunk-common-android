/*
Copyright 2026 Splunk Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.splunk.rum.common.encoder

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
import android.media.MediaFormat
import android.media.MediaMuxer
import android.view.Surface
import com.splunk.rum.common.encoder.Codec.findAvcEncoder
import com.splunk.rum.common.encoder.extensions.rotated
import com.splunk.rum.common.encoder.internal.BitmapLoader
import com.splunk.rum.common.encoder.model.VideoFrame
import com.splunk.rum.common.logger.Logger
import java.io.File

@SuppressLint("NewApi")
class Encoder {

    private companion object {
        const val TAG = "Encoder"
        const val TIMEOUT_USEC = 10_000L
        const val UNDEFINED_VIDEO_TRACK_INDEX = -1
        const val KEY_FRAME_INTERVAL = 1
        const val FILLER_FRAME_LENGTH = 5L

        @JvmStatic
        var bitrateOverride: Int? = null // Because of internal API
    }

    fun start(width: Int, height: Int, frames: List<VideoFrame>, outputFile: File, bitrate: Int, frameRate: Int): Result<Unit> {
        return startEncoding(
            width = width,
            height = height,
            frames = frames.normalize(),
            outputFile = outputFile,
            bitrate = bitrate,
            frameRate = frameRate
        )
    }

    private fun startEncoding(width: Int, height: Int, frames: List<VideoFrame>, outputFile: File, bitrate: Int, frameRate: Int): Result<Unit> {
        Logger.d(TAG, "startEncoding")

        val codecName = findAvcEncoder(MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)?.name
            ?: return Result.failure(IllegalArgumentException("Unable to find an appropriate codec"))
        var muxer: MediaMuxer? = null
        var mediaCodec: MediaCodec? = null
        var surface: Surface? = null

        return try {
            val mediaFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrateOverride ?: bitrate)
            mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, COLOR_FormatSurface)
            mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, KEY_FRAME_INTERVAL)

            mediaCodec = MediaCodec.createByCodecName(codecName)
            mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            // TODO come up with decryption solution then encrypt the writing part
            muxer = MediaMuxer(outputFile.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            surface = mediaCodec.createInputSurface()
            mediaCodec.start()

            val bufferInfo = MediaCodec.BufferInfo()
            var videoTrackIndex = UNDEFINED_VIDEO_TRACK_INDEX
            var frameIndex = 0
            var currentPositionInVideo = 0L
            var outputDone = false
            var firstFrame = true

            Logger.d(TAG, "startEncoding: frameCount: ${frames.size}")

            // else branch of dequeueOutputBuffer gets called drawFrame times + 1
            // We have to draw at least one frame before we can call dequeueOutputBuffer.
            surface.drawFrame(frames.first(), width, height)

            while (!outputDone) {
                when (val outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Logger.d(TAG, "startEncoding outputBuffer: INFO_OUTPUT_FORMAT_CHANGED")
                        val newFormat = mediaCodec.outputFormat
                        videoTrackIndex = muxer.addTrack(newFormat)
                        muxer.start()
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (frameIndex + 1 >= frames.size) {
                            outputDone = true
                        }
                        Logger.d(TAG, "startEncoding outputBuffer: INFO_TRY_AGAIN_LATER")
                    }
                    else -> {
                        val encodedData = mediaCodec.getOutputBuffer(outputBufferIndex) ?: continue
                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                            Logger.d(TAG, "startEncoding Ignoring BUFFER_FLAG_CODEC_CONFIG")
                            bufferInfo.size = 0
                        }

                        if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0) {
                            Logger.d(TAG, "startEncoding BUFFER_FLAG_KEY_FRAME")
                        }

                        if (bufferInfo.size != 0) {
                            if (firstFrame) {
                                currentPositionInVideo = 0
                                firstFrame = false
                            } else {
                                currentPositionInVideo += frames[frameIndex].duration * 1000
                                frameIndex++
                            }

                            // Draw one frame in advance in order for OutputBuffer to be able to process frames.
                            if (frameIndex + 1 < frames.size) {
                                surface.drawFrame(frames[frameIndex + 1], width, height)
                            }

                            bufferInfo.presentationTimeUs = currentPositionInVideo
                            Logger.d(TAG, "startEncoding rendering frame: $frameIndex, presentationTimeUs ${bufferInfo.presentationTimeUs}")
                            muxer.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                        }

                        mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
            }
            Logger.d(TAG, "startEncoding was successful")
            Result.success(Unit)
        } catch (exception: Exception) {
            Logger.d(TAG, "startEncoding failed with $exception")
            Result.failure(exception)
        } finally {
            mediaCodec?.stopAndRelease()
            muxer?.stopAndRelease()
            surface?.release()
        }
    }

    private fun Surface.drawFrame(frame: VideoFrame, width: Int, height: Int) {
        val bitmap = loadBitmap(frame.filePath, frame.orientation, width, height)

        val canvas = lockCanvas(Rect(0, 0, width, height))
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        unlockCanvasAndPost(canvas)

        bitmap.recycle()
    }

    private fun MediaMuxer.stopAndRelease() {
        runCatching {
            stop()
            release()
        }
    }

    private fun MediaCodec.stopAndRelease() {
        runCatching {
            stop()
            release()
        }
    }

    private fun loadBitmap(filePath: String, orientation: VideoFrame.Orientation?, width: Int, height: Int): Bitmap {
        val bitmap = when (orientation) {
            VideoFrame.Orientation.LANDSCAPE -> BitmapLoader.loadBitmap(filePath, height, width)
            else -> BitmapLoader.loadBitmap(filePath, width, height)
        }

        return bitmap.rotated(orientation?.angle ?: 0)
    }

    private fun List<VideoFrame>.normalize(): List<VideoFrame> {
        val newFrames = toMutableList()
        newFrames.first().let { newFrames.add(0, it.copy(duration = FILLER_FRAME_LENGTH)) }
        newFrames.last().let { newFrames.add(it.copy(duration = FILLER_FRAME_LENGTH)) }
        newFrames.last().let { newFrames.add(it.copy(duration = FILLER_FRAME_LENGTH)) }
        return newFrames
    }
}

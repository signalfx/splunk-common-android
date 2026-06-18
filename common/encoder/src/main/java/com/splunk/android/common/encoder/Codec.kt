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

package com.splunk.android.common.encoder

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.splunk.android.common.logger.Logger
import java.util.LinkedList

object Codec {
    private const val TAG = "Codec"

    private val prefEnc: List<String> = emptyList()
    private val blackEnc: List<String>
    private val whiteHevcEnc: List<String>

    init {
        blackEnc = LinkedList()

        if (Build.HARDWARE != "ranchu" || Build.BRAND != "google") {
            blackEnc.add("omx.google")
            blackEnc.add("AVCEncoder")
        }

        blackEnc.add("OMX.ffmpeg")

        blackEnc.add("OMX.qcom.video.encoder.hevcswvdec")
        blackEnc.add("OMX.SEC.hevc.sw.dec")

        whiteHevcEnc = LinkedList()
        if (Build.HARDWARE == "ranchu" && Build.BRAND == "google") {
            whiteHevcEnc.add("omx.google")
        }

        whiteHevcEnc.add("omx.exynos")
        whiteHevcEnc.add("OMX.qcom")

        if (Build.DEVICE.equals("darcy", ignoreCase = true)) {
            whiteHevcEnc.add("omx.nvidia")
        }

        if (Build.MANUFACTURER.equals("Amazon", ignoreCase = true)) {
            whiteHevcEnc.add("omx.mtk")
            whiteHevcEnc.add("omx.amlogic")
        }
    }

    fun findAvcEncoder(profileType: Int): MediaCodecInfo? {
        Logger.v(TAG, "Starting findAvcEncoder()")

        var encoder = findProbableSafeEncoder(MediaFormat.MIMETYPE_VIDEO_AVC, profileType)

        if (encoder == null) {
            Logger.v(TAG, "Find avc encoder: encoder null -> find first")

            encoder = findFirstEncoder(MediaFormat.MIMETYPE_VIDEO_AVC)
        }

        if (encoder == null) {
            Logger.v(TAG, "Find avc encoder: encoder null -> did not find anything")
        } else {
            Logger.v(TAG, "Find avc encoder returning: encoderName = ${encoder.name}, encoderToString = $encoder")
        }
        return encoder
    }

    private fun findProbableSafeEncoder(mimeType: String, requiredProfile: Int): MediaCodecInfo? {
        val info = findPreferredEncoder()
        return info ?: try {
            findKnownSafeEncoder(mimeType, requiredProfile)
        } catch (e: Exception) {
            findFirstEncoder(mimeType)
        }
    }

    private fun findPreferredEncoder(): MediaCodecInfo? {
        prefEnc.forEach { preferredEncoder ->
            getMediaCodecList().forEach { codecInfo ->
                if (!codecInfo.isEncoder) {
                    return@forEach
                }

                if (preferredEncoder.equals(codecInfo.name, ignoreCase = true)) {
                    Logger.v(TAG, "Preferred encoder choice is: codecName = ${codecInfo.name}")
                    return codecInfo
                }
            }
        }
        return null
    }

    private fun findKnownSafeEncoder(mimeType: String, requiredProfile: Int): MediaCodecInfo? {
        for (codecInfo in getMediaCodecList()) {
            if (!codecInfo.isEncoder) {
                continue
            }
            if (isEncoderInList(blackEnc, codecInfo.name)) {
                Logger.v(TAG, "Skipping blacklisted encoder: codecName = ${codecInfo.name}")
                continue
            }
            for (mime in codecInfo.supportedTypes) {
                if (mime.equals(mimeType, ignoreCase = true)) {
                    Logger.v(TAG, "Examining encoder capabilities: codecName = ${codecInfo.name}")

                    val caps = codecInfo.getCapabilitiesForType(mime)
                    if (requiredProfile != -1) {
                        for (profile in caps.profileLevels) {
                            if (profile.profile == requiredProfile) {
                                Logger.v(TAG, "Encoder  supports required profile")
                                return codecInfo
                            }
                        }
                        Logger.v(TAG, "Encoder ${codecInfo.name} does NOT support required profile")
                    } else {
                        return codecInfo
                    }
                }
            }
        }
        return null
    }

    private fun findFirstEncoder(mimeType: String): MediaCodecInfo? {
        getMediaCodecList().forEach { codecInfo ->
            if (!codecInfo.isEncoder) {
                return@forEach
            }

            if (isEncoderInList(blackEnc, codecInfo.name)) {
                Logger.v(TAG, "Skipping blacklisted encoder: codecName = ${codecInfo.name}")
                return@forEach
            }

            codecInfo.supportedTypes.forEach { mime ->
                if (mime.equals(mimeType, ignoreCase = true)) {
                    Logger.v(TAG, "First encoder choice: codecName = ${codecInfo.name}")
                    return codecInfo
                }
            }
        }
        return null
    }

    private fun getMediaCodecList(): LinkedList<MediaCodecInfo> {
        val infoList = LinkedList<MediaCodecInfo>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            infoList.addAll(mediaCodecList.codecInfos)
        } else {
            for (i in 0 until MediaCodecList.getCodecCount()) {
                infoList.add(MediaCodecList.getCodecInfoAt(i))
            }
        }
        return infoList
    }

    private fun isEncoderInList(encoderList: List<String>, encoderName: String): Boolean {
        encoderList.forEach { badPrefix ->
            if (encoderName.length >= badPrefix.length) {
                val prefix = encoderName.substring(0, badPrefix.length)
                if (prefix.equals(badPrefix, ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }
}

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

package com.splunk.android.common.storage.filemanager

import android.os.Build
import android.util.Log
import com.splunk.android.common.storage.encryption.KeyManager
import java.security.UnrecoverableKeyException

object FileManagerFactory {

    fun createEncryptedFileManagerIfPossible(keyAlias: String): IFileManager {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return PlainFileManager()
        }

        val secretKey = try {
            KeyManager.instance.getKey(keyAlias)
        } catch (e: UnrecoverableKeyException) {
            Log.e(TAG, "createEncryptedFileManagerIfPossible(keyAlias: $keyAlias)", e)

            null
        } catch (e: Exception) {
            Log.e(TAG, "createEncryptedFileManagerIfPossible(keyAlias: $keyAlias)", e)

            null
        }

        return if (secretKey == null) PlainFileManager() else EncryptedFileManager(secretKey)
    }

    fun createConditionedEncryptedFileManager(keyAlias: String, isEncrypted: Boolean): IFileManager {
        return if (isEncrypted) createEncryptedFileManagerIfPossible(keyAlias) else PlainFileManager()
    }

    fun createPlainFileManager(): IFileManager {
        return PlainFileManager()
    }

    private const val TAG = "FileManagerFactory"
}

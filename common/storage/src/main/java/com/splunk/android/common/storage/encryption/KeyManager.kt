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

package com.splunk.android.common.storage.encryption

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.splunk.android.common.logger.Logger
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@Suppress("JoinDeclarationAndAssignment")
class KeyManager private constructor() {

    private val keyStore: KeyStore?

    init {
        keyStore = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            KeyStore.getInstance(KEY_STORE_PROVIDER).apply { load(null) }
        else
            null
    }

    private fun KeyStore.tryContainsAlias(keyAlias: String): Boolean {
        return try {
            return containsAlias(keyAlias)
        } catch (e: Exception) {
            Logger.w(TAG, "tryContainsAlias(alias=$keyAlias) threw ${e::class.java.simpleName}: ${e.message}")
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateNewGcmKeyInternal(keyAlias: String): SecretKey {
        val keyGenBuilder = KeyGenParameterSpec.Builder(
            keyAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEY_STORE_PROVIDER)
        keyGenerator.init(keyGenBuilder.build())

        return keyGenerator.generateKey()
    }

    fun getKey(keyAlias: String): SecretKey? {
        // Early exit for API levels below 23 (Marshmallow) where Keystore GCM features are not guaranteed.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || keyStore == null) {
            Logger.w(TAG, "getKey(): Encryption ignored on API level ${Build.VERSION.SDK_INT} (< 23).")
            return null
        }

        try {
            if (keyStore.tryContainsAlias(keyAlias)) {
                val entry = keyStore.getEntry(keyAlias, null)
                if (entry is KeyStore.SecretKeyEntry) {
                    return entry.secretKey
                } else {
                    // Entry exists but is not a SecretKeyEntry (e.g., certificate), delete it.
                    keyStore.deleteEntry(keyAlias)
                    Logger.w(TAG, "getKey(): Deleted non-SecretKeyEntry for alias: $keyAlias!")
                }
            }
        } catch (e: UnrecoverableKeyException) {
            Logger.w(TAG, "getKey(): UnrecoverableKeyException for $keyAlias! Due to ${e.message}!", e)
        } catch (e: Exception) {
            Logger.w(TAG, "getKey(): Error retrieving key for alias: $keyAlias! Due to ${e.message}!", e)
        }

        // If no key was retrieved, generate a new one
        try {
            return generateNewGcmKeyInternal(keyAlias)
        } catch (e: Exception) {
            Logger.w(TAG, "getKey(): Failed to generate new key for alias: $keyAlias! Due to ${e.message}!", e)
            return null
        }
    }

    fun deleteKey(keyAlias: String): Boolean {
        if (keyStore == null) {
            return false
        }

        if (keyStore.containsAlias(keyAlias)) keyStore.deleteEntry(keyAlias)

        return true
    }

    companion object {

        private const val KEY_STORE_PROVIDER = "AndroidKeyStore"
        private const val TAG = "KeyManager"

        val instance: KeyManager by lazy { KeyManager() }
    }
}

package com.example.diffviewer.core.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecretCipher : SecretCipher {
    override fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateSecretKey())
        val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(cipher.iv, encryptedBytes)
            .joinToString(SEPARATOR) { bytes -> Base64.encodeToString(bytes, Base64.NO_WRAP) }
    }

    override fun decrypt(encryptedText: String): String {
        val encryptedPartItems = encryptedText.split(SEPARATOR)
        require(encryptedPartItems.size == ENCRYPTED_PART_COUNT)
        val initializationVector = Base64.decode(encryptedPartItems[0], Base64.NO_WRAP)
        val encryptedBytes = Base64.decode(encryptedPartItems[1], Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            loadOrCreateSecretKey(),
            GCMParameterSpec(AUTHENTICATION_TAG_LENGTH_BITS, initializationVector),
        )
        return cipher.doFinal(encryptedBytes).toString(Charsets.UTF_8)
    }

    private fun loadOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val existingSecretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existingSecretKey != null) return existingSecretKey

        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "diff_viewer_github_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val SEPARATOR = ":"
        const val ENCRYPTED_PART_COUNT = 2
        const val AUTHENTICATION_TAG_LENGTH_BITS = 128
    }
}

package com.example.diffviewer.core.data

interface SecretCipher {
    fun encrypt(plainText: String): String
    fun decrypt(encryptedText: String): String
}

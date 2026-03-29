package com.example.messengerapp.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec

class CryptoManager(private val context: Context) {

    companion object {
        private const val KEY_ALIAS = "messenger_rsa_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val GCM_TAG_LENGTH = 128
        private const val GCM_IV_LENGTH = 12
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    // Генерация RSA пары ключей
    fun generateKeyPair(): String {
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyPairGenerator = java.security.KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()

            keyPairGenerator.initialize(spec)
            keyPairGenerator.generateKeyPair()
        }

        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
        return Base64.encodeToString(publicKey?.encoded, Base64.NO_WRAP)
    }

    fun getPublicKey(): String {
        val publicKey = keyStore.getCertificate(KEY_ALIAS)?.publicKey
        return Base64.encodeToString(publicKey?.encoded, Base64.NO_WRAP)
    }

    // Гибридное шифрование для получателя
    fun encryptForRecipient(plaintext: String, recipientPublicKeyBase64: String): String {
        val aesKey = generateAesKey()
        val (iv, ciphertext) = encryptWithAes(plaintext, aesKey)
        val recipientPublicKey = decodePublicKey(recipientPublicKeyBase64)
        val encryptedAesKey = encryptWithRsa(aesKey.encoded, recipientPublicKey)

        return "${Base64.encodeToString(encryptedAesKey, Base64.NO_WRAP)}:" +
                "${Base64.encodeToString(iv, Base64.NO_WRAP)}:" +
                "${Base64.encodeToString(ciphertext, Base64.NO_WRAP)}"
    }

    // Расшифровка своим ключом
    fun decryptWithMyKey(encryptedPackage: String): String {
        println("🔓 НАЧАЛО РАСШИФРОВКИ")
        println("🔓 Пакет: ${encryptedPackage.take(100)}...")

        val parts = encryptedPackage.split(":")
        println("🔓 Количество частей: ${parts.size}")

        if (parts.size != 3) {
            println("⚠️ Неверный формат, возвращаем как есть")
            return encryptedPackage
        }

        try {
            val encryptedAesKey = Base64.decode(parts[0], Base64.NO_WRAP)
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)

            println("🔓 Размер зашифрованного AES-ключа: ${encryptedAesKey.size}")
            println("🔓 Размер IV: ${iv.size}")
            println("🔓 Размер шифротекста: ${ciphertext.size}")

            val aesKeyBytes = decryptWithRsa(encryptedAesKey)
            println("🔓 AES-ключ расшифрован, размер: ${aesKeyBytes.size}")

            val aesKey = SecretKeySpec(aesKeyBytes, "AES")
            val decrypted = decryptWithAes(ciphertext, iv, aesKey)
            println("🔓 СООБЩЕНИЕ РАСШИФРОВАНО: $decrypted")

            return decrypted
        } catch (e: Exception) {
            println("❌ Ошибка расшифровки: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    // ========== Вспомогательные методы ==========

    private fun generateAesKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }

    private fun encryptWithAes(plaintext: String, key: SecretKey): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray())
        return Pair(iv, ciphertext)
    }

    private fun decryptWithAes(ciphertext: ByteArray, iv: ByteArray, key: SecretKey): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)
        val plaintext = cipher.doFinal(ciphertext)
        return String(plaintext)
    }

    private fun encryptWithRsa(data: ByteArray, publicKey: java.security.PublicKey): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey)
        return cipher.doFinal(data)
    }

    private fun decryptWithRsa(encryptedData: ByteArray): ByteArray {
        val privateKey = keyStore.getKey(KEY_ALIAS, null) as java.security.PrivateKey
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey)
        return cipher.doFinal(encryptedData)
    }

    private fun decodePublicKey(base64Key: String): java.security.PublicKey {
        val keyBytes = Base64.decode(base64Key, Base64.NO_WRAP)
        val keySpec = X509EncodedKeySpec(keyBytes)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(keySpec)
    }

    fun deleteOldKey() {
        try {
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
                println("🗑️ Старый ключ удален")
            }
        } catch (e: Exception) {
            println("❌ Ошибка удаления ключа: ${e.message}")
        }
    }
}
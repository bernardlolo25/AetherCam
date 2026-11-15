package com.aethercam.app.security

import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptionEngine(keyBytes: ByteArray) {

    private val secretKey: SecretKey = SecretKeySpec(keyBytes, "AES")

    /**
     * Encrypts plaintext buffer using AES-256-GCM with deterministic nonce.
     */
    fun encrypt(plaintext: ByteArray, sequenceNumber: Short): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = createNonce(sequenceNumber)
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        return cipher.doFinal(plaintext)
    }

    /**
     * Decrypts ciphertext buffer using AES-256-GCM.
     */
    fun decrypt(ciphertextWithTag: ByteArray, sequenceNumber: Short): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val nonce = createNonce(sequenceNumber)
        val spec = GCMParameterSpec(128, nonce)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertextWithTag)
    }

    private fun createNonce(sequenceNumber: Short): ByteArray {
        val nonce = ByteArray(12)
        val bb = ByteBuffer.wrap(nonce)
        bb.position(10)
        bb.putShort(sequenceNumber)
        return nonce
    }
}

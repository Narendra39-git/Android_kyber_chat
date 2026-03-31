package com.example.kyberchat.crypto

import org.bouncycastle.crypto.AsymmetricCipherKeyPair
import org.bouncycastle.crypto.SecretWithEncapsulation
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAKeyPairGenerator
import org.bouncycastle.pqc.crypto.mldsa.MLDSAParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSAPublicKeyParameters
import org.bouncycastle.pqc.crypto.mldsa.MLDSASigner
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest

object CryptoManager {

    private val secureRandom = SecureRandom()

    // --- ML-KEM (KEM) ---
    private var kyberKeyPair: AsymmetricCipherKeyPair? = null

    fun generateKyberKeys(): String {
        kyberPrivateKey = null // CRITICAL: clear stale loaded key so fresh keyPair is used
        val generator = MLKEMKeyPairGenerator()
        // Use Kyber-768 (Level 3) to match reference app
        generator.init(MLKEMKeyGenerationParameters(secureRandom, MLKEMParameters.ml_kem_768))
        kyberKeyPair = generator.generateKeyPair()
        
        val pubKey = kyberKeyPair!!.public as MLKEMPublicKeyParameters
        return Base64.getEncoder().encodeToString(pubKey.encoded)
    }

    fun decapsulate(ciphertextB64: String): ByteArray {
        val privateKey = kyberPrivateKey ?: (kyberKeyPair?.private as? MLKEMPrivateKeyParameters) 
            ?: throw IllegalStateException("Kyber keys not generated or loaded")
        
        val ciphertext = Base64.getDecoder().decode(ciphertextB64)
        val extractor = MLKEMExtractor(privateKey)
        val sharedSecret = extractor.extractSecret(ciphertext)
        return sharedSecret
    }

    fun encapsulate(recipientKyberPkB64: String): Pair<String, ByteArray> {
        val pubKeyBytes = Base64.getDecoder().decode(recipientKyberPkB64)
        val pubKey = MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, pubKeyBytes)
        
        val generator = MLKEMGenerator(secureRandom)
        val encapsulation = generator.generateEncapsulated(pubKey)
        
        val ciphertextB64 = Base64.getEncoder().encodeToString(encapsulation.encapsulation)
        return Pair(ciphertextB64, encapsulation.secret)
    }

    // --- MLDSA (Signatures) ---
    private var mldsaKeyPair: AsymmetricCipherKeyPair? = null

    fun generateDilithiumKeys(): String {
        dilithiumPrivateKey = null // CRITICAL: clear stale loaded key so fresh keyPair is used
        val generator = MLDSAKeyPairGenerator()
        // Use ML-DSA-65 (FIPS 204 Level 3)
        generator.init(MLDSAKeyGenerationParameters(secureRandom, MLDSAParameters.ml_dsa_65))
        mldsaKeyPair = generator.generateKeyPair()
        
        val pubKey = mldsaKeyPair!!.public as MLDSAPublicKeyParameters
        return Base64.getEncoder().encodeToString(pubKey.encoded)
    }

    fun sign(message: ByteArray): String {
        val privateKey = dilithiumPrivateKey ?: (mldsaKeyPair?.private as? MLDSAPrivateKeyParameters)
            ?: throw IllegalStateException("MLDSA keys not generated or loaded")
        
        val signer = MLDSASigner()
        signer.init(true, privateKey)
        signer.update(message, 0, message.size)
        
        val signature = signer.generateSignature()
        return Base64.getEncoder().encodeToString(signature)
    }

    fun verify(message: ByteArray, signatureB64: String): Boolean {
        if (mldsaKeyPair == null) return false
        val signer = MLDSASigner()
        signer.init(false, mldsaKeyPair!!.public)
        signer.update(message, 0, message.size)
        val signature = Base64.getDecoder().decode(signatureB64)
        return signer.verifySignature(signature)
    }

    // --- Key Export/Import for Persistence ---
    fun getKyberPrivateKeyB64(): String? {
        val privKey = kyberKeyPair?.private as? MLKEMPrivateKeyParameters ?: return null
        return Base64.getEncoder().encodeToString(privKey.encoded)
    }

    fun getDilithiumPrivateKeyB64(): String? {
        val privKey = mldsaKeyPair?.private as? MLDSAPrivateKeyParameters ?: return null
        return Base64.getEncoder().encodeToString(privKey.encoded)
    }

    fun loadKeys(kyberPrivB64: String, dilithiumPrivB64: String) {
        // Load Kyber
        val kyberPrivBytes = Base64.getDecoder().decode(kyberPrivB64)
        val kyberPrivParams = MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_768, kyberPrivBytes)
        // We technically need the Public Key too for the KeyPair object, but for Decapsulation (the main use), we only need Private.
        // BouncyCastle API might be tricky to reconstruct the full KeyPair without Public.
        // However, MLKEMExtractor only needs PrivateKey.
        // Let's just store the keys.
        // Wait, for generateKyberKeys, we set `kyberKeyPair`.
        // If we restore, we should try to restore `kyberKeyPair` or just separate variables.
        // Ideally we should store both, OR derive public from private if possible (not always possible in PQC).
        
        // For simplicity: We will just store the private key parameters separate from the KeyPair object if needed,
        // or just reconstruct what we can. 
        // NOTE: AsymmetricCipherKeyPair holds AsymmetricKeyParameter public, AsymmetricKeyParameter private.
        
        // Let's assume we can't easily derive public from private in this BC version without re-generating.
        // BUT, we only need Private Key to Decapsulate. 
        // Use a separate variable or hack the KeyPair.
        
        // Hack: We will just set the private key for usage in decapsulation.
        // Re-creating the full pair is hard without storing public too.
        // Let's assume we store public key in session too?
        // Actually, we don't need the public key for ongoing operations (we give it to server once).
        // EXCEPT if we want to display it or re-register.
        
        // Let's create a partial pair or just modify decapsulate to use a stored private key.
        // Better: Update `decapsulate` to use `kyberPrivateKey` instead of `kyberKeyPair`.
        
        this.kyberPrivateKey = kyberPrivParams
        
        // Load Dilithium
        val dilithiumPrivBytes = Base64.getDecoder().decode(dilithiumPrivB64)
        val mldsaPrivParams = MLDSAPrivateKeyParameters(MLDSAParameters.ml_dsa_65, dilithiumPrivBytes)
        this.dilithiumPrivateKey = mldsaPrivParams
    }

    private var kyberPrivateKey: MLKEMPrivateKeyParameters? = null
    private var dilithiumPrivateKey: MLDSAPrivateKeyParameters? = null

    // --- AES-GCM ---
    private const val AES_KEY_SIZE = 32 // 256 bits
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    fun deriveAesKey(sharedSecret: ByteArray): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256")
        val keyBytes = digest.digest(sharedSecret)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(message: String, key: SecretKeySpec): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        
        cipher.init(Cipher.ENCRYPT_MODE, key, spec)
        val ciphertext = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
        
        return Pair(
            Base64.getEncoder().encodeToString(ciphertext),
            Base64.getEncoder().encodeToString(iv)
        )
    }

    fun decrypt(ciphertextB64: String, ivB64: String, key: SecretKeySpec): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.getDecoder().decode(ivB64)
        val ciphertext = Base64.getDecoder().decode(ciphertextB64)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        val plaintext = cipher.doFinal(ciphertext)
        
        return String(plaintext, Charsets.UTF_8)
    }
}

package com.example.kyberchat.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SessionManager(context: Context) {

    private val sharedPreferences: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secret_shared_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback to regular SharedPreferences if Keystore is corrupted
        // (common after reinstall on physical devices)
        context.getSharedPreferences("secret_shared_prefs_fallback", Context.MODE_PRIVATE)
    }

    companion object {
        private const val KEY_CLIENT_ID = "client_id"
        private const val KEY_KYBER_PK = "kyber_pk" // We might not need to store PK if we regenerate, but better to keep consistency if server expects same PK. 
        // Actually, for Kyber, we need the PRIVATE KEY to decapsulate. The server has our Public Key.
        // So we MUST store the generated Private Key locally.
        
        // Wait, CryptoManager currently keeps keys in memory. 
        // We need to extend CryptoManager to support exporting/importing keys or just store the seed?
        // Bouncy Castle PQC keys are objects. We should probably serialize them or better yet, store the SEED if possible, 
        // or just the encoded private key bytes. 
        
        // For simplicity in this logical step, let's just assume we need to store the ClientID to know who we are.
        // Recovery of keys is a critical crypto step. 
        // Let's store the encoded keys (Private Keys) as Base64 strings.
        
        private const val KEY_KYBER_PRIV = "kyber_priv"
        private const val KEY_DILITHIUM_PRIV = "dilithium_priv"
    }

    fun saveSession(clientId: String, kyberPrivB64: String, dilithiumPrivB64: String) {
        sharedPreferences.edit()
            .putString(KEY_CLIENT_ID, clientId)
            .putString(KEY_KYBER_PRIV, kyberPrivB64)
            .putString(KEY_DILITHIUM_PRIV, dilithiumPrivB64)
            .apply()
    }

    fun getClientId(): String? {
        return sharedPreferences.getString(KEY_CLIENT_ID, null)
    }

    fun getKyberPrivKey(): String? {
        return sharedPreferences.getString(KEY_KYBER_PRIV, null)
    }

    fun getDilithiumPrivKey(): String? {
        return sharedPreferences.getString(KEY_DILITHIUM_PRIV, null)
    }
    
    fun clearSession() {
        sharedPreferences.edit().clear().apply()
    }
}

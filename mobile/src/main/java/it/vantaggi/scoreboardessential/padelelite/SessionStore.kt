package it.vantaggi.scoreboardessential.padelelite

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Cifra e decifra i byte della sessione. Un'interfaccia perche' il Keystore di Android non esiste
 * sulla JVM dei test: la produzione usa [KeystoreSecretBox], i test una chiave in memoria.
 */
interface SecretBox {
    fun seal(plain: ByteArray): ByteArray

    /** Null se i byte non si decifrano (chiave persa o dato alterato): vale come "nessuna sessione". */
    fun open(sealed: ByteArray): ByteArray?
}

/**
 * AES-256-GCM con una chiave dell'Android Keystore che non lascia mai il telefono (non
 * esportabile, non finisce nei backup). Il risultato e' `IV (12 byte) + testo cifrato`.
 *
 * Si sceglie il Keystore diretto e non `EncryptedSharedPreferences`: la libreria
 * `androidx.security:security-crypto` e' deprecata e porta Tink; qui sono quaranta righe di
 * piattaforma. Non si prova su JVM: la prova e' sul telefono (passo A-7).
 */
class KeystoreSecretBox(
    private val alias: String = "padel_elite_session",
) : SecretBox {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec
                .Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        return generator.generateKey()
    }

    override fun seal(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun open(sealed: ByteArray): ByteArray? =
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
            cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
        }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
        const val IV_BYTES = 12
    }
}

/**
 * Dove vive la sessione di Padel Elite e il gruppo scelto: un file di preferenze privato
 * (`padel_elite_session`, escluso dai backup) in cui la sessione e' cifrata con [SecretBox].
 *
 * Il gruppo non e' un segreto, ma dipende dall'utente: esce con la sessione.
 */
class SessionStore(
    private val prefs: SharedPreferences,
    private val box: SecretBox,
) {
    fun load(): PadelEliteSession? {
        val cifrata = prefs.getString(KEY_SESSION, null) ?: return null
        val chiaro =
            runCatching { box.open(Base64.decode(cifrata, Base64.NO_WRAP)) }.getOrNull()
                ?: return null.also { clear() }
        return runCatching {
            val json = JSONObject(chiaro.toString(Charsets.UTF_8))
            PadelEliteSession(
                json.getString("a"),
                json.getString("r"),
                json.getLong("e"),
                json.getString("u"),
                json.optString("m"),
            )
        }.getOrElse { null.also { clear() } }
    }

    fun save(session: PadelEliteSession) {
        val json =
            JSONObject()
                .put("a", session.accessToken)
                .put("r", session.refreshToken)
                .put("e", session.expiresAtSec)
                .put("u", session.userId)
                .put("m", session.email)
        val cifrata = box.seal(json.toString().toByteArray(Charsets.UTF_8))
        prefs.edit { putString(KEY_SESSION, Base64.encodeToString(cifrata, Base64.NO_WRAP)) }
    }

    /** Il gruppo scelto per l'invio: id e nome, o null se non se n'e' scelto uno. */
    fun selectedGroup(): Pair<String, String>? {
        val id = prefs.getString(KEY_GROUP_ID, null) ?: return null
        return id to prefs.getString(KEY_GROUP_NAME, "").orEmpty()
    }

    fun selectGroup(group: PadelEliteGroup) {
        prefs.edit {
            putString(KEY_GROUP_ID, group.id)
            putString(KEY_GROUP_NAME, group.name)
        }
    }

    fun clearGroup() {
        prefs.edit {
            remove(KEY_GROUP_ID)
            remove(KEY_GROUP_NAME)
        }
    }

    /** Esci: via sessione e gruppo. */
    fun clear() {
        prefs.edit {
            remove(KEY_SESSION)
            remove(KEY_GROUP_ID)
            remove(KEY_GROUP_NAME)
        }
    }

    companion object {
        const val FILE = "padel_elite_session"
        private const val KEY_SESSION = "session"
        private const val KEY_GROUP_ID = "group_id"
        private const val KEY_GROUP_NAME = "group_name"

        fun create(context: Context) = SessionStore(context.getSharedPreferences(FILE, Context.MODE_PRIVATE), KeystoreSecretBox())
    }
}

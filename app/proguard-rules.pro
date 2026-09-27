# ===================================================================
# REGOLAMENTAZIONE PROGUARD / R8 - SecureNotes
# Package: com.cookie.securenotes
# ===================================================================

# 1. Mantieni i Modelli di Dati / Entità
# Impedisce la rinomina delle classi e dei campi usati nei database o serializzazione JSON
-keep class com.cookie.securenotes.data.local.db.** { *; }


# 2. Mantieni l'Application Entry Point e le componenti principali
-keep class com.cookie.securenotes.SecureNotesApplication { *; }

# 3. SQLCipher (Essenziale per la cifratura nativa del DB)
# Evita che il codice C/C++ perda i riferimenti alle classi Java di SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }
-dontwarn net.sqlcipher.**

# 4. AndroidX, Biometric API e Security Crypto
-keep class androidx.** { *; }
-keep class androidx.biometric.** { *; }
-keep class androidx.security.crypto.** { *; }

# 5. Room Database
-keepclassmembers class * {
    @androidx.room.* <methods>;
    @androidx.room.* <fields>;
}
-keep class * extends androidx.room.RoomDatabase

# 6. Mantenimento delle annotazioni e firma per la Reflection
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# 7. Preserva annotazioni di serializzazione (es. GSON/Kotlinx Serialization se usate)
-keepnames class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
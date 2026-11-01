# Aturan R8 untuk DuitKu.
# Room, Hilt, dan kotlinx.serialization membawa consumer rules sendiri.

# Model snapshot/backup dibaca lewat kotlinx.serialization: pertahankan metadata anotasi.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class com.duitku.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.duitku.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ZXing dipakai via refleksi minimal; tidak perlu aturan tambahan.

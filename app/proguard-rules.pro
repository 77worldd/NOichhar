-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable

-keep class com.noichhar.app.database.entity.** { *; }
-keep class com.noichhar.app.database.dao.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class com.noichhar.app.ui.fragment.** { *; }
-keep class com.noichhar.app.ui.activity.** { *; }
-keep class com.noichhar.app.service.** { *; }
-keep class com.noichhar.app.receiver.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
-keep class * extends androidx.work.CoroutineWorker { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn kotlinx.coroutines.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

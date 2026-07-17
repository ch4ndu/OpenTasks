# Logback's desktop artifact references optional integrations that are not used by OpenTasks.
-dontwarn jakarta.servlet.**
-dontwarn jakarta.mail.**
-dontwarn org.codehaus.janino.**
-dontwarn org.codehaus.commons.compiler.**
-dontwarn org.tukaani.xz.**

# kmlog 2.0.3's PrintLogger still references kotlinx-datetime 0.6.x symbols.
# The app resolves kotlinx-datetime 0.7.x through the normal runtime classpath.
-dontwarn com.diamondedge.logging.PrintLogger

# Room loads generated database implementations by name at runtime.
-keep class com.udnahc.opentasks.data.database.AppDatabase_Impl { *; }
-keep class com.udnahc.opentasks.data.database.AppDatabase_Impl$* { *; }
-keep class com.udnahc.opentasks.data.dao.*_Impl { *; }
-keep class com.udnahc.opentasks.data.dao.*_Impl$* { *; }

# Bundled SQLite registers its JNI methods by exact class, name, and signature at library load.
-keepclasseswithmembers class androidx.sqlite.driver.bundled.** {
    native <methods>;
}

# Desktop release uses Java ServiceLoader for logging and Ktor serialization providers.
-keep class ch.qos.logback.classic.spi.LogbackServiceProvider { *; }
-keep class io.ktor.serialization.kotlinx.json.KotlinxSerializationJsonExtensionProvider { *; }

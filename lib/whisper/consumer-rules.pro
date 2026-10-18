# JNI entry points of libaikb_whisper.so are bound by name (Java_com_manjul_aikeyboard_lib_whisper_WhisperJni_*):
# keep the class and its native method names in R8-minified apps.
-keep,includedescriptorclasses class com.manjul.aikeyboard.lib.whisper.WhisperJni {
    native <methods>;
}

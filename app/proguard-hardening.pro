# Copyright (C) 2026 AI Keyboard contributors
# Licensed under the Apache License, Version 2.0
#
# Release/beta hardening rules (TRD §6.3 "No logcat of user text"). Applied in addition to the
# upstream proguard-rules.pro by app/build.gradle.kts for every minified build type.

# android.util.Log verbose/debug/info calls are removed, so text passed to them can never reach
# logcat from a release build. Log.w/Log.e stay (TRD §6.3 lists v/d/i only).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Upstream logging facade (lib/devtools/Flog.kt). flogError/flogWarning/flogInfo/flogDebug are inline
# functions guarded by Flog.checkShouldFlog(), which is only true when BuildConfig.DEBUG. Telling R8
# it always returns false removes every flog block (and the string building inside it) from minified
# builds. The name is mangled by Kotlin (UInt parameters), hence the wildcard.
-assumevalues class com.manjul.aikeyboard.lib.devtools.Flog {
    boolean checkShouldFlog*(...) return false;
}
-assumenosideeffects class com.manjul.aikeyboard.lib.devtools.Flog {
    void log*(...);
}

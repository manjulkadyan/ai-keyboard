/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// Applied by modules with Robolectric tests. Robolectric normally downloads its android-all jar into
// ~/.m2 at test time (and takes a lock file in the home directory). Resolving it through Gradle instead
// keeps the build hermetic and lets the tests run in sandboxes with a read-only home.
// Keep the version in step with the SDK that robolectric 4.x maps SDK 34 to (tests use @Config(sdk = [34])).

val androidAll = configurations.create("robolectricAndroidAll") {
    isCanBeConsumed = false
    isTransitive = false
}

dependencies {
    add("robolectricAndroidAll", "org.robolectric:android-all-instrumented:14-robolectric-10818077-i7")
}

val syncRobolectricAndroidAll = tasks.register<Sync>("syncRobolectricAndroidAll") {
    from(androidAll)
    into(layout.buildDirectory.dir("robolectric-deps"))
}

tasks.withType<Test>().configureEach {
    dependsOn(syncRobolectricAndroidAll)
    val depsDir = layout.buildDirectory.dir("robolectric-deps").get().asFile
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", depsDir.absolutePath)
}

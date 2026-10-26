/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

// :ime:editing: the composing-span helpers voice typing and the translate bar share (TextTarget, composing span,
// undo of our own commit) and the hash-checked SafeReplacer that AI rewrite and Polish write through. Pure Kotlin,
// no Android; the IME service in :app implements TextTarget over the InputConnection. Test fixtures: a fake editor
// and the host-safety checker. Field text is never logged.

plugins {
    alias(libs.plugins.aikeyboard.jvm.library)
    `java-test-fixtures`
}

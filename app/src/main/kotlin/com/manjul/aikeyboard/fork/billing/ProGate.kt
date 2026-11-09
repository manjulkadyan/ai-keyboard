/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.billing

import com.manjul.aikeyboard.core.datastore.FeatureSettingsStore
import com.manjul.aikeyboard.core.datastore.isProAt

/**
 * The Pro gate for the IME and the app screens (S4 plan Task 4). It reads only the signed cache in
 * [FeatureSettingsStore], which `EntitlementRepository` writes, so the IME never touches billing code (ARCH-006).
 * This file must not import `:core:billing`: `FlorisImeService` calls it.
 */
fun proGate(store: FeatureSettingsStore, clock: () -> Long = System::currentTimeMillis): () -> Boolean =
    { store.current.isProAt(clock()) }

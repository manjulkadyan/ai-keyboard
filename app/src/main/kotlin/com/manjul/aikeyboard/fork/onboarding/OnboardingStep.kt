/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.onboarding

/**
 * Onboarding screens O1-O5, the terminal state and O6 (the paywall). The ordinal is what `onboardingStep` stores,
 * so new steps go at the end: PAYWALL comes after DONE in the enum, but before it in the flow.
 */
enum class OnboardingStep { WELCOME, ENABLE, SELECT, LANGUAGES, TRY_IT, DONE, PAYWALL }

/** Which recognizer the voice disclosure names. Task 11 maps the real engine kind onto this. */
enum class DisclosureEngineKind { ON_DEVICE, SYSTEM_SERVICE, WHISPER }

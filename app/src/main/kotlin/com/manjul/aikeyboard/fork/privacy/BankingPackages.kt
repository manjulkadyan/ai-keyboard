/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.privacy

/** Known banking apps per market, offered (never auto-added) as private-app suggestions. */
object BankingPackages {
    // TODO(RN-05): fill per launch market once the list is reviewed.
    val byMarket: Map<String, Set<String>> = emptyMap()

    val all: Set<String> get() = byMarket.values.flatten().toSet()
}

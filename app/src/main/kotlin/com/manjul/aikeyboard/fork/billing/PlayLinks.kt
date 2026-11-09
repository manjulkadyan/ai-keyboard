/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.billing

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.manjul.aikeyboard.core.billing.ProCatalog

/** Google Play pages the app opens. The app never edits a subscription itself (ARCH-006). */
object PlayLinks {
    private const val SUBSCRIPTIONS_URL = "https://play.google.com/store/account/subscriptions"

    /** Play's page for our `pro` subscription, where the user fixes the payment method or cancels. */
    fun manageSubscription(packageName: String): Uri = Uri.parse(SUBSCRIPTIONS_URL).buildUpon()
        .appendQueryParameter("sku", ProCatalog.PRODUCT_ID)
        .appendQueryParameter("package", packageName)
        .build()

    /** Opens [manageSubscription] for this app. Returns false when nothing can open the link. */
    fun openManageSubscription(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, manageSubscription(context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }
}

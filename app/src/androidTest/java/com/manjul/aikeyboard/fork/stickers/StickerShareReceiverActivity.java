/*
 * Copyright (C) 2026 AI Keyboard contributors
 * Licensed under the Apache License, Version 2.0
 */

package com.manjul.aikeyboard.fork.stickers;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.IOException;
import java.io.InputStream;

/**
 * The host app's share target in StickerCompatInsertTest. It runs in the test package's own process, which is not the
 * process that runs the test and has no Kotlin stdlib (so this class is Java). It reports the stream URI and whether
 * it could read it by an explicit broadcast to the app package.
 */
public class StickerShareReceiverActivity extends Activity {
    public static final String ACTION_RECEIVED = "com.manjul.aikeyboard.test.STICKER_RECEIVED";
    public static final String EXTRA_URI = "uri";
    public static final String EXTRA_READABLE = "readable";

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Uri uri = getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
        boolean readable = false;
        if (uri != null) {
            try {
                InputStream stream = getContentResolver().openInputStream(uri);
                if (stream != null) {
                    try {
                        readable = stream.read() >= 0;
                    } finally {
                        stream.close();
                    }
                }
            } catch (IOException | SecurityException e) {
                readable = false;
            }
        }
        String appPackage = getPackageName().replaceAll("\\.test$", "");
        sendBroadcast(new Intent(ACTION_RECEIVED)
                .setPackage(appPackage)
                .putExtra(EXTRA_URI, uri == null ? null : uri.toString())
                .putExtra(EXTRA_READABLE, readable));
        finish();
    }
}

package com.glowseed.noctdock.receiver

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReceiverManifestTest {
    @Test
    fun receiverSupportsTvAndNormalAndroidLaunchers() {
        val manifest = File("src/main/AndroidManifest.xml").readText()

        assertTrue(manifest.contains("android.software.leanback"))
        assertTrue(manifest.contains("android:required=\"false\""))
        assertTrue(manifest.contains("android.intent.category.LEANBACK_LAUNCHER"))
        assertTrue(manifest.contains("android.intent.category.LAUNCHER"))
    }
}

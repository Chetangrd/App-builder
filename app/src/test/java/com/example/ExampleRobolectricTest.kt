package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.GeneratedApk
import com.example.engine.IconHelper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("APK Creator", appName)
  }

  @Test
  fun `verify preset icon rendering`() {
    val bitmap = IconHelper.renderIconBitmap("web", 96)
    assertNotNull(bitmap)
    assertEquals(96, bitmap.width)
    assertEquals(96, bitmap.height)
  }

  @Test
  fun `verify room database storage`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = AppDatabase.getDatabase(context)
    val dao = db.generatedApkDao()

    val apk = GeneratedApk(
      name = "Wikipedia",
      packageName = "com.web2apk.wikipedia",
      targetUrl = "https://en.wikipedia.org",
      filePath = "/tmp/test.apk",
      fileSizeBytes = 32000,
      versionName = "1.0",
      themeColorHex = "#2563EB",
      enablePrint = true
    )
    val id = dao.insertApk(apk)
    val retrieved = dao.getApkById(id)

    assertNotNull(retrieved)
    assertEquals("Wikipedia", retrieved?.name)
    assertEquals("com.web2apk.wikipedia", retrieved?.packageName)
    assertEquals(true, retrieved?.enablePrint)
  }

  @Test
  fun `verify signing credentials resolution`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val credentials = com.example.engine.ApkSignerHelper.getSigningCredentials(context)
    assertNotNull(credentials)
    assertNotNull(credentials.privateKey)
    assertNotNull(credentials.certificate)
    assertEquals("X.509", credentials.certificate.type)
  }

  @Test
  fun `verify full apk building flow`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val apk = com.example.engine.ApkBuilder.buildApk(
      context = context,
      name = "Test Web App",
      packageName = "com.test.webapp",
      targetUrl = "https://example.com",
      isLocalFile = false,
      localHtmlContent = "",
      versionName = "1.0",
      themeColorHex = "#2563EB",
      iconPresetName = "web",
      customIconBitmap = null,
      enablePrint = true
    )

    assertNotNull(apk)
    val file = java.io.File(apk.filePath)
    org.junit.Assert.assertTrue(file.exists())
    org.junit.Assert.assertTrue(file.length() > 0)

    // Verify APK contains signature files
    java.util.zip.ZipFile(file).use { zip ->
      assertNotNull(zip.getEntry("META-INF/MANIFEST.MF"))
      assertNotNull(zip.getEntry("META-INF/CERT.SF"))
      assertNotNull(zip.getEntry("META-INF/CERT.RSA"))
      assertNotNull(zip.getEntry("AndroidManifest.xml"))
      assertNotNull(zip.getEntry("classes.dex"))
      assertNotNull(zip.getEntry("assets/app_config.json"))
    }
  }
}

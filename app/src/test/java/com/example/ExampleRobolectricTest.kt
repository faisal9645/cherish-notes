package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.security.SecurityPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Notes", appName)
  }

  @Test
  fun `test pin verification in security preferences`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val securityPrefs = SecurityPreferences(context)
    securityPrefs.setPin("1234")
    assertTrue(securityPrefs.verifyPin("1234"))
  }

  @Test
  fun `test disguise mode and passcode verification`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val securityPrefs = SecurityPreferences(context)
    assertTrue(securityPrefs.isDisguiseModeEnabled())
    assertTrue(securityPrefs.isDisguiseActive.value)
    
    // Default passcode is now love
    assertEquals("love", securityPrefs.getDisguisePasscode())
    assertTrue(securityPrefs.verifyDisguisePasscode("love"))
    assertTrue(securityPrefs.verifyDisguisePasscode("Love"))
    assertTrue(securityPrefs.verifyDisguisePasscode("LOVE"))
    
    // Unlock secret app
    securityPrefs.revealSecretApp()
    org.junit.Assert.assertFalse(securityPrefs.isDisguiseActive.value)
    
    // Re-disguise
    securityPrefs.reDisguise()
    assertTrue(securityPrefs.isDisguiseActive.value)
  }
}

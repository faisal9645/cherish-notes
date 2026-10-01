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
    // App defaults directly to Chat tab (Issue 10)
    org.junit.Assert.assertFalse(securityPrefs.isDisguiseActive.value)

    securityPrefs.setDisguiseModeEnabled(true)
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

  @Test
  fun `test note entity checklist serialization`() {
    val items = listOf(
        com.example.data.local.notes.ChecklistItem(id = "1", text = "Buy milk", isDone = false),
        com.example.data.local.notes.ChecklistItem(id = "2", text = "Call plumber", isDone = true)
    )
    val encoded = com.example.data.local.notes.NoteEntity.encodeChecklist(items)
    val note = com.example.data.local.notes.NoteEntity(
        title = "Tasks",
        checklistJson = encoded
    )
    val parsed = note.getChecklist()
    assertEquals(2, parsed.size)
    assertEquals("Buy milk", parsed[0].text)
    org.junit.Assert.assertFalse(parsed[0].isDone)
    assertEquals("Call plumber", parsed[1].text)
    assertTrue(parsed[1].isDone)
  }

  @Test
  fun `test multiple note deletion in repository`() = kotlinx.coroutines.runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val db = com.example.data.local.notes.AppNotesDatabase.getInstance(context)
    val dao = db.noteDao()
    val note1 = com.example.data.local.notes.NoteEntity(id = "test_del_1", title = "Note 1")
    val note2 = com.example.data.local.notes.NoteEntity(id = "test_del_2", title = "Note 2")
    dao.insertNotes(listOf(note1, note2))

    assertEquals("Note 1", dao.getNoteById("test_del_1")?.title)
    assertEquals("Note 2", dao.getNoteById("test_del_2")?.title)

    dao.deleteNotesByIds(listOf("test_del_1", "test_del_2"))
    org.junit.Assert.assertNull(dao.getNoteById("test_del_1"))
    org.junit.Assert.assertNull(dao.getNoteById("test_del_2"))
  }
}

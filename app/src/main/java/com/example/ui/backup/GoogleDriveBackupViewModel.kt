package com.example.ui.backup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.BackupState
import com.example.backup.GoogleDriveBackupManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GoogleDriveBackupViewModel(
    private val backupManager: GoogleDriveBackupManager
) : ViewModel() {

    val uiState: StateFlow<BackupState> = backupManager.backupState

    fun backupNow() {
        viewModelScope.launch {
            backupManager.performBackupToGoogleDrive()
        }
    }

    fun restoreNow() {
        viewModelScope.launch {
            backupManager.performRestoreFromGoogleDrive()
        }
    }
}

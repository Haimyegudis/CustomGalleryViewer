package com.example.customgalleryviewer.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.customgalleryviewer.data.VaultDao
import com.example.customgalleryviewer.data.VaultEntity
import com.example.customgalleryviewer.util.VaultManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class VaultViewModel @Inject constructor(
    private val vaultDao: VaultDao,
    private val vaultManager: VaultManager
) : ViewModel() {

    val vaultItems: StateFlow<List<VaultEntity>> = vaultDao.getAllFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    fun setAuthenticated(auth: Boolean) {
        _isAuthenticated.value = auth
    }

    fun restoreItem(id: Long) {
        viewModelScope.launch {
            vaultManager.restoreFromVault(id)
        }
    }

    fun deleteItem(id: Long) {
        viewModelScope.launch {
            vaultManager.deleteFromVault(id)
        }
    }

    fun getFileUri(item: VaultEntity): Uri {
        return vaultManager.getVaultFileUri(item)
    }
}

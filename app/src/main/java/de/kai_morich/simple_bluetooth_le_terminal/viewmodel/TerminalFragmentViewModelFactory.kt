package de.kai_morich.simple_bluetooth_le_terminal.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class TerminalFragmentViewModelFactory(private val context: Context): ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return if (modelClass.isAssignableFrom(TerminalFragmentViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            TerminalFragmentViewModel(context) as T
        } else {
            throw IllegalArgumentException("viewModel not found: $modelClass")
        }
    }
}
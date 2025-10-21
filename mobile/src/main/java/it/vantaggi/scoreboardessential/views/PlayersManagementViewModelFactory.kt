package it.vantaggi.scoreboardessential.views

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import it.vantaggi.scoreboardessential.PlayersManagementViewModel
import it.vantaggi.scoreboardessential.repository.PlayerRepository

class PlayersManagementViewModelFactory(
    private val application: Application,
    private val playerRepository: PlayerRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayersManagementViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PlayersManagementViewModel(application, playerRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

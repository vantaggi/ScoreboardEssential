package com.example.scoreboardessential

import android.os.Bundle
import android.widget.Button
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable

class MatchManagementActivity : ComponentActivity() {

    private val viewModel: WearViewModel by viewModels()
    private lateinit var messageClient: MessageClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_management)

        messageClient = Wearable.getMessageClient(this)
        
        setupClickListeners()
    }

    private fun setupClickListeners() {
        findViewById<Button>(R.id.btn_start_match).setOnClickListener {
            startNewMatch()
            finish() // Return to main screen
        }

        findViewById<Button>(R.id.btn_end_match).setOnClickListener {
            endCurrentMatch()
            finish() // Return to main screen
        }

        findViewById<Button>(R.id.btn_reset_timer).setOnClickListener {
            resetMatchTimer()
            finish() // Return to main screen
        }
    }

    private fun startNewMatch() {
        // Use the new ViewModel function that handles sync
        viewModel.startNewMatch()
    }

    private fun endCurrentMatch() {
        // Use the new ViewModel function that handles sync
        viewModel.endMatch()
    }

    private fun resetMatchTimer() {
        // Send message to mobile app
        sendMessageToMobile("/reset_timer", "RESET_TIMER")
        // Reset local timer
        viewModel.resetMatchTimer()
    }

    private fun sendMessageToMobile(path: String, message: String) {
        val data = message.toByteArray()
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, data)
            }
        }
    }
}
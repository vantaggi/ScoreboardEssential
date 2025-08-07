package com.example.scoreboardessential

import android.content.Intent
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService

// A service that listens for messages from the Wear OS app.
class DataLayerListenerService : WearableListenerService() {

    // Called when a message is received from the Wear OS app.
    override fun onMessageReceived(messageEvent: MessageEvent) {
        // If the message is a score update, send a broadcast to the MainActivity.
        if (messageEvent.path == "/score_update") {
            val team = String(messageEvent.data)
            val intent = Intent("com.example.scoreboardessential.SCORE_UPDATE")
            intent.putExtra("team", team)
            sendBroadcast(intent)
        }
    }
}

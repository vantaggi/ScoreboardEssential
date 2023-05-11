package com.example.scoreboardessential

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var team1EditText: EditText
    private lateinit var team2EditText: EditText
    private lateinit var startGameButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Inizializzazione delle view
        team1EditText = findViewById(R.id.team1_edit_text)
        team2EditText = findViewById(R.id.team2_edit_text)
        startGameButton = findViewById(R.id.start_game_button)

        // Impostazione del listener per il click del pulsante "Start Game"
        startGameButton.setOnClickListener {
            // Avvio dell'activity di Wear OS passando i dati delle squadre come intent extra
            val intent = Intent(Intent.ACTION_VIEW)
            intent.setClassName("com.example.scoreboardessential", "com.example.scoreboardessential.MainActivity")
            intent.putExtra("team1", team1EditText.text.toString())
            intent.putExtra("team2", team2EditText.text.toString())
            startActivity(intent)
        }
    }
}
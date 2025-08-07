package com.example.scoreboardessential

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    private val viewModel: MainViewModel by viewModels()

    private val scoreUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.scoreboardessential.SCORE_UPDATE") {
                val team = intent.getStringExtra("team")
                if (team == "team_1") {
                    viewModel.addTeam1Score()
                    showSelectScorerDialog(1)
                } else if (team == "team_2") {
                    viewModel.addTeam2Score()
                    showSelectScorerDialog(2)
                }
            }
        }
    }

    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var timerEditText: EditText
    private lateinit var team1NameEditText: EditText
    private lateinit var team2NameEditText: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        timerTextView = findViewById(R.id.timer_textview)
        timerEditText = findViewById(R.id.timer_edittext)
        team1NameEditText = findViewById(R.id.team1_name_edittext)
        team2NameEditText = findViewById(R.id.team2_name_edittext)

        viewModel.team1Score.observe(this, Observer { score ->
            team1ScoreTextView.text = score.toString()
        })

        viewModel.team2Score.observe(this, Observer { score ->
            team2ScoreTextView.text = score.toString()
        })

        viewModel.team1Name.observe(this, Observer { name ->
            team1NameEditText.setText(name)
        })

        viewModel.team2Name.observe(this, Observer { name ->
            team2NameEditText.setText(name)
        })

        viewModel.timerValue.observe(this, Observer { time ->
            updateTimerTextView(time)
        })

        findViewById<Button>(R.id.team1_add_button).setOnClickListener {
            viewModel.addTeam1Score()
            showSelectScorerDialog(1)
        }
        findViewById<Button>(R.id.team1_subtract_button).setOnClickListener {
            viewModel.subtractTeam1Score()
        }
        findViewById<Button>(R.id.team2_add_button).setOnClickListener {
            viewModel.addTeam2Score()
            showSelectScorerDialog(2)
        }
        findViewById<Button>(R.id.team2_subtract_button).setOnClickListener {
            viewModel.subtractTeam2Score()
        }
        findViewById<Button>(R.id.set_timer_button).setOnClickListener {
            val seconds = timerEditText.text.toString().toLongOrNull()
            if (seconds != null) {
                viewModel.setTimer(seconds)
            }
        }
        findViewById<Button>(R.id.reset_scores_button).setOnClickListener {
            viewModel.setTeam1Name(team1NameEditText.text.toString())
            viewModel.setTeam2Name(team2NameEditText.text.toString())
            viewModel.resetScores()
        }

        findViewById<Button>(R.id.match_history_button).setOnClickListener {
            val intent = Intent(this, MatchHistoryActivity::class.java)
            startActivity(intent)
        }
        findViewById<ImageButton>(R.id.team1_customize_button).setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Team 1 Color")
                .setPreferenceName("Team1ColorPicker")
                .setPositiveButton(getString(R.string.confirm),
                    ColorEnvelopeListener { envelope, fromUser ->
                        //
                    })
                .setNegativeButton(getString(R.string.cancel)) { dialogInterface, i ->
                    dialogInterface.dismiss()
                }
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .show()
        }

        findViewById<ImageButton>(R.id.team2_customize_button).setOnClickListener {
            ColorPickerDialog.Builder(this)
                .setTitle("Team 2 Color")
                .setPreferenceName("Team2ColorPicker")
                .setPositiveButton(getString(R.string.confirm),
                    ColorEnvelopeListener { envelope, fromUser ->
                        //
                    })
                .setNegativeButton(getString(R.string.cancel)) { dialogInterface, i ->
                    dialogInterface.dismiss()
                }
                .attachAlphaSlideBar(true)
                .attachBrightnessSlideBar(true)
                .show()
        }

        findViewById<ImageButton>(R.id.team1_logo_button).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, TEAM1_LOGO_REQUEST_CODE)
        }

        findViewById<ImageButton>(R.id.team2_logo_button).setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.addCategory(Intent.CATEGORY_OPENABLE)
            intent.type = "image/*"
            startActivityForResult(intent, TEAM2_LOGO_REQUEST_CODE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), POST_NOTIFICATIONS_REQUEST_CODE)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                TEAM1_LOGO_REQUEST_CODE -> {
                    data?.data?.also { uri ->
                        //
                    }
                }
                TEAM2_LOGO_REQUEST_CODE -> {
                    data?.data?.also { uri ->
                        //
                    }
                }
            }
        }
    }

    companion object {
        private const val TEAM1_LOGO_REQUEST_CODE = 1
        private const val TEAM2_LOGO_REQUEST_CODE = 2
        private const val TIMER_NOTIFICATION_ID = 123
        private const val POST_NOTIFICATIONS_REQUEST_CODE = 456
    }

    override fun onResume() {
        super.onResume()
        Wearable.getDataClient(this).addListener(this)
        registerReceiver(scoreUpdateReceiver, IntentFilter("com.example.scoreboardessential.SCORE_UPDATE"))
    }

    override fun onPause() {
        super.onPause()
        viewModel.saveState()
        Wearable.getDataClient(this).removeListener(this)
        unregisterReceiver(scoreUpdateReceiver)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("DataSync", "onDataChanged: $dataEvents")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path?.compareTo(DataSyncObject.SCORE_PATH) == 0) {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    if (dataMap.containsKey(DataSyncObject.TEAM1_SCORE_KEY)) {
                        viewModel.team1Score.value = dataMap.getInt(DataSyncObject.TEAM1_SCORE_KEY)
                        viewModel.team2Score.value = dataMap.getInt(DataSyncObject.TEAM2_SCORE_KEY)
                    }
                    if (dataMap.containsKey(DataSyncObject.TIMER_KEY)) {
                        viewModel.timerValue.value = dataMap.getLong(DataSyncObject.TIMER_KEY)
                        viewModel.isTimerRunning.value = dataMap.getBoolean(DataSyncObject.TIMER_STATE_KEY)
                    }
                }
            }
        }
    }

    fun startStopTimer(view: View) {
        viewModel.startStopTimer()
    }

    fun resetTimer(view: View) {
        viewModel.resetTimer()
    }

    private fun updateTimerTextView(remainingTimeInMillis: Long) {
        val minutes = (remainingTimeInMillis / 1000) / 60
        val seconds = (remainingTimeInMillis / 1000) % 60
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
    }

    private fun showSelectScorerDialog(team: Int) {
        val players = if (team == 1) {
            viewModel.team1Players.value?.toTypedArray()
        } else {
            viewModel.team2Players.value?.toTypedArray()
        }

        if (players != null) {
            val dialog = SelectScorerDialogFragment.newInstance(players)
            dialog.setScorerDialogListener(object : SelectScorerDialogFragment.ScorerDialogListener {
                override fun onScorerSelected(scorer: String) {
                    viewModel.addScorer(team, scorer)
                }
            })
            dialog.show(supportFragmentManager, "SelectScorerDialogFragment")
        }
    }
}

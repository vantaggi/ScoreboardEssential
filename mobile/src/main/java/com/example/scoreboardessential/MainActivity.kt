package com.example.scoreboardessential

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import androidx.lifecycle.lifecycleScope
import com.example.scoreboardessential.database.AppDatabase
import com.example.scoreboardessential.database.Match
import com.example.scoreboardessential.database.MatchDao
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    private var team1Score = 0
    private var team2Score = 0
    private var timer: CountDownTimer? = null
    private var isTimerRunning = false
    private var remainingTimeInMillis = 0L

    private lateinit var team1ScoreTextView: TextView
    private lateinit var team2ScoreTextView: TextView
    private lateinit var timerTextView: TextView
    private lateinit var timerEditText: EditText
    private lateinit var team1NameEditText: EditText
    private lateinit var team2NameEditText: EditText
    private lateinit var dataClient: DataClient
    private lateinit var matchDao: MatchDao
    private lateinit var teamDao: TeamDao
    private var team1: Team? = null
    private var team2: Team? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        team1ScoreTextView = findViewById(R.id.team1_score_textview)
        team2ScoreTextView = findViewById(R.id.team2_score_textview)
        timerTextView = findViewById(R.id.timer_textview)
        timerEditText = findViewById(R.id.timer_edittext)
        team1NameEditText = findViewById(R.id.team1_name_edittext)
        team2NameEditText = findViewById(R.id.team2_name_edittext)


        dataClient = Wearable.getDataClient(this)
        matchDao = AppDatabase.getDatabase(this).matchDao()
        teamDao = AppDatabase.getDatabase(this).teamDao()

        lifecycleScope.launch {
            teamDao.getAllTeams().collectLatest { teams ->
                if (teams.isEmpty()) {
                    teamDao.insert(Team(name = "Team 1", color = 0, logoUri = null))
                    teamDao.insert(Team(name = "Team 2", color = 0, logoUri = null))
                } else {
                    team1 = teams.getOrNull(0)
                    team2 = teams.getOrNull(1)
                    team1NameEditText.setText(team1?.name)
                    team2NameEditText.setText(team2?.name)
                }
            }
        }

        findViewById<Button>(R.id.team1_add_button).setOnClickListener {
            team1Score++
            updateUi(team1Score, team2Score)
            sendScoreUpdate()
        }
        findViewById<Button>(R.id.team1_subtract_button).setOnClickListener {
            if (team1Score > 0) {
                team1Score--
                updateUi(team1Score, team2Score)
                sendScoreUpdate()
            }
        }
        findViewById<Button>(R.id.team2_add_button).setOnClickListener {
            team2Score++
            updateUi(team1Score, team2Score)
            sendScoreUpdate()
        }
        findViewById<Button>(R.id.team2_subtract_button).setOnClickListener {
            if (team2Score > 0) {
                team2Score--
                updateUi(team1Score, team2Score)
                sendScoreUpdate()
            }
        }
        findViewById<Button>(R.id.set_timer_button).setOnClickListener {
            val seconds = timerEditText.text.toString().toLongOrNull()
            if (seconds != null) {
                remainingTimeInMillis = seconds * 1000
                updateTimerTextView()
                sendSetTimerUpdate()
            }
        }
        findViewById<Button>(R.id.reset_scores_button).setOnClickListener {
            lifecycleScope.launch {
                team1?.let { teamDao.update(it.copy(name = team1NameEditText.text.toString())) }
                team2?.let { teamDao.update(it.copy(name = team2NameEditText.text.toString())) }

                val match = Match(
                    team1Id = team1!!.id,
                    team2Id = team2!!.id,
                    team1Score = team1Score,
                    team2Score = team2Score,
                    timestamp = System.currentTimeMillis()
                )
                matchDao.insert(match)
            }

            team1Score = 0
            team2Score = 0
            updateUi(team1Score, team2Score)
            sendResetUpdate()
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
                        lifecycleScope.launch {
                            team1?.let { teamDao.update(it.copy(color = envelope.color)) }
                        }
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
                        lifecycleScope.launch {
                            team2?.let { teamDao.update(it.copy(color = envelope.color)) }
                        }
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
                        lifecycleScope.launch {
                            team1?.let { teamDao.update(it.copy(logoUri = uri.toString())) }
                        }
                    }
                }
                TEAM2_LOGO_REQUEST_CODE -> {
                    data?.data?.also { uri ->
                        lifecycleScope.launch {
                            team2?.let { teamDao.update(it.copy(logoUri = uri.toString())) }
                        }
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

    private fun sendScoreUpdate() {
        val putDataMapReq = PutDataMapRequest.create(DataSyncObject.SCORE_PATH).apply {
            dataMap.putInt(DataSyncObject.TEAM1_SCORE_KEY, team1Score)
            dataMap.putInt(DataSyncObject.TEAM2_SCORE_KEY, team2Score)
            dataMap.putLong(DataSyncObject.TIMER_KEY, remainingTimeInMillis)
            dataMap.putBoolean(DataSyncObject.TIMER_STATE_KEY, isTimerRunning)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq).addOnSuccessListener {
            Log.d("DataSync", "Data sent successfully: $it")
        }.addOnFailureListener {
            Log.e("DataSync", "Data sending failed", it)
        }
    }

    private fun sendSetTimerUpdate() {
        val putDataMapReq = PutDataMapRequest.create(DataSyncObject.SCORE_PATH).apply {
            dataMap.putLong(DataSyncObject.SET_TIMER_KEY, remainingTimeInMillis)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    private fun sendResetUpdate() {
        val putDataMapReq = PutDataMapRequest.create(DataSyncObject.SCORE_PATH).apply {
            dataMap.putBoolean(DataSyncObject.RESET_KEY, true)
        }
        val putDataReq = putDataMapReq.asPutDataRequest().setUrgent()
        dataClient.putDataItem(putDataReq)
    }

    override fun onResume() {
        super.onResume()
        dataClient.addListener(this)
    }

    override fun onPause() {
        super.onPause()
        dataClient.removeListener(this)
    }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        Log.d("DataSync", "onDataChanged: $dataEvents")
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED) {
                val dataItem = event.dataItem
                if (dataItem.uri.path?.compareTo(DataSyncObject.SCORE_PATH) == 0) {
                    val dataMap = DataMapItem.fromDataItem(dataItem).dataMap
                    if (dataMap.containsKey(DataSyncObject.TEAM1_SCORE_KEY)) {
                        team1Score = dataMap.getInt(DataSyncObject.TEAM1_SCORE_KEY)
                        team2Score = dataMap.getInt(DataSyncObject.TEAM2_SCORE_KEY)
                        updateUi(team1Score, team2Score)
                    }
                    if (dataMap.containsKey(DataSyncObject.TIMER_KEY)) {
                        remainingTimeInMillis = dataMap.getLong(DataSyncObject.TIMER_KEY)
                        isTimerRunning = dataMap.getBoolean(DataSyncObject.TIMER_STATE_KEY)
                        updateTimerTextView()
                    }
                }
            }
        }
    }

    private fun updateUi(team1Score: Int, team2Score: Int) {
        runOnUiThread {
            team1ScoreTextView.text = team1Score.toString()
            team2ScoreTextView.text = team2Score.toString()
        }
    }

    fun startStopTimer(view: View) {
        if (isTimerRunning) {
            // stop the timer
            timer?.cancel()
            isTimerRunning = false
        } else {
            // start the timer
            timer = object : CountDownTimer(remainingTimeInMillis, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    remainingTimeInMillis = millisUntilFinished
                    updateTimerTextView()
                }

                override fun onFinish() {
                    isTimerRunning = false
                    val builder = androidx.core.app.NotificationCompat.Builder(this@MainActivity, ScoreboardEssentialApplication.CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_launcher_foreground)
                        .setContentTitle("Timer Finished")
                        .setContentText("The timer has finished.")
                        .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)

                    with(androidx.core.app.NotificationManagerCompat.from(this@MainActivity)) {
                        notify(TIMER_NOTIFICATION_ID, builder.build())
                    }
                }
            }.start()

            isTimerRunning = true
        }
        sendScoreUpdate()
    }

    fun resetTimer(view: View) {
        timer?.cancel()
        isTimerRunning = false
        remainingTimeInMillis = 0
        updateTimerTextView()
        sendScoreUpdate()
    }

    private fun updateTimerTextView() {
        val minutes = (remainingTimeInMillis / 1000) / 60
        val seconds = (remainingTimeInMillis / 1000) % 60
        timerTextView.text = String.format("%02d:%02d", minutes, seconds)
    }
}

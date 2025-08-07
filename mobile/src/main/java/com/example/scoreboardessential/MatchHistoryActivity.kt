package com.example.scoreboardessential

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.scoreboardessential.database.AppDatabase
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MatchHistoryActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_match_history)

        val recyclerView = findViewById<RecyclerView>(R.id.match_history_recyclerview)
        val adapter = MatchHistoryAdapter()
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        val matchDao = AppDatabase.getDatabase(this).matchDao()
        lifecycleScope.launch {
            matchDao.getAllMatches().collectLatest { matches ->
                adapter.submitList(matches)
            }
        }
    }
}

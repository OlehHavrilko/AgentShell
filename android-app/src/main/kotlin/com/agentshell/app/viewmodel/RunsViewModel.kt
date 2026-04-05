package com.agentshell.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.agentshell.app.db.AgentDatabase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class RunsViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AgentDatabase.getInstance(app)

    val runs = db.runDao().allRuns()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

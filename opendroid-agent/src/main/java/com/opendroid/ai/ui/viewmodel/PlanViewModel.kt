package com.opendroid.ai.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.opendroid.ai.core.agent.PlanManager
import com.opendroid.ai.data.models.Plan
import com.opendroid.ai.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val planManager: PlanManager,
    private val planRepository: PlanRepository
) : ViewModel() {

    val currentPlan: StateFlow<Plan?> = planManager.currentPlan

    val planHistory: StateFlow<List<Plan>> = planRepository.allPlans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun loadPlan(planId: String) {
        viewModelScope.launch {
            planManager.loadPlan(planId)
        }
    }

    fun deletePlan(planId: String) {
        viewModelScope.launch {
            planRepository.deletePlan(planId)
        }
    }
}

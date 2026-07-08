package com.example.ui.automation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Event bus for automation commands between AI and screens.
 */
object AutomationEventBus {
    
    data class AutomationEvent(
        val requestId: String,
        val targetScreen: String,
        val action: String,
        val parameters: Map<String, String> = emptyMap()
    )
    
    data class AutomationResult(
        val requestId: String,
        val success: Boolean,
        val message: String
    )
    
    private val _events = MutableSharedFlow<AutomationEvent>()
    val events: SharedFlow<AutomationEvent> = _events.asSharedFlow()
    
    private val _results = MutableSharedFlow<AutomationResult>()
    val results: SharedFlow<AutomationResult> = _results.asSharedFlow()
    
    fun sendEvent(event: AutomationEvent) {
        GlobalScope.launch {
            _events.emit(event)
        }
    }
    
    fun sendResult(result: AutomationResult) {
        GlobalScope.launch {
            _results.emit(result)
        }
    }
}

package io.devforth.step_counter_service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.flutter.plugin.common.EventChannel

typealias EventBroadcastReceiverCallback = (Intent) -> Unit

class EventBroadcastReceiver: BroadcastReceiver() {
    private val callbacks: MutableMap<String, MutableList<EventBroadcastReceiverCallback>> =
        mutableMapOf()

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action

        val callbacks: List<EventBroadcastReceiverCallback> =
            if (callbacks[action] != null) callbacks[action]!! else listOf()
        callbacks.forEach {
            it(intent)
        }
    }

    fun addCallback(action: String, callback: EventBroadcastReceiverCallback) {
        if (callbacks.containsKey(action)) {
            callbacks[action]!!.add(callback)
        } else {
            callbacks[action] = mutableListOf(callback)
        }
    }

    fun removeCallback(action: String, callback: EventBroadcastReceiverCallback) {
        if (callbacks.containsKey(action)) {
            callbacks[action]!!.remove(callback)
        }
    }
}

fun eventStreamHandler(broadcastReceiver: EventBroadcastReceiver, action: String, callback: (events: EventChannel.EventSink?, intent: Intent) -> Unit): EventChannel.StreamHandler {
    return object : EventChannel.StreamHandler {
        private lateinit var eventCallback: EventBroadcastReceiverCallback

        override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
            eventCallback = fun(intent: Intent) { callback(events, intent) }
            broadcastReceiver.addCallback(action, eventCallback)
        }

        override fun onCancel(arguments: Any?) {
            broadcastReceiver.removeCallback(action, eventCallback)
        }
    }
}
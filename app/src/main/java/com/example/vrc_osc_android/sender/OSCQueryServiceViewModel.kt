package com.example.vrc_osc_android.sender

import android.content.Context
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.vrc_osc_android.vrc.oscquery.Attributes
import com.example.vrc_osc_android.vrc.oscquery.OSCQueryService
import com.example.vrc_osc_android.vrc.oscquery.OSCQueryServiceBuilder
import kotlinx.coroutines.launch
import java.util.Random

class RandomWordGenerator {
    private val adjectives = listOf("fuzzy", "loud", "quick", "silent", "soft", "strong", "weak", "wild")
    private val nouns = listOf("bear", "cat", "dog", "fox", "lion", "mouse", "tiger", "wolf")

    fun adjective(): String = adjectives.random()
    fun noun(): String = nouns.random()
}

class OSCQueryServiceViewModel(private val context: Context) : ViewModel() {
    private val _service: OSCQueryService
    private val _intParams = mutableStateListOf<Int>()
    internal val _paramNames = mutableStateListOf<String>()
    private val wordGenerator = RandomWordGenerator()

    init {
        val name = "MyOSCQueryService"
        val tcpPort = 8000
        val oscPort = 9000
        _service = OSCQueryService(context, name, tcpPort, oscPort)
        /*
        _service = OSCQueryServiceBuilder(context)
            .withServiceName(name)
            .withTcpPort(tcpPort)
            .withUdpPort(oscPort)
            .build()

         */
        generateParams(10)
    }

    private fun generateParams(count: Int) {
        repeat(count) {
            val name = "${wordGenerator.adjective()}-${wordGenerator.noun()}"
            _paramNames.add(name)
            _service.addEndpoint<Int>("/$name", Attributes.AccessValues.ReadOnly)
            val newValue = Random().nextInt(100)
            setIntParam(it, newValue)
        }
    }

    fun getIntParam(i: Int): Int = _intParams.getOrElse(i) { -1 }

    fun setIntParam(i: Int, value: Int) {
        Log.d("ExampleService2", "setIntParam(${_intParams.size}) $i, $value")
        if (i == _intParams.size) {
            _intParams.add(value)
        } else if (i in _intParams.indices) {
            _intParams[i] = value
        }
        viewModelScope.launch {
            Log.d("ExampleService2", "_service.setValue $_paramNames[i], $value")
            _service.setValue("/${_paramNames[i]}", value.toString())
        }
    }

    fun randomizeParam(i: Int) {
        val r = Random()
        setIntParam(i, r.nextInt(100))
    }

    fun getParamCount() = _intParams.size

    fun dispose() {
        _service.dispose()
    }
}
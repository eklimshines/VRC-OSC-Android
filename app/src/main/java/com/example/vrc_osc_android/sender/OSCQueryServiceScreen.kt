package com.example.vrc_osc_android.sender

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun OSCQueryServiceScreen(viewModel: OSCQueryServiceViewModel) {
    val paramCount by remember { mutableStateOf(viewModel.getParamCount()) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Params and Values - Press to Randomize",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            LazyColumn {
                itemsIndexed(viewModel._paramNames) { index, name ->
                    ParamButton(
                        name = name,
                        value = viewModel.getIntParam(index),
                        onRandomize = { viewModel.randomizeParam(index) }
                    )
                }
            }
        }
    }
}

@Composable
fun ParamButton(name: String, value: Int, onRandomize: () -> Unit) {
    Button(
        onClick = onRandomize,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = "/$name $value",
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
package com.example.scooterspeedometer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonRow
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsPanel(
    settings: Settings,
    onSave: (Settings) -> Unit,
    onResetTotals: () -> Unit,
    onApplyChargeVoltage: (Double) -> Unit
) {
    var chemIdx by remember { mutableStateOf(settings.chem.ordinal) }
    var packV by remember { mutableStateOf(settings.packV.toString()) }
    var packAh by remember { mutableStateOf(settings.packAh.toString()) }
    var fullV by remember { mutableStateOf(settings.fullV.toString()) }
    var lastCh by remember { mutableStateOf(settings.lastChargeVoltage?.toString() ?: "") }
    var temp by remember { mutableStateOf(settings.temperatureC.toString()) }
    var loss by remember { mutableStateOf(settings.lossPercent.toString()) }

    var askFirst by remember { mutableStateOf(false) }
    var askSecond by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth()) {
        Text("Tip baterije")
        SegmentedButtonRow {
            listOf("Olovna", "Litijumska", "Grafenska").forEachIndexed { i, label ->
                SegmentedButton(
                    selected = chemIdx == i,
                    onClick = { chemIdx = i },
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                ) { Text(label) }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = packV, onValueChange = { packV = it }, label = { Text("Napon paketa (V)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = packAh, onValueChange = { packAh = it }, label = { Text("Kapacitet (Ah)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = fullV, onValueChange = { fullV = it }, label = { Text("Full napon (V)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = lastCh, onValueChange = { lastCh = it }, label = { Text("Trenutna voltaža posle punjenja (V)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = temp, onValueChange = { temp = it }, label = { Text("Temperatura (°C)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = loss, onValueChange = { loss = it }, label = { Text("Loss faktor (%)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val newS = Settings(
                    chem = BatteryChem.values()[chemIdx],
                    packV = packV.toDoubleOrNull() ?: settings.packV,
                    packAh = packAh.toDoubleOrNull() ?: settings.packAh,
                    fullV = fullV.toDoubleOrNull() ?: settings.fullV,
                    lastChargeVoltage = lastCh.toDoubleOrNull(),
                    temperatureC = temp.toDoubleOrNull() ?: settings.temperatureC,
                    lossPercent = loss.toIntOrNull() ?: settings.lossPercent
                )
                onSave(newS)
            }) { Text("Sačuvaj") }

            if (!askFirst && !askSecond) {
                Button(onClick = { askFirst = true }) { Text("Reset UKUPNO") }
            } else if (askFirst && !askSecond) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { askSecond = true }) { Text("Siguran?") }
                    Button(onClick = { askFirst = false }) { Text("Odustani") }
                }
            } else if (askFirst && askSecond) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        onResetTotals()
                        askFirst = false; askSecond = false
                    }) { Text("Zaista siguran?") }
                    Button(onClick = { askFirst = false; askSecond = false }) { Text("Odustani") }
                }
            }

            Button(onClick = {
                val vNow = lastCh.toDoubleOrNull()
                if (vNow != null) onApplyChargeVoltage(vNow)
            }) { Text("Primeni voltažu") }
        }
    }
}
package com.example.scooterspeedometer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.roundToInt

private val Context.dataStore by preferencesDataStore(name = "settings")

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AppScreen() } }
    }
}

enum class BatteryChem { LEAD, LI_ION, GRAPHENE }

data class Settings(
    val chem: BatteryChem = BatteryChem.LEAD,
    val packV: Double = 60.0,
    val packAh: Double = 20.0,
    val fullV: Double = 74.0,
    val lastChargeVoltage: Double? = null,
    val temperatureC: Double = 20.0,
    val lossPercent: Int = 0
)

@Composable
fun AppScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var speedKmh by remember { mutableStateOf(0.0) }
    var tripMeters by remember { mutableStateOf(0.0) }
    var totalMeters by remember { mutableStateOf(0.0) }
    var lastLocation by remember { mutableStateOf<Location?>(null) }
    var settings by remember { mutableStateOf(Settings()) }
    var showSettings by remember { mutableStateOf(false) }

    // Load persisted
    LaunchedEffect(Unit) {
        val p = ctx.dataStore.data.first()
        val total = p[intPreferencesKey("total_meters")] ?: 0
        totalMeters = if (total == 0) 2200_000.0 else total.toDouble()

        val chemIdx = p[intPreferencesKey("chem")] ?: 0
        val packV = p[doublePreferencesKey("packV")] ?: 60.0
        val packAh = p[doublePreferencesKey("packAh")] ?: 20.0
        val fullV = p[doublePreferencesKey("fullV")] ?: 74.0
        val lastCh = p[doublePreferencesKey("lastChargeVoltage")]
        val temp = p[doublePreferencesKey("temperatureC")] ?: 20.0
        val loss = p[intPreferencesKey("lossPercent")] ?: 0
        settings = Settings(
            chem = BatteryChem.values()[chemIdx.coerceIn(0,2)],
            packV = packV, packAh = packAh, fullV = fullV,
            lastChargeVoltage = lastCh, temperatureC = temp, lossPercent = loss
        )
    }

    val requestPermission =
        remember {
            (ctx as ComponentActivity).registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) startLocationUpdates(ctx) { loc ->
                    handleLocation(loc, lastLocation,
                        onSpeed = { speedKmh = it },
                        onTripDelta = { d ->
                            tripMeters += d; totalMeters += d
                        },
                        setLast = { lastLocation = it }
                    )
                }
            }
        }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates(ctx) { loc ->
                handleLocation(loc, lastLocation,
                    onSpeed = { speedKmh = it },
                    onTripDelta = { d -> tripMeters += d; totalMeters += d },
                    setLast = { lastLocation = it }
                )
            }
        } else requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Battery estimate
    val batteryInfo = remember(speedKmh, tripMeters, settings) {
        estimateBattery(settings, tripMeters)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Color.Black).padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${speedKmh.roundToInt()} km/h",
                color = Color(0xFF00FF6A),
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text("Trip: ${(tripMeters/1000.0).format(2)} km", color = Color(0xFF00FF6A), fontSize = 20.sp)
            Text("Ukupno: ${(totalMeters/1000.0).format(2)} km", color = Color(0xFF00FF6A), fontSize = 20.sp)
            Spacer(Modifier.height(8.dp))
            Text("Baterija: ${batteryInfo.percent}% | Preostalo: ${batteryInfo.remainingKm.format(1)} km", color = Color(0xFF00FF6A), fontSize = 18.sp)
        }

        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { tripMeters = 0.0 }) { Text("Reset TRIP") }
                Button(onClick = { showSettings = !showSettings }) { Text(if (showSettings) "Zatvori podešavanja" else "Podešavanja") }
            }
            Spacer(Modifier.height(12.dp))
            if (showSettings) {
                SettingsPanel(
                    settings = settings,
                    onSave = { newS ->
                        settings = newS
                        scope.launch {
                            ctx.dataStore.edit { p ->
                                p[intPreferencesKey("chem")] = newS.chem.ordinal
                                p[doublePreferencesKey("packV")] = newS.packV
                                p[doublePreferencesKey("packAh")] = newS.packAh
                                p[doublePreferencesKey("fullV")] = newS.fullV
                                if (newS.lastChargeVoltage != null) p[doublePreferencesKey("lastChargeVoltage")] = newS.lastChargeVoltage
                                else p.remove(doublePreferencesKey("lastChargeVoltage"))
                                p[doublePreferencesKey("temperatureC")] = newS.temperatureC
                                p[intPreferencesKey("lossPercent")] = newS.lossPercent
                            }
                        }
                    },
                    onResetTotals = {
                        // Reset UKUPNO to 0.0 km
                        val zero = 0.0
                        totalMeters = zero
                        tripMeters = 0.0
                        scope.launch {
                            ctx.dataStore.edit { p -> p[intPreferencesKey("total_meters")] = 0 }
                        }
                    },
                    onApplyChargeVoltage = { vNow ->
                        // Handle trip based on last charge voltage relative to fullV
                        val startRange = estimateBattery(settings.copy(lastChargeVoltage = settings.fullV), 0.0).remainingKm
                        val nowRange = estimateBattery(settings.copy(lastChargeVoltage = vNow), 0.0).remainingKm
                        val usedKm = (startRange - nowRange).coerceAtLeast(0.0)
                        tripMeters = usedKm * 1000.0
                        scope.launch {
                            ctx.dataStore.edit { p ->
                                p[doublePreferencesKey("lastChargeVoltage")] = vNow
                            }
                        }
                    }
                )
            }
        }
    }

    // Persist total meters
    LaunchedEffect(totalMeters) {
        ctx.dataStore.edit { p -> p[intPreferencesKey("total_meters")] = totalMeters.roundToInt() }
    }
}

data class BatteryEstimate(val percent: Int, val remainingKm: Double)

private fun estimateBattery(settings: Settings, tripMeters: Double): BatteryEstimate {
    val energyWh = settings.packV * settings.packAh
    val baselineWhPerKm = 35.0
    val tempPenalty = if (settings.temperatureC < 20.0) (20.0 - settings.temperatureC) * 0.005 else 0.0
    val capacityFactor = max(0.6, 1.0 - tempPenalty)

    val lossFactor = 1.0 + (settings.lossPercent.coerceAtLeast(0) / 100.0)
    val effectiveWhPerKm = baselineWhPerKm * lossFactor

    // Chem curve: simple OCV mapping around fullV; treat GRAPHENE as Li-ion with flatter middle
    fun ocvToSoc(v: Double): Double {
        val full = settings.fullV
        val nominal = settings.packV
        val empty = when (settings.chem) {
            BatteryChem.LEAD -> nominal * 0.9
            BatteryChem.LI_ION -> nominal * 0.85
            BatteryChem.GRAPHENE -> nominal * 0.84
        }
        val midFlatten = when (settings.chem) {
            BatteryChem.LEAD -> 0.0
            BatteryChem.LI_ION -> 0.05
            BatteryChem.GRAPHENE -> 0.08
        }
        val clamped = v.coerceIn(empty, full)
        val span = (full - empty)
        var soc = (clamped - empty) / span
        // flatten middle
        soc = if (soc in 0.2..0.8) soc + midFlatten * (0.5 - kotlin.math.abs(soc - 0.5)) else soc
        return soc.coerceIn(0.0, 1.0)
    }

    val startSoc = ocvToSoc(settings.fullV) * capacityFactor
    val startRangeKm = (energyWh * startSoc) / effectiveWhPerKm

    val usedKm = tripMeters / 1000.0
    val remainingKm = (startRangeKm - usedKm).coerceAtLeast(0.0)

    val voltageNow = settings.lastChargeVoltage ?: settings.fullV - (usedKm / startRangeKm).coerceIn(0.0,1.0) * (settings.fullV - settings.packV*0.85)
    val socNow = (ocvToSoc(voltageNow) * capacityFactor).coerceIn(0.0, 1.0)
    val percent = (socNow * 100.0).roundToInt()

    return BatteryEstimate(percent, remainingKm)
}

private fun Double.format(n: Int): String = "%.${n}f".format(this)

private fun handleLocation(
    loc: Location,
    last: Location?,
    onSpeed: (Double) -> Unit,
    onTripDelta: (Double) -> Unit,
    setLast: (Location) -> Unit
) {
    val kmh = loc.speed.toDouble() * 3.6
    onSpeed(kmh)
    if (last != null) {
        val d = loc.distanceTo(last).toDouble()
        if (d >= 0) onTripDelta(d)
    }
    setLast(loc)
}

private fun startLocationUpdates(ctx: Context, onUpdate: (Location) -> Unit) {
    val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    try {
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f) { loc -> onUpdate(loc) }
    } catch (_: SecurityException) {}
}
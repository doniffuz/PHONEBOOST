package uz.phoneboost.app.core.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import uz.phoneboost.app.R
import uz.phoneboost.app.core.designsystem.BrandColors
import uz.phoneboost.app.domain.HealthLevel
import uz.phoneboost.app.domain.model.BatteryHealth
import uz.phoneboost.app.domain.model.ChargeState
import uz.phoneboost.app.domain.model.PowerSource
import java.util.Locale

@StringRes
fun healthLabelRes(level: HealthLevel?): Int = when (level) {
    HealthLevel.EXCELLENT -> R.string.health_excellent
    HealthLevel.GOOD -> R.string.health_good
    HealthLevel.ATTENTION -> R.string.health_attention
    HealthLevel.CHECK -> R.string.health_check
    null -> R.string.unknown
}

fun healthColor(level: HealthLevel?): Color = when (level) {
    HealthLevel.EXCELLENT, HealthLevel.GOOD -> BrandColors.Good
    HealthLevel.ATTENTION -> BrandColors.Warn
    HealthLevel.CHECK -> BrandColors.Bad
    null -> Color.Gray
}

@Composable
fun chargeStateText(state: ChargeState): String = stringResource(
    when (state) {
        ChargeState.CHARGING -> R.string.charge_charging
        ChargeState.DISCHARGING -> R.string.charge_discharging
        ChargeState.NOT_CHARGING -> R.string.charge_not_charging
        ChargeState.FULL -> R.string.charge_full
        ChargeState.UNKNOWN -> R.string.unknown
    }
)

@Composable
fun powerSourceText(source: PowerSource): String = stringResource(
    when (source) {
        PowerSource.BATTERY -> R.string.power_battery
        PowerSource.AC -> R.string.power_ac
        PowerSource.USB -> R.string.power_usb
        PowerSource.WIRELESS -> R.string.power_wireless
        PowerSource.UNKNOWN -> R.string.unknown
    }
)

/** Null = qurilma batareya salomatligi holatini bermagan. */
@Composable
fun batteryHealthText(health: BatteryHealth): String? {
    val res: Int? = when (health) {
        BatteryHealth.GOOD -> R.string.bh_good
        BatteryHealth.OVERHEAT -> R.string.bh_overheat
        BatteryHealth.DEAD -> R.string.bh_dead
        BatteryHealth.OVER_VOLTAGE -> R.string.bh_over_voltage
        BatteryHealth.FAILURE -> R.string.bh_failure
        BatteryHealth.COLD -> R.string.bh_cold
        BatteryHealth.UNKNOWN -> null
    }
    return if (res == null) null else stringResource(res)
}

@Composable
fun temperatureStatus(tempC: Float?): String = stringResource(
    when {
        tempC == null -> R.string.unknown
        tempC >= 45f -> R.string.temp_hot
        tempC >= 40f -> R.string.temp_warm
        else -> R.string.temp_normal
    }
)

@Composable
fun formatTemp(tempC: Float?): String =
    if (tempC == null) stringResource(R.string.unknown)
    else String.format(Locale.getDefault(), "%.1f°C", tempC)

@Composable
fun hwStateText(state: uz.phoneboost.app.domain.model.HwState): String = stringResource(
    when (state) {
        uz.phoneboost.app.domain.model.HwState.ON -> R.string.hw_on
        uz.phoneboost.app.domain.model.HwState.OFF -> R.string.hw_off
        uz.phoneboost.app.domain.model.HwState.PRESENT -> R.string.hw_present
        uz.phoneboost.app.domain.model.HwState.ABSENT -> R.string.hw_absent
    }
)

@Composable
fun thermalText(level: uz.phoneboost.app.domain.model.ThermalLevel): String? {
    val res: Int? = when (level) {
        uz.phoneboost.app.domain.model.ThermalLevel.NONE -> R.string.thermal_none
        uz.phoneboost.app.domain.model.ThermalLevel.LIGHT -> R.string.thermal_light
        uz.phoneboost.app.domain.model.ThermalLevel.MODERATE -> R.string.thermal_moderate
        uz.phoneboost.app.domain.model.ThermalLevel.SEVERE -> R.string.thermal_severe
        uz.phoneboost.app.domain.model.ThermalLevel.CRITICAL -> R.string.thermal_critical
        uz.phoneboost.app.domain.model.ThermalLevel.UNKNOWN -> null
    }
    return if (res == null) null else stringResource(res)
}

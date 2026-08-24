package com.homedesign.android.presentation.editor

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Web `NumberRow` parity: − / editable / + with optional cm↔unit conversion.
 * When [unit] is set, [valueCm]/[minCm]/[maxCm] are centimetres.
 */
@Composable
fun NumberRow(
    label: String,
    valueCm: Double,
    onChangeCm: (Double) -> Unit,
    modifier: Modifier = Modifier,
    minCm: Double = 1.0,
    maxCm: Double = 10_000.0,
    unit: UnitSystem? = null,
    stepCm: Double = 1.0,
    enabled: Boolean = true,
) {
    val focus = LocalFocusManager.current
    val precision = if (unit != null) UnitFormat.editPrecision(unit) else 0
    val displayStep = if (unit != null) UnitFormat.editStep(unit) else stepCm
    val display = if (unit != null) UnitFormat.toUnit(valueCm, unit) else valueCm
    val displayMin = if (unit != null) UnitFormat.toUnit(minCm, unit) else minCm
    val displayMax = if (unit != null) UnitFormat.toUnit(maxCm, unit) else maxCm
    val shown = quantize(display, precision)
    val rounded = round(shown / displayStep) * displayStep
    var draft by remember(unit) { mutableStateOf<String?>(null) }

    fun commitDisplay(nextDisplay: Double) {
        val raw = if (unit != null) UnitFormat.fromUnit(nextDisplay, unit) else nextDisplay
        if (!raw.isFinite()) return
        onChangeCm(min(maxCm, max(minCm, raw)))
    }

    fun commitDraft() {
        val text = draft ?: return
        val n = text.toDoubleOrNull()
        if (n != null && n.isFinite()) commitDisplay(n)
        draft = null
        focus.clearFocus()
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = HdTheme.typography.bodyMedium, color = HdTheme.colors.ink)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            TextButton(
                onClick = { commitDisplay(max(displayMin, rounded - displayStep)) },
                enabled = enabled && rounded > displayMin,
            ) { Text("−") }
            BasicTextField(
                value = draft ?: formatShown(shown, precision),
                onValueChange = { draft = it },
                enabled = enabled,
                singleLine = true,
                textStyle = HdTheme.typography.bodyMedium.copy(
                    color = HdTheme.colors.ink,
                    textAlign = TextAlign.Center,
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { commitDraft() }),
                modifier = Modifier
                    .widthIn(min = 56.dp, max = 88.dp)
                    .height(36.dp)
                    .border(1.dp, HdTheme.colors.hairline, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .onFocusChanged { if (!it.isFocused) commitDraft() },
            )
            if (unit != null) {
                Text(
                    UnitFormat.suffix(unit),
                    style = HdTheme.typography.labelSmall,
                    color = HdTheme.colors.architectGray,
                )
            }
            TextButton(
                onClick = { commitDisplay(min(displayMax, rounded + displayStep)) },
                enabled = enabled && rounded < displayMax,
            ) { Text("+") }
        }
    }
}

private fun quantize(n: Double, precision: Int): Double {
    if (!n.isFinite()) return 0.0
    if (precision <= 0) return round(n)
    val f = 10.0.pow(precision)
    return round(n * f) / f
}

private fun formatShown(n: Double, precision: Int): String =
    if (precision <= 0) round(n).toInt().toString()
    else String.format("%.${precision}f", n)

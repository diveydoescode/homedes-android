package com.homedesign.android.presentation.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.homedesign.android.core.ui.theme.HdMono
import com.homedesign.android.core.ui.theme.HdSans
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.model.UnitFormat
import com.homedesign.android.domain.model.UnitSystem
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

/**
 * Handoff DimensionEditorRow: label left, − / value+unit / + capsule.
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
    note: String? = null,
) {
    val focus = LocalFocusManager.current
    val ink = HdTheme.colors.architectInk
    val gray = HdTheme.colors.architectGray
    val rule = HdTheme.colors.hairline
    val precision = if (unit != null) UnitFormat.editPrecision(unit) else 0
    val displayStep = if (unit != null) UnitFormat.editStep(unit) else stepCm
    val display = if (unit != null) UnitFormat.toUnit(valueCm, unit) else valueCm
    val displayMin = if (unit != null) UnitFormat.toUnit(minCm, unit) else minCm
    val displayMax = if (unit != null) UnitFormat.toUnit(maxCm, unit) else maxCm
    val shown = quantize(display, precision)
    val rounded = round(shown / displayStep) * displayStep
    var draft by remember(unit) { mutableStateOf<String?>(null) }
    val shape = RoundedCornerShape(10.dp)
    val suffix = if (unit != null) UnitFormat.suffix(unit) else null

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
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = HdSans,
                fontSize = 14.sp,
                color = ink,
            )
            if (note != null) {
                Text(note, fontFamily = HdSans, fontSize = 11.sp, color = gray)
            }
        }
        Row(
            modifier = Modifier
                .height(40.dp)
                .clip(shape)
                .background(Color.White)
                .border(0.5.dp, rule, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .clickable(enabled = enabled && rounded > displayMin) {
                        commitDisplay(max(displayMin, rounded - displayStep))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("−", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(rule),
            )
            Row(
                modifier = Modifier
                    .width(72.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center,
            ) {
                BasicTextField(
                    value = draft ?: formatShown(shown, precision),
                    onValueChange = { draft = it },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontFamily = HdMono,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = ink,
                        textAlign = TextAlign.Center,
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { commitDraft() }),
                    modifier = Modifier
                        .width(44.dp)
                        .onFocusChanged { if (!it.isFocused) commitDraft() },
                )
                if (suffix != null) {
                    Text(
                        suffix,
                        fontFamily = HdMono,
                        fontSize = 10.5.sp,
                        color = gray,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(0.5.dp)
                    .fillMaxHeight()
                    .background(rule),
            )
            Box(
                modifier = Modifier
                    .width(38.dp)
                    .fillMaxHeight()
                    .clickable(enabled = enabled && rounded < displayMax) {
                        commitDisplay(min(displayMax, rounded + displayStep))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("+", color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
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

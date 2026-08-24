package com.homedesign.android.presentation.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homedesign.android.core.ui.components.InkPlateButton
import com.homedesign.android.core.ui.components.JourneyBackButton
import com.homedesign.android.core.ui.components.JourneyEyebrow
import com.homedesign.android.core.ui.components.SerifHeadline
import com.homedesign.android.core.ui.components.UnderlineField
import com.homedesign.android.core.ui.components.UnitChoiceCard
import com.homedesign.android.core.ui.theme.HdTheme
import com.homedesign.android.domain.model.UnitSystem
import com.homedesign.android.domain.settings.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    suspend fun load(): Triple<String, String, UnitSystem> {
        val s = settingsRepository.settings.first()
        return Triple(s.firstName, s.lastName, s.unitSystem)
    }

    fun saveIdentity(first: String, last: String) {
        viewModelScope.launch { settingsRepository.setIdentity(first, last) }
    }

    fun saveUnitSystem(unit: UnitSystem) {
        viewModelScope.launch { settingsRepository.setUnitSystem(unit) }
    }
}

@Composable
fun SetupScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    var step by remember { mutableIntStateOf(1) }
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(UnitSystem.Millimetre) }
    var hydrated by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!hydrated) {
            val (f, l, u) = viewModel.load()
            firstName = f
            lastName = l
            unit = u
            hydrated = true
        }
    }

    val canContinue = step == 2 || firstName.trim().isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HdTheme.colors.paper)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            JourneyBackButton(
                onClick = {
                    if (step == 2) step = 1 else onBack()
                },
            )
            JourneyEyebrow(if (step == 1) "01 / 02" else "02 / 02")
            Spacer(Modifier.padding(start = 48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp),
        ) {
            if (step == 1) {
                SerifHeadline(lead = "What should we", italic = "call you?")
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Text(
                    text = "We'll use this on your designs and exports.",
                    style = HdTheme.typography.bodyMedium,
                    color = HdTheme.colors.stone,
                )
                Spacer(Modifier.height(28.dp))
                UnderlineField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = "First name",
                    tag = "Required",
                    placeholder = "First",
                )
                Spacer(Modifier.height(18.dp))
                UnderlineField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = "Last name",
                    tag = "Optional",
                    placeholder = "Last",
                )
            } else {
                SerifHeadline(lead = "How should we", italic = "measure?")
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.Text(
                    text = "Wall lengths, areas, every measurement. Switch any time with mm / cm / ft in the editor.",
                    style = HdTheme.typography.bodyMedium,
                    color = HdTheme.colors.stone,
                )
                Spacer(Modifier.height(24.dp))
                UnitChoiceCard(
                    title = "Millimetres",
                    sample = "4250 mm · 21.6 m²",
                    detail = "Precision drafting — default",
                    selected = unit == UnitSystem.Millimetre,
                    onClick = {
                        unit = UnitSystem.Millimetre
                        viewModel.saveUnitSystem(UnitSystem.Millimetre)
                    },
                )
                Spacer(Modifier.height(12.dp))
                UnitChoiceCard(
                    title = "Centimetres",
                    sample = "425 cm · 21.6 m²",
                    detail = "Metric lengths and square metres",
                    selected = unit == UnitSystem.Metric,
                    onClick = {
                        unit = UnitSystem.Metric
                        viewModel.saveUnitSystem(UnitSystem.Metric)
                    },
                )
                Spacer(Modifier.height(12.dp))
                UnitChoiceCard(
                    title = "Feet & inches",
                    sample = "14′ 0″ · 232 ft²",
                    detail = "Imperial lengths and square feet",
                    selected = unit == UnitSystem.Imperial,
                    onClick = {
                        unit = UnitSystem.Imperial
                        viewModel.saveUnitSystem(UnitSystem.Imperial)
                    },
                )
            }
        }

        InkPlateButton(
            label = "Continue",
            enabled = canContinue,
            onClick = {
                if (step == 1) {
                    viewModel.saveIdentity(firstName.trim(), lastName.trim())
                    step = 2
                } else {
                    viewModel.saveUnitSystem(unit)
                    onContinue()
                }
            },
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

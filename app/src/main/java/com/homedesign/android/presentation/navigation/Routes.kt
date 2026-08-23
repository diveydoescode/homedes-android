package com.homedesign.android.presentation.navigation

object Routes {
    const val Splash = "splash"
    const val Landing = "landing"
    const val Onboarding = "onboarding"
    const val Setup = "setup"
    const val Auth = "auth"
    const val Dashboard = "dashboard"
    const val Editor = "editor/{id}"
    const val Sketch = "sketch"

    fun editor(id: String) = "editor/$id"
}

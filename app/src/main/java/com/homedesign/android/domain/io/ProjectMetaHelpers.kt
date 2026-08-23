package com.homedesign.android.domain.io

import com.homedesign.android.domain.project.floorAreaM2 as projectFloorAreaM2
import com.homedesign.android.domain.model.Home

/** Re-export for callers that still import from `domain.io`. */
fun floorAreaM2(home: Home): Double = projectFloorAreaM2(home)

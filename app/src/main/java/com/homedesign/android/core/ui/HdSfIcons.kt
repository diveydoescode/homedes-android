package com.homedesign.android.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.homedesign.android.R

/**
 * iOS SF Symbols as Android vector drawables (`res/drawable/sf_*.xml`).
 * Paths exported from SF Symbols — tint at draw time for light/dark chrome.
 */
object HdSfIcons {
    @DrawableRes val chevronBackward = R.drawable.sf_chevron_backward
    @DrawableRes val chevronDown = R.drawable.sf_chevron_down
    @DrawableRes val chevronRight = R.drawable.sf_chevron_right
    @DrawableRes val squareAndArrowUp = R.drawable.sf_square_and_arrow_up
    @DrawableRes val ellipsis = R.drawable.sf_ellipsis
    @DrawableRes val cursorarrow = R.drawable.sf_cursorarrow
    @DrawableRes val ruler = R.drawable.sf_ruler
    @DrawableRes val rulerFill = R.drawable.sf_ruler_fill
    @DrawableRes val plus = R.drawable.sf_plus
    @DrawableRes val chairLounge = R.drawable.sf_chair_lounge
    @DrawableRes val cameraViewfinder = R.drawable.sf_camera_viewfinder
    @DrawableRes val figureWalk = R.drawable.sf_figure_walk
    @DrawableRes val arrowUturnBackward = R.drawable.sf_arrow_uturn_backward
    @DrawableRes val arrowUturnForward = R.drawable.sf_arrow_uturn_forward
    @DrawableRes val rectangle = R.drawable.sf_rectangle
    @DrawableRes val rectangleSplit1x2 = R.drawable.sf_rectangle_split_1x2
    @DrawableRes val cube = R.drawable.sf_cube
    @DrawableRes val arkit = R.drawable.sf_arkit
    @DrawableRes val doorLeftHandOpen = R.drawable.sf_door_left_hand_open
    @DrawableRes val windowVerticalClosed = R.drawable.sf_window_vertical_closed
    @DrawableRes val sofa = R.drawable.sf_sofa
    @DrawableRes val rectangleDashed = R.drawable.sf_rectangle_dashed
    @DrawableRes val cylinder = R.drawable.sf_cylinder
    @DrawableRes val squareSplitBottomrightquarter = R.drawable.sf_square_split_bottomrightquarter
    @DrawableRes val rectangleCompressVertical = R.drawable.sf_rectangle_compress_vertical
    @DrawableRes val rectanglePortraitOnPortrait = R.drawable.sf_rectangle_portrait_on_rectangle_portrait
    @DrawableRes val roadLanes = R.drawable.sf_road_lanes
    @DrawableRes val figureStairs = R.drawable.sf_figure_stairs
    @DrawableRes val rectanglePortrait = R.drawable.sf_rectangle_portrait
    @DrawableRes val rectangleSplit3x3 = R.drawable.sf_rectangle_split_3x3
    @DrawableRes val textformat = R.drawable.sf_textformat
    @DrawableRes val trash = R.drawable.sf_trash
    @DrawableRes val eye = R.drawable.sf_eye
    @DrawableRes val eyeFill = R.drawable.sf_eye_fill
    @DrawableRes val viewfinder = R.drawable.sf_viewfinder
    @DrawableRes val plusSquare = R.drawable.sf_plus_square
    @DrawableRes val plusSquareFill = R.drawable.sf_plus_square_fill
    @DrawableRes val paintbrushFill = R.drawable.sf_paintbrush_fill
    @DrawableRes val paintbrushPointed = R.drawable.sf_paintbrush_pointed
    @DrawableRes val paintbrushPointedFill = R.drawable.sf_paintbrush_pointed_fill
    @DrawableRes val linesMeasurementHorizontal = R.drawable.sf_lines_measurement_horizontal
    @DrawableRes val lineDiagonal = R.drawable.sf_line_diagonal
    @DrawableRes val arrowLeftAndRight = R.drawable.sf_arrow_left_and_right
    @DrawableRes val arrowUpAndDown = R.drawable.sf_arrow_up_and_down
    @DrawableRes val xmarkCircleFill = R.drawable.sf_xmark_circle_fill
    @DrawableRes val locationNorthFill = R.drawable.sf_location_north_fill
    @DrawableRes val wandAndRays = R.drawable.sf_wand_and_rays
    @DrawableRes val lasso = R.drawable.sf_lasso
    @DrawableRes val folder = R.drawable.sf_folder
    @DrawableRes val squareAndPencil = R.drawable.sf_square_and_pencil
    @DrawableRes val cameraAperture = R.drawable.sf_camera_aperture
    @DrawableRes val sunMax = R.drawable.sf_sun_max
    @DrawableRes val sunMaxFill = R.drawable.sf_sun_max_fill
    // Dashboard / New design / journey chrome
    @DrawableRes val magnifyingglass = R.drawable.sf_magnifyingglass
    @DrawableRes val xmark = R.drawable.sf_xmark
    @DrawableRes val squareDashed = R.drawable.sf_square_dashed
    @DrawableRes val squareGrid2x2 = R.drawable.sf_square_grid_2x2
    @DrawableRes val globe = R.drawable.sf_globe
    @DrawableRes val arrowRight = R.drawable.sf_arrow_right
    @DrawableRes val checkmark = R.drawable.sf_checkmark
    @DrawableRes val pencil = R.drawable.sf_pencil
    @DrawableRes val houseFill = R.drawable.sf_house_fill
    @DrawableRes val houseLodgeFill = R.drawable.sf_house_lodge_fill
    @DrawableRes val building2Fill = R.drawable.sf_building_2_fill
    @DrawableRes val buildingColumnsFill = R.drawable.sf_building_columns_fill
    @DrawableRes val personCropCircle = R.drawable.sf_person_crop_circle
    @DrawableRes val ellipsisCircle = R.drawable.sf_ellipsis_circle
    @DrawableRes val photo = R.drawable.sf_photo
    @DrawableRes val photoFill = R.drawable.sf_photo_fill
    @DrawableRes val photoOnRectangle = R.drawable.sf_photo_on_rectangle
    @DrawableRes val cameraFill = R.drawable.sf_camera_fill
    @DrawableRes val square3Layers = R.drawable.sf_square_3_layers
    @DrawableRes val squareAndArrowDown = R.drawable.sf_square_and_arrow_down
}

@Composable
fun SfIcon(
    @DrawableRes id: Int,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 17.dp,
) {
    Icon(
        painter = painterResource(id),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier.size(size),
    )
}

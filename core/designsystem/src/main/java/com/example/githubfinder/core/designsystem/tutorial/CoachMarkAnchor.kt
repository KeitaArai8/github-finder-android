/*
 * Abstract:
 * Composable に付与して、チュートリアル対象UIのRoot基準位置を
 * CoachMarkManager に報告するためのModifier拡張。
 */

package com.example.githubfinder.core.designsystem.tutorial

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot

fun Modifier.coachMarkAnchor(
    step: CoachMarkStep,
    manager: CoachMarkManager,
): Modifier = this.onGloballyPositioned { coordinates ->
    val position = coordinates.positionInRoot()
    val size = coordinates.size
    manager.updateTargetRect(
        step = step,
        rect = Rect(
            offset = position,
            size = Size(size.width.toFloat(), size.height.toFloat()),
        ),
    )
}

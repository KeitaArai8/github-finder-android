/*
 * Abstract:
 * スポットライト型のチュートリアルオーバーレイ。
 * 暗転背景に対象UIの位置だけ穴を空け、パルスリングで強調しつつ吹き出しで説明する。
 */

package com.example.githubfinder.tutorial

import com.example.githubfinder.R
import com.example.githubfinder.core.designsystem.tutorial.CoachMarkStep
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val SpotlightPadding = 6.dp
private val SpotlightCornerRadius = 14.dp
private val TooltipGap = 24.dp

@Composable
fun CoachMarkOverlay(
    step: CoachMarkStep,
    targetRects: Map<CoachMarkStep, Rect>,
    onNext: () -> Unit,
    onSkip: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val screenSize = with(density) { Size(maxWidth.toPx(), maxHeight.toPx()) }
        val paddingPx = with(density) { SpotlightPadding.toPx() }
        val cornerPx = with(density) { SpotlightCornerRadius.toPx() }
        val gapPx = with(density) { TooltipGap.toPx() }

        val rawRect = targetRects[step] ?: fallbackRect(step, screenSize, density)
        val spotlight = rawRect.inflate(paddingPx)

        DimmedBackgroundWithHole(
            spotlight = spotlight,
            cornerRadiusPx = cornerPx,
            onClickAnywhere = onNext,
        )

        PulseRing(
            spotlight = spotlight,
            cornerRadiusPx = cornerPx,
        )

        TooltipCard(
            step = step,
            spotlight = spotlight,
            screenSize = screenSize,
            gapPx = gapPx,
            onNext = onNext,
        )

        SkipButton(
            onSkip = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 16.dp),
        )
    }
}

@Composable
private fun DimmedBackgroundWithHole(
    spotlight: Rect,
    cornerRadiusPx: Float,
    onClickAnywhere: () -> Unit,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClickAnywhere,
            ),
    ) {
        drawRect(color = Color.Black.copy(alpha = 0.75f))
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(spotlight.left, spotlight.top),
            size = Size(spotlight.width, spotlight.height),
            cornerRadius = CornerRadius(cornerRadiusPx),
            blendMode = BlendMode.Clear,
        )
    }
}

@Composable
private fun PulseRing(
    spotlight: Rect,
    cornerRadiusPx: Float,
) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progress",
    )
    val scale = 1f + 0.22f * progress
    val alpha = (1f - progress).coerceIn(0f, 1f)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val centerX = spotlight.center.x
        val centerY = spotlight.center.y
        val w = spotlight.width * scale
        val h = spotlight.height * scale
        drawRoundRect(
            color = Color.White.copy(alpha = alpha),
            topLeft = Offset(centerX - w / 2, centerY - h / 2),
            size = Size(w, h),
            cornerRadius = CornerRadius(cornerRadiusPx),
            style = Stroke(width = 3.dp.toPx()),
        )
    }
}

@Composable
private fun TooltipCard(
    step: CoachMarkStep,
    spotlight: Rect,
    screenSize: Size,
    gapPx: Float,
    onNext: () -> Unit,
) {
    val density = LocalDensity.current
    val isUpperHalf = spotlight.center.y < screenSize.height / 2
    val tooltipTopPx = if (isUpperHalf) spotlight.bottom + gapPx else null
    val bottomPaddingDp = if (!isUpperHalf) {
        with(density) { (screenSize.height - spotlight.top + gapPx).toDp() }
    } else null

    val tooltipModifier = if (tooltipTopPx != null) {
        Modifier
            .offset { IntOffset(0, tooltipTopPx.roundToInt()) }
            .padding(horizontal = 24.dp)
            .fillMaxWidth()
    } else {
        Modifier
            .padding(start = 24.dp, end = 24.dp, bottom = bottomPaddingDp ?: 0.dp)
            .fillMaxWidth()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (isUpperHalf) Alignment.TopStart else Alignment.BottomStart,
    ) {
        Card(
            modifier = tooltipModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(tutorialTitleRes(step)),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(tutorialDescriptionRes(step)),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StepIndicator(currentStep = step)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = onNext,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                        ),
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (step == CoachMarkStep.entries.last()) R.string.tutorial_finish
                                else R.string.tutorial_next
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SkipButton(onSkip: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(
        onClick = onSkip,
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Text(
            stringResource(R.string.tutorial_skip),
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun tutorialTitleRes(step: CoachMarkStep): Int = when (step) {
    CoachMarkStep.SearchBar -> R.string.tutorial_step_search_title
    CoachMarkStep.FavoriteButton -> R.string.tutorial_step_favorite_title
    CoachMarkStep.FavoriteTab -> R.string.tutorial_step_tab_title
}

private fun tutorialDescriptionRes(step: CoachMarkStep): Int = when (step) {
    CoachMarkStep.SearchBar -> R.string.tutorial_step_search_description
    CoachMarkStep.FavoriteButton -> R.string.tutorial_step_favorite_description
    CoachMarkStep.FavoriteTab -> R.string.tutorial_step_tab_description
}

@Composable
private fun StepIndicator(currentStep: CoachMarkStep) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        CoachMarkStep.entries.forEach { step ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (step == currentStep) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    ),
            )
        }
    }
}

private fun fallbackRect(
    step: CoachMarkStep,
    screen: Size,
    density: androidx.compose.ui.unit.Density,
): Rect = with(density) {
    when (step) {
        CoachMarkStep.SearchBar -> Rect(
            offset = Offset(16.dp.toPx(), 88.dp.toPx()),
            size = Size(screen.width - 32.dp.toPx(), 48.dp.toPx()),
        )
        CoachMarkStep.FavoriteButton -> Rect(
            offset = Offset(screen.width - 72.dp.toPx(), 200.dp.toPx()),
            size = Size(48.dp.toPx(), 48.dp.toPx()),
        )
        CoachMarkStep.FavoriteTab -> {
            val tabWidth = screen.width / 3f
            Rect(
                offset = Offset(tabWidth, screen.height - 96.dp.toPx()),
                size = Size(tabWidth, 64.dp.toPx()),
            )
        }
    }
}


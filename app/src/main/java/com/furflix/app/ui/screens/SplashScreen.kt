package com.furflix.app.ui.screens

import androidx.compose.material3.MaterialTheme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.furflix.app.R
import com.furflix.app.ui.components.Particle
import com.furflix.app.ui.theme.Cyan
import com.furflix.app.ui.theme.Pink
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    repository: com.furflix.app.data.repository.FurRepository? = null
) {
    val pawScaleAnim = remember { Animatable(0f) }
    val pawBounceY = remember { Animatable(0f) }
    var glowAlpha by remember { mutableFloatStateOf(0f) }
    var titleAlpha by remember { mutableFloatStateOf(0f) }

    var showLandingBurst by remember { mutableStateOf(false) }
    val landingBurstProgress = remember { Animatable(0f) }

    // 今日站点横幅：先查当日缓存（秒回），未命中按展示策略取数；
    // 动画照常进行，横幅在展示窗口内到达则淡入并小幅延长启动页
    var bannerUrl by remember { mutableStateOf<String?>(null) }
    var bannerFetchJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    // 横幅展示策略（设置→外观）：联网刷新开关 + 横屏上/下 + 竖屏左/右
    val bannerRefresh by (repository?.splashBannerRefreshFlow
        ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(initial = true)
    val bannerLandscapeTop by (repository?.splashBannerLandscapeTopFlow
        ?: kotlinx.coroutines.flow.flowOf(true)).collectAsState(initial = true)
    val bannerPortraitRight by (repository?.splashBannerPortraitRightFlow
        ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(initial = false)

    val density = LocalDensity.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val landingParticles = remember(primaryColor) {
        val colors = listOf(primaryColor, Cyan, Pink, Color.White)
        List(36) { i ->
            val angle = (i * (360f / 36) + Random.nextInt(-5, 5)).toFloat() * (Math.PI / 180f).toFloat()
            Particle(
                id = i,
                angle = angle,
                distance = Random.nextFloat() * 110f + 70f,
                size = Random.nextFloat() * 5f + 3f,
                color = colors[Random.nextInt(colors.size)]
            )
        }
    }

    LaunchedEffect(Unit) {
        if (repository != null) {
            bannerFetchJob = launch {
                // 当日缓存未命中时：开=联网刷新；关=只用本地缓存（可为隔日旧图，零流量）
                val url = repository.loadSiteBanner()
                    ?: if (bannerRefresh) repository.refreshSiteBanner()
                    else repository.loadSiteBannerCached()
                if (!url.isNullOrEmpty()) bannerUrl = url
            }
        }
        pawScaleAnim.animateTo(1f, spring(dampingRatio = 0.55f, stiffness = 350f))
        glowAlpha = 0.6f
        delay(250)
        titleAlpha = 1f
        delay(150)
        // Quick hop up
        pawBounceY.animateTo(-16f, tween(135, easing = FastOutSlowInEasing))
        // Come down — burst fires just as paw hits bottom
        launch {
            delay(20)
            showLandingBurst = true
            landingBurstProgress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
        pawBounceY.animateTo(0f, tween(200, easing = FastOutSlowInEasing))
        delay(660)
        // 横幅展示窗口：最多再等 700ms（缓存命中时瞬时完成）；出现后停留 500ms，
        // 抓取失败/超时不阻塞启动（启动页总延长 ≤1.2s）
        bannerFetchJob?.let { job ->
            withTimeoutOrNull(700) { job.join() }
        }
        if (bannerUrl != null) {
            delay(500)
        }
        onSplashFinished()
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (showLandingBurst && landingBurstProgress.value < 1f) {
            val progress = landingBurstProgress.value
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f - with(density) { 18.dp.toPx() }
                val alpha = if (progress > 0.5f) (1f - progress) * 2f else 1f

                landingParticles.forEach { p ->
                    val currentDist = p.distance * (1f - (1f - progress) * (1f - progress))
                    drawCircle(
                        color = p.color.copy(alpha = alpha),
                        radius = p.size * (1f - progress * 0.3f),
                        center = Offset(
                            x = cx + cos(p.angle) * currentDist,
                            y = cy + sin(p.angle) * currentDist
                        ),
                        style = Fill
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .graphicsLayer { translationY = pawBounceY.value },
                contentAlignment = Alignment.Center
            ) {
                GlowCircle(
                    modifier = Modifier.size(80.dp).alpha(glowAlpha)
                )
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp).scale(pawScaleAnim.value)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "FurFlix",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.alpha(titleAlpha)
            )
        }

        // 今日站点横幅（FA site-banner，1850x300）：完整显示不裁切；
        // 横屏置顶/底部条带，竖屏旋转 90° 沿左/右缘整条展示（设置→外观可调）
        bannerUrl?.let { url ->
            val bannerAlpha by animateFloatAsState(targetValue = 1f, label = "bannerAlpha")
            val isLandscape = LocalConfiguration.current.orientation ==
                android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val bannerAspect = 1850f / 300f
            if (isLandscape) {
                // 横屏：全宽条带，高度按原始比例推算 → 整图完整可见
                val stripHeight = (maxWidth - 32.dp) / bannerAspect
                coil3.compose.AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.cd_site_banner),
                    modifier = Modifier
                        .align(if (bannerLandscapeTop) Alignment.TopCenter else Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 24.dp)
                        .fillMaxWidth()
                        .height(stripHeight)
                        .clip(RoundedCornerShape(14.dp))
                        .alpha(bannerAlpha),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            } else {
                // 竖屏：长轴旋转为纵向贴边展示；requiredSize 允许超出父约束布局，
                // 旋转后视觉条带完整落在屏幕内，translationX 平移到左/右缘
                val stripLen = maxHeight - 48.dp
                val stripThick = stripLen / bannerAspect
                val targetCenterX = if (bannerPortraitRight) {
                    maxWidth - 8.dp - stripThick / 2
                } else {
                    8.dp + stripThick / 2
                }
                coil3.compose.AsyncImage(
                    model = url,
                    contentDescription = stringResource(R.string.cd_site_banner),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .requiredSize(width = stripLen, height = stripThick)
                        .graphicsLayer {
                            rotationZ = if (bannerPortraitRight) 90f else 270f
                            translationX = (targetCenterX - maxWidth / 2).toPx()
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .alpha(bannerAlpha),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        }
    }
}

@Composable
private fun GlowCircle(modifier: Modifier = Modifier) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.4f),
                    primaryColor.copy(alpha = 0.1f),
                    Color.Transparent
                )
            ),
            radius = radius
        )
    }
}

package com.glowseed.noctdock.core

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

enum class BackgroundSurface {
    Handheld,
    Dock,
    Tv,
}

data class AmbientParticle(
    val baseX: Float,
    val baseY: Float,
    val cycleX: Int,
    val cycleY: Int,
    val wobbleX: Float,
    val wobbleY: Float,
    val phaseOffset: Float,
    val size: Float,
    val alpha: Float,
    val depth: Float,
)

data class AmbientParticlePosition(val x: Float, val y: Float, val alpha: Float)

data class BackgroundRuntimeConfig(val animated: Boolean, val particleCount: Int, val glowAlpha: Float, val driftSpeedMultiplier: Float, val showDockOrb: Boolean)

object AmbientParticleField {
    fun generate(count: Int, seed: Int): List<AmbientParticle> {
        val random = Random(seed)
        val safeCount = count.coerceIn(0, 120)
        return List(safeCount) {
            val cycleX = random.nextCycle()
            val cycleY = random.nextCycle(allowZero = cycleX != 0)
            AmbientParticle(
                baseX = random.nextFloat(),
                baseY = random.nextFloat(),
                cycleX = cycleX,
                cycleY = cycleY,
                wobbleX = random.nextFloatIn(0.002f, 0.018f),
                wobbleY = random.nextFloatIn(0.002f, 0.016f),
                phaseOffset = random.nextFloat(),
                size = random.nextFloatIn(0.65f, 1.85f),
                alpha = random.nextFloatIn(0.10f, 0.34f),
                depth = random.nextFloatIn(0.55f, 1.0f),
            )
        }
    }

    fun position(particle: AmbientParticle, phase: Float): AmbientParticlePosition {
        val normalizedPhase = wrap01(phase)
        val wave = sin((normalizedPhase + particle.phaseOffset) * TWO_PI)
        val counterWave = sin((normalizedPhase * 0.73f + particle.phaseOffset) * TWO_PI)
        return AmbientParticlePosition(
            x = wrap01(particle.baseX + particle.cycleX * normalizedPhase + wave * particle.wobbleX * particle.depth),
            y = wrap01(particle.baseY + particle.cycleY * normalizedPhase + counterWave * particle.wobbleY * particle.depth),
            alpha = (particle.alpha * (0.86f + 0.14f * wave)).coerceIn(0f, 1f),
        )
    }

    fun wrap01(value: Float): Float {
        val remainder = value % 1f
        return if (remainder < 0f) remainder + 1f else remainder
    }

    private fun Random.nextCycle(allowZero: Boolean = true): Int {
        val options = if (allowZero) intArrayOf(-1, 0, 1) else intArrayOf(-1, 1)
        return options[nextInt(options.size)]
    }

    private fun Random.nextFloatIn(min: Float, max: Float): Float = min + nextFloat() * (max - min)

    private const val TWO_PI = (PI * 2.0).toFloat()
}

object BackgroundAmbiencePolicy {
    fun effectiveModeLabel(motionMode: BackgroundMotionMode, reducedMotion: Boolean): String = if (reducedMotion) {
        "Reduced Motion"
    } else {
        AppearanceDefaults.backgroundMotionLabel(motionMode)
    }

    fun config(motionMode: BackgroundMotionMode, reducedMotion: Boolean, batterySaver: Boolean, surface: BackgroundSurface): BackgroundRuntimeConfig {
        if (reducedMotion) {
            return BackgroundRuntimeConfig(
                animated = false,
                particleCount = 0,
                glowAlpha = if (surface == BackgroundSurface.Dock) 0.035f else 0.055f,
                driftSpeedMultiplier = 0f,
                showDockOrb = surface == BackgroundSurface.Dock,
            )
        }

        val base =
            when (surface) {
                BackgroundSurface.Handheld ->
                    when (motionMode) {
                        BackgroundMotionMode.AnimatedNebula -> BackgroundRuntimeConfig(true, 58, 0.12f, 1f, false)
                        BackgroundMotionMode.MinimalDrift -> BackgroundRuntimeConfig(true, 28, 0.085f, 0.55f, false)
                        BackgroundMotionMode.DeepSpace -> BackgroundRuntimeConfig(false, 8, 0.045f, 0f, false)
                    }

                BackgroundSurface.Dock ->
                    when (motionMode) {
                        BackgroundMotionMode.AnimatedNebula -> BackgroundRuntimeConfig(true, 6, 0.045f, 0.32f, true)
                        BackgroundMotionMode.MinimalDrift -> BackgroundRuntimeConfig(true, 4, 0.038f, 0.22f, true)
                        BackgroundMotionMode.DeepSpace -> BackgroundRuntimeConfig(false, 0, 0.028f, 0f, true)
                    }

                BackgroundSurface.Tv ->
                    when (motionMode) {
                        BackgroundMotionMode.AnimatedNebula -> BackgroundRuntimeConfig(true, 96, 0.14f, 0.8f, false)
                        BackgroundMotionMode.MinimalDrift -> BackgroundRuntimeConfig(true, 48, 0.10f, 0.45f, false)
                        BackgroundMotionMode.DeepSpace -> BackgroundRuntimeConfig(false, 10, 0.05f, 0f, false)
                    }
            }

        if (!batterySaver) return base

        val saverCount =
            when (surface) {
                BackgroundSurface.Handheld -> base.particleCount.coerceAtMost(18)
                BackgroundSurface.Dock -> base.particleCount.coerceAtMost(3)
                BackgroundSurface.Tv -> base.particleCount.coerceAtMost(20)
            }
        return base.copy(
            animated = base.animated && motionMode != BackgroundMotionMode.DeepSpace,
            particleCount = saverCount,
            glowAlpha = base.glowAlpha * 0.58f,
            driftSpeedMultiplier = base.driftSpeedMultiplier * 0.42f,
        )
    }
}

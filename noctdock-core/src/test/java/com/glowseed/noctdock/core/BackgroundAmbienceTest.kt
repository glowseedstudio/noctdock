package com.glowseed.noctdock.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundAmbienceTest {
    @Test
    fun particleGenerationIsDeterministic() {
        val first = AmbientParticleField.generate(count = 32, seed = 42)
        val second = AmbientParticleField.generate(count = 32, seed = 42)

        assertEquals(first, second)
    }

    @Test
    fun particleGenerationKeepsValuesValid() {
        val particles = AmbientParticleField.generate(count = 160, seed = 7)

        assertEquals(120, particles.size)
        particles.forEach { particle ->
            assertTrue(particle.baseX in 0f..1f)
            assertTrue(particle.baseY in 0f..1f)
            assertTrue(particle.size > 0f)
            assertTrue(particle.alpha in 0f..1f)
            assertTrue(particle.depth in 0f..1f)
            assertTrue(particle.cycleX in -1..1)
            assertTrue(particle.cycleY in -1..1)
            assertFalse(particle.cycleX == 0 && particle.cycleY == 0)
        }
    }

    @Test
    fun particleWrapKeepsPositionsOnScreen() {
        val particle =
            AmbientParticle(
                baseX = 0.92f,
                baseY = 0.04f,
                cycleX = 1,
                cycleY = -1,
                wobbleX = 0.01f,
                wobbleY = 0.01f,
                phaseOffset = 0.25f,
                size = 1f,
                alpha = 0.2f,
                depth = 0.8f,
            )

        val position = AmbientParticleField.position(particle, phase = 1.35f)

        assertTrue(position.x in 0f..1f)
        assertTrue(position.y in 0f..1f)
        assertTrue(position.alpha in 0f..1f)
    }

    @Test
    fun seamlessCycleReturnsParticleToStart() {
        val particle = AmbientParticleField.generate(count = 1, seed = 99).single()

        val start = AmbientParticleField.position(particle, phase = 0f)
        val end = AmbientParticleField.position(particle, phase = 1f)

        assertEquals(start.x, end.x, 0.0001f)
        assertEquals(start.y, end.y, 0.0001f)
        assertEquals(start.alpha, end.alpha, 0.0001f)
    }

    @Test
    fun reducedMotionDisablesBackgroundAnimationAndParticles() {
        val config =
            BackgroundAmbiencePolicy.config(
                motionMode = BackgroundMotionMode.AnimatedNebula,
                reducedMotion = true,
                batterySaver = false,
                surface = BackgroundSurface.Handheld,
            )

        assertFalse(config.animated)
        assertEquals(0, config.particleCount)
        assertEquals(
            "Reduced Motion",
            BackgroundAmbiencePolicy.effectiveModeLabel(BackgroundMotionMode.AnimatedNebula, true),
        )
    }

    @Test
    fun batterySaverCapsHandheldParticles() {
        val normal =
            BackgroundAmbiencePolicy.config(
                motionMode = BackgroundMotionMode.AnimatedNebula,
                reducedMotion = false,
                batterySaver = false,
                surface = BackgroundSurface.Handheld,
            )
        val saver =
            BackgroundAmbiencePolicy.config(
                motionMode = BackgroundMotionMode.AnimatedNebula,
                reducedMotion = false,
                batterySaver = true,
                surface = BackgroundSurface.Handheld,
            )

        assertTrue(normal.particleCount > saver.particleCount)
        assertTrue(saver.particleCount in 0..20)
        assertTrue(saver.glowAlpha < normal.glowAlpha)
        assertTrue(saver.driftSpeedMultiplier < normal.driftSpeedMultiplier)
    }

    @Test
    fun dockModeUsesUltraCheapAmbience() {
        val config =
            BackgroundAmbiencePolicy.config(
                motionMode = BackgroundMotionMode.AnimatedNebula,
                reducedMotion = false,
                batterySaver = true,
                surface = BackgroundSurface.Dock,
            )

        assertTrue(config.particleCount <= 3)
        assertTrue(config.showDockOrb)
        assertTrue(config.glowAlpha < 0.04f)
    }
}

package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMotionTest {

    @Test
    fun wateringAndRainHaveTheSameFramesInEveryLight() {
        val count = CropMotion.water(CropArt.DAY).size
        assertTrue("watering must last past the wet fade", count > CropMotion.WET_TO)
        for (light in CropArt.LIGHTS) {
            assertEquals(light, count, CropMotion.water(light).size)
            assertEquals(light, count, CropMotion.rain(light).size)
        }
    }

    @Test
    fun theWetFadeHappensWhileWatering() {
        assertTrue(CropMotion.WET_FROM in 0 until CropMotion.WET_TO)
    }

    @Test
    fun everyCropIsHarvestedFromItsOwnStages() {
        assertEquals(CropArt.STAGES.keys, CropMotion.HARVEST_STAGES.keys)
        for ((crop, stages) in CropMotion.HARVEST_STAGES) {
            assertTrue(crop, stages.isNotEmpty())
            assertTrue(crop, CropArt.STAGES.getValue(crop).containsAll(stages))
        }
    }

    @Test
    fun hasAHarvestOfTheSameLengthForEveryCropStageAndLight() {
        val count = CropMotion.harvest("radish", "ripe", CropArt.DAY).size
        assertTrue(count > 0)
        for ((crop, stages) in CropMotion.HARVEST_STAGES) {
            for (stage in stages) {
                for (light in CropArt.LIGHTS) {
                    assertEquals("$crop $stage $light", count, CropMotion.harvest(crop, stage, light).size)
                }
            }
        }
    }

    @Test
    fun aCropThatIsNotReadyRipensBeforeItsHarvest() {
        assertEquals("ripe", CropMotion.harvestedAs("carrot", "growing"))
        assertEquals("ripe", CropMotion.harvestedAs("carrot", "ripe"))
        assertEquals("overripe", CropMotion.harvestedAs("carrot", "overripe"))
        assertEquals("bloom", CropMotion.harvestedAs("sunflower", "sprout"))
    }
}

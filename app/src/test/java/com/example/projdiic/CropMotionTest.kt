package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropMotionTest {

    @Test
    fun wateringAndRainHaveTheSameFrames() {
        val count = CropMotion.water().size
        assertTrue("watering must last past the wet fade", count > CropMotion.WET_TO)
        assertEquals(count, CropMotion.rain().size)
    }

    @Test
    fun theWetFadeHappensWhileWatering() {
        assertTrue(CropMotion.WET_FROM in 0 until CropMotion.WET_TO)
    }

    @Test
    fun everyCropIsPlantedFromItsPacketWhileTheBedFadesToItsSeeds() {
        val count = CropMotion.plant("radish").size
        assertTrue("planting must last past the seed fade", count > CropMotion.SEED_TO)
        assertTrue(CropMotion.SEED_FROM in 0 until CropMotion.SEED_TO)
        for (crop in CropArt.STAGES.keys) {
            assertTrue(crop, "seed" in CropArt.STAGES.getValue(crop))
            assertEquals(crop, count, CropMotion.plant(crop).size)
        }
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
    fun hasAHarvestOfTheSameLengthForEveryCropAndStage() {
        val count = CropMotion.harvest("radish", "ripe").size
        assertTrue(count > 0)
        for ((crop, stages) in CropMotion.HARVEST_STAGES) {
            for (stage in stages) {
                assertEquals("$crop $stage", count, CropMotion.harvest(crop, stage).size)
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

package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropArtTest {

    @Test
    fun picksTheLightByTheHour() {
        assertEquals(CropArt.NIGHT, CropArt.lightAt(4))
        assertEquals(CropArt.MORNING, CropArt.lightAt(5))
        assertEquals(CropArt.MORNING, CropArt.lightAt(10))
        assertEquals(CropArt.DAY, CropArt.lightAt(11))
        assertEquals(CropArt.DAY, CropArt.lightAt(16))
        assertEquals(CropArt.DUSK, CropArt.lightAt(17))
        assertEquals(CropArt.NIGHT, CropArt.lightAt(20))
        assertEquals(CropArt.NIGHT, CropArt.lightAt(0))
    }

    @Test
    fun hasAnImageForEveryStageWateredAndDryInEveryLight() {
        for ((crop, stages) in CropArt.STAGES) {
            for (stage in stages) {
                for (wet in listOf(true, false)) {
                    for (light in CropArt.LIGHTS) {
                        assertNotEquals("$crop $stage", 0, CropArt.bed(crop, stage, wet, light))
                    }
                }
            }
        }
    }

    @Test
    fun hasASeedPacketForEveryCrop() {
        val packets = CropArt.STAGES.keys.map { CropArt.seedPacket(it) }
        assertTrue(packets.none { it == 0 })
        assertEquals("each crop has its own packet", packets.size, packets.toSet().size)
    }

    @Test
    fun hasAnEmptyBedWateredAndDryInEveryLight() {
        for (wet in listOf(true, false)) {
            for (light in CropArt.LIGHTS) {
                assertNotEquals(0, CropArt.emptyBed(wet, light))
            }
        }
    }
}

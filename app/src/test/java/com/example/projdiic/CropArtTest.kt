package com.example.projdiic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CropArtTest {

    @Test
    fun hasAnImageForEveryStageWateredAndDry() {
        for ((crop, stages) in CropArt.STAGES) {
            for (stage in stages) {
                for (wet in listOf(true, false)) {
                    assertNotEquals("$crop $stage", 0, CropArt.bed(crop, stage, wet))
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
    fun hasAnEmptyBedWateredAndDry() {
        assertNotEquals(0, CropArt.emptyBed(true))
        assertNotEquals(0, CropArt.emptyBed(false))
        assertNotEquals(CropArt.emptyBed(true), CropArt.emptyBed(false))
    }
}

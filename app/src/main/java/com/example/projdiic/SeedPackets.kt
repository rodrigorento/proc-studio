package com.example.projdiic

import java.util.TimeZone

/** The seed packets offered when planting a task: one per time it can be for, each growing its own crop. */
object SeedPackets {
    /** A time choice ([window], null for Anytime), the crop it grows, and whether it is the time said in the task. */
    data class Packet(val window: TimeWindow?, val crop: String, val said: Boolean = false)

    /** A task about to be planted: its [title] without the time said in it, its [packets], and the one [picked] (-1: none yet). */
    data class Planting(val title: String, val packets: List<Packet>, val picked: Int)

    /** What planting [text] (typed or said) offers at [now], or null when it has no title, only a time. */
    fun planting(text: String, now: Long, zone: TimeZone = TimeZone.getDefault()): Planting? {
        val parsed = WhenParser.parse(text, now, zone)
        val title = TaskRules.cleanTitle(parsed.title) ?: return null
        val packets = forTask(parsed.window, now, zone)
        return Planting(title, packets, packets.indexOfFirst { it.said })
    }

    /**
     * [packet] chosen at [now]: a time chip starts again from now (the packets were left open past midnight, say),
     * while a time said and Anytime stay as they are. A chip that is gone by now (Tonight, late) stays too.
     */
    fun current(packet: Packet, now: Long, zone: TimeZone = TimeZone.getDefault()): Packet {
        if (packet.said || packet.window == null) return packet
        val chip = WhenChoices.chips(now, zone).firstOrNull { it?.key == packet.window.key } ?: return packet
        return Packet(chip, FarmRules.crop(chip, now, zone))
    }

    /**
     * The packets for a task at [now]: the time [said] in it first, then the time chips from soonest, Anytime last.
     * A time said that is one of the chips ("this month") is that chip, marked as said.
     */
    fun forTask(said: TimeWindow?, now: Long, zone: TimeZone = TimeZone.getDefault()): List<Packet> {
        val chips = WhenChoices.chips(now, zone).filterNotNull()
        val saidChip = said != null && chips.any { it.key == said.key }
        val packets = chips.map { chip ->
            if (saidChip && chip.key == said?.key) Packet(said, FarmRules.crop(said, now, zone), said = true)
            else Packet(chip, FarmRules.crop(chip, now, zone))
        } + Packet(null, FarmRules.SUNFLOWER)
        return if (said == null || saidChip) packets else listOf(Packet(said, FarmRules.crop(said, now, zone), said = true)) + packets
    }
}

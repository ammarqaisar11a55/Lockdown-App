package com.example.focuslock.core

import com.example.focuslock.core.time.DurationFormatter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class DurationFormatterTest {
    @Test
    fun `clock format`() {
        assertEquals("02:47:18", DurationFormatter.clock(Duration.ofHours(2).plusMinutes(47).plusSeconds(18)))
        assertEquals("00:00:00", DurationFormatter.clock(Duration.ofSeconds(-5)))
        assertEquals("25:00:00", DurationFormatter.clock(Duration.ofHours(25)))
    }

    @Test
    fun `short format`() {
        assertEquals("4h 12m", DurationFormatter.short(Duration.ofMinutes(252)))
        assertEquals("45m", DurationFormatter.short(Duration.ofMinutes(45)))
        assertEquals("5h 00m", DurationFormatter.short(Duration.ofHours(5)))
    }

    @Test
    fun `spoken format`() {
        assertEquals("2 hours 47 minutes", DurationFormatter.spoken(Duration.ofMinutes(167)))
        assertEquals("1 hour", DurationFormatter.spoken(Duration.ofHours(1)))
        assertEquals("1 minute", DurationFormatter.spoken(Duration.ofSeconds(90)))
        assertEquals("less than a minute", DurationFormatter.spoken(Duration.ofSeconds(30)))
    }
}

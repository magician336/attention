package com.attention.app.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class AgendaBackfillFormTest {

    @Test
    fun default_uses_the_planned_duration_and_keeps_the_five_minute_step() {
        val form = AgendaBackfillForm()

        form.initialize(45)

        assertEquals(45, form.totalMinutes())
        assertNull(form.validate())
    }

    @Test
    fun duration_must_be_at_least_five_minutes_and_use_five_minute_steps() {
        val form = AgendaBackfillForm()
        form.initialize(15)

        form.changeHours("0")
        form.changeMinutes("3")
        assertNotNull(form.validate())

        form.changeMinutes("0")
        assertNotNull(form.validate())

        form.changeMinutes("5")
        assertNull(form.validate())
        assertEquals(5, form.totalMinutes())
    }
}

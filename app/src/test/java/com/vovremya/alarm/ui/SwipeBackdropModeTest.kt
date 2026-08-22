package com.vovremya.alarm.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SwipeBackdropModeTest {
    @Test
    fun `normal reveal shows action buttons without full swipe hint`() {
        assertEquals(
            SwipeBackdropMode.ACTIONS,
            swipeBackdropMode(hasPendingAction = false),
        )
    }

    @Test
    fun `long swipe keeps action buttons until it is released`() {
        assertEquals(
            SwipeBackdropMode.ACTIONS,
            swipeBackdropMode(hasPendingAction = false),
        )
    }

    @Test
    fun `confirmation replaces both buttons and full swipe hint`() {
        assertEquals(
            SwipeBackdropMode.CONFIRMATION,
            swipeBackdropMode(hasPendingAction = true),
        )
    }
}

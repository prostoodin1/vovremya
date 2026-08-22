package com.vovremya.alarm.ui.theme

import androidx.compose.ui.graphics.Color
import com.vovremya.alarm.data.AccentTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `every accent preview is fully opaque`() {
        AccentTheme.entries.forEach { accent ->
            assertEquals(1f, accent.previewColor().alpha, 0f)
        }
    }

    @Test
    fun `custom accent uses the selected argb color`() {
        val selected = 0xFFC03A8B.toInt()

        assertEquals(Color(selected), AccentTheme.CUSTOM.previewColor(selected))
    }
}

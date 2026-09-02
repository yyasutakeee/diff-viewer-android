package com.example.diffviewer

import android.content.SharedPreferences
import com.example.diffviewer.core.diffui.DefaultDiffColorPalette
import com.example.diffviewer.core.diffui.DiffColorPalette
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DiffDisplaySettingsStore(
    private val sharedPreferences: SharedPreferences,
) {
    private val mutableColorPalette = MutableStateFlow(loadColorPalette())
    private val mutableLineWrappingEnabled = MutableStateFlow(loadLineWrappingEnabled())

    val colorPalette: StateFlow<DiffColorPalette> = mutableColorPalette.asStateFlow()
    val lineWrappingEnabled: StateFlow<Boolean> = mutableLineWrappingEnabled.asStateFlow()

    fun toggleLineWrapping() {
        val enabled = !mutableLineWrappingEnabled.value
        sharedPreferences.edit().putBoolean(LINE_WRAPPING_ENABLED_KEY, enabled).apply()
        mutableLineWrappingEnabled.value = enabled
    }

    fun updateColorPalette(diffColorPalette: DiffColorPalette) {
        sharedPreferences.edit()
            .putInt(ADDITION_BACKGROUND_KEY, diffColorPalette.additionBackgroundArgb)
            .putInt(ADDITION_TEXT_KEY, diffColorPalette.additionTextArgb)
            .putInt(DELETION_BACKGROUND_KEY, diffColorPalette.deletionBackgroundArgb)
            .putInt(DELETION_TEXT_KEY, diffColorPalette.deletionTextArgb)
            .apply()
        mutableColorPalette.value = diffColorPalette
    }

    private fun loadColorPalette(): DiffColorPalette {
        return DiffColorPalette(
            additionBackgroundArgb = sharedPreferences.getInt(
                ADDITION_BACKGROUND_KEY,
                DefaultDiffColorPalette.additionBackgroundArgb,
            ),
            additionTextArgb = sharedPreferences.getInt(
                ADDITION_TEXT_KEY,
                DefaultDiffColorPalette.additionTextArgb,
            ),
            deletionBackgroundArgb = sharedPreferences.getInt(
                DELETION_BACKGROUND_KEY,
                DefaultDiffColorPalette.deletionBackgroundArgb,
            ),
            deletionTextArgb = sharedPreferences.getInt(
                DELETION_TEXT_KEY,
                DefaultDiffColorPalette.deletionTextArgb,
            ),
        )
    }

    private fun loadLineWrappingEnabled(): Boolean {
        return sharedPreferences.getBoolean(LINE_WRAPPING_ENABLED_KEY, true)
    }

    private companion object {
        const val ADDITION_BACKGROUND_KEY = "diff_addition_background_argb"
        const val ADDITION_TEXT_KEY = "diff_addition_text_argb"
        const val DELETION_BACKGROUND_KEY = "diff_deletion_background_argb"
        const val DELETION_TEXT_KEY = "diff_deletion_text_argb"
        const val LINE_WRAPPING_ENABLED_KEY = "diff_line_wrapping_enabled"
    }
}

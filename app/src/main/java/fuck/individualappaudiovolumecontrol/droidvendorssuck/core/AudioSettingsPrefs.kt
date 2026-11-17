package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context

class AudioSettingsPrefs(context: Context) {
    private val store = DeferredPreferenceStore(context, PREFS_NAME)

    fun isAutoResumeEnabled(): Boolean {
        return store.getBoolean(KEY_AUTO_RESUME, true)
    }

    fun setAutoResumeEnabled(enabled: Boolean) {
        store.update { put(KEY_AUTO_RESUME, enabled) }
    }

    /**
     * "Reduce effects" (default ON): when enabled the liquid-glass surfaces use
     * a plain semi-transparent fill (no blur). Turn it off on API 31+ to enable
     * the RenderEffect backdrop blur behind in-app panels and the overlay
     * window's blur-behind.
     */
    fun isReduceEffectsEnabled(): Boolean {
        return store.getBoolean(KEY_REDUCE_EFFECTS, true)
    }

    fun setReduceEffectsEnabled(enabled: Boolean) {
        store.update { put(KEY_REDUCE_EFFECTS, enabled) }
    }

    companion object {
        private const val PREFS_NAME = "audio_settings"
        private const val KEY_AUTO_RESUME = "auto_resume"
        private const val KEY_REDUCE_EFFECTS = "reduce_effects"
    }
}

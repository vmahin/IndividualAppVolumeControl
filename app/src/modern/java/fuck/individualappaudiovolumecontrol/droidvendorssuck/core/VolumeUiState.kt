package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

/**
 * Shared, process-wide per-app volume UI state used by the main-screen list,
 * the volume overlay and the controller service so all three surfaces agree.
 *
 * The persisted per-app target volume lives in [VolumeStore] (the single source
 * of truth every surface reads and writes). This object adds the two pieces of
 * state that the store cannot hold:
 *
 *  - [touching]: which volume rows a user is currently dragging. The periodic
 *    list refreshes skip those rows so a rebuild can never destroy a slider
 *    mid-drag or write a stale value back into it.
 *  - [realPercent]: the last volume actually read back from the player after an
 *    apply (0-100). When an apply fails or the app changed its own volume, the
 *    sliders show this real value instead of the un-applied target, so the UI
 *    reflects reality and never silently disagrees with it.
 *
 * All access is synchronized; the controller service writes [setRealPercent]
 * from its apply worker thread while the UI reads it on the main thread.
 */
object VolumeUiState {
    private val lock = Any()
    private val touching = mutableSetOf<String>()
    private val realPercent = mutableMapOf<String, Int>()

    /** Marks a volume row (by [VolumeStore.VolumeKey.storageKey]) as being dragged or released. */
    fun setTouching(storageKey: String, active: Boolean) {
        synchronized(lock) {
            if (active) touching.add(storageKey) else touching.remove(storageKey)
        }
    }

    fun isTouching(storageKey: String): Boolean = synchronized(lock) { storageKey in touching }

    /** True while any slider anywhere (main screen or overlay) is being dragged. */
    fun anyTouching(): Boolean = synchronized(lock) { touching.isNotEmpty() }

    /** Records the volume (0-100) actually read back from the player after an apply. */
    fun setRealPercent(storageKey: String, percent: Int) {
        synchronized(lock) { realPercent[storageKey] = percent }
    }

    /** Last read-back real volume for a row, or null if no read-back happened yet. */
    fun realPercentOrNull(storageKey: String): Int? = synchronized(lock) { realPercent[storageKey] }

    fun clearRealPercent(storageKey: String) {
        synchronized(lock) { realPercent.remove(storageKey) }
    }
}
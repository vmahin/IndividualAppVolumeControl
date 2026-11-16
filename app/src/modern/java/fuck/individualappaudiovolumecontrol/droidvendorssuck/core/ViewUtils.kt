package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import android.widget.SeekBar
import java.util.concurrent.Executors

/**
 * Shared, bounded app-icon cache with async first-loading.
 *
 * Used by both the active/preset playback lists (MainActivity) and the volume
 * overlay (VolumeOverlayService) so each package's icon is resolved once and
 * reused everywhere. Cache misses render a neutral placeholder immediately and
 * resolve the real icon on a background thread, so scrolling/rebuilding rows
 * never blocks on PackageManager calls. The per-ImageView tag check keeps a
 * stale async result from overwriting a row that was rebuilt for another app.
 */
object AppIconLoader {
    private const val PLACEHOLDER = android.R.drawable.sym_def_app_icon

    private val cache = LruCache<String, Drawable>(128)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun loadInto(context: Context, packageName: String, imageView: ImageView) {
        imageView.tag = packageName
        val cached = cache.get(packageName)
        if (cached != null) {
            imageView.setImageDrawable(cached)
            return
        }
        imageView.setImageResource(PLACEHOLDER)
        val appContext = context.applicationContext
        executor.execute {
            val icon = try {
                appContext.packageManager.getApplicationIcon(packageName)
            } catch (_: Throwable) {
                null
            } ?: return@execute
            cache.put(packageName, icon)
            mainHandler.post {
                if (imageView.tag == packageName) {
                    imageView.setImageDrawable(icon)
                }
            }
        }
    }
}

/**
 * Makes a SeekBar's thumb visibly larger (and its touch target taller) without
 * rasterizing the original drawable: the source drawable keeps its own
 * state/tint handling and is drawn at its native size, centered inside the
 * enlarged bounds that AbsSeekBar derives from this wrapper's intrinsics.
 */
fun SeekBar.withBiggerThumb(scale: Float): SeekBar {
    val original = thumb ?: return this
    thumb = ScaledThumbDrawable(original, scale)
    return this
}

private class ScaledThumbDrawable(
    private val source: Drawable,
    private val scale: Float,
) : Drawable() {
    private val scaledSourceBounds = Rect()

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = (b.width() / scale).toInt()
        val h = (b.height() / scale).toInt()
        if (w <= 0 || h <= 0) return
        scaledSourceBounds.set(
            b.left + (b.width() - w) / 2,
            b.top + (b.height() - h) / 2,
            b.left + (b.width() + w) / 2,
            b.top + (b.height() + h) / 2
        )
        source.bounds = scaledSourceBounds
        source.draw(canvas)
    }

    override fun getIntrinsicWidth(): Int = (source.getIntrinsicWidth() * scale).toInt()

    override fun getIntrinsicHeight(): Int = (source.getIntrinsicHeight() * scale).toInt()

    override fun getMinimumWidth(): Int = getIntrinsicWidth()

    override fun getMinimumHeight(): Int = getIntrinsicHeight()

    override fun setAlpha(alpha: Int) {
        source.setAlpha(alpha)
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        source.setColorFilter(colorFilter)
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = source.getOpacity()

    override fun isStateful(): Boolean = source.isStateful()

    // State is forwarded through onStateChange: the framework calls setState() on
    // this wrapper, the base Drawable stores the state array and invokes
    // onStateChange(), which pushes it into the source drawable so its own
    // state-list visuals (pressed etc.) keep working.

    override fun onStateChange(state: IntArray): Boolean = source.setState(state)

    override fun getCurrent(): Drawable = source.getCurrent()

    override fun setTintList(tint: ColorStateList?) {
        source.setTintList(tint)
    }

    override fun setTint(tintColor: Int) {
        source.setTint(tintColor)
    }

    override fun setTintMode(tintMode: PorterDuff.Mode?) {
        source.setTintMode(tintMode)
    }

    override fun mutate(): Drawable {
        source.mutate()
        return this
    }

    override fun getPadding(padding: Rect): Boolean = source.getPadding(padding)
}
package fuck.individualappaudiovolumecontrol.droidvendorssuck.ui

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import fuck.individualappaudiovolumecontrol.droidvendorssuck.R
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.AudioSettingsPrefs

/**
 * Liquid glass kit (XML Views flavor — no Compose, no Material Components).
 *
 * Pieces:
 * - [LiquidGradientDrawable] — soft blue/purple/teal blob gradient background,
 *   theme-aware (light / dark / true-black variants via colors.xml).
 * - [GlassPanelDrawable] — frosted translucent panel: faint white tint fill,
 *   1dp border that fades bright (top-left) to dim (bottom-right), a subtle
 *   inner highlight along the top edge, optional sheen, fully rounded corners,
 *   and a rounded outline so elevation casts a soft shadow.
 * - [View.applyGlass] — applies a glass panel background to any View (rows,
 *   sheets, cards).
 * - [Activity.applyLiquidBackground] — wraps an activity's content in a frame
 *   with the gradient window background plus (on API 31+, unless Reduce Effects)
 *   one full-screen RenderEffect-blurred copy of the gradient behind the
 *   content. That single static blur layer is what the translucent panels
 *   "frost" over — it is drawn once per frame, so no per-item blur inside
 *   scrolling lists, keeping things at 60fps.
 */

private const val BLUR_RADIUS_DP = 24f

/** True when the platform can do RenderEffect blur and the user hasn't turned
 *  "Reduce effects" on (the default). */
fun glassBlurEnabled(context: Context): Boolean {
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        !AudioSettingsPrefs(context).isReduceEffectsEnabled()
}

/** Soft gradient background: base theme color + three radial blob washes. */
class LiquidGradientDrawable(context: Context) : Drawable() {
    private val baseColor = ContextCompat.getColor(context, R.color.app_background)

    private val blobs = listOf(
        BlobSpec(
            ContextCompat.getColor(context, R.color.glass_blob_blue),
            0.04f, 0.08f, 0.90f
        ),
        BlobSpec(
            ContextCompat.getColor(context, R.color.glass_blob_purple),
            0.94f, 0.18f, 0.78f
        ),
        BlobSpec(
            ContextCompat.getColor(context, R.color.glass_blob_teal),
            0.82f, 0.94f, 0.88f
        ),
    )

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return
        canvas.drawColor(baseColor)
        val maxDim = maxOf(w, h)
        for (blob in blobs) {
            val cx = blob.x * w
            val cy = blob.y * h
            val radius = blob.radius * maxDim
            val color = blob.color
            paint.shader = RadialGradient(
                cx, cy, radius,
                intArrayOf(color, color and 0x00FFFFFF),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(bounds, paint)
        }
        paint.shader = null
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE

    private class BlobSpec(val color: Int, val x: Float, val y: Float, val radius: Float)
}

/**
 * Frosted glass panel background. Reads the theme's [R.color.glass_fill] tint;
 * pass [fillOverride] (e.g. a dark glass for the overlay) to force a fill.
 */
class GlassPanelDrawable(
    context: Context,
    cornerRadiusPx: Float,
    fillOverride: Int? = null,
    private val sheen: Boolean = false,
) : Drawable() {
    private val cornerRadius = cornerRadiusPx
    private val normalFill = fillOverride ?: ContextCompat.getColor(context, R.color.glass_fill)
    private val pressedFill = fillOverride ?: ContextCompat.getColor(context, R.color.glass_fill_pressed)

    private val borderColorBright = Color.argb(0x8C, 0xFF, 0xFF, 0xFF)   // ~55% white
    private val borderColorDim = Color.argb(0x26, 0xFF, 0xFF, 0xFF)      // ~15% white
    private val highlightColor = Color.argb(0x45, 0xFF, 0xFF, 0xFF)      // ~27% white
    private val borderWidthPx = (context.resources.displayMetrics.density * 1f).toInt().coerceAtLeast(1)
    private val highlightWidthPx = (context.resources.displayMetrics.density * 1.5f).toInt().coerceAtLeast(1)

    private val path = Path()
    private val highlightPath = Path()
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var pressed = false

    init {
        fillPaint.color = normalFill
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = borderWidthPx.toFloat()
        highlightPaint.style = Paint.Style.STROKE
        highlightPaint.strokeWidth = highlightWidthPx.toFloat()
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return

        path.reset()
        path.addRoundRect(RectF(b), cornerRadius, cornerRadius, Path.Direction.CW)

        // Frosted fill.
        fillPaint.color = if (pressed) pressedFill else normalFill
        canvas.drawPath(path, fillPaint)

        // Optional diagonal sheen (clipped to the panel).
        if (sheen) {
            canvas.save()
            canvas.clipPath(path)
            sheenPaint.shader = LinearGradient(
                b.left.toFloat(), b.top.toFloat(),
                b.right.toFloat(), b.bottom.toFloat(),
                Color.argb(0x24, 0xFF, 0xFF, 0xFF),
                Color.argb(0x00, 0xFF, 0xFF, 0xFF),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(b, sheenPaint)
            sheenPaint.shader = null
            canvas.restore()
        }

        // Border: bright top-left fading to dim bottom-right (shader follows the
        // bounding box diagonally).
        borderPaint.shader = LinearGradient(
            b.left.toFloat(), b.top.toFloat(),
            b.right.toFloat(), b.bottom.toFloat(),
            borderColorBright, borderColorDim,
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, borderPaint)
        borderPaint.shader = null

        // Subtle inner highlight along the top edge: same rounded shape stroked
        // with a vertical top-down fade, clipped to the panel so only the top arc
        // reads as a light rim.
        canvas.save()
        canvas.clipPath(path)
        highlightPath.reset()
        highlightPath.addRoundRect(
            RectF(
                b.left + borderWidthPx.toFloat(),
                b.top + borderWidthPx.toFloat(),
                b.right - borderWidthPx.toFloat(),
                b.bottom - borderWidthPx.toFloat()
            ),
            cornerRadius, cornerRadius, Path.Direction.CW
        )
        highlightPaint.shader = LinearGradient(
            0f, b.top.toFloat(), 0f, b.top.toFloat() + cornerRadius * 2f,
            highlightColor, Color.argb(0x00, 0xFF, 0xFF, 0xFF),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(highlightPath, highlightPaint)
        highlightPaint.shader = null
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT

    override fun isStateful(): Boolean = true

    override fun onStateChange(state: IntArray): Boolean {
        val nowPressed = state.any { it == android.R.attr.state_pressed }
        if (nowPressed != pressed) {
            pressed = nowPressed
            invalidateSelf()
            return true
        }
        return false
    }

    override fun getOutline(outline: Outline) {
        outline.setRoundRect(bounds, cornerRadius)
    }
}

/** Applies a glass panel surface to any view (sheets, cards, rows). */
fun View.applyGlass(
    cornerRadiusDp: Float = 24f,
    fill: Int? = null,
    sheen: Boolean = false,
    elevationDp: Float = 0f,
): View {
    val density = resources.displayMetrics.density
    background = GlassPanelDrawable(context, cornerRadiusDp * density, fill, sheen)
    if (elevationDp > 0f) {
        elevation = elevationDp * density
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = false
    }
    return this
}

/**
 * Gives an activity the liquid-glass backdrop:
 * 1. gradient window background (so the glass has something to blur over),
 * 2. on API 31+ with Reduce Effects off, one full-screen RenderEffect-blurred
 *    copy of the gradient behind the content — the single static blur layer,
 * 3. content wrapped above it, edge-to-edge with system-bar insets applied to
 *    the content (the blur layer stays full-bleed under the bars).
 */
fun Activity.applyLiquidBackground(content: View) {
    val useBlur = glassBlurEnabled(this)
    window.setBackgroundDrawable(LiquidGradientDrawable(this))

    val wrapper = FrameLayout(this)
    if (useBlur) {
        val blurLayer = View(this).apply {
            background = LiquidGradientDrawable(this@applyLiquidBackground)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val radius = BLUR_RADIUS_DP * resources.displayMetrics.density
                setRenderEffect(
                    RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
                )
            }
        }
        wrapper.addView(
            blurLayer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
    wrapper.addView(
        content,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )
    setContentView(wrapper)
    applyEdgeToEdge(content)
}

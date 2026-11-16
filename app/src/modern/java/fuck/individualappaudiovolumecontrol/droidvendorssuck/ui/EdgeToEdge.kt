package fuck.individualappaudiovolumecontrol.droidvendorssuck.ui

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Opts an activity into edge-to-edge layout (content draws behind the status and
 * navigation bars) and pads [content] by the system-bar insets so nothing is
 * hidden underneath them. Also keeps the system bar icons legible by flipping
 * their appearance with the light/dark theme.
 *
 * Implemented with androidx.core only (no Material Components dependency):
 * - WindowCompat.setDecorFitsSystemWindows (core 1.5+)
 * - WindowInsetsControllerCompat / WindowInsetsCompat (core 1.2+)
 */
fun Activity.applyEdgeToEdge(content: View) {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    // Only relevant on API 26-29: with decor-fits disabled the bars sit on top of
    // the window background, so make them transparent. On API 30+ this is the default.
    window.statusBarColor = Color.TRANSPARENT
    window.navigationBarColor = Color.TRANSPARENT

    val darkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val controller = WindowInsetsControllerCompat(window, content)
    controller.isAppearanceLightStatusBars = !darkMode
    controller.isAppearanceLightNavigationBars = !darkMode

    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom + ime.bottom)
        insets
    }
}

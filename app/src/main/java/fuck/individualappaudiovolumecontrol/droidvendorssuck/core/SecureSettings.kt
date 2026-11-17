package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

object SecureSettings {
    private const val TAG = "SecureSettings"

    fun isGranted(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * Best-effort root grant of WRITE_SECURE_SETTINGS via `su -c pm grant`.
     * The device is Magisk-rooted (the app is installed by a Magisk module), so this
     * succeeds when the app is allowed root (Magisk prompt); otherwise it falls back to
     * the module path (privapp allowlist + service.sh pm grant at boot). The result is
     * delivered on [onResult] from a background thread.
     */
    fun tryGrantViaRoot(context: Context, onResult: (Boolean) -> Unit) {
        thread(name = "GrantWss") {
            runCatching {
                val proc = Runtime.getRuntime().exec(
                    arrayOf(
                        "su", "-c",
                        "pm grant ${context.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}"
                    )
                )
                if (!proc.waitFor(15, TimeUnit.SECONDS)) proc.destroy()
            }.onFailure {
                Log.w(TAG, "root grant attempt failed", it)
            }
            onResult(isGranted(context))
        }
    }
}

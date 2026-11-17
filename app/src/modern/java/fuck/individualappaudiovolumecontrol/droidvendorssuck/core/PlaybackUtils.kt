package fuck.individualappaudiovolumecontrol.droidvendorssuck.core

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioPlaybackConfiguration
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.UserHandle
import android.util.Log
import fuck.individualappaudiovolumecontrol.droidvendorssuck.core.VolumeStore.VolumeKey
import kotlin.math.max

object PlaybackUtils {
    private const val TAG = "PlaybackUtils"

    // Hidden-API members are resolved lazily and re-tried after failure.
    // A lookup that fails early (e.g. before VMRuntime hidden-API exemptions are
    // applied on Android 9+) must not permanently disable the feature, so we only
    // cache successful resolutions and log each failing lookup once per process.
    private class HiddenMethodResolver(
        private val label: String,
        private val resolve: () -> java.lang.reflect.Method,
    ) {
        @Volatile
        private var cached: java.lang.reflect.Method? = null
        @Volatile
        private var failureLogged: Boolean = false

        fun get(): java.lang.reflect.Method? {
            cached?.let { return it }
            val resolved = try {
                resolve()
            } catch (t: Throwable) {
                if (!failureLogged) {
                    failureLogged = true
                    Log.w(TAG, "Hidden-API reflection lookup failed: $label", t)
                }
                null
            }
            if (resolved != null) {
                cached = resolved
            }
            return resolved
        }
    }

    private class HiddenIntFieldResolver(
        private val label: String,
        private val resolve: () -> Int,
    ) {
        @Volatile
        private var cached: Int? = null
        @Volatile
        private var failureLogged: Boolean = false

        fun get(): Int? {
            cached?.let { return it }
            val resolved = try {
                resolve()
            } catch (t: Throwable) {
                if (!failureLogged) {
                    failureLogged = true
                    Log.w(TAG, "Hidden-API reflection lookup failed: $label", t)
                }
                null
            }
            if (resolved != null) {
                cached = resolved
            }
            return resolved
        }
    }

    private val clientPackageMethod = HiddenMethodResolver("AudioPlaybackConfiguration.getClientPackageName") {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getClientPackageName").apply {
            isAccessible = true
        }
    }

    private val clientUidMethod = HiddenMethodResolver("AudioPlaybackConfiguration.getClientUid") {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getClientUid").apply {
            isAccessible = true
        }
    }

    private val playerProxyMethod = HiddenMethodResolver("AudioPlaybackConfiguration.getPlayerProxy") {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getPlayerProxy").apply {
            isAccessible = true
        }
    }

    private val playerStateMethod = HiddenMethodResolver("AudioPlaybackConfiguration.getPlayerState") {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getPlayerState").apply {
            isAccessible = true
        }
    }

    private val isActiveMethod = HiddenMethodResolver("AudioPlaybackConfiguration.isActive") {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("isActive").apply {
            isAccessible = true
        }
    }

    private val playerStateStartedValue = HiddenIntFieldResolver("AudioPlaybackConfiguration.PLAYER_STATE_STARTED") {
        AudioPlaybackConfiguration::class.java.getDeclaredField("PLAYER_STATE_STARTED").apply {
            isAccessible = true
        }.getInt(null)
    }

    private val playerVolumeMethod = HiddenMethodResolver("AudioPlaybackConfiguration.getVolume") {
        AudioPlaybackConfiguration::class.java.getDeclaredMethod("getVolume").apply {
            isAccessible = true
        }
    }

    private val userIdMethod = HiddenMethodResolver("UserHandle.getUserId") {
        android.os.UserHandle::class.java.getDeclaredMethod(
            "getUserId",
            Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }
    }

    private val userHandleOfMethod = HiddenMethodResolver("UserHandle.of") {
        android.os.UserHandle::class.java.getDeclaredMethod(
            "of",
            Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }
    }

    private val createContextAsUserMethod = HiddenMethodResolver("Context.createContextAsUser") {
        android.content.Context::class.java.getDeclaredMethod(
            "createContextAsUser",
            android.os.UserHandle::class.java,
            Int::class.javaPrimitiveType
        ).apply {
            isAccessible = true
        }
    }

    private val setVolumeMethodCache = mutableMapOf<Class<*>, java.lang.reflect.Method>()
    private val pauseMethodCache = mutableMapOf<Class<*>, java.lang.reflect.Method>()
    private val playMethodCache = mutableMapOf<Class<*>, java.lang.reflect.Method>()

    fun getClientPackageName(
        context: Context,
        config: AudioPlaybackConfiguration,
        packageManager: PackageManager
    ): String? {
        try {
            val method = clientPackageMethod.get()
            if (method != null) {
                return method.invoke(config) as? String
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read client package name", t)
            // Fallback to resolving by UID if hidden API is blocked.
        }

        return try {
            val uidMethod = clientUidMethod.get() ?: return null
            val uid = uidMethod.invoke(config) as? Int ?: return null
            val packages = getPackagesForUid(context, packageManager, uid)
            packages?.firstOrNull()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve client package", t)
            null
        }
    }

    fun getClientUid(config: AudioPlaybackConfiguration): Int? {
        return try {
            val uidMethod = clientUidMethod.get() ?: return null
            uidMethod.invoke(config) as? Int
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve client uid", t)
            null
        }
    }

    fun getUserIdFromUid(uid: Int?): Int? {
        if (uid == null) return null
        return try {
            val method = userIdMethod.get() ?: return null
            method.invoke(null, uid) as? Int
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to resolve user id from uid", t)
            null
        }
    }

    fun getActivePlaybackConfigurationsSafe(
        audioManager: AudioManager,
        diagnosticsStore: DiagnosticsStore? = null,
    ): List<AudioPlaybackConfiguration> {
        return try {
            audioManager.activePlaybackConfigurations
        } catch (t: Throwable) {
            diagnosticsStore?.recordLastError(
                "activePlaybackConfigurations failed: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
            )
            emptyList()
        }
    }

    fun getActivePlaybackConfigurationsSafe(
        context: Context,
        diagnosticsStore: DiagnosticsStore? = null,
    ): List<AudioPlaybackConfiguration> {
        val audioManager = context.getSystemService(AudioManager::class.java)
        if (audioManager == null) {
            val message = "AudioManager unavailable (getSystemService returned null)"
            Log.w(TAG, message)
            diagnosticsStore?.recordLastError(message)
            return emptyList()
        }
        val baseConfigs = getActivePlaybackConfigurationsSafe(audioManager, diagnosticsStore)
        val hasAcrossUsers = hasAcrossUsersPermission(context)
        val probeParts = mutableListOf(
            "perm=${if (hasAcrossUsers) "granted" else "denied"}",
            "base=${baseConfigs.size}",
        )
        if (!hasAcrossUsers) {
            diagnosticsStore?.recordCrossProfileProbe(probeParts.joinToString(" | "))
            return baseConfigs
        }

        val results = mutableListOf<AudioPlaybackConfiguration>()
        results.addAll(baseConfigs)

        val currentUserId = ProfileUtils.currentUserId()
        val userIds = ProfileUtils.collectUserIds(context)
        probeParts += "current=u${currentUserId ?: "?"}"
        probeParts += "profiles=${if (userIds.isEmpty()) "-" else userIds.joinToString(",") { "u$it" }}"
        for (userId in userIds) {
            if (currentUserId != null && userId == currentUserId) continue
            val userHandle = createUserHandle(userId)
            if (userHandle == null) {
                probeParts += "u$userId:userHandle=fail"
                continue
            }
            val userContext = createContextAsUser(context, userHandle)
            if (userContext == null) {
                probeParts += "u$userId:context=fail"
                continue
            }
            val userAudioManager = userContext.getSystemService(AudioManager::class.java)
            try {
                val configs = userAudioManager.activePlaybackConfigurations
                results.addAll(configs)
                probeParts += "u$userId:configs=${configs.size}"
            } catch (t: Throwable) {
                probeParts += "u$userId:${t.javaClass.simpleName}"
                diagnosticsStore?.recordLastError(
                    "activePlaybackConfigurations failed for u$userId: ${t.javaClass.simpleName}: ${t.message ?: "unknown"}"
                )
            }
        }
        diagnosticsStore?.recordCrossProfileProbe(probeParts.joinToString(" | "))
        return results
    }

    fun hasAcrossUsersPermission(context: Context): Boolean {
        val granted = context.checkSelfPermission(PERMISSION_INTERACT_ACROSS_USERS) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) return true
        return context.checkSelfPermission(PERMISSION_INTERACT_ACROSS_USERS_FULL) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun createUserHandle(userId: Int): UserHandle? {
        return try {
            val method = userHandleOfMethod.get() ?: return null
            method.invoke(null, userId) as? UserHandle
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to create UserHandle for u$userId", t)
            null
        }
    }

    private fun createContextAsUser(context: Context, userHandle: UserHandle): Context? {
        return try {
            val method = createContextAsUserMethod.get() ?: return null
            method.invoke(context, userHandle, 0) as? Context
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to create user context", t)
            null
        }
    }

    private fun getPackagesForUid(
        context: Context,
        packageManager: PackageManager,
        uid: Int,
    ): Array<String>? {
        val direct = runCatching { packageManager.getPackagesForUid(uid) }.getOrNull()
        if (!direct.isNullOrEmpty()) return direct

        val userId = getUserIdFromUid(uid) ?: return direct
        val currentUserId = ProfileUtils.currentUserId()
        if (currentUserId != null && userId == currentUserId) return direct

        val userHandle = createUserHandle(userId) ?: return direct
        val userContext = createContextAsUser(context, userHandle) ?: return direct
        return runCatching { userContext.packageManager.getPackagesForUid(uid) }
            .onFailure { Log.w(TAG, "Failed to resolve uid $uid in user u$userId", it) }
            .getOrNull() ?: direct
    }

    private const val PERMISSION_INTERACT_ACROSS_USERS =
        "android.permission.INTERACT_ACROSS_USERS"
    private const val PERMISSION_INTERACT_ACROSS_USERS_FULL =
        "android.permission.INTERACT_ACROSS_USERS_FULL"

    fun hasActivePlayback(configs: List<AudioPlaybackConfiguration>): Boolean {
        for (config in configs) {
            val isStarted = runCatching {
                val state = playerStateMethod.get()?.invoke(config) as? Int
                val startedValue = playerStateStartedValue.get()
                state != null && startedValue != null && state == startedValue
            }.getOrDefault(false)
            if (isStarted) return true
            val isActive = runCatching {
                (isActiveMethod.get()?.invoke(config) as? Boolean) == true
            }.getOrDefault(false)
            if (isActive) return true
        }
        return false
    }

    /** Live status of every hidden-API reflection lookup, shown on the Diagnostics screen. */
    fun reflectionStatusLines(): List<String> {
        fun status(label: String, ok: Boolean): String =
            " - $label: ${if (ok) "ok" else "missing"}"
        return listOf(
            status("AudioPlaybackConfiguration.getClientPackageName", clientPackageMethod.get() != null),
            status("AudioPlaybackConfiguration.getClientUid", clientUidMethod.get() != null),
            status("AudioPlaybackConfiguration.getPlayerProxy", playerProxyMethod.get() != null),
            status("AudioPlaybackConfiguration.getPlayerState", playerStateMethod.get() != null),
            status("AudioPlaybackConfiguration.isActive", isActiveMethod.get() != null),
            status("AudioPlaybackConfiguration.PLAYER_STATE_STARTED", playerStateStartedValue.get() != null),
            status("UserHandle.getUserId", userIdMethod.get() != null),
            status("UserHandle.of", userHandleOfMethod.get() != null),
            status("Context.createContextAsUser", createContextAsUserMethod.get() != null),
            " - PlayerProxy.setVolume / pause / start: resolved per player at runtime",
        )
    }

    fun applyVolumeToPlayer(config: AudioPlaybackConfiguration, volume: Float): Boolean {
        return try {
            val method = playerProxyMethod.get() ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val proxyClass = playerProxy.javaClass
            val setVolume = synchronized(setVolumeMethodCache) {
                setVolumeMethodCache[proxyClass] ?: run {
                    val resolved = try {
                        proxyClass.getDeclaredMethod("setVolume", Float::class.javaPrimitiveType)
                    } catch (_: Throwable) {
                        proxyClass.getDeclaredMethod(
                            "setVolume",
                            Float::class.javaPrimitiveType,
                            Int::class.javaPrimitiveType
                        )
                    }
                    resolved.isAccessible = true
                    setVolumeMethodCache[proxyClass] = resolved
                    resolved
                }
            }
            try {
                if (setVolume.parameterTypes.size == 2) {
                    setVolume.invoke(playerProxy, volume, 0)
                } else {
                    setVolume.invoke(playerProxy, volume)
                }
                true
            } catch (t: Throwable) {
                synchronized(setVolumeMethodCache) {
                    setVolumeMethodCache.remove(proxyClass)
                }
                Log.w(TAG, "Failed to set player volume", t)
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set player volume", t)
            false
        }
    }

    /**
     * Reads back the real volume of a playback player (0..1) after an apply.
     *
     * Uses `AudioPlaybackConfiguration.getVolume()` (public API since Android 13 /
     * API 33) via the same lazily-resolved reflection the rest of this file uses
     * for version-gated members. On older API levels (or if the player does not
     * expose a volume), the lookup is cached as unavailable and this returns
     * null — callers treat null as "cannot verify" and skip the read-back check.
     */
    fun readPlayerVolume(config: AudioPlaybackConfiguration): Float? {
        return try {
            playerVolumeMethod.get()?.invoke(config) as? Float
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to read player volume", t)
            null
        }
    }

    data class ActivePackageInfo(
        val key: VolumeKey,
        val sessionCount: Int,
        val isRootDetected: Boolean,
    )

    fun shouldUseRootAudioFallback(
        context: Context,
        overlayPrefs: OverlayPrefs? = null,
        includeRootFallback: Boolean = overlayPrefs?.isRootAudioFallbackEnabled() == true,
    ): Boolean {
        return includeRootFallback && !hasAcrossUsersPermission(context)
    }

    fun buildActivePackageInfo(
        context: Context,
        packageManager: PackageManager,
        diagnosticsStore: DiagnosticsStore? = null,
        overlayPrefs: OverlayPrefs? = null,
        filterOverlayIgnore: Boolean = false,
        includeRootFallback: Boolean = overlayPrefs?.isRootAudioFallbackEnabled() == true,
        forceRootRefresh: Boolean = false,
    ): List<ActivePackageInfo> {
        val counts = mutableMapOf<VolumeKey, Pair<Int, Boolean>>()
        val configs = getActivePlaybackConfigurationsSafe(context, diagnosticsStore)
        for (config in configs) {
            val packageName = getClientPackageName(context, config, packageManager) ?: continue
            val uid = getClientUid(config)
            val userId = getUserIdFromUid(uid)
            if (filterOverlayIgnore && overlayPrefs?.isOverlayIgnored(packageName, userId) == true) {
                continue
            }
            val key = VolumeKey(packageName, userId)
            val current = counts[key]
            val nextCount = (current?.first ?: 0) + 1
            counts[key] = nextCount to false
        }
        val useRootFallback = shouldUseRootAudioFallback(context, overlayPrefs, includeRootFallback)
        if (useRootFallback) {
            val rootEntries = RootAudioDumpsys.getActiveEntries(
                diagnosticsStore = diagnosticsStore,
                force = forceRootRefresh
            )
            for (entry in rootEntries) {
                val userId = getUserIdFromUid(entry.uid)
                if (filterOverlayIgnore && overlayPrefs?.isOverlayIgnored(entry.packageName, userId) == true) {
                    continue
                }
                val key = VolumeKey(entry.packageName, userId)
                if (counts.containsKey(key)) continue
                val count = max(1, entry.sessionIds.size)
                counts[key] = count to true
            }
        }
        return counts.map { (key, data) ->
            ActivePackageInfo(
                key = key,
                sessionCount = data.first,
                isRootDetected = data.second,
            )
        }
    }

    fun hasNonIgnoredPlayback(
        context: Context,
        packageManager: PackageManager,
        overlayPrefs: OverlayPrefs,
        diagnosticsStore: DiagnosticsStore? = null,
    ): Boolean {
        val entries = buildActivePackageInfo(
            context = context,
            packageManager = packageManager,
            diagnosticsStore = diagnosticsStore,
            overlayPrefs = overlayPrefs,
            filterOverlayIgnore = true,
            includeRootFallback = overlayPrefs.isRootAudioFallbackEnabled(),
            forceRootRefresh = false
        )
        return entries.isNotEmpty()
    }

    fun applyPreferredDeviceToPlayer(
        config: AudioPlaybackConfiguration,
        device: AudioDeviceInfo
    ): Boolean {
        return try {
            val method = playerProxyMethod.get() ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val proxyClass = playerProxy.javaClass
            val preferred = runCatching {
                proxyClass.getDeclaredMethod("setPreferredDevice", AudioDeviceInfo::class.java)
            }.getOrElse {
                proxyClass.getDeclaredMethod("setDevice", AudioDeviceInfo::class.java)
            }
            preferred.isAccessible = true
            preferred.invoke(playerProxy, device)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to set preferred device", t)
            false
        }
    }

    fun pausePlayer(config: AudioPlaybackConfiguration): Boolean {
        return try {
            val method = playerProxyMethod.get() ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val proxyClass = playerProxy.javaClass
            val pause = synchronized(pauseMethodCache) {
                pauseMethodCache[proxyClass] ?: run {
                    val resolved = resolvePauseMethod(proxyClass) ?: return false
                    resolved.isAccessible = true
                    pauseMethodCache[proxyClass] = resolved
                    resolved
                }
            }
            try {
                if (pause.parameterTypes.isEmpty()) {
                    pause.invoke(playerProxy)
                } else {
                    pause.invoke(playerProxy, true)
                }
                true
            } catch (t: Throwable) {
                synchronized(pauseMethodCache) {
                    pauseMethodCache.remove(proxyClass)
                }
                Log.w(TAG, "Failed to pause player", t)
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to pause player", t)
            false
        }
    }

    fun playPlayer(config: AudioPlaybackConfiguration): Boolean {
        return try {
            val method = playerProxyMethod.get() ?: return false
            val playerProxy = method.invoke(config) ?: return false
            val proxyClass = playerProxy.javaClass
            val play = synchronized(playMethodCache) {
                playMethodCache[proxyClass] ?: run {
                    val resolved = resolvePlayMethod(proxyClass) ?: return false
                    resolved.isAccessible = true
                    playMethodCache[proxyClass] = resolved
                    resolved
                }
            }
            try {
                if (play.parameterTypes.isEmpty()) {
                    play.invoke(playerProxy)
                } else {
                    play.invoke(playerProxy, true)
                }
                true
            } catch (t: Throwable) {
                synchronized(playMethodCache) {
                    playMethodCache.remove(proxyClass)
                }
                Log.w(TAG, "Failed to start player", t)
                false
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to start player", t)
            false
        }
    }

    private fun resolvePauseMethod(proxyClass: Class<*>): java.lang.reflect.Method? {
        return runCatching {
            proxyClass.getDeclaredMethod("pause")
        }.getOrElse {
            runCatching { proxyClass.getDeclaredMethod("pause", Boolean::class.javaPrimitiveType) }.getOrNull()
        }
    }

    private fun resolvePlayMethod(proxyClass: Class<*>): java.lang.reflect.Method? {
        return runCatching {
            proxyClass.getDeclaredMethod("start")
        }.getOrElse {
            runCatching { proxyClass.getDeclaredMethod("play") }.getOrNull()
        }
    }
}

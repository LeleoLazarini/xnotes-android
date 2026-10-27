package com.xnotes.ui

import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import com.xnotes.R

/**
 * Locked notes: the tablet's own fingerprint or screen lock opens them. It hides the note in the
 * app (page, thumbnails, preview); the file itself is not encrypted, so a file manager still sees it.
 */
object LockGate {
    private fun secure(activity: Activity) =
        activity.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    /** Ask for the fingerprint, face or screen lock; [onOk] runs when it passes. */
    fun authenticate(activity: Activity, onOk: () -> Unit, onFail: (String) -> Unit = {}) {
        // With no screen lock on the tablet there is nothing to ask for: let the note open.
        if (Build.VERSION.SDK_INT < 30 || !secure(activity)) { onOk(); return }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(activity.getString(R.string.lock_prompt_title))
            .setSubtitle(activity.getString(R.string.lock_prompt_subtitle))
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFail(errString.toString())
        })
    }

    /** Lock [uri], or unlock it after the fingerprint; refuses to lock a tablet with no screen lock. */
    fun toggle(activity: Activity?, editor: Editor, uri: String) {
        if (activity == null) return
        if (editor.isLocked(uri)) {
            authenticate(activity, { editor.setLocked(uri, false); editor.message = activity.getString(R.string.lock_removed) })
        } else if (!secure(activity)) {
            editor.message = activity.getString(R.string.lock_needs_screen_lock)
        } else {
            editor.setLocked(uri, true)
            editor.message = activity.getString(R.string.lock_added)
        }
    }
}

package com.attunetv.launcher

import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject

/** A passive reading panel. It never steals focus, captures keys, or dims/pauses the video. */
class CompanionOverlay(private val service: HomeButtonService) {
    private val context = Screen.normalize(service)
    private val wm = service.getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var root: LinearLayout? = null
    private var title: TextView? = null
    private var label: TextView? = null
    private var question: TextView? = null
    private var answer: TextView? = null
    private var footer: TextView? = null
    var expectedPackage: String? = null
        private set
    private val expire = Runnable { hide() }
    private val hardExpire = Runnable { hide() }
    // Suppress subsequent heartbeats after a local timeout or app switch, until explicit hide.
    private var dismissed = false

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
    private fun text(size: Float, color: Int) = TextView(context).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTextColor(color)
        includeFontPadding = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    fun update(message: JSONObject): Boolean {
        if (!message.optBoolean("visible")) { hide(); dismissed = false; return true }
        val pkg = message.optString("pkg")
        if (!CompanionPolicy.canShow(pkg) || dismissed) return false
        if (expectedPackage != null && expectedPackage != pkg) { hide(); return false }
        if (root == null) {
            expectedPackage = pkg
            val panel = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(22), dp(22), dp(22), dp(18))
                background = GradientDrawable().apply {
                    setColor(Color.argb(242, 14, 25, 33)); cornerRadius = dp(20).toFloat()
                    setStroke(dp(1), Color.argb(95, 194, 221, 216))
                }
                elevation = dp(12).toFloat()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            label = text(10f, Color.rgb(174, 220, 201)).also {
                it.letterSpacing = .13f; it.maxLines = 1; panel.addView(it)
            }
            title = text(22f, Color.WHITE).also {
                it.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                it.maxLines = 2; it.ellipsize = TextUtils.TruncateAt.END
                panel.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            }
            question = text(12f, Color.rgb(166, 197, 191)).also {
                it.maxLines = 2; it.ellipsize = TextUtils.TruncateAt.END
                panel.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            }
            answer = text(16f, Color.rgb(240, 245, 244)).also {
                it.maxLines = 11; it.ellipsize = TextUtils.TruncateAt.END
                panel.addView(it, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(14) })
            }
            footer = text(10f, Color.rgb(155, 174, 182)).also {
                panel.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
            }
            val params = WindowManager.LayoutParams(dp(340), dp(444),
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply {
                gravity = Gravity.TOP or Gravity.END; x = dp(28); y = dp(28)
                title = "AttuneTV viewing companion"
            }
            try { wm.addView(panel, params) } catch (_: Exception) { hide(); return false }
            root = panel
            service.watchCompanionWindows(true)
            handler.postDelayed(hardExpire, 90_000)
        }
        label?.text = "ATTUNE TV  /  ${message.optString("provider").take(22).uppercase()}"
        title?.text = message.optString("title").take(240)
        question?.text = message.optString("question").take(300)
        answer?.text = message.optString("answer").take(1600)
        footer?.text = "${if (message.optBoolean("busy")) "Thinking · " else ""}Context: ${message.optString("source").take(24)}\nFull answer and Hide on your phone · closes automatically"
        handler.removeCallbacks(expire)
        handler.postDelayed(expire, 9_000) // Mac asleep, ADB lost, or process killed: fail closed.
        return true
    }

    fun onWindow(pkg: String) {
        if (CompanionPolicy.shouldHide(expectedPackage, pkg)) hide()
    }

    fun hide() {
        handler.removeCallbacks(expire); handler.removeCallbacks(hardExpire)
        root?.let { try { wm.removeViewImmediate(it) } catch (_: Exception) {} }
        root = null; title = null; label = null; question = null; answer = null; footer = null
        expectedPackage = null; dismissed = true
        service.watchCompanionWindows(false)
    }
}

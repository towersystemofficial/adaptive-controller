package com.towersys.adaptiveremote.procedural

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

class ProceduralOverlay(
    private val context: Context,
    private val onClose: () -> Unit,
    private val onStop: () -> Unit,
) {
    private val manager = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var title: TextView? = null
    private var detail: TextView? = null
    private var progress: ProgressBar? = null

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(context) || root != null) return root != null
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * density).toInt(), (9 * density).toInt(), (12 * density).toInt(), (9 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.rgb(45, 48, 58))
                setStroke((2 * density).toInt(), Color.rgb(190, 125, 230))
            }
            elevation = 8f * density
        }
        val titleView = TextView(context).apply {
            text = "Procedural • starting"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val detailView = TextView(context).apply {
            text = "Requesting the first Grok batch"
            setTextColor(Color.LTGRAY)
            textSize = 10f
            maxLines = 2
        }
        val progressView = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val closeView = action("CLOSE", Color.rgb(240, 195, 110), onClose)
        val stopView = action("STOP", Color.rgb(255, 145, 150), onStop)
        actions.addView(closeView)
        actions.addView(stopView)
        container.addView(titleView)
        container.addView(detailView)
        container.addView(progressView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (6 * density).toInt()))
        container.addView(actions)
        val params = WindowManager.LayoutParams(
            (230 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * density).toInt()
            y = (150 * density).toInt()
        }
        installDrag(titleView, container, params)
        manager.addView(container, params)
        root = container
        title = titleView
        detail = detailView
        progress = progressView
        return true
    }

    private fun action(label: String, color: Int, callback: () -> Unit) = TextView(context).apply {
        text = "  $label  "
        setTextColor(color)
        textSize = 11f
        setPadding(12, 8, 12, 8)
        setOnClickListener { callback() }
    }

    fun update(titleText: String, detailText: String, progressFraction: Float = 0f) {
        root?.post {
            title?.text = titleText
            detail?.text = detailText
            progress?.progress = (progressFraction.coerceIn(0f, 1f) * 100).toInt()
        }
    }

    fun remove() {
        root?.let { runCatching { manager.removeView(it) } }
        root = null
    }

    private fun installDrag(handle: View, container: View, params: WindowManager.LayoutParams) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX; downY = event.rawY; startX = params.x; startY = params.y; true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX - (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    manager.updateViewLayout(container, params)
                    true
                }
                else -> false
            }
        }
    }
}

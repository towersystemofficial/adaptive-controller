package com.towersys.adaptiveremote.text

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

class TextMonitorOverlay(
    private val context: Context,
    private val stopLabel: String = "STOP TEXT",
    private val onStop: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: LinearLayout? = null
    private var title: TextView? = null
    private var detail: TextView? = null
    private var progress: ProgressBar? = null

    fun show(): Boolean {
        if (!Settings.canDrawOverlays(context) || root != null) return root != null
        val density = context.resources.displayMetrics.density
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * density).toInt(), (9 * density).toInt(), (12 * density).toInt(), (9 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 18 * density
                setColor(Color.rgb(45, 48, 58))
                setStroke((2 * density).toInt(), Color.rgb(130, 160, 230))
            }
            elevation = 8f * density
        }
        val titleView = TextView(context).apply {
            text = "Text • starting"
            setTextColor(Color.WHITE)
            textSize = 13f
        }
        val detailView = TextView(context).apply {
            text = "Waiting for visible text"
            setTextColor(Color.LTGRAY)
            textSize = 10f
            maxLines = 2
        }
        val progressView = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
        }
        val stopView = TextView(context).apply {
            text = "Tap to $stopLabel"
            gravity = Gravity.END
            setTextColor(Color.rgb(255, 145, 150))
            textSize = 10f
        }
        container.addView(titleView)
        container.addView(detailView)
        container.addView(progressView, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (6 * density).toInt()))
        container.addView(stopView)
        val params = WindowManager.LayoutParams(
            (210 * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (12 * density).toInt()
            y = (150 * density).toInt()
        }
        installDragAndTap(container, params, density)
        windowManager.addView(container, params)
        root = container
        title = titleView
        detail = detailView
        progress = progressView
        return true
    }

    fun update(titleText: String, detailText: String, progressFraction: Float) {
        root?.post {
            title?.text = titleText
            detail?.text = detailText
            progress?.progress = (progressFraction.coerceIn(0f, 1f) * 100).toInt()
        }
    }

    fun remove() {
        root?.let { runCatching { windowManager.removeView(it) } }
        root = null
        title = null
        detail = null
        progress = null
    }

    private fun installDragAndTap(view: View, params: WindowManager.LayoutParams, density: Float) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        view.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX - (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = kotlin.math.abs(event.rawX - downX) + kotlin.math.abs(event.rawY - downY)
                    if (moved < 16 * density) onStop()
                    true
                }
                else -> false
            }
        }
    }
}

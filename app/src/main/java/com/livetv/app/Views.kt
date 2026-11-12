package com.livetv.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import java.util.Random
import kotlin.math.abs

class EqualizerView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val n = 9
    private val levels = FloatArray(n) { 0.3f }
    private val targets = FloatArray(n) { 0.3f }
    private val rnd = Random()
    private var running = false
    var barColor: Int = Color.WHITE
    var animated: Boolean = true

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            for (i in 0 until n) {
                if (abs(levels[i] - targets[i]) < 0.05f) {
                    targets[i] = 0.15f + rnd.nextFloat() * 0.85f
                }
                levels[i] += (targets[i] - levels[i]) * 0.25f
            }
            invalidate()
            postDelayed(this, 60)
        }
    }

    fun start() {
        if (!running && animated) {
            running = true
            post(tick)
        } else if (!animated) {
            invalidate()
        }
    }

    fun stop() {
        running = false
        removeCallbacks(tick)
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        paint.color = barColor
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = w / (n * 2f)
        val bw = (w - gap * (n - 1)) / n
        for (i in 0 until n) {
            val bh = h * (if (animated) levels[i] else 0.45f)
            val left = i * (bw + gap)
            c.drawRoundRect(RectF(left, h - bh, left + bw, h), bw / 2, bw / 2, paint)
        }
    }
}

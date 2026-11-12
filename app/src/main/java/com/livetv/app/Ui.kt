package com.livetv.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class Ui(private val ctx: Context) {
    var theme: AppTheme = Themes.get(1)
    var lite: Boolean = false
    private val iconCache = HashMap<String, Drawable>()

    fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun alpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    fun rr(color: Int, radiusDp: Int, strokeColor: Int = 0, strokeDp: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.shape = GradientDrawable.RECTANGLE
        g.cornerRadius = dp(radiusDp).toFloat()
        g.setColor(color)
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor)
        return g
    }

    fun grad(c1: Int, c2: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(c1, c2))
        g.cornerRadius = dp(radiusDp).toFloat()
        return g
    }

    fun stateBg(normal: Drawable, radiusDp: Int): StateListDrawable {
        val t = theme
        val sl = StateListDrawable()
        sl.addState(
            intArrayOf(android.R.attr.state_focused),
            rr(alpha(t.accent, 0x55), radiusDp, t.accent, 3)
        )
        sl.addState(
            intArrayOf(android.R.attr.state_pressed),
            rr(alpha(t.accent, 0x77), radiusDp, t.accent, 2)
        )
        sl.addState(intArrayOf(), normal)
        return sl
    }

    fun lp(
        w: Int, h: Int, wt: Float = 0f,
        l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0
    ): LinearLayout.LayoutParams {
        val p = LinearLayout.LayoutParams(w, h, wt)
        p.setMargins(dp(l), dp(t), dp(r), dp(b))
        return p
    }

    fun letterIcon(name: String): Drawable {
        val colors = intArrayOf(
            0xFF3B82F6.toInt(), 0xFF8B5CF6.toInt(), 0xFF10B981.toInt(),
            0xFFF59E0B.toInt(), 0xFFEF4444.toInt(), 0xFF06B6D4.toInt()
        )
        val ci = (name.hashCode() and 0x7fffffff) % colors.size
        val letter = name.trim().firstOrNull { it.isLetterOrDigit() }
            ?.uppercaseChar()?.toString() ?: "TV"
        val key = "$letter$ci"
        iconCache[key]?.let { return it }
        val size = dp(44)
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = colors[ci]
        c.drawRoundRect(
            RectF(0f, 0f, size.toFloat(), size.toFloat()),
            dp(10).toFloat(), dp(10).toFloat(), p
        )
        p.color = Color.WHITE
        p.textSize = size * 0.5f
        p.textAlign = Paint.Align.CENTER
        p.isFakeBoldText = true
        val y = size / 2f - (p.descent() + p.ascent()) / 2f
        c.drawText(letter, size / 2f, y, p)
        val d = BitmapDrawable(ctx.resources, bmp)
        iconCache[key] = d
        return d
    }

    // A list popup with a clear highlight for remote-control navigation.
    // With searchable = true a search box filters the list while typing.
    fun picker(
        act: AppCompatActivity,
        titleText: String,
        labels: List<String>,
        selected: Int,
        searchable: Boolean = false,
        onPick: (Int) -> Unit
    ) {
        val t = theme
        var dlg: AlertDialog? = null
        var shown: List<Int> = labels.indices.toList()
        val rv = RecyclerView(act)
        val lm = LinearLayoutManager(act)
        rv.layoutManager = lm
        val ad = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(
                parent: ViewGroup, viewType: Int
            ): RecyclerView.ViewHolder {
                val tv = TextView(parent.context)
                val p = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                p.setMargins(dp(8), dp(3), dp(8), dp(3))
                tv.layoutParams = p
                tv.setPadding(dp(14), dp(12), dp(14), dp(12))
                tv.textSize = 16f
                tv.isFocusable = true
                tv.isClickable = true
                return object : RecyclerView.ViewHolder(tv) {}
            }

            override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
                val tv = h.itemView as TextView
                val orig = shown[pos]
                val sel = orig == selected
                tv.text = (if (sel) "✓  " else "") + labels[orig]
                tv.setTextColor(if (sel) t.accent else t.text)
                tv.background = stateBg(rr(t.card, 12), 12)
                tv.setOnClickListener {
                    dlg?.dismiss()
                    onPick(orig)
                }
            }

            override fun getItemCount(): Int = shown.size
        }
        rv.adapter = ad

        val head = TextView(act)
        head.text = titleText
        head.textSize = 18f
        head.setTypeface(null, Typeface.BOLD)
        head.setTextColor(t.text)
        head.setPadding(dp(18), dp(16), dp(18), dp(8))
        val box = LinearLayout(act)
        box.orientation = LinearLayout.VERTICAL
        box.addView(head)
        if (searchable) {
            val et = EditText(act)
            et.hint = act.getString(R.string.search_hint)
            et.setSingleLine(true)
            et.textSize = 15f
            et.setTextColor(t.text)
            et.setHintTextColor(t.sub)
            et.setPadding(dp(14), dp(10), dp(14), dp(10))
            et.background = rr(t.card, 12, alpha(t.sub, 0x66), 1)
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString()?.trim() ?: ""
                    shown = if (q.isEmpty()) {
                        labels.indices.toList()
                    } else {
                        labels.indices.filter { labels[it].contains(q, ignoreCase = true) }
                    }
                    ad.notifyDataSetChanged()
                    lm.scrollToPosition(0)
                }
            })
            val lpEt = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lpEt.setMargins(dp(12), 0, dp(12), dp(6))
            box.addView(et, lpEt)
        }
        box.addView(
            rv,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(if (searchable) 300 else 360)
            )
        )
        val d = AlertDialog.Builder(act).setView(box).create()
        dlg = d
        d.window?.setBackgroundDrawable(rr(t.bg, 16))
        d.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        d.show()
        if (selected in labels.indices) {
            rv.post {
                lm.scrollToPositionWithOffset(selected, dp(120))
                rv.post { rv.findViewHolderForAdapterPosition(selected)?.itemView?.requestFocus() }
            }
        } else {
            rv.post { rv.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus() }
        }
    }
}

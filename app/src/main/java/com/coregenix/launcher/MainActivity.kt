package com.coregenix.launcher

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import kotlin.math.*

val CYAN = 0xFF2FD6FF.toInt(); val VIOLET = 0xFF8B5CF6.toInt(); val PINK = 0xFFFF4FA3.toInt()
val BG = 0xFF050812.toInt(); val PANEL = 0xFF0B1224.toInt(); val MUTED = 0xFF7F8DB0.toInt()

class App(val label: String, val pkg: String, val icon: Drawable, val intent: Intent)

class HexView(c: Context, private val icon: Drawable) : View(c) {
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 4f }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PANEL }
    private fun hex(cx: Float, cy: Float, r: Float) = Path().apply {
        for (k in 0..5) {
            val a = Math.toRadians(-90.0 + 60 * k)
            val x = cx + r * cos(a).toFloat(); val y = cy + r * sin(a).toFloat()
            if (k == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    override fun onDraw(cv: Canvas) {
        val cx = width / 2f; val cy = height / 2f; val r = min(width, height) / 2f - 3f
        stroke.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), intArrayOf(CYAN, VIOLET, PINK), null, Shader.TileMode.CLAMP)
        val outer = hex(cx, cy, r)
        cv.drawPath(outer, fill); cv.drawPath(outer, stroke)
        cv.save(); cv.clipPath(hex(cx, cy, r - 5f))
        val s = (r * 1.15f).toInt()
        icon.setBounds((cx - s / 2).toInt(), (cy - s / 2).toInt(), (cx + s / 2).toInt(), (cy + s / 2).toInt())
        icon.draw(cv); cv.restore()
    }
}

class HelixView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000; repeatCount = ValueAnimator.INFINITE; addUpdateListener { invalidate() }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(cv: Canvas) {
        val t = System.nanoTime() / 1e6
        val cx = width / 2f; val amp = min(width * 0.42f, 520f); val step = 48f
        var i = 0
        while (i * step < height) {
            val y = i * step + step / 2; val ph = t * 0.0006 + i * 0.32
            val s = sin(ph).toFloat(); val z = cos(ph).toFloat()
            if (i % 2 == 0) { p.color = Color.argb(28, 139, 92, 246); p.strokeWidth = 2f; cv.drawLine(cx + s * amp, y, cx - s * amp, y, p) }
            p.color = Color.argb((255 * (0.18 + 0.22 * (z + 1) / 2)).toInt(), 47, 214, 255)
            cv.drawCircle(cx + s * amp, y, 4f + 5f * (z + 1) / 2, p)
            p.color = Color.argb((255 * (0.18 + 0.22 * (1 - z) / 2)).toInt(), 255, 79, 163)
            cv.drawCircle(cx - s * amp, y, 4f + 5f * (1 - z) / 2, p)
            i++
        }
    }
}

class MainActivity : Activity() {
    private var all = listOf<App>()
    private var shown = listOf<App>()
    private lateinit var search: EditText
    private lateinit var grid: GridView
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        window.statusBarColor = Color.TRANSPARENT; window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val root = FrameLayout(this)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(16), dp(22), dp(10)) }
        root.addView(col, FrameLayout.LayoutParams(-1, -1))
        root.setOnApplyWindowInsetsListener { v, i -> v.setPadding(0, i.systemWindowInsetTop, 0, i.systemWindowInsetBottom); i }

        val clock = TextClock(this).apply {
            format12Hour = "HH:mm"; format24Hour = "HH:mm"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f); typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            paint.shader = LinearGradient(0f, 0f, dp(240).toFloat(), 0f, intArrayOf(CYAN, VIOLET, PINK), null, Shader.TileMode.CLAMP)
        }
        val date = TextClock(this).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; setTextColor(MUTED)
        }
        val hello = TextView(this).apply {
            text = "COREGENIX  \u00B7  Sant Kr"; setTextColor(CYAN); setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(0, dp(14), 0, dp(12))
        }
        search = EditText(this).apply {
            hint = "Search apps or the web"; setHintTextColor(MUTED); setTextColor(Color.WHITE)
            isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_GO; setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(0xB30B1224.toInt()); setStroke(dp(1), 0x592FD6FF) }
        }
        grid = GridView(this).apply { numColumns = 4; verticalSpacing = dp(14); selector = ColorDrawable(0); isVerticalScrollBarEnabled = false }
        grid.adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(p: Int) = shown[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, v: View?, g: ViewGroup): View {
                val a = shown[p]
                return LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                    addView(HexView(context, a.icon), LinearLayout.LayoutParams(dp(62), dp(70)))
                    addView(TextView(context).apply {
                        text = a.label; setTextColor(Color.WHITE); setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; gravity = Gravity.CENTER; setPadding(dp(2), dp(4), dp(2), 0)
                    })
                }
            }
        }
        grid.setOnItemClickListener { _, _, i, _ -> startActivity(shown[i].intent) }
        grid.setOnItemLongClickListener { _, _, i, _ ->
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + shown[i].pkg))); true
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { filter() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        search.setOnEditorActionListener { _, _, _ ->
            val q = search.text.toString().trim()
            if (shown.isNotEmpty()) startActivity(shown[0].intent)
            else if (q.isNotEmpty()) startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
            search.setText(""); true
        }

        val dock = LinearLayout(this).apply {
            gravity = Gravity.CENTER; setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply { cornerRadius = dp(26).toFloat(); setColor(0xCC0B1224.toInt()); setStroke(dp(1), 0x738B5CF6) }
        }
        listOf(
            Intent(Intent.ACTION_DIAL), Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_BROWSER), Intent(Settings.ACTION_SETTINGS)
        ).forEach { it ->
            val ri = packageManager.resolveActivity(it, 0) ?: return@forEach
            val hv = HexView(this, ri.loadIcon(packageManager)).apply { setOnClickListener { _ -> startActivity(it) } }
            dock.addView(hv, LinearLayout.LayoutParams(dp(54), dp(60)).apply { setMargins(dp(8), 0, dp(8), 0) })
        }

        col.addView(clock); col.addView(date); col.addView(hello); col.addView(search)
        col.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(20) })
        col.addView(dock, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8) })
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        all = pm.queryIntentActivities(q, 0).filter { it.activityInfo.packageName != packageName }.map {
            val ai = it.activityInfo
            App(it.loadLabel(pm).toString(), ai.packageName, it.loadIcon(pm),
                Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setClassName(ai.packageName, ai.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.sortedBy { it.label.lowercase() }
        filter()
    }

    private fun filter() {
        val q = search.text.toString().trim().lowercase()
        shown = if (q.isEmpty()) all else all.filter { it.label.lowercase().contains(q) }
        (grid.adapter as BaseAdapter).notifyDataSetChanged()
    }

    override fun onNewIntent(i: Intent?) { super.onNewIntent(i); search.setText(""); grid.setSelection(0) }
    @Deprecated("launcher stays on top")
    override fun onBackPressed() {}
}

package com.coregenix.launcher

import android.content.Context
import android.graphics.*
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.*
import kotlin.random.Random

class LiveWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = E()

    inner class E : Engine(), SensorEventListener {
        private val h = Handler(Looper.getMainLooper())
        private val sm = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bgPaint = Paint()
        private var vis = false
        private var w = 1080; private var hh = 2400
        private var gx = 0f; private var gy = 1f; private var tx = 0f; private var ty = 1f
        private var last = SystemClock.uptimeMillis()
        private val N = 64
        private val px = Array(2) { FloatArray(N + 1) }
        private val py = Array(2) { FloatArray(N + 1) }
        private val pz = Array(2) { FloatArray(N + 1) }
        private val pr = Array(2) { FloatArray(N + 1) }
        private val o = FloatArray(3)
        private val ring = Array(6) { FloatArray(3) }
        private val sx = FloatArray(140) { Random.nextFloat() * 2 - 1 }
        private val sy = FloatArray(140) { Random.nextFloat() * 2 - 1 }
        private val sz = FloatArray(140) { Random.nextFloat() }
        private val tick = Runnable { frame() }
        private var yaw = 0f; private var pitch = 0f

        override fun onSurfaceChanged(holder: SurfaceHolder, f: Int, width: Int, height: Int) {
            w = width; hh = height
            bgPaint.shader = RadialGradient(w / 2f, hh * 0.45f, max(w, hh) * 0.75f,
                0xFF0E1434.toInt(), 0xFF030510.toInt(), Shader.TileMode.CLAMP)
        }
        override fun onVisibilityChanged(v: Boolean) {
            vis = v; h.removeCallbacks(tick)
            if (v) {
                sm.registerListener(this, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME)
                last = SystemClock.uptimeMillis(); frame()
            } else sm.unregisterListener(this)
        }
        override fun onDestroy() { h.removeCallbacks(tick); sm.unregisterListener(this); super.onDestroy() }
        override fun onSensorChanged(e: SensorEvent) {
            gx = (-e.values[0] / 9.8f).coerceIn(-1f, 1f); gy = (e.values[1] / 9.8f).coerceIn(-1f, 1f)
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) {}

        private fun frame() {
            var c: Canvas? = null
            try { c = surfaceHolder.lockCanvas(); if (c != null) draw(c) }
            catch (e: Exception) {}
            finally { if (c != null) try { surfaceHolder.unlockCanvasAndPost(c) } catch (e: Exception) {} }
            h.removeCallbacks(tick); if (vis) h.postDelayed(tick, 16)
        }

        // rotate by yaw (around Y) then pitch (around X) into o
        private fun rot(x: Float, y: Float, z: Float, a: Float, b: Float) {
            val x1 = x * cos(a) + z * sin(a); val z1 = -x * sin(a) + z * cos(a)
            o[0] = x1; o[1] = y * cos(b) - z1 * sin(b); o[2] = y * sin(b) + z1 * cos(b)
        }
        private fun col(a: Float, r: Int, g: Int, b: Int) = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), r, g, b)

        private fun draw(c: Canvas) {
            val now = SystemClock.uptimeMillis(); val dt = (now - last) / 1000f; last = now
            val t = now / 1000f
            tx += (gx - tx) * 0.06f; ty += (gy - ty) * 0.06f
            yaw = tx * 0.55f; pitch = (ty - 1f) * 0.6f
            val u = w / 1080f; val cx = w / 2f; val cy = hh * 0.45f; val D = 1400f * u
            c.drawRect(0f, 0f, w.toFloat(), hh.toFloat(), bgPaint)

            // warp stars
            for (i in sx.indices) {
                sz[i] -= dt * 0.08f; if (sz[i] < 0.02f) { sz[i] = 1f; sx[i] = Random.nextFloat() * 2 - 1; sy[i] = Random.nextFloat() * 2 - 1 }
                val f = 1f - sz[i]
                p.color = col(f * 0.8f, 160, 200, 255)
                c.drawCircle(cx + (sx[i] + tx * 0.2f) * w * 0.7f * f, cy + sy[i] * hh * 0.7f * f, (0.8f + 3f * f) * u, p)
            }

            // helix points
            val R = 210f * u; val halfH = hh * 0.40f
            for (i in 0..N) {
                val y = (i / N.toFloat() - 0.5f) * 2f * halfH
                val th = i * 0.32f + t * 0.8f
                for (s in 0..1) {
                    val a = th + s * PI.toFloat()
                    rot(R * cos(a), y, R * sin(a), yaw, pitch)
                    val sc = D / (D + o[2])
                    px[s][i] = cx + o[0] * sc; py[s][i] = cy + o[1] * sc; pz[s][i] = o[2]; pr[s][i] = sc
                }
            }
            // rungs
            p.style = Paint.Style.STROKE
            for (i in 0..N step 2) {
                val near = ((-(pz[0][i] + pz[1][i]) / 2f) / (R * 1.5f) + 1f) / 2f
                p.strokeWidth = 2.5f * u; p.color = col(0.15f + 0.3f * near, 139, 92, 246)
                c.drawLine(px[0][i], py[0][i], px[1][i], py[1][i], p)
            }
            // strands
            for (s in 0..1) for (i in 1..N) {
                val near = ((-pz[s][i]) / (R * 1.5f) + 1f) / 2f
                p.strokeWidth = (2f + 3f * near) * u
                p.color = if (s == 0) col(0.3f + 0.6f * near, 47, 214, 255) else col(0.3f + 0.6f * near, 255, 79, 163)
                c.drawLine(px[s][i - 1], py[s][i - 1], px[s][i], py[s][i], p)
            }
            // dots: far first, then near
            p.style = Paint.Style.FILL
            for (pass in 0..1) for (s in 0..1) for (i in 0..N) {
                val far = pz[s][i] > 0
                if ((pass == 0) != far) continue
                val near = ((-pz[s][i]) / (R * 1.5f) + 1f) / 2f
                val r = (4f + 8f * near) * u * pr[s][i]
                val (cr, cg, cb) = if (s == 0) Triple(47, 214, 255) else Triple(255, 79, 163)
                p.color = col(0.18f * (0.4f + near), cr, cg, cb); c.drawCircle(px[s][i], py[s][i], r * 2.4f, p)
                p.color = col(0.4f + 0.6f * near, cr, cg, cb); c.drawCircle(px[s][i], py[s][i], r, p)
            }

            // orbiting hex rings
            p.style = Paint.Style.STROKE
            for (k in 0..2) {
                val rr = (330f + k * 70f) * u; val yOff = (k - 1) * hh * 0.2f
                val spin = t * (0.4f + 0.2f * k) * (if (k == 1) -1 else 1); val tilt = 0.9f + k * 0.5f + sin(t * 0.5f + k) * 0.2f
                for (v in 0..5) {
                    val a = v * (PI.toFloat() / 3f)
                    rot(rr * cos(a), 0f, rr * sin(a), spin, tilt)
                    val y = o[1] + yOff; val xx = o[0]; val zz = o[2]
                    rot(xx, y, zz, yaw, pitch)
                    val sc = D / (D + o[2]); ring[v][0] = cx + o[0] * sc; ring[v][1] = cy + o[1] * sc; ring[v][2] = o[2]
                }
                p.strokeWidth = 2f * u
                for (v in 0..5) {
                    val n = ring[(v + 1) % 6]; val a = ring[v]
                    val near = ((-(a[2] + n[2]) / 2f) / (rr * 1.2f) + 1f) / 2f
                    p.color = if (k == 1) col(0.1f + 0.35f * near, 139, 92, 246) else col(0.1f + 0.35f * near, 47, 214, 255)
                    c.drawLine(a[0], a[1], n[0], n[1], p)
                }
            }

            // wordmark
            p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.textSize = 34f * u
            p.letterSpacing = 0.35f; p.typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            p.color = col(0.55f, 47, 214, 255)
            c.drawText("COREGENIX", cx, hh * 0.94f, p); p.letterSpacing = 0f
        }
    }
}

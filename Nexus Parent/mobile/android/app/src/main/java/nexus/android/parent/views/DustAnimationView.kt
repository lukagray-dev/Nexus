package nexus.android.parent.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import nexus.android.parent.R
import kotlin.math.*
import kotlin.random.Random

/**
 * DustAnimationView - Ethereal dust particle animation background
 * Port of the desktop dust-animation.js to Android Canvas
 */
class DustAnimationView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Particle storage and rendering
    private val particles = mutableListOf<DustParticle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val noise = SimplexNoise()
    
    // Animation state
    private var time = 0f
    private var touchX = -1000f
    private var touchY = -1000f
    
    // Frame rate control for smooth performance
    private var lastFrameTime = 0L
    private val targetFrameTime = 16L // ~60 FPS
    
    // Animation configuration - adjust these values to customize the effect
    private val config = Config(
        // PARTICLE COUNT: Number of dust particles (lower = better performance)
        // Recommended: 1000-3000 for smooth performance on most devices
        particleCount = 500,
        
        // BASE SPEED: How fast particles move (higher = faster movement)
        // Recommended: 0.05-0.2
        baseSpeed = 0.1f,
        
        // NOISE SCALE: Controls the "wave" size of particle movement (lower = larger waves)
        // Recommended: 0.05-0.15
        noiseScale = 0.1f,
        
        // TIME SCALE: Speed of the noise animation (higher = faster pattern changes)
        // Recommended: 0.0005-0.002
        timeScale = 0.008f,
        
        // PARTICLE COLOR: RGB values (0-255) for particle color
        // Default: Light blue-white (230, 240, 255)
        colorR = 230,
        colorG = 240,
        colorB = 255,
        
        // BOUNCE DAMPENING: Energy loss when bouncing off edges (higher = less bouncy)
        // Recommended: 1.5-3.0
        bounceDampening = 3.0f,
        
        // TOUCH REPEL RADIUS: Distance in pixels where touch affects particles
        // Recommended: 80-150
        touchRepelRadius = 120f,
        
        // TOUCH REPEL STRENGTH: How strongly touch pushes particles away
        // Recommended: 0.05-0.2
        touchRepelStrength = 0.12f
    )
    
    init {
        // Set background color
        setBackgroundColor(context.getColor(R.color.background_primary))
        paint.style = Paint.Style.FILL
        
        // Enable hardware acceleration for GPU rendering (much faster)
        setLayerType(LAYER_TYPE_HARDWARE, paint)
        
        // Disable anti-aliasing for better performance (particles are small anyway)
        paint.isAntiAlias = false
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Reinitialize particles when view size changes (e.g., rotation)
        initParticles()
    }
    
    private fun initParticles() {
        // Clear existing particles and create new ones
        particles.clear()
        for (i in 0 until config.particleCount) {
            particles.add(DustParticle(width.toFloat(), height.toFloat()))
        }
        // Trigger first draw
        invalidate()
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Track touch position for particle repel effect
        when (event.action) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_MOVE -> {
                // Update touch position to current touch
                touchX = event.x
                touchY = event.y
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                // Move touch position off-screen to disable repel effect
                touchX = -1000f
                touchY = -1000f
            }
        }
        return true
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Frame rate limiting for consistent performance
        val currentTime = System.currentTimeMillis()
        val deltaTime = currentTime - lastFrameTime
        
        // Skip frame if we're rendering too fast (prevents unnecessary CPU usage)
        if (deltaTime < targetFrameTime) {
            postInvalidateDelayed(targetFrameTime - deltaTime)
            return
        }
        lastFrameTime = currentTime
        
        // Increment animation time (controls noise pattern evolution)
        time += 1f
        
        // Pre-calculate color for all particles (avoid repeated calculations)
        val baseColor = android.graphics.Color.rgb(config.colorR, config.colorG, config.colorB)
        
        // Update and draw each particle
        for (particle in particles) {
            // Update particle position based on physics and noise
            particle.update(
                noise, time, config,
                width.toFloat(), height.toFloat(),
                touchX, touchY
            )
            
            // Calculate flicker effect for organic feel
            // sin() creates a smooth oscillation, multiplied by particle's life for variation
            val flicker = sin(particle.life * 0.05f) * 0.1f
            
            // Combine base alpha with flicker, clamped between 0 and 1
            val alpha = (particle.baseAlpha + flicker).coerceIn(0f, 1f)
            
            // Set particle color with calculated alpha (transparency)
            paint.color = (baseColor and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)
            
            // Draw particle as a circle at its current position
            canvas.drawCircle(particle.x, particle.y, particle.size, paint)
        }
        
        // Request next frame to continue animation loop
        invalidate()
    }
    
    /**
     * Configuration for dust animation
     */
    private data class Config(
        val particleCount: Int,
        val baseSpeed: Float,
        val noiseScale: Float,
        val timeScale: Float,
        val colorR: Int,
        val colorG: Int,
        val colorB: Int,
        val bounceDampening: Float,
        val touchRepelRadius: Float,
        val touchRepelStrength: Float
    )
    
    /**
     * Individual dust particle with physics simulation
     */
    private class DustParticle(private var width: Float, private var height: Float) {
        // Particle position
        var x = Random.nextFloat() * width
        var y = Random.nextFloat() * height
        
        // Depth (z-axis) - affects size and opacity (0 = far, 1 = near)
        var z = Random.nextFloat()
        
        // Visual properties based on depth
        // SIZE: Particles closer to camera (higher z) appear larger
        // Range: 1.0 to 2.0 pixels
        var size = (1 - z) * 0.5f + 1f + Random.nextFloat() * 0.5f
        
        // BASE ALPHA: Particles closer to camera are more opaque
        // Range: 0.3 to 1.0
        var baseAlpha = (1 - z) * 0.7f + 0.3f
        
        // LIFE: Countdown timer for particle respawn (adds variation)
        var life = Random.nextFloat() * 1000f
        
        // Velocity (speed and direction)
        private var vx = 0f  // Horizontal velocity
        private var vy = 0f  // Vertical velocity
        
        fun reset() {
            // Respawn particle at random position with new properties
            x = Random.nextFloat() * width
            y = Random.nextFloat() * height
            z = Random.nextFloat()
            size = (1 - z) * 0.5f + 1f + Random.nextFloat() * 0.5f
            baseAlpha = (1 - z) * 0.7f + 0.3f
            life = Random.nextFloat() * 1000f
        }
        
        fun update(
            noise: SimplexNoise,
            time: Float,
            config: Config,
            width: Float,
            height: Float,
            touchX: Float,
            touchY: Float
        ) {
            // Update dimensions if view size changed
            this.width = width
            this.height = height
            
            // === 1. NOISE-BASED MOVEMENT (Organic wave-like motion) ===
            // Get 3D Perlin noise value for smooth, natural movement
            val n = noise.noise3D(
                x * config.noiseScale,      // X position in noise space
                y * config.noiseScale,      // Y position in noise space
                time * config.timeScale     // Time dimension (creates animation)
            )
            
            // Convert noise to angle and speed
            val angle = n * PI.toFloat() * 4f  // Noise controls direction
            val speed = config.baseSpeed * (1 - z * 0.5f)  // Farther particles move slower
            
            // Apply noise-based acceleration (gradual movement changes)
            vx += cos(angle) * speed * 0.1f
            vy += sin(angle) * speed * 0.1f
            
            // === 2. TOUCH REPEL EFFECT (Interactive touch response) ===
            val tx = touchX - x  // Distance to touch X
            val ty = touchY - y  // Distance to touch Y
            val tDist = sqrt(tx * tx + ty * ty)  // Total distance to touch
            
            // Only apply repel if within radius and touch is active
            if (tDist < config.touchRepelRadius && tDist > 0) {
                // Calculate repel strength (stronger when closer)
                val repelFactor = config.touchRepelStrength * (1 - tDist / config.touchRepelRadius)
                
                // Push particle away from touch point
                vx -= (tx / tDist) * repelFactor
                vy -= (ty / tDist) * repelFactor
            }
            
            // === 3. UPDATE POSITION ===
            x += vx
            y += vy
            
            // === 4. BOUNDARY COLLISION (Bounce off edges) ===
            // Right edge
            if (x > width) {
                x = width
                vx *= -config.bounceDampening  // Reverse and dampen velocity
            }
            // Left edge
            else if (x < 0) {
                x = 0f
                vx *= -config.bounceDampening
            }
            
            // Bottom edge
            if (y > height) {
                y = height
                vy *= -config.bounceDampening
            }
            // Top edge
            else if (y < 0) {
                y = 0f
                vy *= -config.bounceDampening
            }
            
            // === 5. VELOCITY DAMPENING (Friction/air resistance) ===
            // Gradually slow down particles for smooth, natural motion
            vx *= 0.98f
            vy *= 0.98f
            
            // === 6. PARTICLE LIFECYCLE ===
            // Decrease life counter
            life -= 1f
            
            // Respawn particle when life reaches zero
            if (life <= 0) {
                reset()
            }
        }
    }
    
    /**
     * Simplex Noise implementation for organic particle movement
     * Based on Ken Perlin's improved noise algorithm
     * 
     * This creates smooth, natural-looking random patterns that change gradually
     * over space and time, perfect for simulating organic motion like dust or smoke.
     */
    private class SimplexNoise {
        // Gradient vectors for 3D noise (12 directions)
        private val grad3 = arrayOf(
            intArrayOf(1, 1, 0), intArrayOf(-1, 1, 0), intArrayOf(1, -1, 0),
            intArrayOf(-1, -1, 0), intArrayOf(1, 0, 1), intArrayOf(-1, 0, 1),
            intArrayOf(1, 0, -1), intArrayOf(-1, 0, -1), intArrayOf(0, 1, 1),
            intArrayOf(0, -1, 1), intArrayOf(0, 1, -1), intArrayOf(0, -1, -1)
        )
        
        // Permutation table for pseudo-random gradient selection
        private val p = intArrayOf(
            151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225,
            140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23, 190, 6, 148,
            247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32,
            57, 177, 33, 88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175,
            74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83, 111, 229, 122,
            60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54,
            65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169,
            200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64,
            52, 217, 226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212,
            207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42, 223, 183, 170, 213,
            119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9,
            129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104,
            218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241,
            81, 51, 145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157,
            184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254, 138, 236, 205, 93,
            222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180
        )
        
        // Extended permutation tables for faster lookups
        private val perm = IntArray(512)
        private val permMod12 = IntArray(512)
        
        init {
            // Duplicate permutation table to avoid overflow
            for (i in 0 until 512) {
                perm[i] = p[i and 255]
                permMod12[i] = perm[i] % 12
            }
        }
        
        // Calculate dot product of gradient and distance vectors
        private fun dot(g: IntArray, x: Float, y: Float, z: Float): Float {
            return g[0] * x + g[1] * y + g[2] * z
        }
        
        /**
         * Generate 3D Simplex Noise value
         * 
         * @param xin X coordinate in noise space
         * @param yin Y coordinate in noise space
         * @param zin Z coordinate (typically time for animation)
         * @return Noise value between approximately -1.0 and 1.0
         */
        fun noise3D(xin: Float, yin: Float, zin: Float): Float {
            val F3 = 1.0f / 3.0f
            val G3 = 1.0f / 6.0f
            
            // Skew the input space
            val s = (xin + yin + zin) * F3
            val i = floor(xin + s).toInt()
            val j = floor(yin + s).toInt()
            val k = floor(zin + s).toInt()
            
            val t = (i + j + k) * G3
            val X0 = i - t
            val Y0 = j - t
            val Z0 = k - t
            val x0 = xin - X0
            val y0 = yin - Y0
            val z0 = zin - Z0
            
            // Determine simplex
            val i1: Int
            val j1: Int
            val k1: Int
            val i2: Int
            val j2: Int
            val k2: Int
            
            if (x0 >= y0) {
                if (y0 >= z0) {
                    i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0
                } else if (x0 >= z0) {
                    i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1
                } else {
                    i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1
                }
            } else {
                if (y0 < z0) {
                    i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1
                } else if (x0 < z0) {
                    i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1
                } else {
                    i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0
                }
            }
            
            val x1 = x0 - i1 + G3
            val y1 = y0 - j1 + G3
            val z1 = z0 - k1 + G3
            val x2 = x0 - i2 + 2.0f * G3
            val y2 = y0 - j2 + 2.0f * G3
            val z2 = z0 - k2 + 2.0f * G3
            val x3 = x0 - 1.0f + 3.0f * G3
            val y3 = y0 - 1.0f + 3.0f * G3
            val z3 = z0 - 1.0f + 3.0f * G3
            
            val ii = i and 255
            val jj = j and 255
            val kk = k and 255
            
            val gi0 = permMod12[ii + perm[jj + perm[kk]]]
            val gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]]
            val gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]]
            val gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]]
            
            var n0: Float
            var n1: Float
            var n2: Float
            var n3: Float
            
            var t0 = 0.6f - x0 * x0 - y0 * y0 - z0 * z0
            if (t0 < 0) {
                n0 = 0.0f
            } else {
                t0 *= t0
                n0 = t0 * t0 * dot(grad3[gi0], x0, y0, z0)
            }
            
            var t1 = 0.6f - x1 * x1 - y1 * y1 - z1 * z1
            if (t1 < 0) {
                n1 = 0.0f
            } else {
                t1 *= t1
                n1 = t1 * t1 * dot(grad3[gi1], x1, y1, z1)
            }
            
            var t2 = 0.6f - x2 * x2 - y2 * y2 - z2 * z2
            if (t2 < 0) {
                n2 = 0.0f
            } else {
                t2 *= t2
                n2 = t2 * t2 * dot(grad3[gi2], x2, y2, z2)
            }
            
            var t3 = 0.6f - x3 * x3 - y3 * y3 - z3 * z3
            if (t3 < 0) {
                n3 = 0.0f
            } else {
                t3 *= t3
                n3 = t3 * t3 * dot(grad3[gi3], x3, y3, z3)
            }
            
            return 32.0f * (n0 + n1 + n2 + n3)
        }
    }
}

package nexus.android.child.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin
import kotlin.random.Random
import androidx.core.graphics.toColorInt

data class Particle(
    val id: Int,
    val initialX: Float,
    val initialY: Float,
    val char: Char,
    val speed: Float,
    val size: Int,
    val opacity: Float,
    var currentY: Float = initialY,
)

class ParticleBackgroundView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = "#04FF00".toColorInt()
        textSize = 18f
        typeface = android.graphics.Typeface.MONOSPACE
    }
    
    private var startTime = System.currentTimeMillis()
    private val chars = "ProjectDeltaProjectDeltaProjectDeltaProjectDeltaProjectDeltaProjectDeltaProjectDeltaProjectDelta"
    
    init {
        // Absolute black background
        setBackgroundColor(Color.BLACK)
        initializeParticles()
    }
    
    private fun initializeParticles() {
        particles.clear()
        repeat(120) { id ->
            particles.add(
                Particle(
                    id = id,
                    initialX = Random.nextFloat() * (width.coerceAtLeast(400).toFloat()),
                    initialY = Random.nextFloat() * -200f,
                    char = chars[Random.nextInt(chars.length)],
                    speed = Random.nextFloat() * 1.2f + 0.5f,
                    size = Random.nextInt(10, 16),
                    opacity = Random.nextFloat() * 0.8f + 0.3f
                )
            )
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Fill with absolute black
        canvas.drawColor(Color.BLACK)
        
        val currentTime = System.currentTimeMillis() - startTime
        
        particles.forEach { particle ->
            // Calculate animation progress
            val cycleDuration = (12000 / particle.speed).toLong()
            val progress = (currentTime % cycleDuration) / cycleDuration.toFloat()
            
            // Calculate Y position (falling from top to bottom)
            val yOffset = particle.initialY + (progress * (height + 200).toFloat())
            
            // Reset if particle goes off screen
            if (yOffset > height) {
                particle.currentY = particle.initialY
            } else {
                particle.currentY = yOffset
            }
            
            // Add slight horizontal wave motion
            val xOffset = particle.initialX + sin(progress * Math.PI * 2).toFloat() * 20
            
            // Set paint properties
            paint.textSize = particle.size.toFloat()
            paint.alpha = (particle.opacity * 0.8f * 255).toInt()
            
            // Draw particle
            canvas.drawText(
                particle.char.toString(),
                xOffset,
                particle.currentY,
                paint
            )
        }
        
        // Continue animation
        invalidate()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (particles.isEmpty() || w > 0 && h > 0) {
            initializeParticles()
        }
    }
}

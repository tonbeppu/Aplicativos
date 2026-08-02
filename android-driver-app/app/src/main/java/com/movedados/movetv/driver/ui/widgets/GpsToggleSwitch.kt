package com.movedados.movetv.driver.ui.widgets

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator

/**
 * Slider de "GPS Ligado / GPS Desligado" com trilho colorido (vermelho quando ligado,
 * verde quando desligado), texto visível dentro da área colorida, e um polegar com
 * efeito metálico que o usuário arrasta com o dedo (não é só um toque/clique).
 */
class GpsToggleSwitch @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private var checked = false
    private var thumbFraction = 0f // 0f = esquerda (desligado), 1f = direita (ligado)

    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    private val colorOff = Color.parseColor("#10B981") // verde — GPS Desligado
    private val colorOn = Color.parseColor("#EF4444")   // vermelho — GPS Ligado
    private val argbEvaluator = ArgbEvaluator()

    private var currentTrackColor = colorOff

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#40000000")
    }

    // Controle do arraste
    private var downX = 0f
    private var downY = 0f
    private var dragStartFraction = 0f
    private var dragging = false

    init {
        thumbFraction = if (checked) 1f else 0f
        currentTrackColor = if (checked) colorOn else colorOff
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = (180 * resources.displayMetrics.density).toInt()
        val desiredHeight = (52 * resources.displayMetrics.density).toInt()
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    private fun minCenterX() = height / 2f
    private fun maxCenterX() = width - height / 2f

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val trackRect = RectF(0f, 0f, w, h)
        val cornerRadius = h / 2f

        // Trilho: cor vermelha/verde interpolada conforme a posição do polegar
        trackPaint.color = currentTrackColor
        canvas.drawRoundRect(trackRect, cornerRadius, cornerRadius, trackPaint)

        val thumbRadius = h / 2f - dp(4)
        val minCx = minCenterX()
        val maxCx = maxCenterX()
        val thumbCenterX = minCx + (maxCx - minCx) * thumbFraction

        // Texto: sempre no espaço livre, do lado OPOSTO ao polegar (nunca atrás dele)
        val label = if (thumbFraction > 0.5f) "GPS Ligado" else "GPS Desligado"
        val padding = dp(10)
        val freeSpaceCenterX: Float
        val freeSpaceWidth: Float
        if (thumbFraction > 0.5f) {
            // polegar à direita -> texto no espaço à esquerda dele
            val freeStart = padding
            val freeEnd = thumbCenterX - thumbRadius - dp(6)
            freeSpaceCenterX = (freeStart + freeEnd) / 2f
            freeSpaceWidth = (freeEnd - freeStart).coerceAtLeast(dp(20))
        } else {
            // polegar à esquerda -> texto no espaço à direita dele
            val freeStart = thumbCenterX + thumbRadius + dp(6)
            val freeEnd = w - padding
            freeSpaceCenterX = (freeStart + freeEnd) / 2f
            freeSpaceWidth = (freeEnd - freeStart).coerceAtLeast(dp(20))
        }

        // Ajusta o tamanho do texto para caber no espaço disponível (nunca corta/foge da tela)
        textPaint.textSize = h * 0.30f
        var textWidth = textPaint.measureText(label)
        while (textWidth > freeSpaceWidth && textPaint.textSize > dp(9)) {
            textPaint.textSize -= 1f
            textWidth = textPaint.measureText(label)
        }

        val textY = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, freeSpaceCenterX, textY, textPaint)

        // Sombra do polegar (dá profundidade)
        canvas.drawCircle(thumbCenterX, h / 2f + dp(1.5f), thumbRadius, thumbShadowPaint)

        // Polegar com gradiente radial (efeito metálico/cromado)
        thumbPaint.shader = RadialGradient(
            thumbCenterX - thumbRadius * 0.3f, h / 2f - thumbRadius * 0.3f, thumbRadius * 1.3f,
            intArrayOf(Color.WHITE, Color.parseColor("#D8D8D8"), Color.parseColor("#9A9A9A")),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(thumbCenterX, h / 2f, thumbRadius, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val minCx = minCenterX()
        val maxCx = maxCenterX()
        val range = (maxCx - minCx).coerceAtLeast(1f)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                dragStartFraction = thumbFraction
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                if (!dragging && (kotlin.math.abs(dx) > dp(6) || kotlin.math.abs(event.y - downY) > dp(10))) {
                    dragging = true
                }
                if (dragging) {
                    // O polegar segue o dedo em tempo real, com a cor mudando junto
                    val newFraction = (dragStartFraction + dx / range).coerceIn(0f, 1f)
                    thumbFraction = newFraction
                    currentTrackColor = argbEvaluator.evaluate(newFraction, colorOff, colorOn) as Int
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                if (!dragging) {
                    // Toque simples (sem arrastar) também alterna, por comodidade
                    setChecked(!checked, animate = true, notify = true)
                } else {
                    // Soltou no meio do arraste: comple ta para o lado mais próximo
                    val finalChecked = thumbFraction >= 0.5f
                    setChecked(finalChecked, animate = true, notify = true)
                }
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun isChecked(): Boolean = checked

    fun setChecked(value: Boolean, animate: Boolean = true, notify: Boolean = false) {
        val alreadyThere = checked == value
        checked = value
        val targetFraction = if (value) 1f else 0f
        val targetColor = if (value) colorOn else colorOff
        val startFraction = thumbFraction
        val startColor = currentTrackColor

        if (!animate || (alreadyThere && startFraction == targetFraction)) {
            thumbFraction = targetFraction
            currentTrackColor = targetColor
            invalidate()
        } else {
            val animator = ValueAnimator.ofFloat(startFraction, targetFraction)
            animator.duration = 180
            animator.interpolator = DecelerateInterpolator()
            animator.addUpdateListener { anim ->
                val t = anim.animatedValue as Float
                thumbFraction = t
                val span = (targetFraction - startFraction)
                val progress = if (span == 0f) 1f else ((t - startFraction) / span).coerceIn(0f, 1f)
                currentTrackColor = argbEvaluator.evaluate(progress, startColor, targetColor) as Int
                invalidate()
            }
            animator.start()
        }

        if (notify) onCheckedChangeListener?.invoke(checked)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
    private fun dp(value: Int): Float = value * resources.displayMetrics.density
}

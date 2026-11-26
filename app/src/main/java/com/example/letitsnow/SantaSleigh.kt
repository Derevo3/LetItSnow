package com.example.letitsnow

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Сказочный силуэт Деда Мороза / Санты на санях с упряжкой оленей,
 * парящий высоко в небе в далекой перспективе.
 *
 * Появляется периодически (раз в 5 минут), грациозно пересекает экран
 * с лёгким покачиванием упряжки и оставляет за собой едва заметный
 * мерцающий шлейф золотистой звездной пыли.
 */
class SantaSleigh {

    companion object {
        const val INITIAL_DELAY_SEC = 300f
        const val REPEAT_INTERVAL_SEC = 300f
    }

    var isActive: Boolean = false
        private set

    var progress: Float = 0f
        private set

    // Таймер следующего появления: раз в 5 минут (300 секунд)
    private var timerUntilNextSpawn: Float = INITIAL_DELAY_SEC
    private var durationSec: Float = 11.0f

    // Текущие координаты центра силуэта на экране
    private var currentX: Float = 0f
    private var currentY: Float = 0f

    // Направление полёта: 1 = слева направо, -1 = справа налево
    private var direction: Int = 1
    private var startY: Float = 0f
    private var endY: Float = 0f

    // Фаза бега оленей (галоп)
    private var gallopPhase: Float = 0f

    // Пыльца/звёздный шлейф за санями
    private class Stardust(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var size: Float,
        var alpha: Float,
        var life: Float,
        var maxLife: Float
    )

    private val trailParticles = ArrayList<Stardust>(30)

    // Графика
    private val silhouettePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }
    private val strokePaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
        }
    }
    private val glowPaint by lazy {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
    }

    private val path by lazy { Path() }

    /**
     * Сброс состояния анимации.
     */
    fun reset() {
        isActive = false
        progress = 0f
        timerUntilNextSpawn = INITIAL_DELAY_SEC
        trailParticles.clear()
    }

    /**
     * Обновление симуляции полёта.
     */
    fun update(dtSec: Float, width: Float, height: Float, density: Float) {
        if (width <= 0f || height <= 0f) return

        // Обновление активных частиц звездного следа
        if (trailParticles.isNotEmpty()) {
            val iter = trailParticles.iterator()
            while (iter.hasNext()) {
                val p = iter.next()
                p.life += dtSec
                if (p.life >= p.maxLife) {
                    iter.remove()
                } else {
                    p.x += p.vx * dtSec
                    p.y += p.vy * dtSec
                }
            }
        }

        if (!isActive) {
            timerUntilNextSpawn -= dtSec
            if (timerUntilNextSpawn <= 0f) {
                spawn(width, height, density)
            }
            return
        }

        progress += dtSec / durationSec
        if (progress >= 1.0f) {
            isActive = false
            // Следующее появление через 5 минут (300 секунд)
            timerUntilNextSpawn = REPEAT_INTERVAL_SEC
            return
        }

        // Позиция по X: плавный пролёт от края до края с выходом за экран
        val totalWidth = width + 220f * density
        currentX = if (direction == 1) {
            -110f * density + progress * totalWidth
        } else {
            width + 110f * density - progress * totalWidth
        }

        // Плавная траектория высоко в небе: интерполяция по высоте + легкое волнообразное парение
        val baseAltitude = startY + (endY - startY) * progress
        val waveOffset = sin(progress * PI.toFloat() * 3.5f) * (6f * density)
        currentY = baseAltitude + waveOffset

        // Анимация галопа оленей
        gallopPhase += dtSec * 14f

        // Периодическое добавление искр в звёздный шлейф за санями
        if (Random.nextFloat() < 0.45f) {
            val tailOffsetX = (if (direction == 1) -36f else 36f) * density
            val tailOffsetY = 8f * density
            trailParticles.add(
                Stardust(
                    x = currentX + tailOffsetX + (Random.nextFloat() - 0.5f) * 4f * density,
                    y = currentY + tailOffsetY + (Random.nextFloat() - 0.5f) * 4f * density,
                    vx = (if (direction == 1) -12f else 12f) * density + (Random.nextFloat() - 0.5f) * 6f * density,
                    vy = (2f + Random.nextFloat() * 4f) * density,
                    size = (1.2f + Random.nextFloat() * 2.2f) * density,
                    alpha = 180f + Random.nextFloat() * 75f,
                    life = 0f,
                    maxLife = 1.0f + Random.nextFloat() * 0.8f
                )
            )
        }
    }

    private fun spawn(width: Float, height: Float, density: Float) {
        isActive = true
        progress = 0f
        durationSec = 10.0f + Random.nextFloat() * 4.0f // 10 .. 14 секунд плавного пролёта

        // Случайный выбор направления: 70% слева направо, 30% справа налево
        direction = if (Random.nextFloat() < 0.70f) 1 else -1

        // Высота в верхней части экрана (высоко в небе)
        startY = height * (0.04f + Random.nextFloat() * 0.08f)
        endY = height * (0.04f + Random.nextFloat() * 0.08f)
    }

    /**
     * Отрисовка силуэта и звездного следа.
     */
    fun draw(canvas: Canvas, density: Float, theme: SnowColorTheme) {
        // 1. Отрисовка мерцающего звездного шлейфа
        if (trailParticles.isNotEmpty()) {
            for (p in trailParticles) {
                val frac = (1.0f - p.life / p.maxLife).coerceIn(0f, 1f)
                val alpha = (p.alpha * frac).toInt().coerceIn(0, 255)
                if (alpha <= 2) continue

                glowPaint.color = 0xFFFDE047.toInt() // Теплое звездное золото
                glowPaint.alpha = (alpha * 0.45f).toInt()
                canvas.drawCircle(p.x, p.y, p.size * 1.5f, glowPaint)

                glowPaint.color = 0xFFFFFFFF.toInt()
                glowPaint.alpha = alpha
                canvas.drawCircle(p.x, p.y, p.size * 0.6f, glowPaint)
            }
        }

        if (!isActive) return

        // 2. Цветовая палитра силуэта
        val (silhouetteColor, alphaLevel) = when (theme) {
            SnowColorTheme.WHITE -> Pair(0xFF1E293B.toInt(), 220)
            SnowColorTheme.SCARLET -> Pair(0xFF2B0A10.toInt(), 225)
            SnowColorTheme.PINK -> Pair(0xFF281324.toInt(), 220)
            SnowColorTheme.BLACK -> Pair(0xFF0F172A.toInt(), 235)
        }

        canvas.save()
        canvas.translate(currentX, currentY)
        if (direction == -1) {
            canvas.scale(-1f, 1f)
        }

        // Масштабирование силуэта (легкий масштаб вдалеке)
        val s = density * 0.82f
        canvas.scale(s, s)

        silhouettePaint.color = silhouetteColor
        silhouettePaint.alpha = alphaLevel

        strokePaint.color = silhouetteColor
        strokePaint.alpha = (alphaLevel * 0.85f).toInt()
        strokePaint.strokeWidth = 1.3f

        // 3. Сани Деда Мороза
        drawSleigh(canvas)

        // 4. Фигура Деда Мороза и мешок подарков
        drawSanta(canvas)

        // 5. Упряжка: 2 оленя
        val gallopOffset1 = sin(gallopPhase) * 2.5f
        val gallopOffset2 = sin(gallopPhase + 1.2f) * 2.5f

        drawReindeer(canvas, offsetX = 24f, offsetY = gallopOffset1, isLead = false)
        drawReindeer(canvas, offsetX = 52f, offsetY = gallopOffset2, isLead = true)

        // 6. Линии упряжи (поводья)
        path.reset()
        // От рук Деда Мороза к оленям
        path.moveTo(-16f, -3f)
        path.quadTo(4f, 4f, 24f, -2f + gallopOffset1)
        path.quadTo(38f, 2f, 52f, -2f + gallopOffset2)
        canvas.drawPath(path, strokePaint)

        // Нижняя тяговая сцепка саней
        path.reset()
        path.moveTo(-14f, 7f)
        path.quadTo(5f, 9f, 24f, 3f + gallopOffset1)
        path.quadTo(38f, 7f, 52f, 3f + gallopOffset2)
        canvas.drawPath(path, strokePaint)

        canvas.restore()
    }

    /**
     * Отрисовка саней Деда Мороза.
     */
    private fun drawSleigh(canvas: Canvas) {
        // Полозья саней с изящным завитком спереди
        path.reset()
        path.moveTo(-38f, 8f)
        path.lineTo(-14f, 8f)
        path.quadTo(-10f, 8f, -9f, 3f)
        path.quadTo(-8.5f, 0f, -11f, 1f)
        strokePaint.strokeWidth = 1.8f
        canvas.drawPath(path, strokePaint)

        // Стойки полозьев
        canvas.drawLine(-32f, 8f, -32f, 4f, strokePaint)
        canvas.drawLine(-18f, 8f, -18f, 4f, strokePaint)

        // Корпус саней (деревянная чаша с высокой спинкой)
        path.reset()
        path.moveTo(-38f, -2f)
        path.quadTo(-36f, 5f, -30f, 5f)
        path.lineTo(-16f, 5f)
        path.quadTo(-12f, 5f, -13f, 0f)
        path.quadTo(-16f, 2f, -26f, 2f)
        path.close()
        canvas.drawPath(path, silhouettePaint)
    }

    /**
     * Отрисовка силуэта Деда Мороза и праздничного мешка с подарками.
     */
    private fun drawSanta(canvas: Canvas) {
        // Мешок с подарками в задней части саней
        path.reset()
        path.moveTo(-36f, 2f)
        path.quadTo(-38f, -5f, -31f, -7f)
        path.quadTo(-26f, -7f, -28f, 2f)
        path.close()
        canvas.drawPath(path, silhouettePaint)

        // Узелок мешка
        canvas.drawCircle(-31f, -7.5f, 1.8f, silhouettePaint)

        // Туловище Деда Мороза в шубе
        path.reset()
        path.moveTo(-28f, 3f)
        path.lineTo(-20f, 3f)
        path.lineTo(-18f, -4f)
        path.lineTo(-25f, -4f)
        path.close()
        canvas.drawPath(path, silhouettePaint)

        // Рука с поводьями, вытянутая вперёд
        path.reset()
        path.moveTo(-21f, -2f)
        path.lineTo(-16f, -3f)
        path.lineTo(-17f, -1f)
        path.lineTo(-21f, 0f)
        path.close()
        canvas.drawPath(path, silhouettePaint)

        // Борода
        path.reset()
        path.moveTo(-21f, -5f)
        path.quadTo(-15f, -3f, -17f, 1f)
        path.quadTo(-20f, -1f, -22f, -2f)
        path.close()
        canvas.drawPath(path, silhouettePaint)

        // Голова и шапка с помпоном, развевающимся назад
        canvas.drawCircle(-22f, -6f, 3.2f, silhouettePaint)

        // Колпак/шапка
        path.reset()
        path.moveTo(-25f, -6f)
        path.lineTo(-20f, -7f)
        path.quadTo(-25f, -11f, -30f, -8f)
        path.close()
        canvas.drawPath(path, silhouettePaint)
        // Помпончик
        canvas.drawCircle(-30.5f, -8f, 1.3f, silhouettePaint)
    }

    /**
     * Отрисовка силуэта грациозного оленя в прыжке/полёте.
     */
    private fun drawReindeer(canvas: Canvas, offsetX: Float, offsetY: Float, isLead: Boolean) {
        canvas.save()
        canvas.translate(offsetX, offsetY)

        // Корпус оленя
        path.reset()
        path.moveTo(-8f, 0f)
        path.quadTo(-9f, -3f, -3f, -3f)
        path.lineTo(4f, -3f)
        path.quadTo(8f, -2f, 6f, 1f)
        path.lineTo(-5f, 2f)
        path.close()
        canvas.drawPath(path, silhouettePaint)

        // Шея и голова
        path.reset()
        path.moveTo(4f, -3f)
        path.lineTo(8f, -8f)
        path.lineTo(12f, -7f) // Мордочка
        path.lineTo(9f, -4f)
        path.close()
        canvas.drawPath(path, silhouettePaint)

        // Рога (ветвистые, характерные для оленя)
        strokePaint.strokeWidth = 1.0f
        path.reset()
        path.moveTo(8f, -8f)
        path.quadTo(6f, -13f, 4f, -15f)
        path.moveTo(7f, -10f)
        path.lineTo(9f, -13f)
        path.moveTo(5.5f, -12f)
        path.lineTo(3.5f, -13f)
        canvas.drawPath(path, strokePaint)

        // Ноги оленя в прыжке (галоп)
        strokePaint.strokeWidth = 1.2f
        // Передние ноги (вытянуты вперёд в полёте)
        path.reset()
        path.moveTo(5f, 1f)
        path.lineTo(9f, 6f)
        path.lineTo(13f, 7f)

        path.moveTo(6f, 0f)
        path.lineTo(10f, 4f)
        path.lineTo(12f, 5.5f)

        // Задние ноги (отведены назад)
        path.moveTo(-6f, 1f)
        path.lineTo(-10f, 6f)
        path.lineTo(-14f, 7f)

        path.moveTo(-7f, 0f)
        path.lineTo(-11f, 5f)
        path.lineTo(-13f, 6f)
        canvas.drawPath(path, strokePaint)

        // Если это ведущий олень (Рудольф) — крошечный мерцающий огонёк на носу
        if (isLead) {
            glowPaint.color = 0xFFEF4444.toInt() // Праздничный рубиновый огонёк
            glowPaint.alpha = 230
            canvas.drawCircle(12.5f, -7f, 1.4f, glowPaint)

            glowPaint.alpha = 90
            canvas.drawCircle(12.5f, -7f, 3.2f, glowPaint)
        }

        canvas.restore()
    }
}

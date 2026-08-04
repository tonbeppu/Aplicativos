package com.movedados.witon.processing

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import com.movedados.witon.data.local.entity.SurveyPointEntity
import com.movedados.witon.wifi.RssiScale
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Interpolacao IDW (Inverse Distance Weighting) dos pontos de RSSI numa
 * grade 2D no plano XZ — a altura Y (vertical) nao entra no mapa, so a
 * posicao no chao.
 *
 * Celulas fora do raio de busca ficam transparentes de proposito: o heatmap
 * so pinta onde de fato foi medido, nunca "inventa" cobertura por
 * extrapolacao — a diferenca entre um mapa util e um mapa mentiroso.
 */
object HeatmapRenderer {

    private const val CELL_SIZE_M = 0.10f      // 10 cm por celula
    private const val SEARCH_RADIUS_M = 3.0f   // raio de busca do IDW
    private const val POWER = 2.0              // expoente do IDW (peso ~ 1/d^2)
    private const val MARGIN_M = 0.5f          // margem ao redor dos pontos extremos

    // Limite defensivo — evita gerar um bitmap gigante em leituras muito
    // longas ou esparsas (ambiente grande com poucos pontos capturados).
    private const val MAX_GRID_CELLS = 1200

    data class Result(
        val bitmap: Bitmap,
        /** Coordenadas do mundo AR (metros) que o canto inferior-esquerdo do bitmap representa. */
        val originX: Float,
        val originZ: Float,
        val cellSizeMeters: Float,
        val widthCells: Int,
        val heightCells: Int
    ) {
        val areaM2: Float get() = (widthCells * cellSizeMeters) * (heightCells * cellSizeMeters)
    }

    /** Roda em background (chamado com Dispatchers.Default pelo ViewModel) — varre pixel a pixel. */
    fun render(points: List<SurveyPointEntity>): Result? {
        if (points.isEmpty()) return null

        val minX = points.minOf { it.x } - MARGIN_M
        val maxX = points.maxOf { it.x } + MARGIN_M
        val minZ = points.minOf { it.z } - MARGIN_M
        val maxZ = points.maxOf { it.z } + MARGIN_M

        val widthCells = min(MAX_GRID_CELLS, max(1, ceil((maxX - minX) / CELL_SIZE_M).toInt()))
        val heightCells = min(MAX_GRID_CELLS, max(1, ceil((maxZ - minZ) / CELL_SIZE_M).toInt()))

        val bitmap = Bitmap.createBitmap(widthCells, heightCells, Bitmap.Config.ARGB_8888)

        for (row in 0 until heightCells) {
            val z = minZ + row * CELL_SIZE_M
            for (col in 0 until widthCells) {
                val x = minX + col * CELL_SIZE_M
                bitmap.setPixel(col, row, interpolate(x, z, points))
            }
        }

        return Result(bitmap, minX, minZ, CELL_SIZE_M, widthCells, heightCells)
    }

    private fun interpolate(x: Float, z: Float, points: List<SurveyPointEntity>): Int {
        var weightSum = 0.0
        var valueSum = 0.0
        var closestDist = Float.MAX_VALUE

        for (p in points) {
            val d = hypot((p.x - x).toDouble(), (p.z - z).toDouble()).toFloat()
            if (d < closestDist) closestDist = d

            // Ponto praticamente em cima da amostra — usa o valor exato,
            // evita divisao por um numero perto de zero.
            if (d < 0.02f) {
                return colorWithAlpha(RssiScale.colorFor(RssiScale.quality(p.rssi)), 0.9f)
            }
            if (d > SEARCH_RADIUS_M) continue

            val w = 1.0 / d.toDouble().pow(POWER)
            weightSum += w
            valueSum += w * p.rssi
        }

        if (weightSum == 0.0 || closestDist > SEARCH_RADIUS_M) {
            return AndroidColor.TRANSPARENT
        }

        val rssi = (valueSum / weightSum).toInt()
        // Mais perto de uma amostra real = mais opaco; longe dela (mas ainda
        // dentro do raio) = mais transparente, sinalizando visualmente que
        // e uma extrapolacao, nao uma medicao direta.
        val alpha = (1f - (closestDist / SEARCH_RADIUS_M)).coerceIn(0.35f, 0.85f)
        return colorWithAlpha(RssiScale.color(rssi), alpha)
    }

    private fun colorWithAlpha(color: Color, alpha: Float): Int = AndroidColor.argb(
        (alpha * 255).toInt(),
        (color.red * 255).toInt(),
        (color.green * 255).toInt(),
        (color.blue * 255).toInt()
    )
}

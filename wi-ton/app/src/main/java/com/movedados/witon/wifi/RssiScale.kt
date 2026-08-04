package com.movedados.witon.wifi

import androidx.compose.ui.graphics.Color
import com.movedados.witon.ui.theme.StatusGreen
import com.movedados.witon.ui.theme.StatusLime
import com.movedados.witon.ui.theme.StatusOrange
import com.movedados.witon.ui.theme.StatusRed
import com.movedados.witon.ui.theme.StatusYellow

enum class SignalQuality(val label: String) {
    EXCELENTE("Excelente"),
    BOA("Boa"),
    ACEITAVEL("Aceitavel"),
    FRACA("Fraca"),
    RUIM("Ruim")
}

/**
 * Escala de qualidade do sinal.
 *
 * Nao use interpolacao linear de -100 a 0 dBm: na pratica quase tudo cairia
 * na faixa vermelha. Os cortes abaixo seguem as referencias de projeto de
 * rede sem fio, com -67 dBm como limite util para voz e video.
 */
object RssiScale {

    const val MIN_DBM = -90
    const val MAX_DBM = -40

    fun quality(rssi: Int): SignalQuality = when {
        rssi >= -50 -> SignalQuality.EXCELENTE
        rssi >= -60 -> SignalQuality.BOA
        rssi >= -67 -> SignalQuality.ACEITAVEL
        rssi >= -75 -> SignalQuality.FRACA
        else        -> SignalQuality.RUIM
    }

    fun color(rssi: Int): Color = colorFor(quality(rssi))

    fun colorFor(quality: SignalQuality): Color = when (quality) {
        SignalQuality.EXCELENTE -> StatusGreen
        SignalQuality.BOA       -> StatusLime
        SignalQuality.ACEITAVEL -> StatusYellow
        SignalQuality.FRACA     -> StatusOrange
        SignalQuality.RUIM      -> StatusRed
    }

    /** 0f (pessimo) a 1f (otimo) — usado nas barras e no gradiente do heatmap. */
    fun normalized(rssi: Int): Float =
        ((rssi - MIN_DBM).toFloat() / (MAX_DBM - MIN_DBM)).coerceIn(0f, 1f)
}

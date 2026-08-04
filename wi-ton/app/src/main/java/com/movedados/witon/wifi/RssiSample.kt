package com.movedados.witon.wifi

data class RssiSample(
    /** RSSI suavizado (EMA), em dBm. E o valor que vai para a tela e para o heatmap. */
    val rssi: Int,
    /** Leitura bruta do chipset, sem suavizacao. Guardada para auditoria. */
    val rawRssi: Int,
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val ssid: String?,
    val bssid: String?
) {
    val band: String
        get() = when (frequencyMhz) {
            null -> "?"
            in 2400..2500 -> "2.4 GHz"
            in 5100..5900 -> "5 GHz"
            in 5925..7125 -> "6 GHz"
            else -> "?"
        }
}

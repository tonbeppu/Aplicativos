package com.movedados.witon.wifi

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

/**
 * Amostragem continua do sinal da rede JA CONECTADA.
 *
 * Por que polling e nao WifiManager.startScan():
 * o Android limita startScan() a 4 chamadas a cada 2 minutos, o que inviabiliza
 * tempo real. Esse limite vale para varredura de redes; a leitura do RSSI da
 * conexao atual nao e throttled.
 *
 * getConnectionInfo() esta deprecado desde a API 31, mas continua funcional e e
 * a forma mais estavel de amostrar em intervalo fixo. O caminho moderno
 * (NetworkCallback) e orientado a evento e atualiza de forma irregular — por isso
 * ele fica no WifiStateMonitor, cuidando de conexao/desconexao, e nao da amostragem.
 */
class RssiSampler(context: Context) {

    private val wifi = context.applicationContext
        .getSystemService(Context.WIFI_SERVICE) as WifiManager

    @Volatile
    var latest: RssiSample? = null
        private set

    private var ema: Double? = null

    fun reset() {
        ema = null
        latest = null
    }

    /**
     * @param periodMs 400 ms da fluidez visual sem inventar dado: a maioria dos
     *        chipsets atualiza o RSSI a cada ~1 s, e a EMA cobre o intervalo.
     * @param alpha peso da amostra nova na media exponencial (0..1).
     */
    fun samples(periodMs: Long = 400L, alpha: Double = 0.3): Flow<RssiSample> = flow {
        while (currentCoroutineContext().isActive) {
            read(alpha)?.let {
                latest = it
                emit(it)
            }
            delay(periodMs)
        }
    }

    @Suppress("DEPRECATION")
    private fun read(alpha: Double): RssiSample? {
        val info = wifi.connectionInfo ?: return null
        if (info.networkId == -1 && info.bssid == null) return null

        val raw = info.rssi
        if (raw == 0 || raw < -127) return null

        ema = ema?.let { alpha * raw + (1 - alpha) * it } ?: raw.toDouble()

        return RssiSample(
            rssi = ema!!.roundToInt(),
            rawRssi = raw,
            linkSpeedMbps = info.linkSpeed.takeIf { it > 0 },
            frequencyMhz = info.frequency.takeIf { it > 0 },
            ssid = info.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" },
            bssid = info.bssid?.takeIf { it != "02:00:00:00:00:00" }
        )
    }
}

package com.movedados.movetv.display.ui.pairing

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.movedados.movetv.display.R
import com.movedados.movetv.display.models.Profile
import com.movedados.movetv.display.network.SupabaseClient
import com.movedados.movetv.display.ui.display.DisplayActivity
import com.movedados.movetv.display.utils.PreferenceManager
import kotlinx.coroutines.launch

class PairingActivity : AppCompatActivity() {

    private lateinit var prefs: PreferenceManager
    private lateinit var supabase: SupabaseClient
    private lateinit var gson: Gson

    private lateinit var etDeviceId: EditText
    private lateinit var btnPair: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pairing)

        prefs = PreferenceManager(this)
        supabase = SupabaseClient(this)
        gson = Gson()

        etDeviceId = findViewById(R.id.etDeviceId)
        btnPair = findViewById(R.id.btnPair)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)

        // Se este aparelho já pareou antes, pré-preenche o campo (equivalente ao
        // localStorage.getItem('saved_device_id') do protótipo React)
        prefs.getSavedDeviceId()?.let { etDeviceId.setText(it) }

        btnPair.setOnClickListener { attemptPair() }
    }

    private fun attemptPair() {
        val deviceId = etDeviceId.text.toString().trim()
        val userId = prefs.getUserId()

        if (deviceId.isBlank()) {
            showStatus("Informe o ID do dispositivo", isError = true)
            return
        }
        if (userId == null) {
            showStatus("Sessão inválida — faça login novamente", isError = true)
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            // 1) O dispositivo existe?
            val deviceResult = supabase.fetchDevice(deviceId)
            val device = deviceResult.getOrNull()
            if (deviceResult.isFailure) {
                setLoading(false)
                showStatus("Erro ao validar dispositivo: ${deviceResult.exceptionOrNull()?.message}", isError = true)
                return@launch
            }
            if (device == null) {
                setLoading(false)
                showStatus("Dispositivo não encontrado. Verifique o ID informado.", isError = true)
                return@launch
            }

            // 2) Já está em uso por outro usuário?
            val takenResult = supabase.isDeviceTakenByOther(deviceId, userId)
            if (takenResult.getOrNull() == true) {
                setLoading(false)
                showStatus("Este dispositivo já está associado a outro usuário.", isError = true)
                return@launch
            }

            // 3) Vincula
            val pairResult = supabase.pairDevice(userId, deviceId)
            if (pairResult.isFailure) {
                setLoading(false)
                showStatus("Erro ao vincular: ${pairResult.exceptionOrNull()?.message}", isError = true)
                return@launch
            }

            prefs.saveDeviceId(deviceId)
            showStatus("Dispositivo vinculado com sucesso!", isError = false)

            // Atualiza o perfil salvo localmente com o novo device_id, e segue para a exibição
            val profileResult = supabase.fetchProfile(userId)
            profileResult.getOrNull()?.let { prefs.saveProfileJson(gson.toJson(it)) }

            startActivity(Intent(this@PairingActivity, DisplayActivity::class.java))
            finish()
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnPair.isEnabled = !loading
        etDeviceId.isEnabled = !loading
    }

    private fun showStatus(message: String, isError: Boolean) {
        tvStatus.text = message
        tvStatus.setTextColor(getColor(if (isError) R.color.error else R.color.success))
        tvStatus.visibility = View.VISIBLE
    }
}

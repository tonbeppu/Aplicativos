package com.movedados.movetv.display.ui.login

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
import com.movedados.movetv.display.ui.pairing.PairingActivity
import com.movedados.movetv.display.utils.PreferenceManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var prefs: PreferenceManager
    private lateinit var supabase: SupabaseClient
    private lateinit var gson: Gson

    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        prefs = PreferenceManager(this)
        supabase = SupabaseClient(this)
        gson = Gson()

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        progressBar = findViewById(R.id.progressBar)
        tvError = findViewById(R.id.tvError)

        // Já logado? Verifica o perfil e segue direto (equivalente ao useAuth restaurando a sessão)
        if (prefs.isLoggedIn()) {
            routeAfterAuth()
            return
        }

        btnLogin.setOnClickListener { attemptLogin() }
    }

    private fun attemptLogin() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        if (email.isBlank() || password.isBlank()) {
            showError("Preencha e-mail e senha")
            return
        }

        setLoading(true)
        lifecycleScope.launch {
            val result = supabase.login(email, password)
            if (result.isSuccess) {
                routeAfterAuth()
            } else {
                setLoading(false)
                showError(result.exceptionOrNull()?.message ?: "E-mail ou senha incorretos")
            }
        }
    }

    /** Busca o perfil e decide para onde ir: perfil errado -> erro; sem dispositivo -> pareamento;
     *  com dispositivo -> tela de exibição. Equivalente ao roteamento do App.tsx. */
    private fun routeAfterAuth() {
        val userId = prefs.getUserId() ?: return
        lifecycleScope.launch {
            val result = supabase.fetchProfile(userId)
            val profile = result.getOrNull()

            if (profile == null) {
                setLoading(false)
                showError("Perfil não encontrado")
                return@launch
            }

            prefs.saveProfileJson(gson.toJson(profile))

            if (profile.role != "tela") {
                setLoading(false)
                showError("Esta conta não tem o perfil \"tela\". Use uma conta de tela para acessar este app.")
                prefs.clearAll()
                return@launch
            }

            if (profile.device_id.isNullOrBlank()) {
                startActivity(Intent(this@LoginActivity, PairingActivity::class.java))
            } else {
                startActivity(Intent(this@LoginActivity, DisplayActivity::class.java))
            }
            finish()
        }
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        btnLogin.isEnabled = !loading
    }

    private fun showError(message: String) {
        tvError.text = message
        tvError.visibility = View.VISIBLE
    }
}

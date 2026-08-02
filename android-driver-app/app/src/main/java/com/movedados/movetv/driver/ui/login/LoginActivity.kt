package com.movedados.movetv.driver.ui.login

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.movedados.movetv.driver.R
import com.movedados.movetv.driver.databinding.ActivityLoginBinding
import com.movedados.movetv.driver.models.Profile
import com.movedados.movetv.driver.network.SupabaseClient
import com.movedados.movetv.driver.ui.MainActivity
import com.movedados.movetv.driver.utils.PreferenceManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: PreferenceManager
    private lateinit var supabase: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = PreferenceManager(this)
        supabase = SupabaseClient(this)

        setTwoToneTitle()

        if (prefs.isLoggedIn()) {
            navigateToMain()
            return
        }

        binding.btnLogin.setOnClickListener { validateAndLogin() }
        binding.tvForgotPassword.setOnClickListener { showForgotPassword() }
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, com.movedados.movetv.driver.ui.register.RegisterActivity::class.java))
        }
    }

    private fun setTwoToneTitle() {
        val text = "MoveDados"
        val spannable = SpannableString(text)
        spannable.setSpan(ForegroundColorSpan(Color.WHITE), 0, 4, 0)
        spannable.setSpan(ForegroundColorSpan(getColor(R.color.accent)), 4, text.length, 0)
        binding.tvAppName.text = spannable
    }

    private fun validateAndLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty()) {
            binding.tilEmail.error = "Informe seu email"
            return
        }
        if (password.isEmpty()) {
            binding.tilPassword.error = "Informe sua senha"
            return
        }

        binding.tilEmail.error = null
        binding.tilPassword.error = null
        setLoading(true)

        lifecycleScope.launch {
            val result = supabase.login(email, password)
            if (result.isSuccess) {
                val auth = result.getOrNull()!!
                prefs.saveAuthData(auth.accessToken, auth.refreshToken, auth.userId, email)

                val profileResult = supabase.fetchProfile(auth.userId)
                if (profileResult.isSuccess) {
                    val profile = profileResult.getOrNull()!!
                    prefs.saveProfileJson(Gson().toJson(profile))
                    supabase.insertAuditLog(auth.userId, "login")
                }

                Snackbar.make(binding.root, "Bem-vindo!", Snackbar.LENGTH_SHORT).show()
                navigateToMain()
            } else {
                setLoading(false)
                Snackbar.make(binding.root, result.exceptionOrNull()?.message ?: "Erro ao fazer login", Snackbar.LENGTH_LONG)
                    .setBackgroundTint(getColor(com.google.android.material.R.color.design_default_color_error))
                    .show()
            }
        }
    }

    private fun showForgotPassword() {
        val email = binding.etEmail.text.toString().trim()
        if (email.isEmpty()) {
            Snackbar.make(binding.root, "Digite seu email primeiro", Snackbar.LENGTH_SHORT).show()
            return
        }
        lifecycleScope.launch {
            val result = supabase.resetPassword(email)
            if (result.isSuccess) {
                Snackbar.make(binding.root, "Email de recuperação enviado", Snackbar.LENGTH_LONG).show()
            } else {
                Snackbar.make(binding.root, "Erro ao enviar email", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.etEmail.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.text = if (loading) "Entrando..." else "Entrar"
    }

    private fun navigateToMain() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}

package com.movedados.movetv.driver.ui.register

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.movedados.movetv.driver.R
import com.movedados.movetv.driver.network.SupabaseClient
import com.movedados.movetv.driver.ui.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.*

class RegisterActivity : AppCompatActivity() {

    private lateinit var supabase: SupabaseClient
    private val publicClient = OkHttpClient()
    private val gson = Gson()

    private lateinit var progressBar: ProgressBar
    private lateinit var etFullName: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var etCpf: EditText
    private lateinit var etBirthDate: EditText
    private lateinit var etPhone: EditText
    private lateinit var spState: Spinner
    private lateinit var spCity: Spinner
    private lateinit var photoPicker: FrameLayout
    private lateinit var photoPlaceholder: LinearLayout
    private lateinit var ivPhotoPreview: ImageView

    private var selectedPhotoBitmap: Bitmap? = null
    private val citiesCache = mutableMapOf<String, List<String>>()

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadBitmapFromUri(it) }
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { showPhotoPreview(it) }
    }
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePictureLauncher.launch(null)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        supabase = SupabaseClient(this)

        bindViews()
        setupHeader()
        setupState()
        setupMasks()
        setupPhotoPicker()

        findViewById<View>(R.id.btnVoltar).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCadastrar).setOnClickListener { validateAndRegister() }
    }

    private fun bindViews() {
        progressBar = findViewById(R.id.progressBar)
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        etCpf = findViewById(R.id.etCpf)
        etBirthDate = findViewById(R.id.etBirthDate)
        etPhone = findViewById(R.id.etPhone)
        spState = findViewById(R.id.spState)
        spCity = findViewById(R.id.spCity)
        photoPicker = findViewById(R.id.photoPicker)
        photoPlaceholder = findViewById(R.id.photoPlaceholder)
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview)
    }

    private fun setupHeader() {
        val title = findViewById<TextView>(R.id.tvHeaderTitle)
        val spannable = android.text.SpannableString("move dados system")
        spannable.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.text_primary)), 0, 10, 0)
        spannable.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.accent)), 10, spannable.length, 0)
        title.text = spannable
    }

    // ==================== ESTADO -> CIDADE (API do IBGE) ====================

    private fun setupState() {
        val estados = resources.getStringArray(R.array.estados_brasil).toList()
        simpleAdapter(spState, estados)
        simpleAdapter(spCity, listOf("Selecione o estado primeiro"))

        spState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    simpleAdapter(spCity, listOf("Selecione o estado primeiro"))
                    return
                }
                val uf = estados[position].substringBefore(" - ")
                loadCities(uf)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun simpleAdapter(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun loadCities(uf: String) {
        citiesCache[uf]?.let { simpleAdapter(spCity, it); return }
        simpleAdapter(spCity, listOf("Carregando..."))
        lifecycleScope.launch {
            val cities = withContext(Dispatchers.IO) { fetchCitiesFromIbge(uf) }
            citiesCache[uf] = cities
            simpleAdapter(spCity, cities)
        }
    }

    private fun fetchCitiesFromIbge(uf: String): List<String> {
        return try {
            val request = Request.Builder()
                .url("https://servicodados.ibge.gov.br/api/v1/localidades/estados/$uf/municipios")
                .build()
            val response = publicClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val array = gson.fromJson(body, JsonArray::class.java)
            val names = array.map { it.asJsonObject.get("nome").asString }.sorted()
            listOf("Selecione") + names
        } catch (e: Exception) {
            listOf("Erro ao carregar cidades")
        }
    }

    // ==================== MÁSCARAS (CPF, telefone, data) ====================

    private fun setupMasks() {
        etCpf.addTextChangedListener(MaskWatcher(etCpf, "###.###.###-##"))
        etPhone.addTextChangedListener(MaskWatcher(etPhone, "(##) #####-####"))
        etBirthDate.addTextChangedListener(MaskWatcher(etBirthDate, "##/##/####"))
    }

    private class MaskWatcher(private val editText: EditText, private val mask: String) : TextWatcher {
        private var isUpdating = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (isUpdating || s == null) return
            isUpdating = true
            val digits = s.toString().filter { it.isDigit() }
            val formatted = StringBuilder()
            var i = 0
            for (m in mask) {
                if (i >= digits.length) break
                if (m == '#') { formatted.append(digits[i]); i++ } else formatted.append(m)
            }
            editText.setText(formatted.toString())
            editText.setSelection(formatted.length)
            isUpdating = false
        }
    }

    private fun parseBirthDate(display: String): String? {
        if (display.length != 10) return null
        val parts = display.split("/")
        if (parts.size != 3) return null
        val day = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val year = parts[2].toIntOrNull() ?: return null
        if (month !in 1..12) return null
        val daysInMonth = when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
        if (day !in 1..daysInMonth) return null
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        if (year < 1900 || year > currentYear) return null
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    // ==================== FOTO DE PERFIL ====================

    private fun setupPhotoPicker() {
        photoPicker.setOnClickListener {
            val options = arrayOf("Tirar foto", "Escolher da galeria")
            android.app.AlertDialog.Builder(this)
                .setTitle("Foto de Perfil")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> requestCameraPermission.launch(android.Manifest.permission.CAMERA)
                        1 -> pickImageLauncher.launch("image/*")
                    }
                }
                .show()
        }
    }

    private fun loadBitmapFromUri(uri: Uri) {
        try {
            val stream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bitmap != null) showPhotoPreview(bitmap)
        } catch (e: Exception) {
            Snackbar.make(photoPicker, "Não foi possível carregar a imagem", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun showPhotoPreview(bitmap: Bitmap) {
        selectedPhotoBitmap = bitmap
        ivPhotoPreview.setImageBitmap(bitmap)
        ivPhotoPreview.visibility = View.VISIBLE
        photoPlaceholder.visibility = View.GONE
    }

    private fun compressToJpeg(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }

    // ==================== VALIDAÇÃO E CADASTRO ====================

    private fun validateAndRegister() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        val cpfDigits = etCpf.text.toString().filter { it.isDigit() }
        val phone = etPhone.text.toString().trim()

        if (fullName.isEmpty()) return showError("Informe o nome completo")
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
            return showError("Informe um email válido")
        if (password.length < 6) return showError("A senha deve ter ao menos 6 caracteres")
        if (password != confirmPassword) return showError("As senhas não coincidem")
        if (!isValidCpf(cpfDigits)) return showError("CPF inválido")
        val birthDateIso = parseBirthDate(etBirthDate.text.toString())
            ?: return showError("Informe uma data de nascimento válida (DD/MM/AAAA)")
        if (phone.filter { it.isDigit() }.length < 10) return showError("Informe um telefone válido")

        val state = spinnerValueOrNull(spState, "Selecione")
            ?: return showError("Selecione o estado")
        val city = spinnerValueOrNull(spCity, "Selecione", "Carregando...", "Erro ao carregar cidades", "Selecione o estado primeiro")
            ?: return showError("Selecione a cidade")

        setLoading(true)

        lifecycleScope.launch {
            val signUpResult = supabase.signUp(email, password)
            if (signUpResult.isFailure) {
                setLoading(false)
                showError(signUpResult.exceptionOrNull()?.message ?: "Erro ao criar conta")
                return@launch
            }

            val auth = signUpResult.getOrNull()!!

            if (auth.accessToken.isEmpty()) {
                setLoading(false)
                Snackbar.make(photoPicker, "Cadastro criado! Confirme seu email antes de entrar.", Snackbar.LENGTH_LONG).show()
                finish()
                return@launch
            }

            var photoUrl: String? = null
            selectedPhotoBitmap?.let { bitmap ->
                val jpegBytes = withContext(Dispatchers.Default) { compressToJpeg(bitmap) }
                val uploadResult = supabase.uploadProfilePhoto(auth.userId, auth.accessToken, jpegBytes)
                photoUrl = uploadResult.getOrNull()
            }

            // Veículo e PIX ficam null aqui de propósito — o motorista completa depois no Perfil.
            val profileResult = supabase.registerDriverProfile(
                userId = auth.userId,
                email = email,
                fullName = fullName,
                cpf = cpfDigits,
                phone = phone,
                birthDate = birthDateIso,
                city = city,
                state = state,
                profilePhotoUrl = photoUrl
            )

            setLoading(false)

            if (profileResult.isSuccess) {
                Snackbar.make(photoPicker, "Cadastro realizado com sucesso!", Snackbar.LENGTH_LONG).show()
                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } else {
                showError(profileResult.exceptionOrNull()?.message ?: "Conta criada, mas houve erro ao salvar os dados. Fale com o suporte.")
            }
        }
    }

    private fun spinnerValueOrNull(spinner: Spinner, vararg invalid: String): String? {
        val value = spinner.selectedItem as? String ?: return null
        return if (invalid.contains(value)) null else value
    }

    private fun isValidCpf(cpf: String): Boolean {
        if (cpf.length != 11 || cpf.all { it == cpf[0] }) return false
        return try {
            val digits = cpf.map { it.toString().toInt() }
            var sum = 0
            for (i in 0..8) sum += digits[i] * (10 - i)
            var firstCheck = (sum * 10) % 11
            if (firstCheck == 10) firstCheck = 0
            if (firstCheck != digits[9]) return false
            sum = 0
            for (i in 0..9) sum += digits[i] * (11 - i)
            var secondCheck = (sum * 10) % 11
            if (secondCheck == 10) secondCheck = 0
            secondCheck == digits[10]
        } catch (e: Exception) {
            false
        }
    }

    private fun showError(message: String) {
        Snackbar.make(photoPicker, message, Snackbar.LENGTH_LONG)
            .setBackgroundTint(getColor(com.google.android.material.R.color.design_default_color_error))
            .show()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnCadastrar).isEnabled = !loading
        findViewById<View>(R.id.btnVoltar).isEnabled = !loading
    }
}

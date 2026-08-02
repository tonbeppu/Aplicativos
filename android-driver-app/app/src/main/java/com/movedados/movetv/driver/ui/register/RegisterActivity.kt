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
    private lateinit var spVehicleBrand: Spinner
    private lateinit var spVehicleModel: Spinner
    private lateinit var spVehicleYear: Spinner
    private lateinit var spVehicleColor: Spinner
    private lateinit var etVehicleColorOther: EditText
    private lateinit var etPlate: EditText
    private lateinit var spMotorType: Spinner
    private lateinit var spPixType: Spinner
    private lateinit var etPixKey: EditText
    private lateinit var photoPicker: FrameLayout
    private lateinit var photoPlaceholder: LinearLayout
    private lateinit var ivPhotoPreview: ImageView

    private var selectedPhotoBitmap: Bitmap? = null
    private var pixMaskWatcher: TextWatcher? = null

    private val citiesCache = mutableMapOf<String, List<String>>()
    private var brandCodeMap: Map<String, String> = emptyMap()

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
        setupStaticSpinners()
        setupCascadingSpinners()
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
        spVehicleBrand = findViewById(R.id.spVehicleBrand)
        spVehicleModel = findViewById(R.id.spVehicleModel)
        spVehicleYear = findViewById(R.id.spVehicleYear)
        spVehicleColor = findViewById(R.id.spVehicleColor)
        etVehicleColorOther = findViewById(R.id.etVehicleColorOther)
        etPlate = findViewById(R.id.etPlate)
        spMotorType = findViewById(R.id.spMotorType)
        spPixType = findViewById(R.id.spPixType)
        etPixKey = findViewById(R.id.etPixKey)
        photoPicker = findViewById(R.id.photoPicker)
        photoPlaceholder = findViewById(R.id.photoPlaceholder)
        ivPhotoPreview = findViewById(R.id.ivPhotoPreview)
    }

    private fun setupHeader() {
        val title = findViewById<TextView>(R.id.tvHeaderTitle)
        val spannable = android.text.SpannableString("movedados system")
        spannable.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.text_primary)), 0, 9, 0)
        spannable.setSpan(android.text.style.ForegroundColorSpan(getColor(R.color.accent)), 9, spannable.length, 0)
        title.text = spannable
    }

    // ==================== SPINNERS ESTÁTICOS ====================

    private fun setupStaticSpinners() {
        simpleAdapter(spVehicleColor, resources.getStringArray(R.array.cores_veiculo).toList())
        spVehicleColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val tilColorOther = findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.tilVehicleColorOther)
                tilColorOther.visibility = if (spVehicleColor.selectedItem == "Outra") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        simpleAdapter(spMotorType, resources.getStringArray(R.array.tipos_motor).toList())
        simpleAdapter(spPixType, resources.getStringArray(R.array.tipos_pix).toList())

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = mutableListOf("Selecione")
        for (y in currentYear + 1 downTo 1990) years.add(y.toString())
        simpleAdapter(spVehicleYear, years)
    }

    private fun simpleAdapter(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    // ==================== CASCATA: ESTADO -> CIDADE (API do IBGE) ====================
    // ==================== CASCATA: MARCA -> MODELO (API FIPE) ====================

    private fun setupCascadingSpinners() {
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

        simpleAdapter(spVehicleBrand, listOf("Carregando..."))
        simpleAdapter(spVehicleModel, listOf("Selecione a marca primeiro"))
        loadVehicleBrands()

        spVehicleBrand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val brandName = (spVehicleBrand.selectedItem as? String) ?: return
                if (position == 0) {
                    simpleAdapter(spVehicleModel, listOf("Selecione a marca primeiro"))
                    return
                }
                val code = brandCodeMap[brandName] ?: return
                loadVehicleModels(code)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun loadCities(uf: String) {
        citiesCache[uf]?.let {
            simpleAdapter(spCity, it)
            return
        }
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

    private fun loadVehicleBrands() {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetchVehicleBrands() }
            brandCodeMap = result
            simpleAdapter(spVehicleBrand, listOf("Selecione") + result.keys.toList())
        }
    }

    private fun fetchVehicleBrands(): Map<String, String> {
        return try {
            val request = Request.Builder()
                .url("https://parallelum.com.br/fipe/api/v1/carros/marcas")
                .build()
            val response = publicClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val array = gson.fromJson(body, JsonArray::class.java)
            val map = linkedMapOf<String, String>()
            array.forEach {
                val obj = it.asJsonObject
                map[obj.get("nome").asString] = obj.get("codigo").asString
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun loadVehicleModels(brandCode: String) {
        simpleAdapter(spVehicleModel, listOf("Carregando..."))
        lifecycleScope.launch {
            val models = withContext(Dispatchers.IO) { fetchVehicleModels(brandCode) }
            simpleAdapter(spVehicleModel, listOf("Selecione") + models)
        }
    }

    private fun fetchVehicleModels(brandCode: String): List<String> {
        return try {
            val request = Request.Builder()
                .url("https://parallelum.com.br/fipe/api/v1/carros/marcas/$brandCode/modelos")
                .build()
            val response = publicClient.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val modelosArray = obj.getAsJsonArray("modelos") ?: JsonArray()
            modelosArray.map { it.asJsonObject.get("nome").asString }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ==================== MÁSCARAS (CPF e TELEFONE) ====================

    private fun setupMasks() {
        etCpf.addTextChangedListener(MaskWatcher(etCpf, "###.###.###-##"))
        etPhone.addTextChangedListener(MaskWatcher(etPhone, "(##) #####-####"))
        etBirthDate.addTextChangedListener(MaskWatcher(etBirthDate, "##/##/####"))
        etPlate.addTextChangedListener(PlateWatcher(etPlate))
        setupPixMask()
    }

    private fun setupPixMask() {
        spPixType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                pixMaskWatcher?.let { etPixKey.removeTextChangedListener(it) }
                pixMaskWatcher = null
                etPixKey.setText("")
                when (spPixType.selectedItem as? String) {
                    "CPF" -> {
                        etPixKey.hint = "000.000.000-00"
                        etPixKey.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        pixMaskWatcher = MaskWatcher(etPixKey, "###.###.###-##")
                    }
                    "CNPJ" -> {
                        etPixKey.hint = "00.000.000/0000-00"
                        etPixKey.inputType = android.text.InputType.TYPE_CLASS_NUMBER
                        pixMaskWatcher = MaskWatcher(etPixKey, "##.###.###/####-##")
                    }
                    "Telefone" -> {
                        etPixKey.hint = "(00) 00000-0000"
                        etPixKey.inputType = android.text.InputType.TYPE_CLASS_PHONE
                        pixMaskWatcher = MaskWatcher(etPixKey, "(##) #####-####")
                    }
                    "Email" -> {
                        etPixKey.hint = "seuemail@exemplo.com"
                        etPixKey.inputType = android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                    }
                    "Chave Aleatória" -> {
                        etPixKey.hint = "Chave aleatória (UUID)"
                        etPixKey.inputType = android.text.InputType.TYPE_CLASS_TEXT
                    }
                    else -> {
                        etPixKey.hint = null
                        etPixKey.inputType = android.text.InputType.TYPE_CLASS_TEXT
                    }
                }
                pixMaskWatcher?.let { etPixKey.addTextChangedListener(it) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** TextWatcher simples que aplica uma máscara de dígitos (# = dígito) enquanto o usuário digita. */
    private class MaskWatcher(private val editText: EditText, private val mask: String) : TextWatcher {
        private var isUpdating = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        override fun afterTextChanged(s: Editable?) {
            if (isUpdating || s == null) return
            isUpdating = true

            val digits = s.toString().filter { it.isDigit() }
            val formatted = StringBuilder()
            var digitIndex = 0
            for (maskChar in mask) {
                if (digitIndex >= digits.length) break
                if (maskChar == '#') {
                    formatted.append(digits[digitIndex])
                    digitIndex++
                } else {
                    formatted.append(maskChar)
                }
            }
            editText.setText(formatted.toString())
            editText.setSelection(formatted.length)
            isUpdating = false
        }
    }

    private class PlateWatcher(private val editText: EditText) : TextWatcher {
        private var isUpdating = false
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) {
            if (isUpdating || s == null) return
            isUpdating = true
            val clean = s.toString().uppercase().filter { it.isLetterOrDigit() }.take(7)
            val formatted = if (clean.length > 3) "${clean.substring(0, 3)}-${clean.substring(3)}" else clean
            editText.setText(formatted)
            editText.setSelection(formatted.length)
            isUpdating = false
        }
    }

    // ==================== DATA DE NASCIMENTO (digitável, DD/MM/AAAA) ====================

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

    // ==================== VALIDAÇÃO E CADASTRO ====================

    private fun validateAndRegister() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        val cpfDigits = etCpf.text.toString().filter { it.isDigit() }
        val phone = etPhone.text.toString().trim()
        val plate = etPlate.text.toString().trim().uppercase()
        val pixKey = etPixKey.text.toString().trim()

        if (fullName.isEmpty()) return showError("Informe o nome completo")
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
            return showError("Informe um email válido")
        if (password.length < 6) return showError("A senha deve ter ao menos 6 caracteres")
        if (password != confirmPassword) return showError("As senhas não coincidem")
        if (!isValidCpf(cpfDigits)) return showError("CPF inválido")
        val birthDateIso = parseBirthDate(etBirthDate.text.toString())
        if (birthDateIso == null) return showError("Informe uma data de nascimento válida (DD/MM/AAAA)")
        if (phone.filter { it.isDigit() }.length < 10) return showError("Informe um telefone válido")

        val state = spinnerValueOrNull(spState, "Selecione")?.substringBefore(" - ")
            ?: return showError("Selecione o estado")
        val city = spinnerValueOrNull(spCity, "Selecione", "Carregando...", "Erro ao carregar cidades", "Selecione o estado primeiro")
            ?: return showError("Selecione a cidade")
        val brand = spinnerValueOrNull(spVehicleBrand, "Selecione", "Carregando...")
            ?: return showError("Selecione o fabricante do veículo")
        val model = spinnerValueOrNull(spVehicleModel, "Selecione", "Carregando...", "Selecione a marca primeiro")
            ?: return showError("Selecione o modelo do veículo")
        val yearStr = spinnerValueOrNull(spVehicleYear, "Selecione")
            ?: return showError("Selecione o ano do veículo")
        val colorSelected = spinnerValueOrNull(spVehicleColor, "Selecione")
            ?: return showError("Selecione a cor do veículo")
        val color = if (colorSelected == "Outra") {
            val custom = etVehicleColorOther.text.toString().trim()
            if (custom.isBlank()) return showError("Informe a cor do veículo")
            custom.lowercase().replaceFirstChar { it.uppercase() }
        } else colorSelected
        if (plate.replace("-", "").length < 7) return showError("Informe a placa do veículo")
        val motorTypeDisplay = spinnerValueOrNull(spMotorType, "Selecione")
            ?: return showError("Selecione o tipo de motor")
        // O banco exige "Combustao" (sem til) exatamente; os demais valores ficam como estão
        val motorType = if (motorTypeDisplay == "Combustão") "Combustao" else motorTypeDisplay
        val pixType = spinnerValueOrNull(spPixType, "Selecione")
            ?: return showError("Selecione o tipo de chave PIX")
        if (pixKey.isEmpty()) return showError("Informe a chave PIX")

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
                // Projeto exige confirmação por email antes do primeiro login
                setLoading(false)
                Snackbar.make(photoPicker, "Cadastro criado! Confirme seu email antes de entrar.", Snackbar.LENGTH_LONG).show()
                finish()
                return@launch
            }

            // Se o motorista escolheu uma foto, envia para o bucket antes de gravar o perfil
            var photoUrl: String? = null
            selectedPhotoBitmap?.let { bitmap ->
                val jpegBytes = withContext(Dispatchers.Default) { compressToJpeg(bitmap) }
                val uploadResult = supabase.uploadProfilePhoto(auth.userId, auth.accessToken, jpegBytes)
                photoUrl = uploadResult.getOrNull()
                // Se o upload falhar, o cadastro segue sem foto — não travamos o motorista por isso
            }

            val profileResult = supabase.registerDriverProfile(
                userId = auth.userId,
                email = email,
                fullName = fullName,
                cpf = cpfDigits,
                phone = phone,
                birthDate = birthDateIso,
                city = city,
                state = state,
                vehicleManufacturer = brand,
                vehicleModel = model,
                vehicleYear = yearStr.toIntOrNull(),
                vehiclePlate = plate,
                vehicleColor = color,
                vehicleMotorType = motorType,
                pixType = pixType,
                pixKey = pixKey,
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

    private fun compressToJpeg(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }

    private fun spinnerValueOrNull(spinner: Spinner, vararg invalidValues: String): String? {
        val value = spinner.selectedItem as? String ?: return null
        return if (invalidValues.contains(value)) null else value
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

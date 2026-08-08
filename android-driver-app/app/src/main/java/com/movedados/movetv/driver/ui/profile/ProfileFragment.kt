package com.movedados.movetv.driver.ui.profile

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Outline
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.movedados.movetv.driver.R
import com.movedados.movetv.driver.models.Profile
import com.movedados.movetv.driver.network.SupabaseClient
import com.movedados.movetv.driver.ui.login.LoginActivity
import com.movedados.movetv.driver.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.util.Locale

class ProfileFragment : Fragment() {

    private lateinit var prefs: PreferenceManager
    private lateinit var supabase: SupabaseClient
    private lateinit var gson: Gson
    private var profile: Profile? = null
    private val publicClient = OkHttpClient()

    private val editFields = mutableMapOf<String, EditText>()
    private val editSpinners = mutableMapOf<String, Spinner>()
    private var pixMaskWatcher: TextWatcher? = null

    private val citiesCache = mutableMapOf<String, List<String>>()
    private var brandCodeMap: Map<String, String> = emptyMap()

    private val ufList = listOf(
        "Selecione", "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
        "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI", "RJ", "RN",
        "RS", "RO", "RR", "SC", "SP", "SE", "TO"
    )
    private val motorOptions = listOf("Selecione", "Combustão", "Elétrico")
    private val pixTypeOptions = listOf("Selecione", "CPF", "CNPJ", "EMAIL", "TELEFONE", "ALEATÓRIA")
    private val colorOptions = listOf(
        "Selecione", "Branco", "Preto", "Prata", "Cinza", "Vermelho", "Azul", "Verde",
        "Amarelo", "Marrom", "Bege", "Dourado", "Laranja", "Roxo", "Vinho", "Outra"
    )

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { loadBitmapFromUri(it) }
    }
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { onPhotoPicked(it) }
    }
    private val requestCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) takePictureLauncher.launch(null)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager(requireContext())
        supabase = SupabaseClient(requireContext())
        gson = Gson()

        val avatarPhoto = view.findViewById<ImageView>(R.id.ivAvatarPhoto)
        avatarPhoto.clipToOutline = true
        avatarPhoto.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setOval(0, 0, v.width, v.height)
            }
        }

        profile = prefs.getProfileJson()?.let { gson.fromJson(it, Profile::class.java) }

        view.findViewById<View>(R.id.btnLogout).setOnClickListener { logout() }
        view.findViewById<View>(R.id.avatarFrame).setOnClickListener { showPhotoPickerDialog() }

        view.findViewById<MaterialButton>(R.id.btnEditPersonal).setOnClickListener { enterEditPersonal(view) }
        view.findViewById<View>(R.id.btnCancelPersonal).setOnClickListener { exitEditPersonal(view) }
        view.findViewById<View>(R.id.btnSavePersonal).setOnClickListener { savePersonal(view) }

        view.findViewById<MaterialButton>(R.id.btnEditVehicle).setOnClickListener { enterEditVehicle(view) }
        view.findViewById<View>(R.id.btnCancelVehicle).setOnClickListener { exitEditVehicle(view) }
        view.findViewById<View>(R.id.btnSaveVehicle).setOnClickListener { saveVehicle(view) }

        view.findViewById<MaterialButton>(R.id.btnEditPayment).setOnClickListener { enterEditPayment(view) }
        view.findViewById<View>(R.id.btnCancelPayment).setOnClickListener { exitEditPayment(view) }
        view.findViewById<View>(R.id.btnSavePayment).setOnClickListener { savePayment(view) }

        if (profile != null) {
            displayProfile(view)
        } else {
            val userId = prefs.getUserId()
            if (userId != null) {
                viewLifecycleOwner.lifecycleScope.launch {
                    val result = supabase.fetchProfile(userId)
                    val fetched = result.getOrNull()
                    requireActivity().runOnUiThread {
                        if (fetched != null) {
                            profile = fetched
                            prefs.saveProfileJson(gson.toJson(fetched))
                            displayProfile(view)
                        } else {
                            view.findViewById<TextView>(R.id.tvUserName).text = "Erro ao carregar perfil"
                            view.findViewById<TextView>(R.id.tvUserEmail).text =
                                result.exceptionOrNull()?.message ?: "Verifique sua conexão"
                        }
                    }
                }
            }
        }
    }

    // ==================== BANNER DE CADASTRO INCOMPLETO ====================

    private fun updateIncompleteBanner(view: View) {
        val p = profile ?: return
        val missing = mutableListOf<String>()
        if (p.vehicle_manufacturer.isNullOrBlank()) missing.add("Fabricante do veículo")
        if (p.vehicle_model.isNullOrBlank()) missing.add("Modelo do veículo")
        if (p.vehicle_plate.isNullOrBlank()) missing.add("Placa")
        if (p.vehicle_color.isNullOrBlank()) missing.add("Cor do veículo")
        if (p.vehicle_motor_type.isNullOrBlank()) missing.add("Tipo de motor")
        if (p.pix_type.isNullOrBlank()) missing.add("Tipo de chave PIX")
        if (p.pix_key.isNullOrBlank()) missing.add("Chave PIX")

        val banner = view.findViewById<View>(R.id.incompleteBanner)
        if (missing.isEmpty()) {
            banner.visibility = View.GONE
        } else {
            banner.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvIncompleteFields).text =
                "Falta preencher: " + missing.joinToString(", ") + ". Toque em \"Editar\" no card correspondente."
        }
    }

    fun isProfileIncomplete(p: Profile): Boolean {
        return p.vehicle_manufacturer.isNullOrBlank() || p.vehicle_plate.isNullOrBlank() ||
            p.pix_type.isNullOrBlank() || p.pix_key.isNullOrBlank()
    }

    // ==================== EXIBIÇÃO (modo leitura) ====================

    private fun displayProfile(view: View) {
        val p = profile ?: return
        view.findViewById<TextView>(R.id.tvUserName).text = p.full_name
        view.findViewById<TextView>(R.id.tvUserEmail).text = p.email

        loadAvatar(view, p.profile_photo_url)
        updateIncompleteBanner(view)

        val personalContainer = view.findViewById<LinearLayout>(R.id.personalInfoContainer)
        personalContainer.removeAllViews()
        addReadRow(personalContainer, "Nome", p.full_name)
        addReadRow(personalContainer, "CPF", formatCpf(p.cpf))
        addReadRow(personalContainer, "Telefone", p.phone ?: "Não informado")
        addReadRow(personalContainer, "Nascimento", formatDateDisplay(p.birth_date))
        addReadRow(personalContainer, "Estado", p.state ?: "Não informado")
        addReadRow(personalContainer, "Cidade", p.city ?: "Não informado")

        val vehicleContainer = view.findViewById<LinearLayout>(R.id.vehicleContainer)
        vehicleContainer.removeAllViews()
        addReadRow(vehicleContainer, "Fabricante", p.vehicle_manufacturer ?: "Não informado")
        addReadRow(vehicleContainer, "Modelo", p.vehicle_model ?: "Não informado")
        addReadRow(vehicleContainer, "Ano", p.vehicle_year?.toString() ?: "Não informado")
        addReadRow(vehicleContainer, "Placa", p.vehicle_plate ?: "Não informado")
        addReadRow(vehicleContainer, "Cor", p.vehicle_color ?: "Não informado")
        addReadRow(vehicleContainer, "Motor", p.vehicle_motor_type ?: "Não informado")

        val paymentContainer = view.findViewById<LinearLayout>(R.id.paymentContainer)
        paymentContainer.removeAllViews()
        addReadRow(paymentContainer, "Tipo de Chave PIX", p.pix_type ?: "Não informado")
        addReadRow(paymentContainer, "Chave PIX", p.pix_key ?: "Não informado")
    }

    private fun addReadRow(container: LinearLayout, label: String, value: String) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(6), 0, dp(6))
        }
        row.addView(TextView(context).apply {
            text = label
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(TextView(context).apply {
            text = value
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        container.addView(row)
    }

    private fun loadAvatar(view: View, url: String?) {
        val photo = view.findViewById<ImageView>(R.id.ivAvatarPhoto)
        val placeholder = view.findViewById<ImageView>(R.id.ivAvatarPlaceholder)
        if (url.isNullOrBlank()) {
            photo.visibility = View.GONE
            placeholder.visibility = View.VISIBLE
            return
        }
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bmp = java.net.URL(url).openStream().use { BitmapFactory.decodeStream(it) }
                if (bmp != null && isAdded) {
                    requireActivity().runOnUiThread {
                        photo.setImageBitmap(bmp)
                        photo.visibility = View.VISIBLE
                        placeholder.visibility = View.GONE
                    }
                }
            } catch (e: Exception) { }
        }
    }

    // ==================== FOTO ====================

    private fun showPhotoPickerDialog() {
        val options = arrayOf("Tirar foto", "Escolher da galeria")
        AlertDialog.Builder(requireContext())
            .setTitle("Foto de Perfil")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> requestCameraPermission.launch(android.Manifest.permission.CAMERA)
                    1 -> pickImageLauncher.launch("image/*")
                }
            }
            .show()
    }

    private fun loadBitmapFromUri(uri: Uri) {
        try {
            val stream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (bitmap != null) onPhotoPicked(bitmap)
        } catch (e: Exception) {
            Snackbar.make(requireView(), "Não foi possível carregar a imagem", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun onPhotoPicked(bitmap: Bitmap) {
        val view = view ?: return
        view.findViewById<ImageView>(R.id.ivAvatarPhoto).apply {
            setImageBitmap(bitmap)
            visibility = View.VISIBLE
        }
        view.findViewById<ImageView>(R.id.ivAvatarPlaceholder).visibility = View.GONE

        val userId = prefs.getUserId() ?: return
        val accessToken = prefs.getAccessToken() ?: return
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val jpegBytes = withContext(Dispatchers.Default) { compressToJpeg(bitmap) }
            val uploadResult = supabase.uploadProfilePhoto(userId, accessToken, jpegBytes)
            val photoUrl = uploadResult.getOrNull()

            if (photoUrl != null) {
                val updated = profile?.copy(profile_photo_url = photoUrl)
                if (updated != null) {
                    val saveResult = supabase.updateProfile(updated)
                    if (saveResult.isSuccess) {
                        profile = updated
                        prefs.saveProfileJson(gson.toJson(updated))
                        Snackbar.make(view, "Foto de perfil atualizada!", Snackbar.LENGTH_SHORT).show()
                    } else {
                        Snackbar.make(view, "Foto enviada, mas houve erro ao salvar no perfil", Snackbar.LENGTH_LONG).show()
                    }
                }
            } else {
                Snackbar.make(view, "Erro ao enviar a foto: ${uploadResult.exceptionOrNull()?.message ?: ""}", Snackbar.LENGTH_LONG).show()
            }
            progressBar.visibility = View.GONE
        }
    }

    private fun compressToJpeg(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return stream.toByteArray()
    }

    // ==================== CARD: INFORMAÇÕES PESSOAIS ====================

    private fun enterEditPersonal(view: View) {
        val p = profile ?: return
        editFields.keys.filter { it in setOf("full_name", "cpf", "phone", "birth_date") }.forEach { editFields.remove(it) }
        editSpinners.remove("state"); editSpinners.remove("city")

        view.findViewById<View>(R.id.btnEditPersonal).visibility = View.GONE
        view.findViewById<View>(R.id.personalActionsRow).visibility = View.VISIBLE

        val container = view.findViewById<LinearLayout>(R.id.personalInfoContainer)
        container.removeAllViews()
        addEditRow(container, "full_name", "Nome", p.full_name)
        addEditRow(container, "cpf", "CPF", formatCpf(p.cpf).takeIf { it != "Não informado" } ?: "", mask = "###.###.###-##")
        addEditRow(container, "phone", "Telefone", p.phone ?: "", mask = "(##) #####-####")
        addEditRow(container, "birth_date", "Nascimento (dd/mm/aaaa)", formatDateDisplay(p.birth_date).takeIf { it != "Não informado" } ?: "")

        val spState = buildSpinner(container, "Estado", ufList)
        editSpinners["state"] = spState
        val spCity = buildSpinner(container, "Cidade", listOf("Selecione o estado primeiro"))
        editSpinners["city"] = spCity

        spState.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                if (position == 0) { simpleAdapter(spCity, listOf("Selecione o estado primeiro")); return }
                loadCities(spCity, ufList[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        val ufIndex = ufList.indexOf(p.state?.uppercase()).let { if (it < 0) 0 else it }
        spState.setSelection(ufIndex)
        if (ufIndex > 0) {
            loadCities(spCity, ufList[ufIndex]) {
                p.city?.let { city ->
                    val idx = (spCity.adapter as? ArrayAdapter<String>)?.getPosition(city) ?: -1
                    if (idx >= 0) spCity.setSelection(idx)
                }
            }
        }
    }

    private fun savePersonal(view: View) {
        val p = profile ?: return
        val birthDateText = editFields["birth_date"]?.text?.toString()?.trim().orEmpty()
        val birthDateIso = if (birthDateText.isNotBlank()) parseBirthDate(birthDateText) else null
        if (birthDateText.isNotBlank() && birthDateIso == null) {
            Snackbar.make(view, "Data de nascimento inválida (use dd/mm/aaaa)", Snackbar.LENGTH_LONG).show()
            return
        }
        val stateSelected = spinnerValue(editSpinners["state"], "Selecione")
        val citySelected = spinnerValue(editSpinners["city"], "Selecione", "Selecione o estado primeiro", "Carregando...", "Erro ao carregar cidades")

        val updated = p.copy(
            full_name = editFields["full_name"]?.text?.toString()?.trim()?.ifBlank { p.full_name } ?: p.full_name,
            cpf = editFields["cpf"]?.text?.toString()?.filter { it.isDigit() }?.ifBlank { null },
            phone = editFields["phone"]?.text?.toString()?.trim()?.ifBlank { null },
            birth_date = birthDateIso ?: p.birth_date,
            state = stateSelected ?: p.state,
            city = citySelected ?: p.city
        )
        persistCardSave(view, updated) { exitEditPersonal(view) }
    }

    private fun exitEditPersonal(view: View) {
        view.findViewById<View>(R.id.btnEditPersonal).visibility = View.VISIBLE
        view.findViewById<View>(R.id.personalActionsRow).visibility = View.GONE
        displayProfile(view)
    }

    // ==================== CARD: VEÍCULO ====================

    private fun enterEditVehicle(view: View) {
        val p = profile ?: return
        view.findViewById<View>(R.id.btnEditVehicle).visibility = View.GONE
        view.findViewById<View>(R.id.vehicleActionsRow).visibility = View.VISIBLE

        val container = view.findViewById<LinearLayout>(R.id.vehicleContainer)
        container.removeAllViews()

        val spBrand = buildSpinner(container, "Fabricante", listOf("Carregando..."))
        editSpinners["vehicle_manufacturer"] = spBrand
        val brandOtherRow = addEditRow(container, "vehicle_manufacturer_other", "Nome do fabricante", "")
        brandOtherRow.visibility = View.GONE

        val spModel = buildSpinner(container, "Modelo", listOf("Selecione a marca primeiro"))
        editSpinners["vehicle_model"] = spModel
        val modelOtherRow = addEditRow(container, "vehicle_model_other", "Nome do modelo", "")
        modelOtherRow.visibility = View.GONE

        addEditRow(container, "vehicle_year", "Ano", p.vehicle_year?.toString() ?: "")
        addEditRow(container, "vehicle_plate", "Placa", p.vehicle_plate ?: "", plateMask = true)

        val spColor = buildSpinner(container, "Cor", colorOptions)
        editSpinners["vehicle_color"] = spColor
        val colorOtherRow = addEditRow(container, "vehicle_color_other", "Qual cor?", "")
        colorOtherRow.visibility = View.GONE
        val colorIdx = colorOptions.indexOf(p.vehicle_color).let { if (it < 0 && !p.vehicle_color.isNullOrBlank()) colorOptions.size - 1 else if (it < 0) 0 else it }
        spColor.setSelection(colorIdx)
        if (colorIdx == colorOptions.size - 1 && !p.vehicle_color.isNullOrBlank() && !colorOptions.contains(p.vehicle_color)) {
            colorOtherRow.visibility = View.VISIBLE
            editFields["vehicle_color_other"]?.setText(p.vehicle_color)
        }
        spColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                colorOtherRow.visibility = if (spColor.selectedItem == "Outra") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val spMotor = buildSpinner(container, "Motor", motorOptions)
        editSpinners["vehicle_motor_type"] = spMotor
        val storedMotor = if (p.vehicle_motor_type == "Combustao") "Combustão" else p.vehicle_motor_type
        spMotor.setSelection(motorOptions.indexOf(storedMotor).let { if (it < 0) 0 else it })

        loadVehicleBrands(spBrand) {
            val currentBrand = p.vehicle_manufacturer
            val adapter = spBrand.adapter as? ArrayAdapter<String>
            val idx = if (currentBrand != null) adapter?.getPosition(currentBrand) ?: -1 else -1
            if (idx >= 0) {
                spBrand.setSelection(idx)
                val code = brandCodeMap[currentBrand]
                if (code != null) {
                    loadVehicleModels(spModel, code) {
                        val modelAdapter = spModel.adapter as? ArrayAdapter<String>
                        val modelIdx = p.vehicle_model?.let { modelAdapter?.getPosition(it) } ?: -1
                        if (modelIdx >= 0) spModel.setSelection(modelIdx)
                        else if (!p.vehicle_model.isNullOrBlank()) {
                            spModel.setSelection((modelAdapter?.count ?: 1) - 1)
                            modelOtherRow.visibility = View.VISIBLE
                            editFields["vehicle_model_other"]?.setText(p.vehicle_model)
                        }
                    }
                }
            } else if (!currentBrand.isNullOrBlank()) {
                val lastIdx = (adapter?.count ?: 1) - 1
                spBrand.setSelection(lastIdx)
                brandOtherRow.visibility = View.VISIBLE
                editFields["vehicle_manufacturer_other"]?.setText(currentBrand)
                simpleAdapter(spModel, listOf("Outro"))
                modelOtherRow.visibility = View.VISIBLE
                editFields["vehicle_model_other"]?.setText(p.vehicle_model ?: "")
            }
        }

        spBrand.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val brandName = (spBrand.selectedItem as? String) ?: return
                if (brandName == "Outros") {
                    brandOtherRow.visibility = View.VISIBLE
                    simpleAdapter(spModel, listOf("Outro"))
                    modelOtherRow.visibility = View.VISIBLE
                    return
                }
                brandOtherRow.visibility = View.GONE
                if (position == 0) { simpleAdapter(spModel, listOf("Selecione a marca primeiro")); modelOtherRow.visibility = View.GONE; return }
                val code = brandCodeMap[brandName] ?: return
                loadVehicleModels(spModel, code)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        spModel.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                val modelName = (spModel.selectedItem as? String)
                modelOtherRow.visibility = if (modelName == "Outro") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun saveVehicle(view: View) {
        val p = profile ?: return
        val brandSelected = spinnerValue(editSpinners["vehicle_manufacturer"], "Selecione", "Carregando...")
        val finalBrand = if (brandSelected == "Outros") {
            capitalizeFirstOnly(editFields["vehicle_manufacturer_other"]?.text?.toString() ?: "")
        } else brandSelected

        val modelSelected = spinnerValue(editSpinners["vehicle_model"], "Selecione", "Selecione a marca primeiro", "Carregando...")
        val finalModel = if (modelSelected == "Outro" || modelSelected == null) {
            capitalizeFirstOnly(editFields["vehicle_model_other"]?.text?.toString() ?: "")
        } else modelSelected

        val colorSelected = spinnerValue(editSpinners["vehicle_color"], "Selecione")
        val finalColor = if (colorSelected == "Outra") {
            capitalizeFirstOnly(editFields["vehicle_color_other"]?.text?.toString() ?: "")
        } else colorSelected

        val motorDisplay = spinnerValue(editSpinners["vehicle_motor_type"], "Selecione")
        val motorFinal = if (motorDisplay == "Combustão") "Combustao" else motorDisplay

        val updated = p.copy(
            vehicle_manufacturer = finalBrand?.ifBlank { null } ?: p.vehicle_manufacturer,
            vehicle_model = finalModel?.ifBlank { null } ?: p.vehicle_model,
            vehicle_year = editFields["vehicle_year"]?.text?.toString()?.trim()?.toIntOrNull() ?: p.vehicle_year,
            vehicle_plate = editFields["vehicle_plate"]?.text?.toString()?.trim()?.uppercase()?.ifBlank { null } ?: p.vehicle_plate,
            vehicle_color = finalColor?.ifBlank { null } ?: p.vehicle_color,
            vehicle_motor_type = motorFinal ?: p.vehicle_motor_type
        )
        persistCardSave(view, updated) { exitEditVehicle(view) }
    }

    private fun exitEditVehicle(view: View) {
        view.findViewById<View>(R.id.btnEditVehicle).visibility = View.VISIBLE
        view.findViewById<View>(R.id.vehicleActionsRow).visibility = View.GONE
        displayProfile(view)
    }

    // ==================== CARD: PAGAMENTO ====================

    private fun enterEditPayment(view: View) {
        val p = profile ?: return
        view.findViewById<View>(R.id.btnEditPayment).visibility = View.GONE
        view.findViewById<View>(R.id.paymentActionsRow).visibility = View.VISIBLE

        val container = view.findViewById<LinearLayout>(R.id.paymentContainer)
        container.removeAllViews()

        val spPixType = buildSpinner(container, "Tipo de Chave PIX", pixTypeOptions)
        editSpinners["pix_type"] = spPixType
        addEditRow(container, "pix_key", "Chave PIX", p.pix_key ?: "")

        val pixIdx = pixTypeOptions.indexOf(p.pix_type?.uppercase()).let { if (it < 0) 0 else it }
        spPixType.setSelection(pixIdx)
        applyPixMask(p.pix_type?.uppercase())

        spPixType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                editFields["pix_key"]?.setText("")
                applyPixMask(spPixType.selectedItem as? String)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun savePayment(view: View) {
        val p = profile ?: return
        val pixTypeFinal = spinnerValue(editSpinners["pix_type"], "Selecione")
        val updated = p.copy(
            pix_type = pixTypeFinal ?: p.pix_type,
            pix_key = editFields["pix_key"]?.text?.toString()?.trim()?.ifBlank { null } ?: p.pix_key
        )
        persistCardSave(view, updated) { exitEditPayment(view) }
    }

    private fun exitEditPayment(view: View) {
        view.findViewById<View>(R.id.btnEditPayment).visibility = View.VISIBLE
        view.findViewById<View>(R.id.paymentActionsRow).visibility = View.GONE
        displayProfile(view)
    }

    // ==================== SALVAMENTO COMPARTILHADO ====================

    private fun persistCardSave(view: View, updated: Profile, onDone: () -> Unit) {
        val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
        progressBar.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            val result = supabase.updateProfile(updated)
            progressBar.visibility = View.GONE
            if (result.isSuccess) {
                profile = updated
                prefs.saveProfileJson(gson.toJson(updated))
                onDone()
                Snackbar.make(view, "Dados atualizados com sucesso!", Snackbar.LENGTH_SHORT).show()
            } else {
                Snackbar.make(view, "Erro ao salvar: ${result.exceptionOrNull()?.message ?: ""}", Snackbar.LENGTH_LONG).show()
            }
        }
    }

    private fun applyPixMask(type: String?) {
        val field = editFields["pix_key"] ?: return
        pixMaskWatcher?.let { field.removeTextChangedListener(it) }
        pixMaskWatcher = null
        when (type) {
            "CPF" -> { field.hint = "000.000.000-00"; pixMaskWatcher = SimpleMaskWatcher(field, "###.###.###-##") }
            "CNPJ" -> { field.hint = "00.000.000/0000-00"; pixMaskWatcher = SimpleMaskWatcher(field, "##.###.###/####-##") }
            "TELEFONE" -> { field.hint = "(00) 00000-0000"; pixMaskWatcher = SimpleMaskWatcher(field, "(##) #####-####") }
            "EMAIL" -> field.hint = "seuemail@exemplo.com"
            "ALEATÓRIA" -> field.hint = "Chave aleatória (UUID)"
            else -> field.hint = null
        }
        pixMaskWatcher?.let { field.addTextChangedListener(it) }
    }

    // ---- Helpers de construção de linhas/spinners ----

    private fun addEditRow(container: LinearLayout, key: String, label: String, value: String, mask: String? = null, plateMask: Boolean = false): LinearLayout {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        column.addView(TextView(context).apply {
            text = label
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
        })
        val editText = EditText(context).apply {
            setText(value)
            setTextColor(getColor(R.color.text_primary))
            textSize = 14f
            setBackgroundResource(R.drawable.bg_stat_card)
            setPadding(dp(10), dp(8), dp(10), dp(8))
            if (mask != null) addTextChangedListener(SimpleMaskWatcher(this, mask))
            if (plateMask) addTextChangedListener(PlateWatcher(this))
        }
        editFields[key] = editText
        column.addView(editText)
        container.addView(column)
        return column
    }

    private fun buildSpinner(container: LinearLayout, label: String, items: List<String>): Spinner {
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(6))
        }
        column.addView(TextView(context).apply {
            text = label
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
        })
        val spinner = Spinner(context, Spinner.MODE_DIALOG).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(48))
            setBackgroundResource(R.drawable.bg_stat_card)
            setPadding(dp(10), 0, dp(10), 0)
        }
        simpleAdapter(spinner, items)
        column.addView(spinner)
        container.addView(column)
        return spinner
    }

    private fun simpleAdapter(spinner: Spinner, items: List<String>) {
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, items)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
    }

    private fun loadCities(spinner: Spinner, uf: String, onLoaded: (() -> Unit)? = null) {
        citiesCache[uf]?.let { simpleAdapter(spinner, it); onLoaded?.invoke(); return }
        simpleAdapter(spinner, listOf("Carregando..."))
        viewLifecycleOwner.lifecycleScope.launch {
            val cities = withContext(Dispatchers.IO) { fetchCitiesFromIbge(uf) }
            citiesCache[uf] = cities
            simpleAdapter(spinner, cities)
            onLoaded?.invoke()
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
            listOf("Selecione") + array.map { it.asJsonObject.get("nome").asString }.sorted()
        } catch (e: Exception) {
            listOf("Erro ao carregar cidades")
        }
    }

    private fun loadVehicleBrands(spinner: Spinner, onLoaded: (() -> Unit)? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { fetchVehicleBrands() }
            brandCodeMap = result
            simpleAdapter(spinner, listOf("Selecione") + result.keys.toList() + listOf("Outros"))
            onLoaded?.invoke()
        }
    }

    private fun fetchVehicleBrands(): Map<String, String> {
        return try {
            val request = Request.Builder().url("https://parallelum.com.br/fipe/api/v1/carros/marcas").build()
            val response = publicClient.newCall(request).execute()
            val body = response.body?.string() ?: "[]"
            val array = gson.fromJson(body, JsonArray::class.java)
            val map = linkedMapOf<String, String>()
            array.forEach {
                val obj = it.asJsonObject
                map[obj.get("nome").asString] = obj.get("codigo").asString
            }
            map
        } catch (e: Exception) { emptyMap() }
    }

    private fun loadVehicleModels(spinner: Spinner, brandCode: String, onLoaded: (() -> Unit)? = null) {
        simpleAdapter(spinner, listOf("Carregando..."))
        viewLifecycleOwner.lifecycleScope.launch {
            val models = withContext(Dispatchers.IO) { fetchVehicleModels(brandCode) }
            simpleAdapter(spinner, listOf("Selecione") + models + listOf("Outro"))
            onLoaded?.invoke()
        }
    }

    private fun fetchVehicleModels(brandCode: String): List<String> {
        return try {
            val request = Request.Builder().url("https://parallelum.com.br/fipe/api/v1/carros/marcas/$brandCode/modelos").build()
            val response = publicClient.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            val obj = gson.fromJson(body, com.google.gson.JsonObject::class.java)
            val modelosArray = obj.getAsJsonArray("modelos") ?: JsonArray()
            modelosArray.map { it.asJsonObject.get("nome").asString }
        } catch (e: Exception) { emptyList() }
    }

    private fun spinnerValue(spinner: Spinner?, vararg invalid: String): String? {
        val value = spinner?.selectedItem as? String ?: return null
        return if (invalid.contains(value)) null else value
    }

    private fun capitalizeFirstOnly(s: String): String {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return trimmed
        return trimmed.lowercase().replaceFirstChar { it.uppercase() }
    }

    private class SimpleMaskWatcher(private val editText: EditText, private val mask: String) : TextWatcher {
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
        return String.format(Locale.US, "%04d-%02d-%02d", year, month, day)
    }

    private fun formatDateDisplay(iso: String?): String {
        if (iso.isNullOrBlank()) return "Não informado"
        return try {
            val parts = iso.split("T")[0].split("-")
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } catch (e: Exception) { iso }
    }

    private fun formatCpf(cpf: String?): String {
        if (cpf.isNullOrBlank()) return "Não informado"
        val digits = cpf.filter { it.isDigit() }
        return if (digits.length == 11) {
            "${digits.substring(0,3)}.${digits.substring(3,6)}.${digits.substring(6,9)}-${digits.substring(9,11)}"
        } else cpf
    }

    private fun logout() {
        prefs.clearAll()
        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun getColor(resId: Int): Int = resources.getColor(resId, null)
}

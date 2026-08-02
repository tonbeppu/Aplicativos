package com.movedados.movetv.driver.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.movedados.movetv.driver.ui.widgets.GpsToggleSwitch
import com.google.gson.Gson
import com.movedados.movetv.driver.R
import com.movedados.movetv.driver.models.Campaign
import com.movedados.movetv.driver.models.CampaignType
import com.movedados.movetv.driver.models.Profile
import com.movedados.movetv.driver.network.SupabaseClient
import com.movedados.movetv.driver.services.LocationService
import com.movedados.movetv.driver.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var prefs: PreferenceManager
    private lateinit var supabase: SupabaseClient
    private lateinit var gson: Gson
    private var profile: Profile? = null

    private var campaignsJob: kotlinx.coroutines.Job? = null
    private var suppressSwitch = false
    private val handler = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() {
            view?.let { updateStats(it) }
            handler.postDelayed(this, 1000)
        }
    }

    // Atualiza a lista de campanhas sozinha a cada 45s enquanto a Home estiver na tela
    // (ex: quando um novo motorista é vinculado a uma campanha pelo painel web).
    private val campaignRefreshTicker = object : Runnable {
        override fun run() {
            view?.let { loadCampaigns(it) }
            handler.postDelayed(this, 45_000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = PreferenceManager(requireContext())
        supabase = SupabaseClient(requireContext())
        gson = Gson()

        profile = prefs.getProfileJson()?.let { gson.fromJson(it, Profile::class.java) }

        setupMonitoringCard(view)
        // Não chama loadCampaigns aqui: onResume() já dispara logo em seguida (parte normal
        // do ciclo de vida do fragmento) — chamar nos dois lugares causava cards duplicados.

        val swipeRefresh = view.findViewById<SwipeRefreshLayout>(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener {
            loadCampaigns(view)
            swipeRefresh.isRefreshing = false
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            updateMonitoringUi(it)
            loadCampaigns(it) // atualiza na hora ao voltar pra Home, sem esperar o ciclo periódico
        }
        handler.post(ticker)
        handler.postDelayed(campaignRefreshTicker, 45_000)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
        handler.removeCallbacks(campaignRefreshTicker)
    }

    // ==================== MONITORAMENTO ====================

    private fun setupMonitoringCard(view: View) {
        val switch = view.findViewById<GpsToggleSwitch>(R.id.switchMonitoring)
        updateMonitoringUi(view)

        switch.onCheckedChangeListener = checkedListener@ { isChecked ->
            if (suppressSwitch) return@checkedListener
            if (isChecked) {
                prefs.setGpsEnabled(true)
                if (hasLocationPermission()) {
                    ContextCompat.startForegroundService(
                        requireContext(),
                        Intent(requireContext(), LocationService::class.java)
                    )
                } else {
                    ActivityCompat.requestPermissions(
                        requireActivity(),
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ),
                        100
                    )
                }
            } else {
                prefs.setGpsEnabled(false)
                requireContext().stopService(Intent(requireContext(), LocationService::class.java))
            }
            updateMonitoringUi(view)
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun updateMonitoringUi(view: View) {
        val switch = view.findViewById<GpsToggleSwitch>(R.id.switchMonitoring)
        val chip = view.findViewById<TextView>(R.id.tvMonitoringChip)
        val sub = view.findViewById<TextView>(R.id.tvMonitoringSub)
        val hint = view.findViewById<TextView>(R.id.tvSwitchHint)
        val active = prefs.isGpsEnabled()

        suppressSwitch = true
        switch.setChecked(active, animate = false)
        suppressSwitch = false

        if (active) {
            chip.text = "ATIVO"
            chip.setTextColor(getColor(R.color.success))
            sub.text = "Sistema Ativo"
            hint.text = "Deslize para a esquerda para desativar"
        } else {
            chip.text = "INATIVO"
            chip.setTextColor(getColor(R.color.text_secondary))
            sub.text = "Sistema Inativo"
            hint.text = "Deslize para ativar o monitoramento"
        }
        updateStats(view)
    }

    private fun updateStats(view: View) {
        val tempo = view.findViewById<TextView>(R.id.tvStatTempo)
        val tempoSub = view.findViewById<TextView>(R.id.tvStatTempoSub)
        val pontos = view.findViewById<TextView>(R.id.tvStatPontos)
        val precisao = view.findViewById<TextView>(R.id.tvStatPrecisao)

        val start = prefs.getMonitoringStart()
        if (prefs.isGpsEnabled() && start > 0) {
            val elapsed = (System.currentTimeMillis() - start) / 1000
            val h = elapsed / 3600
            val m = (elapsed % 3600) / 60
            val s = elapsed % 60
            tempo.text = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
            tempoSub.text = String.format(Locale.US, "%.2fh", elapsed / 3600.0)
        } else {
            tempo.text = "00:00:00"
            tempoSub.text = "0.00h"
        }

        pontos.text = prefs.getGpsCount().toString()

        val acc = prefs.getLastAccuracy()
        precisao.text = if (acc >= 0f) "±${acc.toInt()}m" else "—"
    }

    // ==================== CAMPANHAS ====================

    private fun loadCampaigns(view: View) {
        val driverId = profile?.id ?: prefs.getUserId()
        val container = view.findViewById<LinearLayout>(R.id.campaignsContainer)
        val emptyText = view.findViewById<TextView>(R.id.tvEmptyCampaigns)

        // Limpa antes de recarregar — essencial para não duplicar os cards a cada atualização
        container.removeAllViews()
        emptyText.visibility = View.GONE
        emptyText.text = "Nenhuma campanha encontrada"

        if (driverId.isNullOrBlank()) {
            emptyText.visibility = View.VISIBLE
            return
        }

        // Cancela qualquer busca anterior ainda em andamento — evita que duas chamadas
        // sobrepostas (ex: onResume disparando duas vezes seguidas) dupliquem os cards
        campaignsJob?.cancel()
        campaignsJob = viewLifecycleOwner.lifecycleScope.launch {
            val result = supabase.fetchDriverCampaigns(driverId)
            if (result.isSuccess) {
                val campaigns = result.getOrNull()!!
                if (campaigns.isEmpty()) {
                    emptyText.visibility = View.VISIBLE
                } else {
                    campaigns.forEach { campaign -> addCampaignCard(container, campaign) }
                }
            } else {
                emptyText.visibility = View.VISIBLE
                emptyText.text = "Erro ao carregar campanhas"
            }
        }
    }

    private fun addCampaignCard(container: LinearLayout, campaign: Campaign) {
        val card = MaterialCardView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
            cardElevation = dp(2).toFloat()
            radius = dp(14).toFloat()
            setCardBackgroundColor(getColor(R.color.card_bg))
            strokeWidth = dp(1)
            strokeColor = if (campaign.status == "active") getColor(R.color.success) else getColor(R.color.card_bg_secondary)
        }

        val cardColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Melhoria 1: foto de capa inteira, sem cortar (CENTER_CROP cortava; FIT_CENTER mostra tudo)
        // — o card não cresce mais que antes: a altura da moldura continua a mesma (170dp).
        campaign.image_url?.takeIf { it.isNotBlank() }?.let { url ->
            val cover = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
                scaleType = ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(getColor(R.color.card_bg_secondary))
            }
            cardColumn.addView(cover)
            loadImageInto(url, cover)
        }

        val inner = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        // Melhoria 2: "Campanha: {nome}"
        val nameView = TextView(context).apply {
            text = "Campanha: ${campaign.name}"
            setTextColor(getColor(R.color.text_primary))
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        inner.addView(nameView)

        // Melhoria 3: "Tipo de campanha: X" abaixo do nome (saiu do badge ao lado do título)
        campaign.campaign_type?.takeIf { it.isNotBlank() }?.let { type ->
            val typeView = TextView(context).apply {
                text = "Tipo de campanha: ${type.replaceFirstChar { c -> c.uppercase() }}"
                setTextColor(getColor(R.color.text_secondary))
                textSize = 13f
                setPadding(0, dp(4), 0, 0)
            }
            inner.addView(typeView)
        }

        // Melhoria 4: Início e Fim separados
        val inicioView = TextView(context).apply {
            text = "Início: ${formatDateShort(campaign.start_date)}"
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
            setPadding(0, dp(10), 0, 0)
            compoundDrawablePadding = dp(6)
            setCompoundDrawablesWithIntrinsicBounds(tintedDrawable(R.drawable.ic_calendar, R.color.text_secondary), null, null, null)
        }
        inner.addView(inicioView)

        campaign.end_date?.takeIf { it.isNotBlank() }?.let { endDate ->
            val fimView = TextView(context).apply {
                text = "Fim: ${formatDateShort(endDate)}"
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                setPadding(0, dp(4), 0, 0)
                compoundDrawablePadding = dp(6)
                setCompoundDrawablesWithIntrinsicBounds(tintedDrawable(R.drawable.ic_calendar, R.color.text_secondary), null, null, null)
            }
            inner.addView(fimView)

            // Retirada do material = dia seguinte ao fim da campanha (agora aparece ANTES dos dias)
            val retiradaDate = addOneDay(endDate)
            if (retiradaDate != null) {
                val retiradaView = TextView(context).apply {
                    text = "Quando você retira o adesivo/material: ${formatDateShort(retiradaDate)}"
                    setTextColor(getColor(R.color.text_primary))
                    textSize = 13f
                    setPadding(0, dp(8), 0, 0)
                    compoundDrawablePadding = dp(6)
                    setCompoundDrawablesWithIntrinsicBounds(tintedDrawable(R.drawable.ic_calendar, R.color.text_secondary), null, null, null)
                }
                inner.addView(retiradaView)
            }

            // Quantidade de dias da campanha (agora aparece DEPOIS da retirada)
            val days = daysBetween(campaign.start_date, endDate)
            if (days != null) {
                val diasView = TextView(context).apply {
                    text = "Dias da campanha: $days"
                    setTextColor(getColor(R.color.text_secondary))
                    textSize = 13f
                    setPadding(0, dp(8), 0, 0)
                }
                inner.addView(diasView)
            }
        }

        // Melhoria 8: Benefício (valor que o motorista recebe)
        campaign.driver_payment_value?.let { paymentValue ->
            val beneficioView = TextView(context).apply {
                text = "Benefício: R$ %.2f".format(paymentValue)
                setTextColor(getColor(R.color.success))
                textSize = 13f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(0, dp(10), 0, 0)
            }
            inner.addView(beneficioView)
        }

        // Status
        val statusView = TextView(context).apply {
            text = "Status: ${translateStatus(campaign.status)}"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 12f
            setPadding(0, dp(8), 0, 0)
        }
        inner.addView(statusView)

        // Ver detalhes (expande os tipos de mídia disponíveis + status de adesivação)
        val typesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            visibility = View.GONE
        }

        val detailsBtn = TextView(context).apply {
            text = "⌄  Ver detalhes"
            setTextColor(getColor(R.color.text_secondary))
            textSize = 13f
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.bg_stat_card)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
            }
            setOnClickListener {
                val opening = typesContainer.visibility == View.GONE
                typesContainer.visibility = if (opening) View.VISIBLE else View.GONE
                text = if (opening) "⌃  Ocultar detalhes" else "⌄  Ver detalhes"
            }
        }
        inner.addView(detailsBtn)
        inner.addView(typesContainer)

        cardColumn.addView(inner)
        card.addView(cardColumn)
        container.addView(card)

        // Adesivação e tipos de mídia: ambos só aparecem ao abrir "Ver Detalhes"
        viewLifecycleOwner.lifecycleScope.launch {
            val driverId = profile?.id ?: prefs.getUserId()

            // 1) Status de adesivação (se o motorista já concluiu) — SEM a foto (melhoria 7):
            // a foto já foi enviada ao sistema no momento da adesivação, não precisa reexibir aqui.
            val adhesion = driverId?.let { supabase.fetchDriverAdhesion(campaign.id, it).getOrNull() }
            if (adhesion != null) {
                requireActivity().runOnUiThread {
                    val box = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        setBackgroundResource(R.drawable.bg_box_green)
                        setPadding(dp(12), dp(12), dp(12), dp(12))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { bottomMargin = dp(10) }
                    }
                    val title = TextView(context).apply {
                        text = "Adesivação Concluída"
                        setTextColor(getColor(R.color.success))
                        textSize = 14f
                        setTypeface(typeface, android.graphics.Typeface.BOLD)
                        compoundDrawablePadding = dp(6)
                        setCompoundDrawablesWithIntrinsicBounds(
                            tintedDrawable(R.drawable.ic_check, R.color.success), null, null, null
                        )
                    }
                    box.addView(title)

                    adhesion.completed_at?.let { completedAt ->
                        val date = TextView(context).apply {
                            text = "Concluído em ${formatDate(completedAt)}"
                            setTextColor(getColor(R.color.text_primary))
                            textSize = 13f
                            setPadding(0, dp(4), 0, 0)
                        }
                        box.addView(date)
                    }

                    typesContainer.addView(box)
                }
            }

            // 2) Tipos de mídia e benefício da campanha
            val typesResult = supabase.fetchCampaignTypes(campaign.id)
            if (typesResult.isSuccess) {
                val types = typesResult.getOrNull()!!
                requireActivity().runOnUiThread {
                    types.forEach { type -> addTypeRow(typesContainer, type) }
                }
            }
        }
    }

    private fun addTypeRow(container: LinearLayout, type: CampaignType) {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setBackgroundColor(getColor(R.color.card_bg_secondary))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            }
        }

        val infoText = TextView(context).apply {
            text = "${type.type} - Disponível: ${type.quantity - type.accepted_count}/${type.quantity}"
            setTextColor(getColor(R.color.text_primary))
            textSize = 13f
        }

        val benefit = TextView(context).apply {
            text = when (type.benefit_type) {
                "dinheiro" -> "R$ ${type.benefit_value ?: 0}"
                "voucher" -> "Voucher: ${type.benefit_description ?: ""}"
                "brinde" -> "Brinde: ${type.benefit_description ?: ""}"
                else -> ""
            }
            setTextColor(getColor(R.color.accent))
            textSize = 12f
        }

        val col = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        col.addView(infoText)
        col.addView(benefit)
        row.addView(col)
        container.addView(row)
    }

    private fun loadImageInto(url: String, imageView: ImageView) {
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bmp = java.net.URL(url).openStream().use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
                if (bmp != null && isAdded) {
                    requireActivity().runOnUiThread { imageView.setImageBitmap(bmp) }
                }
            } catch (_: Exception) {
                // Foto indisponível: o card segue sem imagem
            }
        }
    }

    // ==================== UTILS ====================

    private fun tintedDrawable(resId: Int, colorRes: Int): android.graphics.drawable.Drawable? {
        val d = androidx.core.content.ContextCompat.getDrawable(requireContext(), resId)?.mutate()
        d?.setTint(getColor(colorRes))
        val size = dp(16)
        d?.setBounds(0, 0, size, size)
        return d
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun getColor(resId: Int): Int = resources.getColor(resId, null)
    private fun translateStatus(status: String): String = when (status) {
        "active" -> "Ativa"; "completed" -> "Concluída"; "scheduled" -> "Agendada"
        "pending" -> "Pendente"; "rejected" -> "Rejeitada"; else -> status
    }
    // dd/MM/aa — formato curto pedido para o card da campanha
    private fun formatDateShort(dateStr: String): String {
        return try {
            val parts = dateStr.split("T")[0].split("-")
            "${parts[2]}/${parts[1]}/${parts[0].takeLast(2)}"
        } catch (e: Exception) { dateStr }
    }

    private fun daysBetween(start: String, end: String): Int? {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d1 = sdf.parse(start.split("T")[0])!!.time
            val d2 = sdf.parse(end.split("T")[0])!!.time
            ((d2 - d1) / 86400000L + 1).toInt()
        } catch (e: Exception) { null }
    }

    // Retirada do material = dia seguinte ao fim da campanha (regra fixa pedida)
    private fun addOneDay(dateStr: String): String? {
        return try {
            val cal = Calendar.getInstance()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            cal.time = sdf.parse(dateStr.split("T")[0])!!
            cal.add(Calendar.DAY_OF_MONTH, 1)
            sdf.format(cal.time)
        } catch (e: Exception) { null }
    }

    private fun formatDate(dateStr: String): String {
        return try {
            val parts = dateStr.split("T")[0].split("-")
            "${parts[2]}/${parts[1]}/${parts[0]}"
        } catch (e: Exception) { dateStr }
    }
    private fun formatPeriod(start: String, end: String?): String {
        val startFmt = formatDate(start)
        if (end == null) return "$startFmt - Em andamento"
        val endFmt = formatDate(end)
        val days = try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val d1 = sdf.parse(start.split("T")[0])!!.time
            val d2 = sdf.parse(end.split("T")[0])!!.time
            ((d2 - d1) / 86400000L + 1).toInt()
        } catch (e: Exception) { null }
        return if (days != null) "$startFmt - $endFmt ($days dias)" else "$startFmt - $endFmt"
    }
}

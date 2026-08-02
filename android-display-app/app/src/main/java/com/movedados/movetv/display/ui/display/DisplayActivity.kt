package com.movedados.movetv.display.ui.display

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.gms.location.*
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.movedados.movetv.display.R
import com.movedados.movetv.display.models.MediaItem
import com.movedados.movetv.display.models.Profile
import com.movedados.movetv.display.network.SupabaseClient
import com.movedados.movetv.display.ui.login.LoginActivity
import com.movedados.movetv.display.utils.PreferenceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale

class DisplayActivity : AppCompatActivity() {

    private lateinit var prefs: PreferenceManager
    private lateinit var supabase: SupabaseClient
    private lateinit var gson: Gson
    private val publicClient = OkHttpClient()

    private var profile: Profile? = null
    private var deviceId: String? = null
    private var campaignId: String? = null
    private var mediaItems: List<MediaItem> = emptyList()
    private var currentIndex = 0

    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var advanceRunnable: Runnable? = null

    // Views
    private lateinit var ivMedia: ImageView
    private lateinit var playerView: PlayerView
    private lateinit var webView: WebView
    private lateinit var rssContainer: LinearLayout
    private lateinit var tvIndicator: TextView
    private lateinit var loadingContainer: LinearLayout
    private lateinit var errorContainer: LinearLayout
    private lateinit var tvErrorMessage: TextView
    private lateinit var emptyContainer: LinearLayout

    // Saída do modo quiosque: 30 toques em menos de 3s (equivalente ao protótipo React)
    private var tapCount = 0
    private var lastTapTime = 0L

    // GPS
    private lateinit var fusedClient: FusedLocationProviderClient
    private var locationCallback: LocationCallback? = null
    private val requestLocationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startLocationUpdates()
    }

    // RSS
    private var rssItems: List<RssItem> = emptyList()
    private var rssIndex = 0
    private val rssRefreshRunnable = object : Runnable {
        override fun run() {
            fetchRss()
            handler.postDelayed(this, 300_000L) // atualiza o feed a cada 5 minutos
        }
    }
    data class RssItem(val title: String, val description: String, val pubDate: String?, val image: String?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enterImmersiveMode()

        prefs = PreferenceManager(this)
        supabase = SupabaseClient(this)
        gson = Gson()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        profile = prefs.getProfileJson()?.let { gson.fromJson(it, Profile::class.java) }
        deviceId = profile?.device_id

        bindViews()
        setupExitGesture()
        setupExoPlayer()
        requestLocationAndTrack()

        loadCampaignMedia()
    }

    private fun bindViews() {
        ivMedia = findViewById(R.id.ivMedia)
        playerView = findViewById(R.id.playerView)
        webView = findViewById(R.id.webView)
        rssContainer = findViewById(R.id.rssContainer)
        tvIndicator = findViewById(R.id.tvIndicator)
        loadingContainer = findViewById(R.id.loadingContainer)
        errorContainer = findViewById(R.id.errorContainer)
        tvErrorMessage = findViewById(R.id.tvErrorMessage)
        emptyContainer = findViewById(R.id.emptyContainer)

        findViewById<View>(R.id.btnBackToLogin).setOnClickListener { logoutAndExit() }
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()
    }

    private fun enterImmersiveMode() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
            )
    }

    // ==================== SAÍDA DO MODO QUIOSQUE ====================

    private fun setupExitGesture() {
        val root = findViewById<View>(android.R.id.content)
        root.setOnClickListener { registerExitTap() }
        ivMedia.setOnClickListener { registerExitTap() }
        playerView.setOnClickListener { registerExitTap() }
        webView.setOnClickListener { registerExitTap() }
        rssContainer.setOnClickListener { registerExitTap() }
    }

    private fun registerExitTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime > 3000) {
            tapCount = 1
        } else {
            tapCount++
        }
        lastTapTime = now
        if (tapCount >= 30) {
            logoutAndExit()
        }
    }

    override fun onBackPressed() {
        // No app de exibição, o botão Voltar não deve sair do modo quiosque por acidente —
        // só o gesto de 30 toques (ou o botão na tela de erro) encerra a sessão.
    }

    private fun logoutAndExit() {
        stopLocationUpdates()
        prefs.clearAll()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    // ==================== CARREGAMENTO DA CAMPANHA/MÍDIA ====================

    private fun loadCampaignMedia() {
        showOnly(loadingContainer)
        val devId = deviceId
        if (devId.isNullOrBlank()) {
            showError("Dispositivo não configurado para este usuário")
            return
        }

        lifecycleScope.launch {
            val campaignResult = supabase.fetchCampaignIdForDevice(devId)
            val campId = campaignResult.getOrNull()
            if (campaignResult.isFailure) {
                showError("Erro ao obter campanha do dispositivo")
                return@launch
            }
            if (campId.isNullOrBlank()) {
                showError("Este dispositivo não está associado a nenhuma campanha ativa")
                return@launch
            }
            campaignId = campId

            val mediaResult = supabase.fetchCampaignMedia(campId)
            val items = mediaResult.getOrNull()
            if (mediaResult.isFailure) {
                showError("Erro ao obter mídias da campanha")
                return@launch
            }
            if (items.isNullOrEmpty()) {
                showOnly(emptyContainer)
                return@launch
            }

            mediaItems = items
            currentIndex = 0
            playCurrentMedia()
        }
    }

    /** Recarrega a lista de mídia sem reiniciar o player do zero — usado no polling periódico
     *  que substitui o "tempo real" do painel web (o app confere a cada 60s se algo mudou). */
    private fun refreshCampaignMediaSilently() {
        val campId = campaignId ?: return
        lifecycleScope.launch {
            val result = supabase.fetchCampaignMedia(campId)
            result.getOrNull()?.let { items ->
                if (items.map { it.id } != mediaItems.map { it.id }) {
                    mediaItems = items
                    if (currentIndex >= items.size) currentIndex = 0
                }
            }
        }
    }

    private val refreshPollRunnable = object : Runnable {
        override fun run() {
            refreshCampaignMediaSilently()
            handler.postDelayed(this, 60_000L)
        }
    }

    // ==================== REPRODUÇÃO ====================

    private fun playCurrentMedia() {
        if (mediaItems.isEmpty()) {
            showOnly(emptyContainer)
            return
        }
        val current = mediaItems[currentIndex]
        showOnly(null) // esconde loading/erro/vazio — o conteúdo real assume

        tvIndicator.text = "${currentIndex + 1} / ${mediaItems.size}"
        tvIndicator.visibility = if (current.file_format == "rss" || current.file_format == "youtube" || current.file_format == "website") View.GONE else View.VISIBLE

        cancelScheduledAdvance()
        exoPlayer?.stop()

        when (current.file_format) {
            "rss" -> playRss(current)
            "youtube" -> playWebView(getYoutubeEmbedUrl(current.file_url))
            "website" -> playWebView(current.file_url)
            else -> if (current.file_type == "video") playVideo(current) else playImage(current)
        }
    }

    private fun setVisible(view: View, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun hideAllMediaViews() {
        setVisible(ivMedia, false)
        setVisible(playerView, false)
        setVisible(webView, false)
        setVisible(rssContainer, false)
    }

    private fun playImage(item: MediaItem) {
        hideAllMediaViews()
        setVisible(ivMedia, true)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bmp = java.net.URL(item.file_url).openStream().use { BitmapFactory.decodeStream(it) }
                withContext(Dispatchers.Main) { ivMedia.setImageBitmap(bmp) }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { advanceToNext() }
                return@launch
            }
            withContext(Dispatchers.Main) {
                val durationSec = item.duration ?: 5
                trackPlay(item.id, durationSec)
                scheduleAdvance(durationSec * 1000L)
            }
        }
    }

    private var pendingVideoItem: MediaItem? = null

    private fun playVideo(item: MediaItem) {
        hideAllMediaViews()
        setVisible(playerView, true)
        val player = exoPlayer ?: return
        player.setMediaItem(ExoMediaItem.fromUri(Uri.parse(item.file_url)))
        player.prepare()
        player.play()
        // O avanço acontece no listener onPlaybackStateChanged (STATE_ENDED), não por temporizador —
        // assim o vídeo sempre toca até o fim, igual ao <video onEnded> do protótipo React.
        pendingVideoItem = item
    }

    private fun playWebView(url: String) {
        hideAllMediaViews()
        setVisible(webView, true)
        webView.loadUrl(url)
        // YouTube/website ficam em loop e não avançam sozinhos (igual ao protótipo React) —
        // o motor de avanço aqui é só o polling de 60s que troca a campanha, se mudar.
    }

    private fun playRss(item: MediaItem) {
        hideAllMediaViews()
        setVisible(rssContainer, true)
        findViewById<TextView>(R.id.tvRssFeedTitle).text = item.title
        fetchRss(item.file_url, item.duration ?: 15)
    }

    private fun scheduleAdvance(delayMs: Long) {
        val r = Runnable { advanceToNext() }
        advanceRunnable = r
        handler.postDelayed(r, delayMs)
    }

    private fun cancelScheduledAdvance() {
        advanceRunnable?.let { handler.removeCallbacks(it) }
        advanceRunnable = null
    }

    private fun advanceToNext() {
        if (mediaItems.isEmpty()) return
        currentIndex = (currentIndex + 1) % mediaItems.size
        playCurrentMedia()
    }

    private fun trackPlay(mediaId: String, durationSeconds: Int) {
        val campId = campaignId ?: return
        val devId = deviceId ?: return
        lifecycleScope.launch {
            supabase.insertMediaPlay(mediaId, campId, devId, durationSeconds)
        }
    }

    // ==================== EXOPLAYER ====================

    private fun setupExoPlayer() {
        exoPlayer = ExoPlayer.Builder(this).build().also { player ->
            playerView.player = player
            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == androidx.media3.common.Player.STATE_ENDED) {
                        pendingVideoItem?.let { trackPlay(it.id, it.duration ?: 10) }
                        advanceToNext()
                    }
                }
            })
        }
    }

    // ==================== RSS ====================

    private fun fetchRss(url: String? = null, duration: Int = 15) {
        val current = mediaItems.getOrNull(currentIndex) ?: return
        val rssUrl = url ?: current.file_url
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("https://kfrdgbdoqiyzzxtoaikc.supabase.co/functions/v1/fetch-rss?url=${Uri.encode(rssUrl)}")
                    .addHeader("Authorization", "Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImtmcmRnYmRvcWl5enp4dG9haWtjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgwMDQxMjksImV4cCI6MjA5MzU4MDEyOX0.xfU4yDEjlBtk7Yc46GSSscrpxtsk6A1Zyns1TXL4Xb0")
                    .build()
                val response = publicClient.newCall(request).execute()
                val body = response.body?.string() ?: "{}"
                val json = gson.fromJson(body, JsonObject::class.java)
                val itemsArray = json.getAsJsonArray("items") ?: JsonArray()
                val parsed = itemsArray.map { el ->
                    val o = el.asJsonObject
                    RssItem(
                        title = o.get("title")?.asString ?: "",
                        description = o.get("description")?.asString ?: "",
                        pubDate = o.get("pubDate")?.asString,
                        image = o.get("image")?.takeIf { !it.isJsonNull }?.asString
                    )
                }
                withContext(Dispatchers.Main) {
                    if (parsed.isNotEmpty()) {
                        rssItems = parsed
                        rssIndex = 0
                        showRssItem(rssItems[0])
                        handler.removeCallbacks(rssCycleRunnable)
                        handler.postDelayed(rssCycleRunnable, duration * 1000L)
                    } else {
                        advanceToNext()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { advanceToNext() }
            }
        }
    }

    private val rssCycleRunnable = object : Runnable {
        override fun run() {
            if (rssItems.isEmpty()) return
            rssIndex = (rssIndex + 1) % rssItems.size
            showRssItem(rssItems[rssIndex])
            val current = mediaItems.getOrNull(currentIndex)
            handler.postDelayed(this, (current?.duration ?: 15) * 1000L)
        }
    }

    private fun showRssItem(item: RssItem) {
        findViewById<TextView>(R.id.tvRssTitle).text = item.title
        findViewById<TextView>(R.id.tvRssDescription).text = item.description
        val ivRss = findViewById<ImageView>(R.id.ivRssImage)
        if (item.image.isNullOrBlank()) {
            ivRss.visibility = View.GONE
        } else {
            ivRss.visibility = View.VISIBLE
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val bmp = java.net.URL(item.image).openStream().use { BitmapFactory.decodeStream(it) }
                    withContext(Dispatchers.Main) { ivRss.setImageBitmap(bmp) }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { ivRss.visibility = View.GONE }
                }
            }
        }
        val tvDate = findViewById<TextView>(R.id.tvRssDate)
        if (!item.pubDate.isNullOrBlank()) {
            tvDate.visibility = View.VISIBLE
            tvDate.text = formatRssDate(item.pubDate)
        } else {
            tvDate.visibility = View.GONE
        }
    }

    private fun formatRssDate(pubDate: String): String {
        val formats = listOf(
            "EEE, dd MMM yyyy HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ssXXX"
        )
        for (f in formats) {
            try {
                val date = SimpleDateFormat(f, Locale.US).parse(pubDate)
                if (date != null) {
                    return SimpleDateFormat("dd 'de' MMMM 'de' yyyy, HH:mm", Locale("pt", "BR")).format(date)
                }
            } catch (_: Exception) { }
        }
        return pubDate
    }

    private fun getYoutubeEmbedUrl(url: String): String {
        val regex = Regex("""(?:youtu\.be/|v/|u/\w/|embed/|watch\?v=|&v=)([^#&?]{11})""")
        val match = regex.find(url)
        val videoId = match?.groupValues?.get(1)
        return if (videoId != null) {
            "https://www.youtube.com/embed/$videoId?autoplay=1&mute=1&loop=1&playlist=$videoId"
        } else url
    }

    // ==================== GPS (a cada 30s, igual ao protótipo React) ====================

    private fun requestLocationAndTrack() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .build()
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { loc ->
                    val userId = prefs.getUserId() ?: return
                    lifecycleScope.launch {
                        supabase.insertScreenLocation(
                            userId, deviceId, campaignId,
                            loc.latitude, loc.longitude, loc.accuracy.toDouble()
                        )
                    }
                }
            }
        }
        try {
            fusedClient.requestLocationUpdates(request, locationCallback!!, Looper.getMainLooper())
        } catch (_: SecurityException) { }
    }

    private fun stopLocationUpdates() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
    }

    // ==================== ESTADOS DE TELA ====================

    private fun showOnly(container: LinearLayout?) {
        loadingContainer.visibility = if (container == loadingContainer) View.VISIBLE else View.GONE
        errorContainer.visibility = if (container == errorContainer) View.VISIBLE else View.GONE
        emptyContainer.visibility = if (container == emptyContainer) View.VISIBLE else View.GONE
        if (container != null) hideAllMediaViews()
    }

    private fun showError(message: String) {
        showOnly(errorContainer)
        tvErrorMessage.text = message
    }

    override fun onResume() {
        super.onResume()
        enterImmersiveMode()
        handler.postDelayed(refreshPollRunnable, 60_000L)
        handler.postDelayed(rssRefreshRunnable, 300_000L)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshPollRunnable)
        handler.removeCallbacks(rssRefreshRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelScheduledAdvance()
        handler.removeCallbacksAndMessages(null)
        stopLocationUpdates()
        exoPlayer?.release()
        exoPlayer = null
    }
}

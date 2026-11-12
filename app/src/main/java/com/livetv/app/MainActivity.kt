package com.livetv.app

import android.Manifest
import android.app.PictureInPictureParams
import android.app.UiModeManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.telephony.TelephonyManager
import android.util.Rational
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.OrientationEventListener
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var store: Store
    private lateinit var ui: Ui
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var root: LinearLayout
    private lateinit var left: LinearLayout
    private lateinit var videoBox: FrameLayout
    private lateinit var bar: LinearLayout
    private lateinit var panel: LinearLayout
    private lateinit var pickRow: LinearLayout
    private lateinit var title: TextView
    private lateinit var status: TextView
    private lateinit var search: EditText
    private lateinit var tabViews: List<TextView>
    private lateinit var btnFav: TextView
    private lateinit var btnPlay: TextView
    private lateinit var btnRec: TextView
    private lateinit var btnQ: TextView
    private lateinit var btnFull: TextView
    private lateinit var btnMore: TextView
    private lateinit var btnAction: TextView
    private lateinit var btnScan: TextView
    private lateinit var btnMode: TextView
    private lateinit var btnItem: TextView
    private lateinit var radioCard: LinearLayout
    private lateinit var cardLogo: ImageView
    private lateinit var cardName: TextView
    private lateinit var cardSong: TextView
    private lateinit var eq: EqualizerView
    private lateinit var orient: OrientationEventListener

    private val adapter = ChAdapter()
    private var isTv = false
    private var tvFull = false
    private var tab = 0
    private var mode = 0
    private var curIdx = 0
    private var themeIdx = 1
    private var qIdx = 0
    private var fitIdx = 0
    private var dataSaver = false
    private var lite = false
    private var hideDead = true
    private var verifiedOnly = false
    private var sortIdx = 0
    private var boostIdx = 0
    private var waitingNet = false
    private var enhancer: LoudnessEnhancer? = null
    private var netCb: ConnectivityManager.NetworkCallback? = null
    private val boostGain = intArrayOf(0, 300, 600, 1000)
    private val boostNames = arrayOf("", "+3 dB", "+6 dB", "+10 dB")

    private val resumeTick = object : Runnable {
        override fun run() {
            saveResume()
            val c = nowPlaying
            if (c != null && c.url.startsWith("content:")) handler.postDelayed(this, 15000)
        }
    }
    private var audioOnly = false
    private var attempt = 0
    private var urlIdx = 0
    private var urlQueue: List<String> = emptyList()
    private var currentKey = ""
    private var nowPlaying: Channel? = null
    private var loaded: List<Channel> = emptyList()
    private var favs: MutableList<Channel> = ArrayList()
    private var files: MutableList<Channel> = ArrayList()
    private var playlists: MutableList<Playlist> = ArrayList()
    private var openPlaylist: Playlist? = null
    private var rec: Recorder? = null
    private var checker: Checker? = null
    private var scanning = false
    private var countryItems: List<Item> = Data.countries
    private var curItems: List<Item> = Data.countries
    private var safeMode = false
    private var inPip = false
    private var prevChannel: Channel? = null
    private var recents: MutableList<Channel> = ArrayList()
    private var blocked: MutableSet<String> = HashSet()
    private var lockedCats: MutableSet<String> = HashSet()
    private val unlocked = HashSet<String>()
    private var numBuf = ""

    private val numRun = Runnable {
        val n = numBuf.toIntOrNull() ?: 0
        numBuf = ""
        val items = adapter.items.filter { !it.url.startsWith("pl:") && it.url != "back:" }
        if (n in 1..items.size) play(items[n - 1])
    }

    private val sleepRun = Runnable {
        player.pause()
        val r = rec
        if (r != null && r.active) r.stop()
        setStatus(getString(R.string.sleep_done))
    }

    private val recStopRun = Runnable {
        val r = rec
        if (r != null && r.active) r.stop()
    }

    private val backupLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) doBackup(uri)
        }

    private val restoreLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) doRestore(uri)
        }

    private val retryRun = Runnable {
        player.seekToDefaultPosition()
        player.prepare()
    }

    private val recTick = object : Runnable {
        override fun run() {
            val r = rec
            if (r != null && r.active) {
                btnRec.text = "■ " + (r.bytes / 1048576) + " MB"
                handler.postDelayed(this, 1000)
            }
        }
    }

    private val pickFiles =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            for (u in uris) {
                try {
                    contentResolver.takePersistableUriPermission(
                        u, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                }
                val s = u.toString()
                if (files.none { it.url == s }) files.add(Channel(displayName(u), s))
            }
            store.saveList("files", files)
            if (tab == 3) refreshList()
        }

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startRecording()
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val n = nowPlaying?.name ?: ""
            if (state == Player.STATE_BUFFERING) {
                setStatus(getString(R.string.status_buffering, n))
            } else if (state == Player.STATE_READY) {
                attempt = 0
                waitingNet = false
                urlQueue.getOrNull(urlIdx)?.let { Health.mark(it, true) }
                setStatus(getString(R.string.status_playing, n))
                updateRadioCard()
            } else if (state == Player.STATE_ENDED && tab >= 3) {
                step(1)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            handleError()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            applyBoost()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            updatePlayBtn()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlayBtn()
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateRadioCard()
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            updateSong()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN)

        store = Store(this)
        ui = Ui(this)
        themeIdx = store.getInt("theme", 1)
        qIdx = store.getInt("q", 0)
        mode = store.getInt("mode", 0)
        fitIdx = store.getInt("fit", 0)
        dataSaver = store.getBool("datasaver", false)
        lite = store.getBool("lite", false)
        hideDead = store.getBool("hidedead", true)
        verifiedOnly = store.getBool("verified", false)
        sortIdx = store.getInt("sort", 0)
        boostIdx = store.getInt("boost", 0)
        audioOnly = store.getBool("audioonly", false)
        favs = store.getList("fav")
        files = store.getList("files")
        playlists = store.getPlaylists()
        recents = store.getList("recent")
        blocked = store.getSet("blocked")
        lockedCats = store.getSet("locked")
        ui.lite = lite

        // Safe start: if the last run crashed, skip auto-play and refresh saved lists.
        val crashText = store.getString("crash_text", "")
        safeMode = store.getBool("crashed", false) || store.getBool("launching", false)
        store.putBool("crashed", false)
        store.putString("crash_text", "")
        store.putBool("launching", true)
        handler.postDelayed({ store.putBool("launching", false) }, 8000)
        if (safeMode) {
            store.clearCache()
            Health.clear()
        }

        val um = getSystemService(UI_MODE_SERVICE) as UiModeManager
        isTv = um.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            !packageManager.hasSystemFeature("android.hardware.touchscreen")

        player = PlayerHolder.get(this)
        player.addListener(listener)
        buildUi()
        setupOrientation()
        setupBack()
        applyQuality()
        applyAudioOnly()
        applyBoost()
        registerNet()
        loadCountriesFromNet()

        if (store.getString("sel0_0", "").isEmpty()) {
            val c = detectCountry()
            if (c.isNotEmpty()) {
                store.putString("sel0_0", c)
                store.putString("sel1_0", c)
            }
        }
        selectTab(0)

        val last = store.getList("last_ch").firstOrNull()
        if (last != null && player.mediaItemCount > 0) {
            nowPlaying = last
            urlQueue = last.allUrls()
            updateFavBtn()
            updateRadioCard()
        } else if (last != null && !safeMode) {
            try {
                play(last)
            } catch (e: Exception) {
            }
        }

        if (Build.VERSION.SDK_INT >= 33 && !isTv &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (safeMode) {
            if (crashText.isNotEmpty()) showCrash(crashText) else setStatus(getString(R.string.safe_start))
        } else {
            val now = System.currentTimeMillis()
            val lastCheck = store.getString("upd_ts", "0").toLongOrNull() ?: 0L
            if (now - lastCheck > 86400000L) {
                store.putString("upd_ts", now.toString())
                Updater.check(this, false)
            }
        }
    }

    // ---------- helpers ----------

    private fun setStatus(s: String) {
        status.text = s
    }

    private fun onOff(b: Boolean): String = getString(if (b) R.string.on else R.string.off)

    @Suppress("DEPRECATION")
    private fun isMobileData(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetworkInfo?.type == ConnectivityManager.TYPE_MOBILE
        } catch (e: Exception) {
            false
        }
    }

    private fun detectCountry(): String {
        try {
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            var c = tm?.networkCountryIso ?: ""
            if (c.isEmpty()) c = tm?.simCountryIso ?: ""
            if (c.isNotEmpty()) return if (c == "gb") "uk" else c.lowercase()
        } catch (e: Exception) {
        }
        return ""
    }

    private fun displayName(u: Uri): String {
        var n: String? = null
        try {
            contentResolver.query(u, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) n = c.getString(i)
                }
            }
        } catch (e: Exception) {
        }
        return n ?: (u.lastPathSegment ?: "File")
    }

    private fun showCrash(text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 11f
        tv.setTextIsSelectable(true)
        tv.setPadding(ui.dp(16), ui.dp(8), ui.dp(16), ui.dp(8))
        val sv = ScrollView(this)
        sv.addView(tv)
        AlertDialog.Builder(this)
            .setTitle(R.string.crash_title)
            .setMessage(R.string.crash_msg)
            .setView(sv)
            .setPositiveButton(R.string.crash_copy) { _, _ ->
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("error", text))
            }
            .setNegativeButton(R.string.dlg_ok, null)
            .show()
    }

    // ---------- player ----------

    private fun applyQuality() {
        var cap = when (qIdx) {
            1 -> 2160
            2 -> 1080
            3 -> 720
            4 -> 480
            5 -> 360
            6 -> 240
            else -> 1080
        }
        if (qIdx == 0 && isMobileData()) cap = minOf(cap, 720)
        if (dataSaver) cap = minOf(cap, 480)
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(cap * 16 / 9, cap)
            .build()
    }

    private fun applyAudioOnly() {
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
    }

    private fun applyFit() {
        playerView.resizeMode = when (fitIdx) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    private fun rank(state: Int): Int = when (state) {
        1 -> 0
        0 -> 1
        else -> 2
    }

    private fun play(ch: Channel) {
        checker?.stop()
        checker = null
        if (scanning) {
            scanning = false
            Health.save()
        }
        saveResume()
        val before = nowPlaying
        if (before != null && before.url != ch.url) prevChannel = before
        nowPlaying = ch
        addRecent(ch)
        waitingNet = false
        handler.removeCallbacks(retryRun)
        handler.removeCallbacks(resumeTick)
        if (ch.url.startsWith("content:")) handler.postDelayed(resumeTick, 15000)
        attempt = 0
        urlQueue = ch.allUrls().sortedBy { rank(Health.state(it)) }
        urlIdx = 0
        store.saveList("last_ch", listOf(ch))
        setStatus(getString(R.string.status_loading, ch.name))
        startUrl(ch, urlQueue[0])
        try {
            startService(Intent(this, PlaybackService::class.java))
        } catch (e: Exception) {
        }
        updateFavBtn()
        updatePlayBtn()
        adapter.notifyDataSetChanged()
        updateRadioCard()
    }

    private fun startUrl(ch: Channel, url: String) {
        val mb = MediaMetadata.Builder().setTitle(ch.name)
        if (ch.logo.isNotEmpty()) mb.setArtworkUri(Uri.parse(ch.logo))
        val item = MediaItem.Builder().setUri(url).setMediaMetadata(mb.build()).build()
        val startAt = if (url.startsWith("content:")) resumePos(url) else 0L
        if (startAt > 0L) player.setMediaItem(item, startAt) else player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
    }

    private fun handleError() {
        val ch = nowPlaying ?: return
        if (urlIdx + 1 < urlQueue.size) {
            if (isOnline()) urlQueue.getOrNull(urlIdx)?.let { Health.mark(it, false) }
            urlIdx++
            setStatus(getString(R.string.status_alt))
            startUrl(ch, urlQueue[urlIdx])
        } else if (urlQueue.size <= 1 && attempt < 3) {
            attempt++
            setStatus(getString(R.string.status_retry, attempt, 3))
            handler.postDelayed(retryRun, 3000)
        } else {
            if (isOnline()) urlQueue.getOrNull(urlIdx)?.let { Health.mark(it, false) }
            waitingNet = true
            setStatus(getString(R.string.status_dead))
        }
    }

    private fun step(d: Int) {
        val items = adapter.items.filter { !it.url.startsWith("pl:") && it.url != "back:" }
        if (items.isEmpty()) return
        val cur = items.indexOfFirst { it.url == nowPlaying?.url }
        val next = if (cur < 0) 0 else (cur + d + items.size) % items.size
        play(items[next])
    }

    private fun updateRadioCard() {
        val np = nowPlaying
        val isAudio = np != null && player.playbackState == Player.STATE_READY &&
            !player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        if (isAudio && np != null) {
            radioCard.visibility = View.VISIBLE
            cardName.text = np.name
            val icon = ui.letterIcon(np.name)
            if (np.logo.isNotEmpty() && !lite) {
                Glide.with(cardLogo).load(np.logo).placeholder(icon).error(icon).into(cardLogo)
            } else {
                Glide.with(cardLogo).clear(cardLogo)
                cardLogo.setImageDrawable(icon)
            }
            updateSong()
            eq.animated = !lite
            eq.start()
        } else {
            radioCard.visibility = View.GONE
            eq.stop()
        }
    }

    private fun updateSong() {
        val t = player.mediaMetadata.title?.toString() ?: ""
        val n = nowPlaying?.name ?: ""
        cardSong.text = if (t.isNotEmpty() && t != n) t else ""
    }

    // ---------- UI ----------

    private fun mkBtn(label: String, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.gravity = Gravity.CENTER
        b.textSize = 13f
        b.setPadding(ui.dp(4), ui.dp(12), ui.dp(4), ui.dp(12))
        b.isFocusable = true
        b.isClickable = true
        b.setOnClickListener { onClick() }
        b.setOnFocusChangeListener { v, has ->
            if (!lite) {
                val sc = if (has) 1.06f else 1f
                v.animate().scaleX(sc).scaleY(sc).setDuration(120).start()
            }
        }
        return b
    }

    private fun buildRadioCard() {
        radioCard = LinearLayout(this)
        radioCard.orientation = LinearLayout.VERTICAL
        radioCard.gravity = Gravity.CENTER
        radioCard.visibility = View.GONE
        radioCard.background = ui.grad(0xFF0B1B3A.toInt(), 0xFF1E3A8A.toInt(), 0)
        radioCard.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
        cardLogo = ImageView(this)
        cardLogo.scaleType = ImageView.ScaleType.FIT_CENTER
        radioCard.addView(cardLogo, LinearLayout.LayoutParams(ui.dp(64), ui.dp(64)))
        cardName = TextView(this)
        cardName.textSize = 17f
        cardName.setTypeface(null, Typeface.BOLD)
        cardName.setTextColor(Color.WHITE)
        cardName.gravity = Gravity.CENTER
        cardName.maxLines = 1
        cardName.ellipsize = TextUtils.TruncateAt.END
        radioCard.addView(cardName, ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0f, 0, 6, 0, 0))
        cardSong = TextView(this)
        cardSong.textSize = 13f
        cardSong.setTextColor(0xFFBFD3FF.toInt())
        cardSong.gravity = Gravity.CENTER
        cardSong.maxLines = 1
        cardSong.ellipsize = TextUtils.TruncateAt.END
        radioCard.addView(cardSong, ui.lp(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        eq = EqualizerView(this)
        eq.barColor = 0xFF22D3EE.toInt()
        radioCard.addView(eq, ui.lp(ui.dp(110), ui.dp(30), 0f, 0, 8, 0, 0))
    }

    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

        root = LinearLayout(this)
        left = LinearLayout(this)
        left.orientation = LinearLayout.VERTICAL

        playerView = PlayerView(this)
        playerView.useController = false
        playerView.player = player
        applyFit()
        buildRadioCard()

        videoBox = FrameLayout(this)
        videoBox.setBackgroundColor(Color.BLACK)
        videoBox.addView(playerView, FrameLayout.LayoutParams(match, match))
        videoBox.addView(radioCard, FrameLayout.LayoutParams(match, match))

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!isTv) toggleRotate()
                return true
            }
        })
        videoBox.setOnTouchListener { _, ev ->
            gd.onTouchEvent(ev)
            true
        }

        btnPlay = mkBtn("▶") { togglePlay() }
        btnFav = mkBtn("♡ " + getString(R.string.b_fav)) { nowPlaying?.let { toggleFav(it) } }
        btnRec = mkBtn("● " + getString(R.string.b_rec)) { toggleRecord() }
        btnQ = mkBtn(getString(R.string.b_quality)) { showQuality() }
        btnFull = mkBtn(getString(R.string.b_full)) { goFull() }
        btnMore = mkBtn(getString(R.string.b_more)) { showMore() }
        bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(ui.dp(4), ui.dp(2), ui.dp(4), ui.dp(2))
        for (b in listOf(btnPlay, btnFav, btnRec, btnQ, btnFull, btnMore)) {
            bar.addView(b, ui.lp(0, wrap, 1f, 2, 2, 2, 2))
        }

        left.addView(videoBox)
        left.addView(bar, ui.lp(match, wrap, 0f, 8, 8, 8, 4))

        val logoIv = ImageView(this)
        logoIv.setImageResource(R.drawable.ic_launcher)
        title = TextView(this)
        title.text = getString(R.string.app_name)
        title.textSize = 20f
        title.setTypeface(null, Typeface.BOLD)
        status = TextView(this)
        status.textSize = 12f
        status.maxLines = 1
        status.ellipsize = TextUtils.TruncateAt.END
        val tcol = LinearLayout(this)
        tcol.orientation = LinearLayout.VERTICAL
        tcol.addView(title)
        tcol.addView(status)
        val header = LinearLayout(this)
        header.orientation = LinearLayout.HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(4))
        header.addView(logoIv, ui.lp(ui.dp(40), ui.dp(40), 0f, 0, 0, 10, 0))
        header.addView(tcol, ui.lp(0, wrap, 1f))
        btnScan = mkBtn("") { if (scanning) stopScan() else startScan() }
        btnScan.setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
        btnScan.visibility = View.GONE
        header.addView(btnScan, ui.lp(wrap, wrap, 0f, 6, 0, 0, 0))

        val labels = listOf(
            getString(R.string.tab_tv), getString(R.string.tab_radio), getString(R.string.tab_fav),
            getString(R.string.tab_files), getString(R.string.tab_lists)
        )
        tabViews = labels.mapIndexed { i, s -> mkBtn(s) { selectTab(i) } }
        val tabs = LinearLayout(this)
        tabs.orientation = LinearLayout.HORIZONTAL
        for (t in tabViews) tabs.addView(t, ui.lp(0, wrap, 1f, 3, 0, 3, 0))

        btnAction = mkBtn("") { onAction() }
        btnAction.textSize = 14f
        btnAction.visibility = View.GONE

        btnMode = mkBtn("") {
            val opts = listOf(
                getString(R.string.mode_country), getString(R.string.mode_category),
                getString(R.string.mode_world)
            )
            ui.picker(this, getString(R.string.pick_mode), opts, mode) { pos ->
                val worldLocked = tab == 0 && pos == 2 &&
                    store.getString("pin", "").isNotEmpty() &&
                    lockedCats.isNotEmpty() && !unlocked.contains("world")
                if (worldLocked) {
                    askPin {
                        unlocked.add("world")
                        applyMode(pos)
                    }
                } else {
                    applyMode(pos)
                }
            }
        }
        btnItem = mkBtn("") {
            ui.picker(
                this,
                getString(if (mode == 0) R.string.pick_country else R.string.pick_category),
                curItems.map { it.label }, curIdx, true
            ) { pos -> selectItem(pos) }
        }
        for (b in listOf(btnMode, btnItem)) {
            b.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            b.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
            b.maxLines = 1
            b.ellipsize = TextUtils.TruncateAt.END
            b.textSize = 14f
        }
        pickRow = LinearLayout(this)
        pickRow.orientation = LinearLayout.HORIZONTAL
        pickRow.addView(btnMode, ui.lp(wrap, wrap, 0f, 0, 0, 4, 0))
        pickRow.addView(btnItem, ui.lp(0, wrap, 1f))

        search = EditText(this)
        search.hint = getString(R.string.search_hint)
        search.setSingleLine(true)
        search.textSize = 14f
        search.setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                refreshList()
            }
        })

        val list = RecyclerView(this)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(0, 0, 0, ui.dp(8))

        panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        panel.addView(header)
        panel.addView(tabs, ui.lp(match, wrap, 0f, 8, 4, 8, 4))
        panel.addView(btnAction, ui.lp(match, wrap, 0f, 8, 2, 8, 4))
        val filterRow = LinearLayout(this)
        filterRow.orientation = LinearLayout.HORIZONTAL
        filterRow.gravity = Gravity.CENTER_VERTICAL
        filterRow.addView(pickRow, ui.lp(0, wrap, 1.7f, 0, 0, 4, 0))
        filterRow.addView(search, ui.lp(0, wrap, 1f))
        panel.addView(filterRow, ui.lp(match, wrap, 0f, 8, 2, 8, 4))
        panel.addView(list, ui.lp(match, 0, 1f))

        root.addView(left)
        root.addView(panel)
        setContentView(root)
        applyTheme()
        applyLayout()
        updatePlayBtn()
    }

    private fun applyTheme() {
        val t = Themes.get(themeIdx)
        ui.theme = t
        root.background = ui.grad(t.bg, t.card2, 0)
        bar.background = ui.grad(t.card, t.card2, 16)
        bar.elevation = ui.dp(6).toFloat()
        title.setTextColor(t.text)
        status.setTextColor(t.sub)
        for (b in listOf(btnPlay, btnFav, btnRec, btnQ, btnFull, btnMore)) {
            b.setTextColor(t.text)
            b.background = ui.stateBg(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT), 12)
        }
        btnAction.setTextColor(t.accent)
        btnAction.background = ui.stateBg(ui.rr(ui.alpha(t.accent, 0x28), 12, t.accent, 1), 12)
        btnScan.setTextColor(t.accent)
        btnScan.background = ui.stateBg(ui.rr(ui.alpha(t.accent, 0x28), 12, t.accent, 1), 12)
        for (b in listOf(btnMode, btnItem)) {
            b.setTextColor(t.text)
            b.background = ui.stateBg(ui.rr(t.card, 12, ui.alpha(t.sub, 0x66), 1), 12)
        }
        search.setTextColor(t.text)
        search.setHintTextColor(t.sub)
        search.background = ui.rr(t.card, 12, ui.alpha(t.sub, 0x66), 1)
        adapter.notifyDataSetChanged()
        updateTabs()
    }

    private fun updateTabs() {
        val t = Themes.get(themeIdx)
        val onAccent = if (themeIdx == 2) Color.BLACK else Color.WHITE
        for (i in tabViews.indices) {
            val b = tabViews[i]
            if (i == tab) {
                b.setTextColor(onAccent)
                b.background = ui.stateBg(ui.rr(t.accent, 12), 12)
            } else {
                b.setTextColor(t.text)
                b.background = ui.stateBg(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT), 12)
            }
        }
    }

    private fun updateRows() {
        pickRow.visibility = if (tab <= 1) View.VISIBLE else View.GONE
        val showAction = tab == 3 || (tab == 4 && openPlaylist == null)
        btnAction.visibility = if (showAction) View.VISIBLE else View.GONE
        btnAction.text = if (tab == 3) getString(R.string.add_files) else getString(R.string.new_playlist_btn)
        btnScan.visibility = if (tab == 0) View.VISIBLE else View.GONE
        btnScan.text = if (scanning) "■ " + getString(R.string.scan_stop) else scanLabel()
    }

    private fun updatePickerLabels() {
        val m = when (mode) {
            0 -> R.string.mode_country
            2 -> R.string.mode_world
            else -> R.string.mode_category
        }
        btnMode.text = getString(m) + "  ▾"
        btnItem.visibility = if (mode == 2) View.GONE else View.VISIBLE
        if (curIdx in curItems.indices) btnItem.text = curItems[curIdx].label + "  ▾"
    }

    private fun applyLayout() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val ctrl = WindowCompat.getInsetsController(window, window.decorView)
        val full = inPip || (!isTv && land) || (isTv && tvFull)
        if (full) {
            root.orientation = LinearLayout.VERTICAL
            left.layoutParams = LinearLayout.LayoutParams(match, match)
            videoBox.layoutParams = LinearLayout.LayoutParams(match, 0, 1f)
            bar.visibility = View.GONE
            panel.visibility = View.GONE
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ctrl.show(WindowInsetsCompat.Type.systemBars())
            bar.visibility = View.VISIBLE
            panel.visibility = View.VISIBLE
            if (isTv) {
                root.orientation = LinearLayout.HORIZONTAL
                left.layoutParams = LinearLayout.LayoutParams(0, match, 2f)
                videoBox.layoutParams = LinearLayout.LayoutParams(match, 0, 1f)
                panel.layoutParams = LinearLayout.LayoutParams(0, match, 1f)
            } else {
                root.orientation = LinearLayout.VERTICAL
                val w = resources.displayMetrics.widthPixels
                left.layoutParams = LinearLayout.LayoutParams(match, wrap)
                videoBox.layoutParams = LinearLayout.LayoutParams(match, w * 9 / 16)
                panel.layoutParams = LinearLayout.LayoutParams(match, 0, 1f)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyLayout()
    }

    private fun toggleRotate() {
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        requestedOrientation = if (land) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
    }

    private fun goFull() {
        if (isTv) {
            tvFull = true
            applyLayout()
        } else {
            toggleRotate()
        }
    }

    private fun setupOrientation() {
        orient = object : OrientationEventListener(this) {
            override fun onOrientationChanged(a: Int) {
                if (a < 0) return
                val ro = requestedOrientation
                if (ro == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) return
                val physLand = (a in 70..110) || (a in 250..290)
                val physPort = a <= 20 || a >= 340 || (a in 160..200)
                if ((ro == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE && physLand) ||
                    (ro == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT && physPort)
                ) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
        if (orient.canDetectOrientation()) orient.enable()
    }

    private fun setupBack() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val land = resources.configuration.orientation ==
                    Configuration.ORIENTATION_LANDSCAPE
                if (isTv && tvFull) {
                    tvFull = false
                    applyLayout()
                } else if (!isTv && land) {
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else if (tab == 4 && openPlaylist != null) {
                    openPlaylist = null
                    updateRows()
                    refreshList()
                } else {
                    isEnabled = false
                    this@MainActivity.onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Number keys: type a channel number from the remote
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9 && currentFocus !is EditText) {
            numBuf += (keyCode - KeyEvent.KEYCODE_0).toString()
            if (numBuf.length > 4) numBuf = numBuf.takeLast(4)
            setStatus(getString(R.string.status_ch_number, numBuf.toInt()))
            handler.removeCallbacks(numRun)
            handler.postDelayed(numRun, 1500)
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_LAST_CHANNEL -> {
                goPrevious()
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_UP, KeyEvent.KEYCODE_MEDIA_NEXT,
            KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> {
                step(1)
                return true
            }
            KeyEvent.KEYCODE_CHANNEL_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS,
            KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> {
                step(-1)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                player.playWhenReady = !player.playWhenReady
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PLAY -> {
                player.playWhenReady = true
                return true
            }
            KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> {
                player.pause()
                return true
            }
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                seekBy(10000L)
                return true
            }
            KeyEvent.KEYCODE_MEDIA_REWIND -> {
                seekBy(-10000L)
                return true
            }
            KeyEvent.KEYCODE_PROG_RED -> {
                nowPlaying?.let { toggleFav(it) }
                return true
            }
            KeyEvent.KEYCODE_PROG_GREEN -> {
                showQuality()
                return true
            }
            KeyEvent.KEYCODE_PROG_YELLOW -> {
                toggleRecord()
                return true
            }
            KeyEvent.KEYCODE_PROG_BLUE -> {
                showMore()
                return true
            }
            KeyEvent.KEYCODE_MENU -> {
                val t = currentFocus?.tag
                if (t is Channel) showItemMenu(t) else showMore()
                return true
            }
            KeyEvent.KEYCODE_SEARCH -> {
                search.requestFocus()
                return true
            }
            KeyEvent.KEYCODE_GUIDE -> {
                if (tvFull) {
                    tvFull = false
                    applyLayout()
                }
                return true
            }
        }
        if (isTv) {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    am.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI
                    )
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    am.adjustStreamVolume(
                        AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI
                    )
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_MUTE -> {
                    if (Build.VERSION.SDK_INT >= 23) {
                        am.adjustStreamVolume(
                            AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE,
                            AudioManager.FLAG_SHOW_UI
                        )
                        return true
                    }
                }
            }
        }
        if (isTv && tvFull) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_PAGE_DOWN -> {
                    step(-1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_PAGE_UP -> {
                    step(1)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (nowPlaying?.url?.startsWith("content:") == true) {
                        seekBy(-10000L)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (nowPlaying?.url?.startsWith("content:") == true) {
                        seekBy(10000L)
                        return true
                    }
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    tvFull = false
                    applyLayout()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    // ---------- tabs and lists ----------

    private fun selectTab(t: Int) {
        tab = t
        openPlaylist = null
        search.setText("")
        if (t <= 1) {
            val prefix = if (t == 0) "tv/" else "radio/"
            if (!currentKey.startsWith(prefix)) {
                loaded = emptyList()
                currentKey = ""
            }
            setItemsForMode()
        }
        updateTabs()
        updateRows()
        refreshList()
    }

    private fun setItemsForMode() {
        curItems = if (mode == 2) {
            listOf(Item("World", "world"))
        } else if (mode == 0) {
            countryItems
        } else if (tab == 0) {
            Data.categories
        } else {
            RData.tags
        }
        val saved = store.getString(
            "sel${tab}_$mode",
            if (mode == 0) "pk" else if (mode == 2) "world" else "news"
        )
        val idx = curItems.indexOfFirst { it.key == saved }
        curIdx = if (idx < 0) 0 else idx
        updatePickerLabels()
        loadList(curItems[curIdx])
    }

    private fun isLocked(item: Item): Boolean {
        if (tab != 0 || store.getString("pin", "").isEmpty() || unlocked.contains(item.key)) return false
        return (mode == 1 && lockedCats.contains(item.key)) ||
            (mode == 2 && lockedCats.isNotEmpty())
    }

    private fun selectItem(pos: Int) {
        if (pos !in curItems.indices) return
        val item = curItems[pos]
        if (isLocked(item)) {
            askPin {
                unlocked.add(item.key)
                selectItem(pos)
            }
            return
        }
        curIdx = pos
        store.putString("sel${tab}_$mode", item.key)
        updatePickerLabels()
        loadList(item)
    }

    private fun parseAny(key: String, text: String): List<Channel> =
        if (key.startsWith("tv/")) parseM3u(text) else parseRadio(text)

    private fun fetchFor(key: String, item: Item): String? {
        if (key == "tv/world") {
            return store.download("https://iptv-org.github.io/iptv/index.m3u")
        }
        if (key.startsWith("tv/")) {
            return store.download(
                "https://iptv-org.github.io/iptv/" + key.removePrefix("tv/") + ".m3u"
            )
        }
        var q = "?hidebroken=true&order=clickcount&reverse=true&limit=500"
        val path = if (key == "radio/world") {
            q = "?hidebroken=true"
            "topclick/1000"
        } else if (key.startsWith("radio/country/")) {
            var cc = item.key.uppercase()
            if (cc == "UK") cc = "GB"
            "bycountrycodeexact/$cc"
        } else {
            "bytag/" + item.key
        }
        for (h in listOf("de1", "nl1", "at1")) {
            val t = store.download("https://$h.api.radio-browser.info/json/stations/$path$q")
            if (t != null && t.startsWith("[")) return t
        }
        return null
    }

    private fun loadList(item: Item) {
        if (isLocked(item)) {
            stopScan()
            currentKey = ""
            loaded = emptyList()
            refreshList()
            setStatus(getString(R.string.lock_locked))
            return
        }
        val key = if (tab == 0) {
            when (mode) {
                0 -> "tv/countries/" + item.key
                1 -> "tv/categories/" + item.key
                else -> "tv/world"
            }
        } else {
            when (mode) {
                0 -> "radio/country/" + item.key
                1 -> "radio/tag/" + item.key
                else -> "radio/world"
            }
        }
        if (key == currentKey) return
        stopScan()
        currentKey = key
        loaded = emptyList()
        refreshList()
        setStatus(getString(R.string.status_loading_list))
        Thread {
            // Lists are downloaded once and kept on the device.
            var text = store.readCache(key)
            if (text == null) {
                text = fetchFor(key, item)
                if (text != null) store.writeCache(key, text)
            }
            val parsed = if (text != null) parseAny(key, text) else null
            handler.post {
                if (key != currentKey) return@post
                if (parsed != null) {
                    loaded = parsed
                    refreshList()
                    updateRows()
                } else {
                    setStatus(getString(R.string.status_list_failed))
                }
            }
        }.start()
    }

    private fun refreshList() {
        val pl = openPlaylist
        var base: List<Channel> = when (tab) {
            0, 1 -> loaded
            2 -> favs
            3 -> files
            else -> if (pl == null) {
                playlists.map { Channel("▸ " + it.name + " (" + it.items.size + ")", "pl:" + it.name) }
            } else {
                pl.items
            }
        }
        if (tab <= 1 && blocked.isNotEmpty()) base = base.filter { !blocked.contains(it.url) }
        var hidden = 0
        if (tab == 0 && hideDead) {
            val keep = ArrayList<Channel>()
            for (c in base) {
                if (c.allUrls().all { Health.state(it) == -1 }) hidden++ else keep.add(c)
            }
            base = keep
        }
        if (tab == 0 && verifiedOnly) {
            base = base.filter { c -> c.allUrls().any { Health.state(it) == 1 } }
        }
        if (tab <= 1) {
            base = when (sortIdx) {
                1 -> base.sortedBy { it.name.lowercase() }
                2 -> base.sortedBy { c -> if (c.allUrls().any { Health.state(it) == 1 }) 0 else 1 }
                else -> base
            }
        }
        val q = search.text.toString().trim()
        var f: List<Channel> =
            if (q.isEmpty()) base else base.filter { it.name.contains(q, ignoreCase = true) }
        if (tab == 4 && pl != null) f = listOf(Channel(getString(R.string.back_item), "back:")) + f
        adapter.items = f
        adapter.notifyDataSetChanged()
        var s = getString(R.string.status_items, f.size)
        if (tab == 0) {
            val v = f.count { c -> c.allUrls().any { Health.state(it) == 1 } }
            if (v > 0) s += "  ·  " + getString(R.string.status_verified, v)
        }
        if (hidden > 0) s += "  ·  " + getString(R.string.status_hidden, hidden)
        if (!scanning) setStatus(s)
    }

    private fun loadCountriesFromNet() {
        val cached = store.readCache("countries.json")
        if (cached != null) applyCountries(cached)
        if (cached != null) return
        Thread {
            val text = store.download("https://iptv-org.github.io/api/countries.json")
            if (text != null) {
                store.writeCache("countries.json", text)
                handler.post { applyCountries(text) }
            }
        }.start()
    }

    private fun applyCountries(text: String) {
        try {
            val arr = JSONArray(text)
            val l = ArrayList<Item>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                l.add(Item(o.getString("name"), o.getString("code").lowercase()))
            }
            if (l.size > 20) {
                l.sortBy { it.label }
                countryItems = l
                if (mode == 0 && tab <= 1 && ::btnItem.isInitialized) setItemsForMode()
            }
        } catch (e: Exception) {
        }
    }

    // ---------- channel scan (run once, saved on the device) ----------

    private fun onAction() {
        when (tab) {
            0 -> if (scanning) stopScan() else startScan()
            3 -> pickFiles.launch(arrayOf("audio/*", "video/*"))
            4 -> newPlaylist(null)
        }
    }

    private fun startScan() {
        stopScan()
        val urls = ArrayList<String>()
        for (c in loaded) for (u in c.allUrls()) if (Health.state(u) == 0) urls.add(u)
        if (urls.isEmpty()) {
            Health.save()
            refreshList()
            return
        }
        scanning = true
        updateRows()
        val limit = if (mode == 2) 4000 else 2000
        val ck = Checker(if (dataSaver || lite) 2 else if (mode == 2) 6 else 3)
        checker = ck
        var lastRefresh = 0L
        ck.start(urls.take(limit)) { d, t ->
            handler.post {
                if (ck !== checker) return@post
                if (d >= t) {
                    scanning = false
                    checker = null
                    Health.save()
                    updateRows()
                    refreshList()
                } else {
                    setStatus(getString(R.string.status_checking, d, t))
                    val now = System.currentTimeMillis()
                    if (now - lastRefresh > 4000L) {
                        lastRefresh = now
                        refreshList()
                    }
                }
            }
        }
    }

    private fun stopScan() {
        checker?.stop()
        checker = null
        if (scanning) {
            scanning = false
            Health.save()
            if (::btnAction.isInitialized) updateRows()
        }
    }

    // ---------- favorites, playlists ----------

    private fun updateFavBtn() {
        val np = nowPlaying
        val on = np != null && favs.any { it.url == np.url }
        btnFav.text = (if (on) "♥ " else "♡ ") + getString(R.string.b_fav)
    }

    private fun toggleFav(ch: Channel) {
        val ex = favs.firstOrNull { it.url == ch.url }
        if (ex != null) favs.remove(ex) else favs.add(ch)
        store.saveList("fav", favs)
        updateFavBtn()
        if (tab == 2) refreshList() else adapter.notifyDataSetChanged()
        setStatus(getString(if (ex != null) R.string.fav_removed else R.string.fav_added, ch.name))
    }

    private fun onItemClick(ch: Channel) {
        if (ch.url == "back:") {
            openPlaylist = null
            updateRows()
            refreshList()
            return
        }
        if (ch.url.startsWith("pl:")) {
            openPlaylist = playlists.firstOrNull { "pl:" + it.name == ch.url }
            updateRows()
            refreshList()
            return
        }
        play(ch)
    }

    private fun newPlaylist(first: Channel?) {
        val et = EditText(this)
        et.hint = getString(R.string.dlg_name_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.dlg_new_playlist)
            .setView(et)
            .setPositiveButton(R.string.dlg_create) { _, _ ->
                var n = et.text.toString().trim()
                if (n.isEmpty()) n = getString(R.string.playlist_default, playlists.size + 1)
                while (playlists.any { it.name == n }) n += "_"
                val p = Playlist(n, ArrayList())
                if (first != null) p.items.add(first)
                playlists.add(p)
                store.savePlaylists(playlists)
                if (tab == 4) refreshList()
            }
            .setNegativeButton(R.string.dlg_cancel, null)
            .show()
    }

    private fun addToPlaylist(ch: Channel) {
        val names = ArrayList<String>()
        for (p in playlists) names.add(p.name)
        names.add(getString(R.string.new_playlist_btn))
        ui.picker(this, getString(R.string.dlg_choose_playlist), names, -1) { which ->
            if (which == playlists.size) {
                newPlaylist(ch)
            } else {
                val p = playlists[which]
                if (p.items.none { it.url == ch.url }) p.items.add(ch)
                store.savePlaylists(playlists)
                setStatus(getString(R.string.added_to, p.name))
            }
        }
    }

    private fun moveIn(list: MutableList<Channel>, ch: Channel, d: Int) {
        val i = list.indexOfFirst { it.url == ch.url }
        val j = i + d
        if (i < 0 || j < 0 || j >= list.size) return
        val t = list[i]
        list[i] = list[j]
        list[j] = t
    }

    private fun showItemMenu(ch: Channel) {
        if (ch.url == "back:") return
        if (ch.url.startsWith("pl:")) {
            val p = playlists.firstOrNull { "pl:" + it.name == ch.url } ?: return
            ui.picker(this, p.name, listOf(getString(R.string.m_delete_playlist)), -1) {
                playlists.remove(p)
                store.savePlaylists(playlists)
                refreshList()
            }
            return
        }
        val isFav = favs.any { it.url == ch.url }
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        labels.add(getString(if (isFav) R.string.m_fav_remove else R.string.m_fav_add))
        acts.add { toggleFav(ch) }
        labels.add(getString(R.string.m_playlist_add))
        acts.add { addToPlaylist(ch) }
        if (tab <= 1) {
            labels.add(getString(R.string.m_hide))
            acts.add {
                blocked.add(ch.url)
                store.putSet("blocked", blocked)
                refreshList()
            }
        }
        if (tab == 2) {
            labels.add(getString(R.string.m_up))
            acts.add {
                moveIn(favs, ch, -1)
                store.saveList("fav", favs)
                refreshList()
            }
            labels.add(getString(R.string.m_down))
            acts.add {
                moveIn(favs, ch, 1)
                store.saveList("fav", favs)
                refreshList()
            }
        }
        if (tab == 3) {
            labels.add(getString(R.string.m_remove_list))
            acts.add {
                files.removeAll { it.url == ch.url }
                store.saveList("files", files)
                refreshList()
            }
        }
        if (tab == 4 && openPlaylist != null) {
            labels.add(getString(R.string.m_up))
            acts.add {
                openPlaylist?.items?.let { moveIn(it, ch, -1) }
                store.savePlaylists(playlists)
                refreshList()
            }
            labels.add(getString(R.string.m_down))
            acts.add {
                openPlaylist?.items?.let { moveIn(it, ch, 1) }
                store.savePlaylists(playlists)
                refreshList()
            }
            labels.add(getString(R.string.m_remove_playlist))
            acts.add {
                openPlaylist?.items?.removeAll { it.url == ch.url }
                store.savePlaylists(playlists)
                refreshList()
            }
        }
        ui.picker(this, ch.name, labels, -1) { i -> acts[i]() }
    }

    // ---------- recording ----------

    private fun toggleRecord() {
        val r = rec
        if (r != null && r.active) {
            r.stop()
            setStatus(getString(R.string.rec_saving))
            return
        }
        val ch = nowPlaying
        if (ch == null) {
            setStatus(getString(R.string.rec_play_first))
            return
        }
        if (ch.url.startsWith("content:")) {
            setStatus(getString(R.string.rec_own_file))
            return
        }
        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val ch = nowPlaying ?: return
        val old = rec
        if (old != null && old.active) return
        val url = urlQueue.getOrNull(urlIdx) ?: ch.url
        val r = Recorder(this) { msg -> handler.post { recDone(msg) } }
        rec = r
        r.start(url, ch.name, ch.radio)
        setStatus(getString(R.string.rec_started))
        handler.post(recTick)
    }

    private fun recDone(msg: String) {
        btnRec.text = "● " + getString(R.string.b_rec)
        setStatus(msg)
    }

    // ---------- menus ----------

    private fun showQuality() {
        val names = listOf(
            getString(R.string.q_auto), "4K", "1080p", "720p", "480p", "360p", "240p"
        )
        ui.picker(this, getString(R.string.quality_title), names, qIdx) { which ->
            qIdx = which
            store.putInt("q", which)
            applyQuality()
        }
    }

    private fun showTheme() {
        val names = listOf(
            getString(R.string.theme_light), getString(R.string.theme_dark),
            getString(R.string.theme_amoled)
        )
        ui.picker(this, getString(R.string.set_theme), names, themeIdx) { which ->
            themeIdx = which
            store.putInt("theme", which)
            applyTheme()
        }
    }

    private fun showMore() {
        val fitName = when (fitIdx) {
            1 -> getString(R.string.fit_fill)
            2 -> getString(R.string.fit_zoom)
            else -> getString(R.string.fit_fit)
        }
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        labels.add(getString(R.string.more_last))
        acts.add { goPrevious() }
        labels.add(getString(R.string.more_recent))
        acts.add { showRecent() }
        labels.add(getString(R.string.more_fit, fitName))
        acts.add {
            fitIdx = (fitIdx + 1) % 3
            store.putInt("fit", fitIdx)
            applyFit()
        }
        labels.add(getString(R.string.more_sleep))
        acts.add { showSleep() }
        labels.add(getString(R.string.more_rec_timer))
        acts.add { showRecTimer() }
        labels.add(getString(R.string.more_boost, boostLabel()))
        acts.add {
            boostIdx = (boostIdx + 1) % boostGain.size
            store.putInt("boost", boostIdx)
            applyBoost()
        }
        labels.add(getString(R.string.more_sort, sortLabel()))
        acts.add {
            sortIdx = (sortIdx + 1) % 3
            store.putInt("sort", sortIdx)
            refreshList()
        }
        labels.add(getString(R.string.more_verified, onOff(verifiedOnly)))
        acts.add {
            verifiedOnly = !verifiedOnly
            store.putBool("verified", verifiedOnly)
            refreshList()
        }
        labels.add(getString(R.string.more_settings))
        acts.add { showSettings() }
        ui.picker(this, getString(R.string.more_title), labels, -1) { i -> acts[i]() }
    }

    private fun showSettings() {
        val labels = ArrayList<String>()
        val acts = ArrayList<() -> Unit>()
        fun add(l: String, a: () -> Unit) {
            labels.add(l)
            acts.add(a)
        }
        add(getString(R.string.set_language)) { showLanguage() }
        add(getString(R.string.set_theme)) { showTheme() }
        add(getString(R.string.on_off, getString(R.string.set_data_saver), onOff(dataSaver))) {
            dataSaver = !dataSaver
            store.putBool("datasaver", dataSaver)
            applyQuality()
            setStatus(getString(R.string.on_off, getString(R.string.set_data_saver), onOff(dataSaver)))
        }
        add(getString(R.string.on_off, getString(R.string.set_audio_only), onOff(audioOnly))) {
            audioOnly = !audioOnly
            store.putBool("audioonly", audioOnly)
            applyAudioOnly()
            setStatus(getString(R.string.on_off, getString(R.string.set_audio_only), onOff(audioOnly)))
        }
        add(getString(R.string.on_off, getString(R.string.set_lite), onOff(lite))) {
            lite = !lite
            store.putBool("lite", lite)
            recreate()
        }
        add(getString(R.string.on_off, getString(R.string.set_hide_dead), onOff(hideDead))) {
            hideDead = !hideDead
            store.putBool("hidedead", hideDead)
            refreshList()
        }
        add(getString(R.string.on_off, getString(R.string.set_boot), onOff(store.getBool("boot", false)))) {
            store.putBool("boot", !store.getBool("boot", false))
            setStatus(getString(R.string.on_off, getString(R.string.set_boot), onOff(store.getBool("boot", false))))
        }
        add(getString(R.string.set_parental)) { showParental() }
        add(getString(R.string.set_backup)) { backupLauncher.launch("livetv-backup.json") }
        add(getString(R.string.set_restore)) { restoreLauncher.launch(arrayOf("application/json", "*/*")) }
        add(getString(R.string.set_unhide)) {
            blocked.clear()
            store.putSet("blocked", blocked)
            refreshList()
            setStatus(getString(R.string.done_msg))
        }
        add(getString(R.string.set_updates)) { Updater.check(this, true) }
        add(getString(R.string.set_reset)) {
            stopScan()
            Health.clear()
            store.clearCache()
            currentKey = ""
            setItemsForMode()
            setStatus(getString(R.string.done_msg))
        }
        ui.picker(this, getString(R.string.settings_title), labels, -1) { i -> acts[i]() }
    }

    // ---------- part 2: language, lock, backup, timers, picture-in-picture ----------

    private fun applyMode(pos: Int) {
        mode = pos
        store.putInt("mode", pos)
        setItemsForMode()
    }

    private fun scanLabel(): String {
        var left = 0
        for (c in loaded) for (u in c.allUrls()) if (Health.state(u) == 0) left++
        return if (left > 0) getString(R.string.scan_left, left) else "✓ " + getString(R.string.scan_short)
    }

    @Suppress("DEPRECATION")
    private fun isOnline(): Boolean {
        return try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.activeNetworkInfo?.isConnected == true
        } catch (e: Exception) {
            false
        }
    }

    // When the internet comes back, a channel that stopped is started again by itself.
    private fun registerNet() {
        try {
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    handler.post { onNetBack() }
                }
            }
            val req = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            cm.registerNetworkCallback(req, cb)
            netCb = cb
        } catch (e: Exception) {
        }
    }

    private fun unregisterNet() {
        try {
            val cb = netCb
            if (cb != null) {
                val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(cb)
            }
        } catch (e: Exception) {
        }
        netCb = null
    }

    private fun onNetBack() {
        val ch = nowPlaying ?: return
        if (!waitingNet) return
        waitingNet = false
        attempt = 0
        urlIdx = 0
        if (urlQueue.isEmpty()) urlQueue = ch.allUrls()
        setStatus(getString(R.string.status_reconnecting))
        startUrl(ch, urlQueue[0])
    }

    private fun applyBoost() {
        try {
            enhancer?.release()
            enhancer = null
            if (boostIdx > 0) {
                val sid = player.audioSessionId
                if (sid != C.AUDIO_SESSION_ID_UNSET) {
                    val e = LoudnessEnhancer(sid)
                    e.setTargetGain(boostGain[boostIdx])
                    e.enabled = true
                    enhancer = e
                }
            }
        } catch (e: Exception) {
        }
    }

    private fun boostLabel(): String =
        if (boostIdx == 0) getString(R.string.off) else boostNames[boostIdx]

    private fun sortLabel(): String = when (sortIdx) {
        1 -> getString(R.string.sort_az)
        2 -> getString(R.string.sort_verified)
        else -> getString(R.string.sort_default)
    }

    // Gallery videos and songs continue from where they were stopped.
    private fun saveResume() {
        val ch = nowPlaying ?: return
        if (!ch.url.startsWith("content:")) return
        try {
            val dur = player.duration
            val pos = player.currentPosition
            val o = try {
                JSONObject(store.getString("resume", "{}"))
            } catch (e: Exception) {
                JSONObject()
            }
            if (dur > 0 && pos > 5000L && pos < dur - 10000L) o.put(ch.url, pos) else o.remove(ch.url)
            if (o.length() > 60) {
                val k = o.keys().next()
                o.remove(k)
            }
            store.putString("resume", o.toString())
        } catch (e: Exception) {
        }
    }

    private fun resumePos(url: String): Long {
        return try {
            JSONObject(store.getString("resume", "{}")).optLong(url, 0L)
        } catch (e: Exception) {
            0L
        }
    }

    private fun seekBy(ms: Long) {
        val cur = nowPlaying ?: return
        if (!cur.url.startsWith("content:")) return
        player.seekTo((player.currentPosition + ms).coerceAtLeast(0L))
    }

    private fun showLanguage() {
        val cur = store.getString("lang", "en")
        val idx = Lang.list.indexOfFirst { it.second == cur }
        ui.picker(this, getString(R.string.set_language), Lang.list.map { it.first }, idx) { i ->
            val code = Lang.list[i].second
            store.putString("lang", code)
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
        }
    }

    private fun addRecent(ch: Channel) {
        recents.removeAll { it.url == ch.url }
        recents.add(0, ch)
        while (recents.size > 30) recents.removeAt(recents.size - 1)
        store.saveList("recent", recents)
    }

    private fun updatePlayBtn() {
        if (!::btnPlay.isInitialized) return
        btnPlay.text = if (nowPlaying != null && player.playWhenReady) "❚❚" else "▶"
    }

    // Play / pause for TV, radio and your own files
    private fun togglePlay() {
        val ch = nowPlaying
        if (ch == null) {
            setStatus(getString(R.string.rec_play_first))
            return
        }
        if (player.mediaItemCount == 0) {
            play(ch)
            return
        }
        val st = player.playbackState
        if (st == Player.STATE_IDLE || st == Player.STATE_ENDED) {
            if (st == Player.STATE_ENDED) player.seekToDefaultPosition()
            player.prepare()
            player.playWhenReady = true
        } else {
            player.playWhenReady = !player.playWhenReady
        }
        updatePlayBtn()
    }

    private fun goPrevious() {
        val p = prevChannel
        if (p == null) setStatus(getString(R.string.status_none)) else play(p)
    }

    private fun showRecent() {
        if (recents.isEmpty()) {
            setStatus(getString(R.string.status_none))
            return
        }
        val list = ArrayList(recents)
        ui.picker(this, getString(R.string.more_recent), list.map { it.name }, -1) { i ->
            play(list[i])
        }
    }

    private fun showSleep() {
        val mins = listOf(0, 15, 30, 45, 60, 90, 120)
        val names = mins.map {
            if (it == 0) getString(R.string.sleep_off) else getString(R.string.minutes, it)
        }
        ui.picker(this, getString(R.string.sleep_title), names, -1) { i ->
            handler.removeCallbacks(sleepRun)
            if (mins[i] > 0) {
                handler.postDelayed(sleepRun, mins[i] * 60000L)
                setStatus(getString(R.string.sleep_set, mins[i]))
            } else {
                setStatus(getString(R.string.sleep_off))
            }
        }
    }

    private fun showRecTimer() {
        val mins = listOf(5, 15, 30, 60, 120)
        ui.picker(this, getString(R.string.rec_for_title), mins.map { getString(R.string.minutes, it) }, -1) { i ->
            val r = rec
            if (r == null || !r.active) toggleRecord()
            handler.removeCallbacks(recStopRun)
            handler.postDelayed(recStopRun, mins[i] * 60000L)
        }
    }

    private fun askPin(onOk: () -> Unit) {
        val pin = store.getString("pin", "")
        if (pin.isEmpty()) {
            onOk()
            return
        }
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_enter)
            .setView(et)
            .setPositiveButton(R.string.dlg_ok) { _, _ ->
                if (et.text.toString() == pin) onOk() else setStatus(getString(R.string.pin_wrong))
            }
            .setNegativeButton(R.string.dlg_cancel, null)
            .show()
    }

    private fun setNewPin() {
        val et = EditText(this)
        et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
        AlertDialog.Builder(this)
            .setTitle(R.string.pin_new)
            .setView(et)
            .setPositiveButton(R.string.dlg_ok) { _, _ ->
                val v = et.text.toString()
                if (v.length >= 4) {
                    store.putString("pin", v)
                    setStatus(getString(R.string.pin_saved))
                }
            }
            .setNegativeButton(R.string.dlg_cancel, null)
            .show()
    }

    private fun showParental() {
        askPin {
            val labels = ArrayList<String>()
            val acts = ArrayList<() -> Unit>()
            labels.add(getString(R.string.pl_set_pin))
            acts.add { setNewPin() }
            labels.add(getString(R.string.pl_categories))
            acts.add { chooseLocked() }
            labels.add(getString(R.string.pl_remove_pin))
            acts.add {
                store.putString("pin", "")
                unlocked.clear()
                setStatus(getString(R.string.pin_removed))
            }
            ui.picker(this, getString(R.string.set_parental), labels, -1) { i -> acts[i]() }
        }
    }

    private fun chooseLocked() {
        val cats = Data.categories
        val checked = BooleanArray(cats.size) { lockedCats.contains(cats[it].key) }
        AlertDialog.Builder(this)
            .setTitle(R.string.pl_categories)
            .setMultiChoiceItems(cats.map { it.label }.toTypedArray(), checked) { _, i, c ->
                checked[i] = c
            }
            .setPositiveButton(R.string.dlg_ok) { _, _ ->
                lockedCats.clear()
                for (i in cats.indices) if (checked[i]) lockedCats.add(cats[i].key)
                store.putSet("locked", lockedCats)
                unlocked.clear()
                if (store.getString("pin", "").isEmpty()) setNewPin()
            }
            .setNegativeButton(R.string.dlg_cancel, null)
            .show()
    }

    private fun doBackup(uri: Uri) {
        try {
            val o = JSONObject()
            o.put("fav", JSONArray(store.getString("fav", "[]")))
            o.put("files", JSONArray(store.getString("files", "[]")))
            o.put("playlists", JSONArray(store.getString("playlists", "[]")))
            o.put("blocked", JSONArray(store.getString("blocked", "[]")))
            contentResolver.openOutputStream(uri)?.use { it.write(o.toString().toByteArray()) }
            setStatus(getString(R.string.backup_done))
        } catch (e: Exception) {
            setStatus(getString(R.string.backup_failed))
        }
    }

    private fun doRestore(uri: Uri) {
        try {
            val text = contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
            val o = JSONObject(text)
            for (k in listOf("fav", "files", "playlists", "blocked")) {
                val a = o.optJSONArray(k)
                if (a != null) store.putString(k, a.toString())
            }
            favs = store.getList("fav")
            files = store.getList("files")
            playlists = store.getPlaylists()
            blocked = store.getSet("blocked")
            updateFavBtn()
            refreshList()
            setStatus(getString(R.string.restore_done))
        } catch (e: Exception) {
            setStatus(getString(R.string.restore_failed))
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT >= 26 && !isTv && player.isPlaying &&
            player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)
        ) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
                )
            } catch (e: Exception) {
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
        applyLayout()
    }

    // ---------- lifecycle ----------

    override fun onStart() {
        super.onStart()
        if (nowPlaying != null && player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) {
            player.playWhenReady = true
        }
    }

    override fun onStop() {
        super.onStop()
        saveResume()
        if (player.currentTracks.isTypeSelected(C.TRACK_TYPE_VIDEO)) player.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        saveResume()
        unregisterNet()
        try {
            enhancer?.release()
        } catch (e: Exception) {
        }
        enhancer = null
        store.putBool("launching", false)
        handler.removeCallbacksAndMessages(null)
        checker?.stop()
        Health.save()
        rec?.stop()
        orient.disable()
        player.removeListener(listener)
        playerView.player = null
        eq.stop()
    }

    // ---------- list adapter ----------

    inner class ChAdapter : RecyclerView.Adapter<ChAdapter.VH>() {
        var items: List<Channel> = emptyList()

        inner class VH(
            val row: LinearLayout,
            val logo: ImageView,
            val name: TextView,
            val heart: TextView
        ) : RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val ctx = parent.context
            val row = LinearLayout(ctx)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            val p = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            p.setMargins(ui.dp(8), ui.dp(4), ui.dp(8), ui.dp(4))
            row.layoutParams = p
            row.setPadding(ui.dp(10), ui.dp(8), ui.dp(12), ui.dp(8))
            row.isFocusable = true
            row.isClickable = true

            val iv = ImageView(ctx)
            iv.scaleType = ImageView.ScaleType.FIT_CENTER
            iv.setPadding(ui.dp(2), ui.dp(2), ui.dp(2), ui.dp(2))
            row.addView(iv, LinearLayout.LayoutParams(ui.dp(46), ui.dp(46)))

            val tv = TextView(ctx)
            tv.textSize = 16f
            tv.maxLines = 2
            tv.ellipsize = TextUtils.TruncateAt.END
            val tp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            tp.setMargins(ui.dp(12), 0, ui.dp(8), 0)
            row.addView(tv, tp)

            val heart = TextView(ctx)
            heart.text = "♥"
            heart.textSize = 16f
            row.addView(
                heart,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            return VH(row, iv, tv, heart)
        }

        override fun onBindViewHolder(h: VH, position: Int) {
            val t = Themes.get(themeIdx)
            val ch = items[position]
            val fav = favs.any { it.url == ch.url }
            val playing = ch.url == nowPlaying?.url

            h.row.background = ui.stateBg(ui.rr(t.card, 14), 14)
            h.name.text = if (isTv && tab <= 3) "${position + 1}   " + ch.name else ch.name
            h.name.setTextColor(if (playing) t.accent else t.text)
            h.name.setTypeface(null, if (playing) Typeface.BOLD else Typeface.NORMAL)
            h.heart.setTextColor(t.accent)
            h.heart.visibility = if (fav) View.VISIBLE else View.GONE
            h.logo.background = ui.rr(ui.alpha(t.sub, 0x22), 10)

            val icon = ui.letterIcon(ch.name)
            if (ch.logo.isNotEmpty() && !lite) {
                Glide.with(h.logo)
                    .load(ch.logo)
                    .placeholder(icon)
                    .error(icon)
                    .override(132, 132)
                    .into(h.logo)
            } else {
                Glide.with(h.logo).clear(h.logo)
                h.logo.setImageDrawable(icon)
            }

            h.row.setOnClickListener { onItemClick(ch) }
            h.row.tag = ch
            h.row.setOnLongClickListener {
                val special = ch.url.startsWith("pl:") || ch.url == "back:"
                if (isTv && !special) toggleFav(ch) else showItemMenu(ch)
                true
            }
        }

        override fun getItemCount(): Int = items.size
    }
}

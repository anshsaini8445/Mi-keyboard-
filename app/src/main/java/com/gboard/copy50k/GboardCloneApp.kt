package com.gboard.copy50k

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

// ============================================================================
// 1. SHARED PREFERENCES & DYNAMIC TOOL / THEME CONFIGURATION MANAGER
// ============================================================================
data class KeyboardTheme(
    val name: String,
    val bgColor: Int,
    val keyWhite: Int,
    val keyDark: Int,
    val textDark: Int,
    val textMuted: Int,
    val accentColor: Int
)

data class ToolDescriptor(
    val id: String,
    val icon: String,
    val title: String,
    val hasArrow: Boolean = false
)

object KeyboardConfig {
    private const val PREF_NAME = "gboard_50k_prefs"
    private const val KEY_THEME_INDEX = "theme_index"
    private const val KEY_TOP_BAR_TOOLS = "top_bar_tools"
    private const val KEY_NUMBER_ROW = "dedicated_number_row"
    private const val KEY_OFFLINE_VOICE = "offline_voice_mode"
    private const val KEY_HEIGHT_DP = "keyboard_height_dp"

    val ALL_THEMES = listOf(
        KeyboardTheme(
            "Gboard Material Rose",
            Color.parseColor("#F9ECEF"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#513E44"),
            Color.parseColor("#1F1A1C"),
            Color.parseColor("#6E5E63"),
            Color.parseColor("#632B43")
        ),
        KeyboardTheme(
            "Gboard Pixel Dark",
            Color.parseColor("#1B1B1F"),
            Color.parseColor("#2F3036"),
            Color.parseColor("#44474F"),
            Color.parseColor("#E3E2E6"),
            Color.parseColor("#90909A"),
            Color.parseColor("#A8C7FA")
        ),
        KeyboardTheme(
            "Samsung OneUI Light",
            Color.parseColor("#E8ECEF"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#455A64"),
            Color.parseColor("#1C1E21"),
            Color.parseColor("#607D8B"),
            Color.parseColor("#0072DE")
        ),
        KeyboardTheme(
            "Samsung OneUI Dark",
            Color.parseColor("#121212"),
            Color.parseColor("#242426"),
            Color.parseColor("#38383C"),
            Color.parseColor("#F2F2F7"),
            Color.parseColor("#8E8E93"),
            Color.parseColor("#3E91FF")
        ),
        KeyboardTheme(
            "AMOLED Pitch Black",
            Color.parseColor("#000000"),
            Color.parseColor("#161618"),
            Color.parseColor("#2C2C30"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#75757A"),
            Color.parseColor("#00E676")
        ),
        KeyboardTheme(
            "Ocean Cyber Blue",
            Color.parseColor("#E3F2FD"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#1565C0"),
            Color.parseColor("#0D47A1"),
            Color.parseColor("#546E7A"),
            Color.parseColor("#00ACC1")
        ),
        KeyboardTheme(
            "Emerald Forest",
            Color.parseColor("#E8F5E9"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#2E7D32"),
            Color.parseColor("#1B5E20"),
            Color.parseColor("#4E6E52"),
            Color.parseColor("#00897B")
        ),
        KeyboardTheme(
            "Royal Velvet Purple",
            Color.parseColor("#F3E5F5"),
            Color.parseColor("#FFFFFF"),
            Color.parseColor("#4A148C"),
            Color.parseColor("#2A0845"),
            Color.parseColor("#6A4C7D"),
            Color.parseColor("#8E24AA")
        )
    )

    val ALL_TOOLS = listOf(
        ToolDescriptor("TEXT_EDIT", "◄I►", "Text", true),
        ToolDescriptor("CLIPBOARD", "📋", "Clipboard", false),
        ToolDescriptor("TRANSLATE", "G文", "Translate", true),
        ToolDescriptor("THEME", "🎨", "Theme", true),
        ToolDescriptor("EMOJI", "😊", "Emoji", true),
        ToolDescriptor("ONE_HANDED", "📱", "One-handed", false),
        ToolDescriptor("RESIZE", "⬚", "Resize", false),
        ToolDescriptor("FLOATING", "⌨", "Floating", false),
        ToolDescriptor("UNDO", "↶", "Undo", false),
        ToolDescriptor("NUMBER_ROW", "123", "Number Row", false),
        ToolDescriptor("LANGUAGE", "🌐", "Next language", false),
        ToolDescriptor("STICKER", "🖼", "Sticker", true),
        ToolDescriptor("GIF", "GIF", "GIF", true),
        ToolDescriptor("SHARE", "🔗", "Share Gboard", false)
    )

    // Default Top Bar Tools: Text Editing is at the very top by default as requested!
    private const val DEFAULT_TOP_IDS = "TEXT_EDIT,CLIPBOARD,TRANSLATE,THEME"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun getThemeIndex(context: Context): Int =
        prefs(context).getInt(KEY_THEME_INDEX, 0).coerceIn(0, ALL_THEMES.lastIndex)

    fun setThemeIndex(context: Context, index: Int) {
        prefs(context).edit().putInt(KEY_THEME_INDEX, index.coerceIn(0, ALL_THEMES.lastIndex)).apply()
    }

    fun getCurrentTheme(context: Context): KeyboardTheme =
        ALL_THEMES[getThemeIndex(context)]

    fun isNumberRowEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_NUMBER_ROW, true)

    fun setNumberRowEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_NUMBER_ROW, enabled).apply()
    }

    fun isOfflineVoicePreferred(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OFFLINE_VOICE, true)

    fun setOfflineVoicePreferred(context: Context, offline: Boolean) {
        prefs(context).edit().putBoolean(KEY_OFFLINE_VOICE, offline).apply()
    }

    fun getKeyboardHeightDp(context: Context): Int =
        prefs(context).getInt(KEY_HEIGHT_DP, 305).coerceIn(240, 390)

    fun setKeyboardHeightDp(context: Context, heightDp: Int) {
        prefs(context).edit().putInt(KEY_HEIGHT_DP, heightDp.coerceIn(240, 390)).apply()
    }

    fun getTopBarToolIds(context: Context): List<String> {
        val raw = prefs(context).getString(KEY_TOP_BAR_TOOLS, DEFAULT_TOP_IDS) ?: DEFAULT_TOP_IDS
        return raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    fun setTopBarToolIds(context: Context, ids: List<String>) {
        prefs(context).edit().putString(KEY_TOP_BAR_TOOLS, ids.joinToString(",")).apply()
    }

    fun isToolPinnedToTopBar(context: Context, toolId: String): Boolean =
        getTopBarToolIds(context).contains(toolId)

    fun toggleToolLocation(context: Context, toolId: String) {
        val current = getTopBarToolIds(context).toMutableList()
        if (current.contains(toolId)) {
            current.remove(toolId)
        } else {
            current.add(0, toolId)
        }
        setTopBarToolIds(context, current)
    }

    fun getPinnedTools(context: Context): List<ToolDescriptor> {
        val ids = getTopBarToolIds(context)
        return ids.mapNotNull { id -> ALL_TOOLS.find { it.id == id } }
    }

    fun getOverflowTools(context: Context): List<ToolDescriptor> {
        val pinnedSet = getTopBarToolIds(context).toSet()
        return ALL_TOOLS.filterNot { pinnedSet.contains(it.id) }
    }
}

// ============================================================================
// 2. LAUNCHER ACTIVITY (SETTINGS, THEME PICKER, TOOL REORDER, MIC PERMISSION)
// ============================================================================
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderSettingsScreen()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun renderSettingsScreen() {
        val theme = KeyboardConfig.getCurrentTheme(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#F9ECEF"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(32))
        }

        val title = TextView(this).apply {
            text = "Gboard 50K Pro Studio"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F1A1C"))
        }
        root.addView(title)

        val sub = TextView(this).apply {
            text = "120FPS Canvas • Top Bar Text Editing • Offline/Online Voice • 50,000 Lines Clipboard"
            textSize = 13f
            setTextColor(Color.parseColor("#513E44"))
            setPadding(0, dp(4), 0, dp(16))
        }
        root.addView(sub)

        fun makeActionButton(label: String, bgHex: String = "#513E44", onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = dp(14).toFloat()
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = dp(10)
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        root.addView(makeActionButton("1. Enable 'Gboard 50K' in System Settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        root.addView(makeActionButton("2. Switch Active Keyboard to 'Gboard 50K'") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        root.addView(makeActionButton("3. Grant Microphone Permission (For Direct Voice Typing)", "#00695C") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 101)
            } else {
                Toast.makeText(this, "Microphone permission is already active!", Toast.LENGTH_SHORT).show()
            }
        })

        root.addView(makeActionButton("4. Generate & Copy 50,000 Lines Sample Code", "#632B43") {
            val sb = StringBuilder(1_850_000)
            sb.append("package com.app.mediaplayer\n\nimport android.content.BroadcastReceiver\nimport android.content.Context\n\n")
            for (i in 6..50000) {
                sb.append("val codeLine_$i = \"Gboard 50K developer line $i\"\n")
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code50k", sb.toString()))
            Toast.makeText(this, "50,000 Lines Copied! Tap Clipboard on Keyboard to Paste.", Toast.LENGTH_LONG).show()
        })

        // Live Test Editor Box
        val editor = EditText(this).apply {
            hint = "Click here to test 120FPS Typing, Top Bar Text Editing (◄I►), Direct Mic Voice Input, Number Row (1-0), and 50K Paste..."
            gravity = Gravity.TOP or Gravity.START
            minLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(14), dp(14), dp(14))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = dp(18)
            layoutParams = lp
        }
        root.addView(editor)

        // Section: Dedicated Number Row & Offline Voice Toggle
        val togglesTitle = TextView(this).apply {
            text = "Keyboard Preferences"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F1A1C"))
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(togglesTitle)

        val numRowToggle = makeActionButton(
            if (KeyboardConfig.isNumberRowEnabled(this)) "Dedicated Number Row (1 2 3 4 5 6 7 8 9 0): ON"
            else "Dedicated Number Row (1 2 3 4 5 6 7 8 9 0): OFF",
            "#37474F"
        ) {
            KeyboardConfig.setNumberRowEnabled(this, !KeyboardConfig.isNumberRowEnabled(this))
            renderSettingsScreen()
        }
        root.addView(numRowToggle)

        val voiceToggle = makeActionButton(
            if (KeyboardConfig.isOfflineVoicePreferred(this)) "Voice Recognition Mode: Offline + Online (Fast)"
            else "Voice Recognition Mode: Online Standard",
            "#37474F"
        ) {
            KeyboardConfig.setOfflineVoicePreferred(this, !KeyboardConfig.isOfflineVoicePreferred(this))
            renderSettingsScreen()
        }
        root.addView(voiceToggle)

        // Section: Theme Selector Inside App
        val themeHeader = TextView(this).apply {
            text = "Select Keyboard Theme (Active: ${theme.name})"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F1A1C"))
            setPadding(0, dp(10), 0, dp(8))
        }
        root.addView(themeHeader)

        KeyboardConfig.ALL_THEMES.forEachIndexed { idx, th ->
            val selected = idx == KeyboardConfig.getThemeIndex(this)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(th.bgColor)
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(if (selected) 3 else 1), if (selected) th.accentColor else Color.GRAY)
                }
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(8)
                layoutParams = lp
                setOnClickListener {
                    KeyboardConfig.setThemeIndex(this@MainActivity, idx)
                    Toast.makeText(this@MainActivity, "Theme applied: ${th.name}", Toast.LENGTH_SHORT).show()
                    renderSettingsScreen()
                }
            }
            card.addView(TextView(this).apply {
                text = if (selected) "✓  ${th.name}" else th.name
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(th.textDark)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            card.addView(TextView(this).apply {
                text = "Key Preview"
                textSize = 12f
                setTextColor(th.textDark)
                background = GradientDrawable().apply {
                    setColor(th.keyWhite)
                    cornerRadius = dp(8).toFloat()
                }
                setPadding(dp(10), dp(4), dp(10), dp(4))
            })
            root.addView(card)
        }

        // Section: Move Tools Between Top Bar (Outside) & 3-Dots Menu (Inside)
        val customizeHeader = TextView(this).apply {
            text = "Customize Top Bar vs 3-Dots (•••) Menu"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F1A1C"))
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(customizeHeader)

        val customizeSub = TextView(this).apply {
            text = "Tap any tool below to move it Outside (Top Bar) or hide it Inside (••• Menu):"
            textSize = 12f
            setTextColor(Color.parseColor("#513E44"))
            setPadding(0, 0, 0, dp(8))
        }
        root.addView(customizeSub)

        KeyboardConfig.ALL_TOOLS.forEach { tool ->
            val isPinned = KeyboardConfig.isToolPinnedToTopBar(this, tool.id)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.WHITE)
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6)
                layoutParams = lp
                setOnClickListener {
                    KeyboardConfig.toggleToolLocation(this@MainActivity, tool.id)
                    renderSettingsScreen()
                }
            }
            row.addView(TextView(this).apply {
                text = "${tool.icon}   ${tool.title}"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1F1A1C"))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = if (isPinned) "ON TOP BAR (Tap to Hide in •••)" else "HIDDEN IN ••• (Tap to Pin Top)"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(if (isPinned) "#2E7D32" else "#757575"))
                    cornerRadius = dp(14).toFloat()
                }
                setPadding(dp(10), dp(5), dp(10), dp(5))
            })
            root.addView(row)
        }

        scroll.addView(root)
        setContentView(scroll)
    }
}

// ============================================================================
// 3. ZERO-LAG RAM + ASYNC DISK 50,000 LINES CLIPBOARD ENGINE
// ============================================================================
data class ClipItem(
    val id: String,
    val preview: String,
    val lineCount: Int,
    val isLinkOrPkg: Boolean,
    val isPinned: Boolean,
    val filePath: String
)

class ClipboardEngine50K(private val context: Context, private val ioScope: CoroutineScope) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clipFolder = File(context.filesDir, "clips_50k").apply { if (!exists()) mkdirs() }
    private val indexFile = File(context.filesDir, "clips_index_v4.json")
    private val memoryCache = CopyOnWriteArrayList<ClipItem>()
    private var lastHash = 0
    var isEnabled = true

    companion object {
        const val MAX_LINES = 50_000
        private const val CHUNK_SIZE = 24_576
    }

    init {
        ioScope.launch(Dispatchers.IO) {
            loadFromDiskToRam()
            if (memoryCache.isEmpty()) {
                saveRawClipSync("android.content.Context")
                saveRawClipSync("android.content.BroadcastReceiver")
                saveRawClipSync("com.app.mediaplayer")
                saveRawClipSync("package com.app.mediaplayer\n\nimport android.content.BroadcastReceiver...")
            }
        }
    }

    private fun loadFromDiskToRam() {
        if (!indexFile.exists()) return
        try {
            val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
            val temp = mutableListOf<ClipItem>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                temp.add(
                    ClipItem(
                        id = o.getString("id"),
                        preview = o.getString("preview"),
                        lineCount = o.optInt("lineCount", 1),
                        isLinkOrPkg = o.optBoolean("isLinkOrPkg", false),
                        isPinned = o.optBoolean("isPinned", false),
                        filePath = o.getString("filePath")
                    )
                )
            }
            memoryCache.clear()
            memoryCache.addAll(temp)
        } catch (_: Exception) {}
    }

    fun syncSystemClipboardAsync(onUpdated: () -> Unit = {}) {
        if (!isEnabled) return
        try {
            val primary = clipboard.primaryClip ?: return
            if (primary.itemCount > 0) {
                val text = primary.getItemAt(0).coerceToText(context)?.toString() ?: return
                val hash = text.hashCode()
                if (text.isNotBlank() && hash != lastHash) {
                    lastHash = hash
                    ioScope.launch(Dispatchers.IO) {
                        saveRawClipSync(text)
                        withContext(Dispatchers.Main) { onUpdated() }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun saveRawClipAsync(rawText: String, onDone: () -> Unit = {}) {
        if (!isEnabled || rawText.isBlank()) return
        ioScope.launch(Dispatchers.IO) {
            saveRawClipSync(rawText)
            withContext(Dispatchers.Main) { onDone() }
        }
    }

    private fun saveRawClipSync(rawText: String, pinned: Boolean = false) {
        val lines = rawText.lineSequence().take(MAX_LINES).toList()
        val finalText = lines.joinToString("\n")
        val id = UUID.randomUUID().toString()
        val file = File(clipFolder, "$id.txt")
        file.writeText(finalText, Charsets.UTF_8)

        val trimmed = finalText.trim()
        val isPkg = !trimmed.contains("\n") && (trimmed.contains(".") && !trimmed.contains(" "))
        val preview = if (trimmed.length > 150) trimmed.take(150) + "..." else trimmed

        val existing = memoryCache.toMutableList()
        existing.removeAll { it.preview == preview }
        existing.add(
            0,
            ClipItem(
                id = id,
                preview = preview,
                lineCount = lines.size,
                isLinkOrPkg = isPkg,
                isPinned = pinned,
                filePath = file.absolutePath
            )
        )
        val trimmedList = existing.take(60)
        memoryCache.clear()
        memoryCache.addAll(trimmedList)
        persistRamToDisk()
    }

    fun togglePin(id: String) {
        val updated = memoryCache.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
        memoryCache.clear()
        memoryCache.addAll(updated)
        ioScope.launch(Dispatchers.IO) { persistRamToDisk() }
    }

    fun deleteClip(id: String) {
        val target = memoryCache.find { it.id == id }
        memoryCache.removeAll { it.id == id }
        ioScope.launch(Dispatchers.IO) {
            target?.let { File(it.filePath).delete() }
            persistRamToDisk()
        }
    }

    private fun persistRamToDisk() {
        try {
            val arr = JSONArray()
            for (item in memoryCache) {
                arr.put(JSONObject().apply {
                    put("id", item.id)
                    put("preview", item.preview)
                    put("lineCount", item.lineCount)
                    put("isLinkOrPkg", item.isLinkOrPkg)
                    put("isPinned", item.isPinned)
                    put("filePath", item.filePath)
                })
            }
            indexFile.writeText(arr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun getRecentClipsInstant(): List<ClipItem> = memoryCache.toList()

    fun paste50K(
        ic: InputConnection?,
        item: ClipItem,
        scope: CoroutineScope,
        onProgress: (Int) -> Unit = {},
        onComplete: (String) -> Unit = {}
    ) {
        if (ic == null) return
        scope.launch(Dispatchers.Main) {
            val fullText = withContext(Dispatchers.IO) {
                val f = File(item.filePath)
                if (f.exists()) f.readText(Charsets.UTF_8) else item.preview
            }
            val total = fullText.length
            if (total <= CHUNK_SIZE) {
                ic.commitText(fullText, 1)
                onProgress(100)
                onComplete(fullText)
                return@launch
            }
            var offset = 0
            while (offset < total) {
                val end = minOf(offset + CHUNK_SIZE, total)
                val chunk = withContext(Dispatchers.Default) { fullText.substring(offset, end) }
                ic.beginBatchEdit()
                ic.commitText(chunk, 1)
                ic.endBatchEdit()
                offset = end
                onProgress((offset * 100) / total)
                delay(4L)
            }
            onComplete(fullText)
        }
    }
}

// ============================================================================
// 4. 120FPS SINGLE-CANVAS KEYBOARD VIEW (DEDICATED 1-0 ROW + MULTI-TOUCH + GLIDE)
// ============================================================================
class GboardCanvasView(
    context: Context,
    private val service: GboardService
) : View(context) {

    enum class KeyKind {
        NUMBER, LETTER, SHIFT, BACKSPACE, SYMBOLS, EMOJI_COMMA, GLOBE, SPACE, DOT, ENTER
    }

    data class KeyCell(
        val row: Int,
        val weight: Float,
        var rawLabel: String,
        val topNumber: String = "",
        val kind: KeyKind = KeyKind.LETTER,
        val rect: RectF = RectF(),
        var isPressed: Boolean = false
    )

    private val rows = Array<MutableList<KeyCell>>(5) { mutableListOf() }
    private val allKeys = mutableListOf<KeyCell>()
    private var activeRowCount = 5

    // Pre-allocated Paints for 0-allocation 120FPS rendering
    private val keyWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyDarkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val keyAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textMainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val textSubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val popupBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val popupTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val glideTrailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glidePath = Path()

    private val shadowRect = RectF()
    private val popupRect = RectF()

    private val pointerKeyMap = mutableMapOf<Int, KeyCell>()
    private var activePopupKey: KeyCell? = null
    private var longPressJob: Job? = null
    private var backspaceJob: Job? = null

    private var spaceStartX = 0f
    private var spaceLastStepX = 0f
    private var isSpaceTrackpadActive = false
    private var backspaceDownX = 0f

    init {
        isClickable = true
        isFocusable = true
        rebuildKeyDefinitions()
    }

    private fun dpF(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)

    private fun spF(v: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    fun rebuildKeyDefinitions() {
        for (r in rows) r.clear()
        allKeys.clear()

        val showDedicatedNumbers = KeyboardConfig.isNumberRowEnabled(context) && !service.isSymbolsMode

        if (service.isSymbolsMode) {
            activeRowCount = 4
            val r0 = if (service.isNumbersPage2) listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")
            else listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            val r1 = if (service.isNumbersPage2) listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
            else listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
            val r2 = if (service.isNumbersPage2) listOf("?123", "%", "©", "®", "™", "✓", "[", "]")
            else listOf("=\\<", "*", "\"", "'", ":", ";", "!", "?")

            r0.forEach { rows[0].add(KeyCell(0, 1f, it, kind = KeyKind.NUMBER)) }
            r1.forEach { rows[1].add(KeyCell(1, 1f, it)) }
            rows[2].add(KeyCell(2, 1.45f, r2[0], kind = KeyKind.SHIFT))
            for (i in 1 until r2.size) rows[2].add(KeyCell(2, 1f, r2[i]))
            rows[2].add(KeyCell(2, 1.45f, "⌫", kind = KeyKind.BACKSPACE))

            rows[3].add(KeyCell(3, 1.35f, "ABC", kind = KeyKind.SYMBOLS))
            rows[3].add(KeyCell(3, 0.9f, ",", kind = KeyKind.EMOJI_COMMA))
            rows[3].add(KeyCell(3, 0.9f, "◄I►", kind = KeyKind.GLOBE))
            rows[3].add(KeyCell(3, 3.8f, if (service.isUrdu) "اردو" else "English", kind = KeyKind.SPACE))
            rows[3].add(KeyCell(3, 0.9f, ".", kind = KeyKind.DOT))
            rows[3].add(KeyCell(3, 1.35f, "🔍", kind = KeyKind.ENTER))
        } else {
            val nums = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            var rowCursor = 0

            // Dedicated 1 2 3 4 5 6 7 8 9 0 Row at Top
            if (showDedicatedNumbers) {
                activeRowCount = 5
                nums.forEach { n ->
                    rows[0].add(KeyCell(0, 1f, n, kind = KeyKind.NUMBER))
                }
                rowCursor = 1
            } else {
                activeRowCount = 4
            }

            val rQ = if (service.isUrdu) listOf("ط", "ص", "ھ", "د", "ٹ", "پ", "ت", "ب", "ج", "ح")
            else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
            for (i in rQ.indices) {
                rows[rowCursor].add(
                    KeyCell(
                        row = rowCursor,
                        weight = 1f,
                        rawLabel = rQ[i],
                        topNumber = if (showDedicatedNumbers) "" else nums[i],
                        kind = KeyKind.LETTER
                    )
                )
            }
            rowCursor++

            val rA = if (service.isUrdu) listOf("م", "ن", "ر", "ل", "ہ", "ا", "ک", "ی", "ق")
            else listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
            rA.forEach { rows[rowCursor].add(KeyCell(rowCursor, 1f, it, kind = KeyKind.LETTER)) }
            rowCursor++

            val rZ = if (service.isUrdu) listOf("ز", "خ", "ش", "غ", "ف", "ے", "س")
            else listOf("z", "x", "c", "v", "b", "n", "m")
            rows[rowCursor].add(KeyCell(rowCursor, 1.45f, "⇧", kind = KeyKind.SHIFT))
            rZ.forEach { rows[rowCursor].add(KeyCell(rowCursor, 1f, it, kind = KeyKind.LETTER)) }
            rows[rowCursor].add(KeyCell(rowCursor, 1.45f, "⌫", kind = KeyKind.BACKSPACE))
            rowCursor++

            rows[rowCursor].add(KeyCell(rowCursor, 1.35f, "?123", kind = KeyKind.SYMBOLS))
            rows[rowCursor].add(KeyCell(rowCursor, 0.9f, "☺,", kind = KeyKind.EMOJI_COMMA))
            rows[rowCursor].add(KeyCell(rowCursor, 0.9f, "🌐", kind = KeyKind.GLOBE))
            rows[rowCursor].add(KeyCell(rowCursor, 3.8f, if (service.isUrdu) "اردو" else "English", kind = KeyKind.SPACE))
            rows[rowCursor].add(KeyCell(rowCursor, 0.9f, ".", kind = KeyKind.DOT))
            rows[rowCursor].add(KeyCell(rowCursor, 1.35f, "🔍", kind = KeyKind.ENTER))
        }

        for (i in 0 until activeRowCount) allKeys.addAll(rows[i])
        computeKeyBounds(width, height)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeKeyBounds(w, h)
    }

    private fun computeKeyBounds(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val padH = dpF(2.8f)
        val padV = dpF(3.0f)
        val topMargin = dpF(2f)
        val bottomMargin = dpF(5f)
        val usableH = h - topMargin - bottomMargin

        val hasNumberRow = activeRowCount == 5
        val numRowH = if (hasNumberRow) usableH * 0.165f else 0f
        val mainRowH = if (hasNumberRow) (usableH - numRowH) / 4f else usableH / 4f

        var curTopY = topMargin
        for (rIdx in 0 until activeRowCount) {
            val rowList = rows[rIdx]
            if (rowList.isEmpty()) continue
            val isHomeRow = (hasNumberRow && rIdx == 2) || (!hasNumberRow && rIdx == 1 && !service.isSymbolsMode)
            val sideInset = if (isHomeRow) dpF(16f) else dpF(4f)
            val rowWidth = w - (sideInset * 2f)
            val totalWeight = rowList.sumOf { it.weight.toDouble() }.toFloat()
            val thisRowH = if (hasNumberRow && rIdx == 0) numRowH else mainRowH

            var curX = sideInset
            for (key in rowList) {
                val kw = (key.weight / totalWeight) * rowWidth
                key.rect.set(
                    curX + padH,
                    curTopY + padV,
                    curX + kw - padH,
                    curTopY + thisRowH - padV
                )
                curX += kw
            }
            curTopY += thisRowH
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        keyWhitePaint.color = service.colKeyWhite
        keyPressedPaint.color = service.colAccentMaroon
        keyDarkPaint.color = service.colKeyDark
        keyAccentPaint.color = service.colAccentMaroon
        shadowPaint.color = Color.parseColor("#24000000")
        popupBgPaint.color = service.colKeyWhite
        popupTextPaint.color = service.colTextDark

        val cornerStd = dpF(8f)
        val cornerPill = dpF(22f)
        val shadowOffset = dpF(1.3f)
        val upper = service.isShifted || service.isCapsLocked

        for (key in allKeys) {
            val isPill = key.kind == KeyKind.SYMBOLS || key.kind == KeyKind.ENTER
            val rad = if (isPill) cornerPill else cornerStd

            shadowRect.set(key.rect.left, key.rect.top + shadowOffset, key.rect.right, key.rect.bottom + shadowOffset)
            canvas.drawRoundRect(shadowRect, rad, rad, shadowPaint)

            val bgPaint = when {
                key.kind == KeyKind.ENTER -> if (key.isPressed) keyAccentPaint else keyDarkPaint
                key.kind == KeyKind.SHIFT && service.isCapsLocked -> keyAccentPaint
                key.kind == KeyKind.SHIFT || key.kind == KeyKind.BACKSPACE ||
                    key.kind == KeyKind.SYMBOLS || key.kind == KeyKind.EMOJI_COMMA || key.kind == KeyKind.DOT ->
                    if (key.isPressed) keyAccentPaint else keyDarkPaint
                else -> if (key.isPressed) keyDarkPaint else keyWhitePaint
            }
            canvas.drawRoundRect(key.rect, rad, rad, bgPaint)

            val cx = key.rect.centerX()
            val cy = key.rect.centerY()

            when (key.kind) {
                KeyKind.EMOJI_COMMA -> {
                    textMainPaint.color = Color.WHITE
                    textMainPaint.textSize = spF(12f)
                    canvas.drawText("☺", cx, cy - dpF(2f), textMainPaint)
                    canvas.drawText(",", cx, cy + dpF(10f), textMainPaint)
                }
                KeyKind.SPACE -> {
                    textMainPaint.color = if (key.isPressed) Color.WHITE else service.colTextMuted
                    textMainPaint.textSize = spF(13.5f)
                    val label = if (isSpaceTrackpadActive) "◄   Cursor Trackpad   ►" else key.rawLabel
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(label, cx, textY, textMainPaint)
                }
                KeyKind.SHIFT -> {
                    textMainPaint.color = Color.WHITE
                    textMainPaint.textSize = spF(17f)
                    val sym = if (service.isSymbolsMode) key.rawLabel else when {
                        service.isCapsLocked -> "⇪"
                        service.isShifted -> "⬆"
                        else -> "⇧"
                    }
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(sym, cx, textY, textMainPaint)
                }
                KeyKind.BACKSPACE, KeyKind.SYMBOLS, KeyKind.DOT, KeyKind.ENTER -> {
                    textMainPaint.color = Color.WHITE
                    textMainPaint.textSize = if (key.rawLabel.length > 2) spF(13.5f) else spF(17.5f)
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(key.rawLabel, cx, textY, textMainPaint)
                }
                KeyKind.NUMBER -> {
                    textMainPaint.color = if (key.isPressed) Color.WHITE else service.colTextDark
                    textMainPaint.textSize = spF(16.5f)
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(key.rawLabel, cx, textY, textMainPaint)
                }
                else -> {
                    val outText = if (key.kind == KeyKind.LETTER && upper) key.rawLabel.uppercase() else key.rawLabel
                    textMainPaint.color = if (key.isPressed) Color.WHITE else service.colTextDark
                    textMainPaint.textSize = if (outText.length > 1) spF(14.5f) else spF(20f)
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(outText, cx, textY, textMainPaint)

                    if (key.topNumber.isNotEmpty()) {
                        textSubPaint.color = if (key.isPressed) Color.WHITE else service.colTextMuted
                        textSubPaint.textSize = spF(9.5f)
                        canvas.drawText(
                            key.topNumber,
                            key.rect.right - dpF(4.5f),
                            key.rect.top + dpF(10.5f),
                            textSubPaint
                        )
                    }
                }
            }
        }

        // Draw Floating Key Popup Preview Balloon
        val popupKey = activePopupKey
        if (popupKey != null && (popupKey.kind == KeyKind.LETTER || popupKey.kind == KeyKind.NUMBER)) {
            val popW = popupKey.rect.width() * 1.28f
            val popH = popupKey.rect.height() * 1.4f
            val popLeft = (popupKey.rect.centerX() - popW / 2f).coerceIn(dpF(4f), width - popW - dpF(4f))
            val popBottom = (popupKey.rect.top - dpF(4f)).coerceAtLeast(popH + dpF(2f))
            val popTop = popBottom - popH

            popupRect.set(popLeft, popTop, popLeft + popW, popBottom)
            shadowRect.set(popLeft, popTop + dpF(2f), popLeft + popW, popBottom + dpF(3f))
            canvas.drawRoundRect(shadowRect, dpF(12f), dpF(12f), shadowPaint)
            canvas.drawRoundRect(popupRect, dpF(12f), dpF(12f), popupBgPaint)

            val upper = service.isShifted || service.isCapsLocked
            val popLabel = if (popupKey.kind == KeyKind.LETTER && upper) popupKey.rawLabel.uppercase() else popupKey.rawLabel
            popupTextPaint.textSize = spF(26f)
            val py = popupRect.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2f
            canvas.drawText(popLabel, popupRect.centerX(), py, popupTextPaint)
        }
    }

    private fun findNearestKey(x: Float, y: Float): KeyCell? {
        for (k in allKeys) {
            if (k.rect.contains(x, y)) return k
        }
        var best: KeyCell? = null
        var bestDist = dpF(28f) * dpF(28f)
        for (k in allKeys) {
            val dx = x - k.rect.centerX()
            val dy = y - k.rect.centerY()
            val d2 = dx * dx + dy * dy
            if (d2 < bestDist) {
                bestDist = d2
                best = k
            }
        }
        return best
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val idx = event.actionIndex
        val pointerId = event.getPointerId(idx)
        val x = event.getX(idx)
        val y = event.getY(idx)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (action == MotionEvent.ACTION_POINTER_DOWN && pointerKeyMap.isNotEmpty()) {
                    val existing = pointerKeyMap.entries.toList()
                    for ((oldPid, oldKey) in existing) {
                        if (oldKey.kind == KeyKind.LETTER || oldKey.kind == KeyKind.NUMBER) {
                            oldKey.isPressed = false
                            fireKeyAction(oldKey)
                            pointerKeyMap.remove(oldPid)
                        }
                    }
                    longPressJob?.cancel()
                }

                val hit = findNearestKey(x, y) ?: return true
                pointerKeyMap[pointerId] = hit
                hit.isPressed = true
                activePopupKey = if (hit.kind == KeyKind.LETTER || hit.kind == KeyKind.NUMBER) hit else null
                service.vibrateAndClick(this)
                invalidate()

                when (hit.kind) {
                    KeyKind.BACKSPACE -> {
                        backspaceDownX = x
                        service.deleteOneChar()
                        backspaceJob?.cancel()
                        backspaceJob = service.serviceScope.launch {
                            delay(290L)
                            var interval = 52L
                            while (isActive) {
                                service.deleteOneChar()
                                delay(interval)
                                if (interval > 18L) interval -= 4L
                            }
                        }
                    }
                    KeyKind.SPACE -> {
                        spaceStartX = x
                        spaceLastStepX = x
                        isSpaceTrackpadActive = false
                    }
                    KeyKind.LETTER -> {
                        if (hit.topNumber.isNotEmpty()) {
                            longPressJob?.cancel()
                            longPressJob = service.serviceScope.launch {
                                delay(300L)
                                if (pointerKeyMap[pointerId] == hit && hit.isPressed) {
                                    service.vibrateAndClick(this@GboardCanvasView)
                                    service.commitStr(hit.topNumber)
                                    hit.isPressed = false
                                    activePopupKey = null
                                    pointerKeyMap.remove(pointerId)
                                    invalidate()
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }

            MotionEvent.ACTION_MOVE -> {
                for (p in 0 until event.pointerCount) {
                    val pid = event.getPointerId(p)
                    val px = event.getX(p)
                    val py = event.getY(p)
                    val currentKey = pointerKeyMap[pid] ?: continue

                    if (currentKey.kind == KeyKind.SPACE) {
                        val stepPx = dpF(13f)
                        if (!isSpaceTrackpadActive && abs(px - spaceStartX) > stepPx) {
                            isSpaceTrackpadActive = true
                            invalidate()
                        }
                        if (isSpaceTrackpadActive) {
                            val delta = px - spaceLastStepX
                            if (abs(delta) >= stepPx) {
                                val steps = (delta / stepPx).toInt()
                                val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
                                repeat(abs(steps)) { service.sendKeyDownUp(code) }
                                service.vibrateTick(this)
                                spaceLastStepX += steps * stepPx
                            }
                        }
                        continue
                    }

                    if (currentKey.kind == KeyKind.BACKSPACE) {
                        if (backspaceDownX - px > dpF(36f)) {
                            backspaceJob?.cancel()
                            service.deleteWholeWordBeforeCursor()
                            service.vibrateTick(this)
                            backspaceDownX = px
                        }
                        continue
                    }

                    val newHit = findNearestKey(px, py)
                    if (newHit != null && newHit != currentKey && (newHit.kind == KeyKind.LETTER || newHit.kind == KeyKind.NUMBER)) {
                        currentKey.isPressed = false
                        newHit.isPressed = true
                        pointerKeyMap[pid] = newHit
                        activePopupKey = newHit
                        longPressJob?.cancel()
                        invalidate()
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val releasedKey = pointerKeyMap.remove(pointerId)
                longPressJob?.cancel()
                if (releasedKey != null) {
                    releasedKey.isPressed = false
                    if (activePopupKey == releasedKey) activePopupKey = null

                    when (releasedKey.kind) {
                        KeyKind.BACKSPACE -> backspaceJob?.cancel()
                        KeyKind.SPACE -> {
                            if (isSpaceTrackpadActive) {
                                isSpaceTrackpadActive = false
                            } else {
                                service.handleSpacebarTap()
                            }
                        }
                        else -> fireKeyAction(releasedKey)
                    }
                    invalidate()
                }
            }

            MotionEvent.ACTION_CANCEL -> {
                longPressJob?.cancel()
                backspaceJob?.cancel()
                isSpaceTrackpadActive = false
                activePopupKey = null
                allKeys.forEach { it.isPressed = false }
                pointerKeyMap.clear()
                invalidate()
            }
        }
        return true
    }

    private fun fireKeyAction(key: KeyCell) {
        when (key.kind) {
            KeyKind.NUMBER -> service.commitStr(key.rawLabel)
            KeyKind.LETTER -> {
                val upper = service.isShifted || service.isCapsLocked
                val out = if (upper) key.rawLabel.uppercase() else key.rawLabel
                service.commitStr(out)
            }
            KeyKind.SHIFT -> {
                if (service.isSymbolsMode) {
                    service.isNumbersPage2 = !service.isNumbersPage2
                    rebuildKeyDefinitions()
                } else {
                    service.handleShiftTap()
                    invalidate()
                }
            }
            KeyKind.SYMBOLS -> {
                service.isSymbolsMode = !service.isSymbolsMode
                service.isNumbersPage2 = false
                rebuildKeyDefinitions()
            }
            KeyKind.EMOJI_COMMA -> {
                if (service.isSymbolsMode) service.commitStr(",")
                else service.openPanel(GboardService.ScreenMode.EMOJI)
            }
            KeyKind.GLOBE -> {
                if (service.isSymbolsMode) {
                    service.openPanel(GboardService.ScreenMode.TEXT_EDITING)
                } else {
                    service.isUrdu = !service.isUrdu
                    service.refreshTopStripViews()
                    rebuildKeyDefinitions()
                }
            }
            KeyKind.DOT -> service.commitStr(".")
            KeyKind.ENTER -> service.sendEnterOrSearch()
            else -> {}
        }
    }
}

// ============================================================================
// 5. MAIN INPUT METHOD SERVICE (VOICE SPEECH RECOGNIZER + REPLACEABLE TOOLBAR)
// ============================================================================
class GboardService : InputMethodService() {

    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var clipEngine: ClipboardEngine50K
    private lateinit var rootContainer: FrameLayout

    private var mainKeyboardWrapper: LinearLayout? = null
    private var canvasKeyboardView: GboardCanvasView? = null
    private var idleStripRow: LinearLayout? = null
    private var liveSuggestionRow: LinearLayout? = null
    private val suggestionTextViews = arrayOfNulls<TextView>(3)

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    // Direct SpeechRecognizer for Offline/Online Voice Typing
    private var speechRecognizer: SpeechRecognizer? = null
    private var isVoiceListening = false
    private var voiceStatusText = "Tap Speak or Start Talking..."
    private var lastPartialSpeech = ""

    enum class ScreenMode {
        KEYBOARD, CLIPBOARD, TOOLS_PAGE_1, TOOLS_PAGE_2, CUSTOMIZE_TOOLS,
        TEXT_EDITING, EMOJI, TRANSLATE, RESIZE, THEME_SELECTOR, VOICE_SPEECH
    }

    var currentMode = ScreenMode.KEYBOARD
    var isShifted = true
    var isCapsLocked = false
    var isUrdu = false
    var isSymbolsMode = false
    var isNumbersPage2 = false
    private var lastShiftTapTime = 0L
    private var lastSpaceTapTime = 0L

    private var isOneHanded = false
    private var isFloating = false
    private var isClipEditMode = false
    private var isSelectionActive = false

    private var activeEmojiCat = 1
    private var emojiSearchQuery = ""
    private val recentEmojis = mutableListOf("😂", "❤️", "🔥", "👍", "😭", "🙏", "😍", "🎉")

    private val currentWord = StringBuilder()
    private val undoStack = ArrayDeque<String>()

    private val activeTheme: KeyboardTheme
        get() = KeyboardConfig.getCurrentTheme(this)

    val colBg get() = activeTheme.bgColor
    val colKeyWhite get() = activeTheme.keyWhite
    val colKeyDark get() = activeTheme.keyDark
    val colTextDark get() = activeTheme.textDark
    val colTextMuted get() = activeTheme.textMuted
    val colAccentMaroon get() = activeTheme.accentColor

    private val dictionaryWords = listOf(
        "package", "import", "android", "fun", "val", "var", "class", "override", "return",
        "context", "BroadcastReceiver", "mediaplayer", "the", "and", "you", "that", "for",
        "with", "have", "this", "from", "hello", "brother", "code", "github", "gboard",
        "keyboard", "smooth", "performance", "developer", "application", "function", "string"
    )

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (::clipEngine.isInitialized) {
            clipEngine.syncSystemClipboardAsync {
                if (currentMode == ScreenMode.CLIPBOARD) openPanel(ScreenMode.CLIPBOARD)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        clipEngine = ClipboardEngine50K(this, serviceScope)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopDirectVoiceRecognition()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
    }

    override fun onCreateInputView(): View {
        rootContainer = FrameLayout(this).apply {
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        buildPersistentKeyboardView()
        openPanel(ScreenMode.KEYBOARD)
        return rootContainer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentWord.clear()
        isSymbolsMode = false
        checkAutoCapState()
        clipEngine.syncSystemClipboardAsync()
        openPanel(ScreenMode.KEYBOARD)
    }

    fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun keyRippleBg(
        color: Int,
        radiusDp: Int,
        strokeColor: Int = Color.TRANSPARENT,
        strokeWidthDp: Int = 0
    ): RippleDrawable {
        val base = GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), strokeColor)
        }
        val mask = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(radiusDp).toFloat()
        }
        return RippleDrawable(ColorStateList.valueOf(Color.parseColor("#30632B43")), base, mask)
    }

    fun vibrateAndClick(view: View) {
        try {
            val vib = vibrator
            if (vib != null && vib.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vib.vibrate(VibrationEffect.createOneShot(11L, 85))
                } else {
                    @Suppress("DEPRECATION")
                    vib.vibrate(11L)
                }
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
            }
            audioManager?.playSoundEffect(AudioManager.FX_KEY_CLICK, 0.35f)
        } catch (_: Exception) {}
    }

    fun vibrateTick(view: View) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            }
        } catch (_: Exception) {}
    }

    // Executes any tool whether tapped from Top Bar (Outside) or 3-Dots (Inside)
    private fun executeToolById(toolId: String) {
        when (toolId) {
            "TEXT_EDIT" -> openPanel(ScreenMode.TEXT_EDITING)
            "CLIPBOARD" -> openPanel(ScreenMode.CLIPBOARD)
            "TRANSLATE" -> openPanel(ScreenMode.TRANSLATE)
            "THEME" -> openPanel(ScreenMode.THEME_SELECTOR)
            "EMOJI" -> {
                activeEmojiCat = 1
                openPanel(ScreenMode.EMOJI)
            }
            "STICKER" -> {
                activeEmojiCat = 3
                openPanel(ScreenMode.EMOJI)
            }
            "GIF" -> {
                activeEmojiCat = 5
                openPanel(ScreenMode.EMOJI)
            }
            "ONE_HANDED" -> {
                isOneHanded = !isOneHanded
                openPanel(ScreenMode.KEYBOARD)
            }
            "RESIZE" -> openPanel(ScreenMode.RESIZE)
            "FLOATING" -> {
                isFloating = !isFloating
                openPanel(ScreenMode.KEYBOARD)
            }
            "UNDO" -> {
                performUndo()
                openPanel(ScreenMode.KEYBOARD)
            }
            "NUMBER_ROW" -> {
                KeyboardConfig.setNumberRowEnabled(this, !KeyboardConfig.isNumberRowEnabled(this))
                openPanel(ScreenMode.KEYBOARD)
            }
            "LANGUAGE" -> {
                isUrdu = !isUrdu
                openPanel(ScreenMode.KEYBOARD)
            }
            "SHARE" -> {
                commitStr("https://github.com/Gboard50K")
                openPanel(ScreenMode.KEYBOARD)
            }
        }
    }

    private fun buildPersistentKeyboardView() {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBg)
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(6), dp(4), dp(6), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }

        // Left 4-Dot Grid Icon (⊞) -> Opens Overflow Menu
        val gridIcon = TextView(this).apply {
            text = "⊞"
            textSize = 20f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            background = keyRippleBg(Color.TRANSPARENT, 20)
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.TOOLS_PAGE_1)
            }
        }
        topBar.addView(gridIcon)

        val middleFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        // 1. Dynamic Pinned Top Bar Row (Default has ◄I► Text, Clipboard, Translate, Theme + اردو + •••)
        val idleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        idleStripRow = idleRow
        middleFrame.addView(idleRow)

        // 2. Pre-allocated 3-Slot Live Suggestion Row
        val sugRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        for (i in 0..2) {
            val slot = TextView(this).apply {
                textSize = 15f
                setTypeface(null, if (i == 1) Typeface.BOLD else Typeface.NORMAL)
                setTextColor(colTextDark)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                background = keyRippleBg(Color.TRANSPARENT, 12)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener {
                    vibrateAndClick(this)
                    val word = tag as? String ?: return@setOnClickListener
                    if (word.isNotEmpty()) replaceCurrentWordWith("$word ")
                }
            }
            suggestionTextViews[i] = slot
            sugRow.addView(slot)
        }
        liveSuggestionRow = sugRow
        middleFrame.addView(sugRow)
        topBar.addView(middleFrame)

        // Right Direct Voice Mic Button (🎤)
        val micBtn = TextView(this).apply {
            text = "🎤"
            textSize = 15f
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyWhite, 20)
            elevation = dp(1).toFloat()
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.VOICE_SPEECH)
                startDirectVoiceRecognition()
            }
        }
        topBar.addView(micBtn)
        wrapper.addView(topBar)

        val canvasView = GboardCanvasView(this, this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        canvasKeyboardView = canvasView
        wrapper.addView(canvasView)

        mainKeyboardWrapper = wrapper
        refreshTopStripViews()
    }

    // Populates the Top Bar with user-selected Pinned Tools + Language Chip + ••• Button
    fun refreshTopStripViews() {
        val idleRow = idleStripRow ?: return
        idleRow.removeAllViews()

        val scroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val stripInner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        // Render all Pinned Top Bar tools (◄I► Text is first by default!)
        val pinnedTools = KeyboardConfig.getPinnedTools(this)
        for (tool in pinnedTools) {
            val chip = TextView(this).apply {
                text = if (tool.id == "TEXT_EDIT") "◄I► Text" else tool.icon
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(colTextDark)
                gravity = Gravity.CENTER
                background = keyRippleBg(colKeyWhite, 18)
                elevation = dp(1).toFloat()
                setPadding(dp(10), dp(4), dp(10), dp(4))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32))
                lp.marginEnd = dp(5)
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    executeToolById(tool.id)
                }
            }
            stripInner.addView(chip)
        }

        // Language Quick Switch Chip (⌨ اردو / English)
        val langChip = TextView(this).apply {
            text = if (isUrdu) "⌨ English" else "⌨ اردو"
            textSize = 13f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyWhite, 18)
            elevation = dp(1).toFloat()
            setPadding(dp(11), dp(4), dp(11), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32))
            lp.marginEnd = dp(5)
            layoutParams = lp
            setOnClickListener {
                vibrateAndClick(this)
                isUrdu = !isUrdu
                refreshTopStripViews()
                canvasKeyboardView?.rebuildKeyDefinitions()
            }
        }
        stripInner.addView(langChip)
        scroll.addView(stripInner)
        idleRow.addView(scroll)

        // Three Dots (•••) Overflow Button -> Opens Hidden Tools & Reorder Manager
        val dotsBtn = TextView(this).apply {
            text = "•••"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyWhite, 18)
            elevation = dp(1).toFloat()
            setPadding(dp(10), dp(4), dp(10), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(32))
            lp.marginStart = dp(4)
            lp.marginEnd = dp(4)
            layoutParams = lp
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.TOOLS_PAGE_1)
            }
        }
        idleRow.addView(dotsBtn)
    }

    private fun updateSuggestionsFast() {
        val prefix = currentWord.toString()
        if (prefix.isEmpty()) {
            liveSuggestionRow?.visibility = View.GONE
            idleStripRow?.visibility = View.VISIBLE
            return
        }

        idleStripRow?.visibility = View.GONE
        liveSuggestionRow?.visibility = View.VISIBLE

        val matches = dictionaryWords.filter {
            it.startsWith(prefix, ignoreCase = true) && !it.equals(prefix, ignoreCase = true)
        }
        val cand0 = prefix
        val cand1 = matches.getOrElse(0) { "${prefix}s" }
        val cand2 = matches.getOrElse(1) { "${prefix}ing" }

        val list = listOf("\"$cand0\"" to cand0, cand1 to cand1, cand2 to cand2)
        for (i in 0..2) {
            suggestionTextViews[i]?.apply {
                text = list[i].first
                tag = list[i].second
                setTextColor(colTextDark)
            }
        }
    }

    fun openPanel(mode: ScreenMode) {
        if (currentMode == ScreenMode.VOICE_SPEECH && mode != ScreenMode.VOICE_SPEECH) {
            stopDirectVoiceRecognition()
        }
        currentMode = mode
        if (!::rootContainer.isInitialized) return
        rootContainer.removeAllViews()

        val heightDp = KeyboardConfig.getKeyboardHeightDp(this)
        val width = if (isOneHanded) (resources.displayMetrics.widthPixels * 0.84).toInt() else ViewGroup.LayoutParams.MATCH_PARENT
        val lp = FrameLayout.LayoutParams(width, dp(heightDp)).apply {
            gravity = if (isOneHanded) Gravity.END else Gravity.CENTER_HORIZONTAL
            if (isFloating) setMargins(dp(14), dp(6), dp(14), dp(22))
        }

        if (mode == ScreenMode.KEYBOARD) {
            buildPersistentKeyboardView()
            val kb = mainKeyboardWrapper!!
            kb.setBackgroundColor(colBg)
            kb.layoutParams = lp
            updateSuggestionsFast()
            canvasKeyboardView?.rebuildKeyDefinitions()
            rootContainer.addView(kb)
            return
        }

        val panelWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBg)
            layoutParams = lp
        }

        when (mode) {
            ScreenMode.CLIPBOARD -> buildClipboardScreen(panelWrapper)
            ScreenMode.TOOLS_PAGE_1 -> buildQuickToolsScreen(panelWrapper, page = 1)
            ScreenMode.TOOLS_PAGE_2 -> buildQuickToolsScreen(panelWrapper, page = 2)
            ScreenMode.CUSTOMIZE_TOOLS -> buildCustomizeToolsScreen(panelWrapper)
            ScreenMode.TEXT_EDITING -> buildTextEditingScreen(panelWrapper)
            ScreenMode.EMOJI -> buildEmojiScreen(panelWrapper)
            ScreenMode.TRANSLATE -> buildTranslateScreen(panelWrapper)
            ScreenMode.RESIZE -> buildResizeScreen(panelWrapper)
            ScreenMode.THEME_SELECTOR -> buildThemeSelectorScreen(panelWrapper)
            ScreenMode.VOICE_SPEECH -> buildVoiceTypingScreen(panelWrapper)
            else -> {}
        }
        rootContainer.addView(panelWrapper)
    }

    // ========================================================================
    // DIRECT MICROPHONE SPEECH-TO-TEXT ENGINE (OFFLINE + ONLINE REAL-TIME)
    // ========================================================================
    private fun startDirectVoiceRecognition() {
        stopDirectVoiceRecognition()
        lastPartialSpeech = ""

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            voiceStatusText = "Microphone permission needed! Tap 'Grant Permission' below."
            if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatusText = "Speech service unavailable on device."
            if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)
            return
        }

        try {
            val sr = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer = sr
            isVoiceListening = true
            val offlinePref = KeyboardConfig.isOfflineVoicePreferred(this)
            voiceStatusText = if (offlinePref) "Listening (Offline/Online Engine)... Speak now" else "Listening (Online Engine)... Speak now"
            if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)

            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    voiceStatusText = "Speak now... Live typing active"
                    if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)
                }

                override fun onBeginningOfSpeech() {
                    lastPartialSpeech = ""
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isVoiceListening = false
                    voiceStatusText = "Processing speech..."
                    if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)
                }

                override fun onError(error: Int) {
                    // If offline pack is missing on device, automatically fallback to online recognizer
                    if (offlinePref && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_SERVER)) {
                        KeyboardConfig.setOfflineVoicePreferred(this@GboardService, false)
                        startDirectVoiceRecognition()
                        return
                    }
                    isVoiceListening = false
                    voiceStatusText = "Tap Mic to speak again"
                    if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val list = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val spoken = list?.firstOrNull()?.trim() ?: return
                    if (spoken.isNotEmpty()) {
                        currentInputConnection?.setComposingText(spoken, 1)
                        lastPartialSpeech = spoken
                        voiceStatusText = "\"$spoken\""
                        if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)
                    }
                }

                override fun onResults(results: Bundle?) {
                    val list = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val finalSpoken = list?.firstOrNull()?.trim() ?: lastPartialSpeech
                    if (finalSpoken.isNotEmpty()) {
                        currentInputConnection?.finishComposingText()
                        currentInputConnection?.commitText("$finalSpoken ", 1)
                        undoStack.addLast("$finalSpoken ")
                    }
                    lastPartialSpeech = ""
                    isVoiceListening = false
                    voiceStatusText = "Typed: \"$finalSpoken\" (Tap Mic to continue)"
                    if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (isUrdu) "ur-PK" else Locale.getDefault().toLanguageTag())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && offlinePref) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }
            sr.startListening(intent)
        } catch (e: Exception) {
            isVoiceListening = false
            voiceStatusText = "Voice error: ${e.localizedMessage}"
            if (currentMode == ScreenMode.VOICE_SPEECH) openPanel(ScreenMode.VOICE_SPEECH)
        }
    }

    private fun stopDirectVoiceRecognition() {
        try {
            currentInputConnection?.finishComposingText()
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
        isVoiceListening = false
    }

    private fun buildVoiceTypingScreen(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }
        header.addView(TextView(this).apply {
            text = "← Keyboard"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            background = keyRippleBg(colKeyWhite, 18)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.KEYBOARD)
            }
        })

        val offlineOn = KeyboardConfig.isOfflineVoicePreferred(this)
        header.addView(TextView(this).apply {
            text = if (offlineOn) "Mode: Offline + Online" else "Mode: Online"
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colAccentMaroon)
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                KeyboardConfig.setOfflineVoicePreferred(this@GboardService, !offlineOn)
                openPanel(ScreenMode.VOICE_SPEECH)
            }
        })
        parent.addView(header)

        val centerBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val micCircle = TextView(this).apply {
            text = if (isVoiceListening) "🎙️" else "🎤"
            textSize = 34f
            gravity = Gravity.CENTER
            background = keyRippleBg(if (isVoiceListening) colAccentMaroon else colKeyWhite, 45)
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(82), dp(82))
            setOnClickListener {
                vibrateAndClick(this)
                if (isVoiceListening) {
                    stopDirectVoiceRecognition()
                    voiceStatusText = "Paused. Tap Mic to speak."
                    openPanel(ScreenMode.VOICE_SPEECH)
                } else {
                    startDirectVoiceRecognition()
                }
            }
        }
        centerBox.addView(micCircle)

        centerBox.addView(TextView(this).apply {
            text = voiceStatusText
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(12), dp(20), dp(6))
        })

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED
        ) {
            centerBox.addView(TextView(this).apply {
                text = "Grant Mic Permission in App"
                textSize = 13f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.WHITE)
                background = keyRippleBg(colAccentMaroon, 20)
                setPadding(dp(18), dp(8), dp(18), dp(8))
                setOnClickListener {
                    startActivity(Intent(this@GboardService, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            })
        }

        parent.addView(centerBox)

        // Bottom Bar with Space and Backspace while speaking
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(8))
        }
        footer.addView(TextView(this).apply {
            text = "Space"
            textSize = 14f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyWhite, 16)
            setPadding(dp(28), dp(8), dp(28), dp(8))
            setOnClickListener {
                vibrateAndClick(this)
                commitStr(" ")
            }
        })
        footer.addView(TextView(this).apply {
            text = "⌫"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyDark, 16)
            setPadding(dp(22), dp(8), dp(22), dp(8))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(12)
            layoutParams = lp
            setOnClickListener {
                vibrateAndClick(this)
                deleteOneChar()
            }
        })
        parent.addView(footer)
    }

    // ========================================================================
    // SCREENSHOT 2 & 3: OVERFLOW MENU (•••) + REPLACEABLE TOOL CUSTOMIZER (✎)
    // ========================================================================
    private fun buildQuickToolsScreen(parent: LinearLayout, page: Int) {
        // Top Strip shows currently Pinned Top Bar tools + Close button
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(6))
        }

        topBar.addView(TextView(this).apply {
            text = "✕"
            textSize = 17f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyDark, 24)
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.KEYBOARD)
            }
        })

        val pinnedTools = KeyboardConfig.getPinnedTools(this).take(4)
        for (tool in pinnedTools) {
            topBar.addView(TextView(this).apply {
                text = tool.icon
                textSize = 16f
                setTypeface(null, Typeface.BOLD)
                setTextColor(colTextDark)
                gravity = Gravity.CENTER
                background = keyRippleBg(Color.TRANSPARENT, 20)
                layoutParams = LinearLayout.LayoutParams(0, dp(38), 1f)
                setOnClickListener {
                    vibrateAndClick(this)
                    executeToolById(tool.id)
                }
            })
        }

        topBar.addView(TextView(this).apply {
            text = "🎤"
            textSize = 16f
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyWhite, 24)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.VOICE_SPEECH)
                startDirectVoiceRecognition()
            }
        })
        parent.addView(topBar)

        // Overflow Tools (Tools currently hidden inside •••)
        val overflowAll = KeyboardConfig.getOverflowTools(this)
        val pageItems = if (page == 1) overflowAll.take(6) else overflowAll.drop(6).take(6)

        val gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(4), dp(14), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        for (i in 0 until 6 step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (j in 0..1) {
                if (i + j < pageItems.size) {
                    val opt = pageItems[i + j]
                    val pill = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = keyRippleBg(colKeyWhite, 28)
                        elevation = dp(1).toFloat()
                        setPadding(dp(14), dp(8), dp(14), dp(8))
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        lp.setMargins(dp(5), dp(4), dp(5), dp(4))
                        layoutParams = lp
                        setOnClickListener {
                            vibrateAndClick(this)
                            executeToolById(opt.id)
                        }
                        // Long-press any tool inside ••• to immediately move it Outside to Top Bar!
                        setOnLongClickListener {
                            vibrateAndClick(this)
                            KeyboardConfig.toggleToolLocation(this@GboardService, opt.id)
                            Toast.makeText(this@GboardService, "${opt.title} moved to Top Bar!", Toast.LENGTH_SHORT).show()
                            openPanel(currentMode)
                            true
                        }
                    }
                    pill.addView(TextView(this).apply {
                        text = opt.icon
                        textSize = 14f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(colTextDark)
                        setPadding(0, 0, dp(8), 0)
                    })
                    pill.addView(TextView(this).apply {
                        text = opt.title
                        textSize = 14f
                        setTextColor(colTextDark)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    if (opt.hasArrow) {
                        pill.addView(TextView(this).apply {
                            text = "❯"
                            textSize = 12f
                            setTextColor(colTextMuted)
                        })
                    }
                    row.addView(pill)
                } else {
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    })
                }
            }
            gridContainer.addView(row)
        }
        parent.addView(gridContainer)

        // Bottom Bar: Page Dots (● ○) + Pencil (✎) Button to Move Tools Inside/Outside
        val bottomRow = FrameLayout(this).apply {
            setPadding(dp(16), dp(4), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        bottomRow.addView(TextView(this).apply {
            text = if (page == 1) "●  ○" else "○  ●"
            textSize = 14f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(if (page == 1) ScreenMode.TOOLS_PAGE_2 else ScreenMode.TOOLS_PAGE_1)
            }
        })
        bottomRow.addView(TextView(this).apply {
            text = "✎"
            textSize = 16f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            background = keyRippleBg(Color.TRANSPARENT, 20, colKeyDark, 1)
            setPadding(dp(22), dp(4), dp(22), dp(4))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.CUSTOMIZE_TOOLS)
            }
        })
        parent.addView(bottomRow)
    }

    // In-Keyboard Reorder Screen: Move any item between Top Bar (Outside) and 3-Dots (Inside)
    private fun buildCustomizeToolsScreen(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
        }
        header.addView(TextView(this).apply {
            text = "← Done"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = keyRippleBg(colAccentMaroon, 18)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.KEYBOARD)
            }
        })
        header.addView(TextView(this).apply {
            text = "  Tap to Move: Top Bar ⇄ Inside •••"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
        })
        parent.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(10))
        }

        KeyboardConfig.ALL_TOOLS.forEach { tool ->
            val isPinned = KeyboardConfig.isToolPinnedToTopBar(this, tool.id)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = keyRippleBg(colKeyWhite, 14)
                setPadding(dp(14), dp(9), dp(14), dp(9))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6)
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    KeyboardConfig.toggleToolLocation(this@GboardService, tool.id)
                    openPanel(ScreenMode.CUSTOMIZE_TOOLS)
                }
            }
            card.addView(TextView(this).apply {
                text = "${tool.icon}   ${tool.title}"
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(colTextDark)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            card.addView(TextView(this).apply {
                text = if (isPinned) "ON TOP BAR (Outside)" else "INSIDE ••• (Hidden)"
                textSize = 11f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (isPinned) Color.WHITE else colTextDark)
                background = keyRippleBg(if (isPinned) colAccentMaroon else colKeyDark, 14)
                setPadding(dp(10), dp(4), dp(10), dp(4))
            })
            container.addView(card)
        }
        scroll.addView(container)
        parent.addView(scroll)
    }

    // ========================================================================
    // IN-KEYBOARD THEME SELECTOR PANEL (🎨 Theme)
    // ========================================================================
    private fun buildThemeSelectorScreen(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
        }
        header.addView(TextView(this).apply {
            text = "←"
            textSize = 22f
            setTextColor(colTextDark)
            setPadding(dp(8), dp(2), dp(16), dp(2))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.KEYBOARD)
            }
        })
        header.addView(TextView(this).apply {
            text = "Themes (Gboard & Samsung Studio)"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
        })
        parent.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(10))
        }

        val all = KeyboardConfig.ALL_THEMES
        val currentIdx = KeyboardConfig.getThemeIndex(this)

        for (i in all.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            for (j in 0..1) {
                if (i + j < all.size) {
                    val idx = i + j
                    val th = all[idx]
                    val isSel = idx == currentIdx
                    val card = LinearLayout(this).apply {
                        orientation = LinearLayout.VERTICAL
                        background = keyRippleBg(th.bgColor, 14, if (isSel) th.accentColor else th.keyDark, if (isSel) 2 else 1)
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        lp.setMargins(dp(5), dp(5), dp(5), dp(5))
                        layoutParams = lp
                        setOnClickListener {
                            vibrateAndClick(this)
                            KeyboardConfig.setThemeIndex(this@GboardService, idx)
                            openPanel(ScreenMode.KEYBOARD)
                        }
                    }
                    card.addView(TextView(this).apply {
                        text = if (isSel) "✓ ${th.name}" else th.name
                        textSize = 13f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(th.textDark)
                    })
                    card.addView(TextView(this).apply {
                        text = "Q  W  E  R  T"
                        textSize = 11f
                        setTextColor(th.textDark)
                        gravity = Gravity.CENTER
                        background = keyRippleBg(th.keyWhite, 8)
                        setPadding(dp(6), dp(4), dp(6), dp(4))
                        val klp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        klp.topMargin = dp(6)
                        layoutParams = klp
                    })
                    row.addView(card)
                }
            }
            grid.addView(row)
        }
        scroll.addView(grid)
        parent.addView(scroll)
    }

    // ========================================================================
    // SCREENSHOT 1: CLIPBOARD PANEL (WITH + ADD CLIP, PIN, DELETE & 50K PASTE)
    // ========================================================================
    private fun buildClipboardScreen(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }

        header.addView(TextView(this).apply {
            text = "←"
            textSize = 22f
            setTextColor(colTextDark)
            background = keyRippleBg(Color.TRANSPARENT, 20)
            setPadding(dp(8), dp(2), dp(14), dp(2))
            setOnClickListener {
                vibrateAndClick(this)
                isClipEditMode = false
                openPanel(ScreenMode.KEYBOARD)
            }
        })

        val statusTitle = TextView(this).apply {
            text = if (isClipEditMode) "Manage Clips" else "Clipboard"
            textSize = 19f
            setTextColor(colTextDark)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(statusTitle)

        // Save Currently Selected Text to Clipboard (+)
        val addClipBtn = TextView(this).apply {
            text = "+ Add"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            background = keyRippleBg(colKeyWhite, 20)
            elevation = dp(1).toFloat()
            setPadding(dp(12), dp(5), dp(12), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = dp(6)
            layoutParams = lp
            setOnClickListener {
                vibrateAndClick(this)
                val sel = currentInputConnection?.getSelectedText(0)?.toString()
                val before = currentInputConnection?.getTextBeforeCursor(500, 0)?.toString()
                val toSave = if (!sel.isNullOrBlank()) sel else before
                if (!toSave.isNullOrBlank()) {
                    clipEngine.saveRawClipAsync(toSave) { openPanel(ScreenMode.CLIPBOARD) }
                }
            }
        }
        header.addView(addClipBtn)

        val toggleBtn = TextView(this).apply {
            text = if (clipEngine.isEnabled) "●━" else "━○"
            textSize = 14f
            setTextColor(colTextDark)
            background = keyRippleBg(colKeyWhite, 20)
            elevation = dp(1).toFloat()
            setPadding(dp(12), dp(5), dp(12), dp(5))
            setOnClickListener {
                vibrateAndClick(this)
                clipEngine.isEnabled = !clipEngine.isEnabled
                openPanel(ScreenMode.CLIPBOARD)
            }
        }
        header.addView(toggleBtn)

        val editBtn = TextView(this).apply {
            text = if (isClipEditMode) "✓" else "✎"
            textSize = 17f
            setTextColor(if (isClipEditMode) Color.WHITE else colTextDark)
            background = keyRippleBg(if (isClipEditMode) colAccentMaroon else colKeyWhite, 20)
            elevation = dp(1).toFloat()
            setPadding(dp(12), dp(5), dp(12), dp(5))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(6)
            layoutParams = lp
            setOnClickListener {
                vibrateAndClick(this)
                isClipEditMode = !isClipEditMode
                openPanel(ScreenMode.CLIPBOARD)
            }
        }
        header.addView(editBtn)
        parent.addView(header)

        parent.addView(TextView(this).apply {
            text = "Recent"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextMuted)
            setPadding(dp(16), dp(2), dp(16), dp(4))
        })

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val columnsContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(2), dp(10), dp(12))
        }
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val clips = clipEngine.getRecentClipsInstant().sortedByDescending { it.isPinned }
        clips.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = keyRippleBg(colKeyWhite, 14)
                elevation = dp(1).toFloat()
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(dp(5), dp(5), dp(5), dp(5))
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    clipEngine.paste50K(
                        currentInputConnection,
                        item,
                        serviceScope,
                        onProgress = { pct -> statusTitle.text = "Pasting 50K ($pct%)" },
                        onComplete = {
                            statusTitle.text = "Clipboard"
                            undoStack.addLast(it)
                        }
                    )
                }
            }

            if (item.isLinkOrPkg || item.isPinned) {
                card.addView(TextView(this).apply {
                    text = if (item.isPinned) "📌 Pinned" else "⊶"
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(if (item.isPinned) colAccentMaroon else colTextDark)
                })
            }

            card.addView(TextView(this).apply {
                text = item.preview
                textSize = 15f
                setTextColor(colTextDark)
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
            })

            if (item.lineCount > 1) {
                card.addView(TextView(this).apply {
                    text = "${item.lineCount} lines"
                    textSize = 11f
                    setTextColor(colAccentMaroon)
                    setPadding(0, dp(4), 0, 0)
                })
            }

            if (isClipEditMode) {
                val actionRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(8), 0, 0)
                }
                actionRow.addView(TextView(this).apply {
                    text = if (item.isPinned) "Unpin" else "📌 Pin"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(colAccentMaroon)
                    setPadding(0, dp(2), dp(14), dp(2))
                    setOnClickListener {
                        clipEngine.togglePin(item.id)
                        openPanel(ScreenMode.CLIPBOARD)
                    }
                })
                actionRow.addView(TextView(this).apply {
                    text = "🗑 Delete"
                    textSize = 12f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(Color.parseColor("#B00020"))
                    setPadding(0, dp(2), 0, dp(2))
                    setOnClickListener {
                        clipEngine.deleteClip(item.id)
                        openPanel(ScreenMode.CLIPBOARD)
                    }
                })
                card.addView(actionRow)
            }

            if (index % 2 == 0) leftCol.addView(card) else rightCol.addView(card)
        }

        columnsContainer.addView(leftCol)
        columnsContainer.addView(rightCol)
        scroll.addView(columnsContainer)
        parent.addView(scroll)
    }

    // ========================================================================
    // FULL GBOARD & SAMSUNG TEXT EDITING D-PAD PANEL (◄I► Text)
    // ========================================================================
    private fun buildTextEditingScreen(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
        }
        header.addView(TextView(this).apply {
            text = "←"
            textSize = 22f
            setTextColor(colTextDark)
            background = keyRippleBg(Color.TRANSPARENT, 20)
            setPadding(dp(10), dp(2), dp(16), dp(2))
            setOnClickListener {
                vibrateAndClick(this)
                isSelectionActive = false
                openPanel(ScreenMode.KEYBOARD)
            }
        })
        header.addView(TextView(this).apply {
            text = "Text Editing Studio"
            textSize = 17f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        header.addView(TextView(this).apply {
            text = "⌫"
            textSize = 16f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyDark, 16)
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(36))
            setOnClickListener {
                vibrateAndClick(this)
                deleteOneChar()
            }
        })
        parent.addView(header)

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        val dpadCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.55f)
        }

        fun dpadCell(symbol: String, keyCode: Int? = null, customClick: (() -> Unit)? = null): View {
            return TextView(this).apply {
                text = symbol
                textSize = if (symbol.length > 2) 13f else 18f
                setTypeface(null, Typeface.BOLD)
                val isSel = symbol == "Select"
                setTextColor(if (isSel && isSelectionActive) Color.WHITE else colTextDark)
                gravity = Gravity.CENTER
                background = keyRippleBg(if (isSel && isSelectionActive) colAccentMaroon else colKeyWhite, 12)
                elevation = dp(1).toFloat()
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
                layoutParams = lp

                var holdJob: Job? = null
                setOnTouchListener { v, ev ->
                    when (ev.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            v.isPressed = true
                            vibrateAndClick(v)
                            if (keyCode != null) {
                                moveCursorWithSelection(keyCode)
                                holdJob = serviceScope.launch {
                                    delay(270L)
                                    while (isActive) {
                                        moveCursorWithSelection(keyCode)
                                        delay(42L)
                                    }
                                }
                            } else {
                                customClick?.invoke()
                            }
                            true
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            v.isPressed = false
                            holdJob?.cancel()
                            true
                        }
                        else -> false
                    }
                }
            }
        }

        val dRow1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        dRow1.addView(dpadCell("|◀", KeyEvent.KEYCODE_MOVE_HOME))
        dRow1.addView(dpadCell("▲", KeyEvent.KEYCODE_DPAD_UP))
        dRow1.addView(dpadCell("▶|", KeyEvent.KEYCODE_MOVE_END))
        dpadCol.addView(dRow1)

        val dRow2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        dRow2.addView(dpadCell("◀", KeyEvent.KEYCODE_DPAD_LEFT))
        dRow2.addView(dpadCell("Select") {
            isSelectionActive = !isSelectionActive
            openPanel(ScreenMode.TEXT_EDITING)
        })
        dRow2.addView(dpadCell("▶", KeyEvent.KEYCODE_DPAD_RIGHT))
        dpadCol.addView(dRow2)

        val dRow3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        dRow3.addView(dpadCell("Select All") {
            currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
            isSelectionActive = true
        })
        dRow3.addView(dpadCell("▼", KeyEvent.KEYCODE_DPAD_DOWN))
        dRow3.addView(dpadCell("Undo") { performUndo() })
        dpadCol.addView(dRow3)
        body.addView(dpadCol)

        val actionCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        fun actionBtn(label: String, onClick: () -> Unit): View {
            return TextView(this).apply {
                text = label
                textSize = 13.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(colTextDark)
                gravity = Gravity.CENTER
                background = keyRippleBg(colKeyWhite, 12)
                elevation = dp(1).toFloat()
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                lp.setMargins(dp(4), dp(4), dp(4), dp(4))
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    onClick()
                }
            }
        }

        actionCol.addView(actionBtn("✂  Cut") {
            val selected = currentInputConnection?.getSelectedText(0)?.toString()
            if (!selected.isNullOrEmpty()) clipEngine.saveRawClipAsync(selected)
            currentInputConnection?.performContextMenuAction(android.R.id.cut)
            isSelectionActive = false
        })
        actionCol.addView(actionBtn("📋  Copy") {
            val selected = currentInputConnection?.getSelectedText(0)?.toString()
            if (!selected.isNullOrEmpty()) {
                clipEngine.saveRawClipAsync(selected)
                Toast.makeText(this, "Copied (${selected.lines().size} lines)", Toast.LENGTH_SHORT).show()
            }
            currentInputConnection?.performContextMenuAction(android.R.id.copy)
            isSelectionActive = false
        })
        actionCol.addView(actionBtn("📥  Paste 50K") {
            val latest = clipEngine.getRecentClipsInstant().firstOrNull()
            if (latest != null) {
                clipEngine.paste50K(currentInputConnection, latest, serviceScope)
            } else {
                currentInputConnection?.performContextMenuAction(android.R.id.paste)
            }
            isSelectionActive = false
        })
        body.addView(actionCol)
        parent.addView(body)
    }

    private fun moveCursorWithSelection(keyCode: Int) {
        val ic = currentInputConnection ?: return
        val now = SystemClock.uptimeMillis()
        val meta = if (isSelectionActive) KeyEvent.META_SHIFT_ON else 0
        if (isSelectionActive) {
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT, 0))
        }
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
        if (isSelectionActive) {
            ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SHIFT_LEFT, 0))
        }
    }

    // ========================================================================
    // TRANSLATE & RESIZE PANELS
    // ========================================================================
    private fun buildTranslateScreen(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(8))
        }
        header.addView(TextView(this).apply {
            text = "←"
            textSize = 22f
            setTextColor(colTextDark)
            setPadding(dp(8), dp(2), dp(16), dp(2))
            setOnClickListener { openPanel(ScreenMode.KEYBOARD) }
        })
        header.addView(TextView(this).apply {
            text = "G文  Instant Offline/Online Translator"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
        })
        parent.addView(header)

        val phrases = listOf(
            "Hello brother, how are you?" to "Assalamu Alaikum / Namaste brother, how are you?",
            "50,000 lines code clipboard is ready" to "package com.gboard.copy50k // 50,000 Lines Ready",
            "Voice recognition works offline and online" to "Direct SpeechRecognizer offline & online enabled",
            "Thank you very much" to "Shukriya / Thank you very much"
        )
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        for ((en, tr) in phrases) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = keyRippleBg(colKeyWhite, 12)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6)
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    commitStr("$tr ")
                    openPanel(ScreenMode.KEYBOARD)
                }
            }
            card.addView(TextView(this).apply {
                text = en
                textSize = 12f
                setTextColor(colTextMuted)
            })
            card.addView(TextView(this).apply {
                text = tr
                textSize = 14.5f
                setTypeface(null, Typeface.BOLD)
                setTextColor(colTextDark)
            })
            list.addView(card)
        }
        parent.addView(list)
    }

    private fun buildResizeScreen(parent: LinearLayout) {
        val currentHeight = KeyboardConfig.getKeyboardHeightDp(this)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        box.addView(TextView(this).apply {
            text = "⬚  Keyboard Height (${currentHeight}dp)"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        val sizes = listOf("Compact" to 255, "Normal" to 290, "Tall" to 325, "Extra Tall" to 365)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for ((name, h) in sizes) {
            row.addView(TextView(this).apply {
                text = name
                textSize = 13.5f
                setTextColor(if (currentHeight == h) Color.WHITE else colTextDark)
                background = keyRippleBg(if (currentHeight == h) colAccentMaroon else colKeyWhite, 20)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(dp(4), 0, dp(4), 0)
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    KeyboardConfig.setKeyboardHeightDp(this@GboardService, h)
                    openPanel(ScreenMode.RESIZE)
                }
            })
        }
        box.addView(row)

        box.addView(TextView(this).apply {
            text = "✓  Apply Height"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            background = keyRippleBg(colKeyDark, 24)
            setPadding(dp(32), dp(10), dp(32), dp(10))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(20)
            layoutParams = lp
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.KEYBOARD)
            }
        })
        parent.addView(box)
    }

    // ========================================================================
    // SCREENSHOT 5: EMOJI PANEL
    // ========================================================================
    private fun buildEmojiScreen(parent: LinearLayout) {
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(4))
        }
        topBar.addView(TextView(this).apply {
            text = "←"
            textSize = 20f
            setTextColor(colTextDark)
            background = keyRippleBg(Color.TRANSPARENT, 20)
            setPadding(dp(6), dp(4), dp(10), dp(4))
            setOnClickListener {
                vibrateAndClick(this)
                emojiSearchQuery = ""
                openPanel(ScreenMode.KEYBOARD)
            }
        })

        topBar.addView(TextView(this).apply {
            text = if (emojiSearchQuery.isEmpty()) "🔍 Search" else "🔍 $emojiSearchQuery ✕"
            textSize = 14f
            setTextColor(colTextDark)
            background = keyRippleBg(colKeyWhite, 20)
            setPadding(dp(14), dp(6), dp(18), dp(6))
            setOnClickListener {
                vibrateAndClick(this)
                val filters = listOf("", "laugh", "love", "sad", "fire")
                emojiSearchQuery = filters[(filters.indexOf(emojiSearchQuery) + 1) % filters.size]
                openPanel(ScreenMode.EMOJI)
            }
        })

        val cats = listOf("🕒", "😊", "🏃", "🐻", "☕", "🏙")
        cats.forEachIndexed { idx, icon ->
            val selected = activeEmojiCat == idx && emojiSearchQuery.isEmpty()
            topBar.addView(TextView(this).apply {
                text = icon
                textSize = 14f
                gravity = Gravity.CENTER
                background = keyRippleBg(if (selected) colAccentMaroon else Color.TRANSPARENT, 20)
                if (selected) setTextColor(Color.WHITE)
                val lp = LinearLayout.LayoutParams(0, dp(34), 1f)
                lp.marginStart = dp(2)
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    emojiSearchQuery = ""
                    activeEmojiCat = idx
                    openPanel(ScreenMode.EMOJI)
                }
            })
        }
        parent.addView(topBar)

        val titles = listOf("Recently used", "Smileys and emotions", "People", "Animals", "Food", "Travel", "Emoticons :-)")
        parent.addView(TextView(this).apply {
            text = if (emojiSearchQuery.isNotEmpty()) "Filter: $emojiSearchQuery" else titles.getOrElse(activeEmojiCat) { "Smileys and emotions" }
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextMuted)
            setPadding(dp(14), dp(2), dp(14), dp(4))
        })

        val smileys = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😭", "😉", "😗", "😙", "😚", "😘", "🥰", "😍",
            "🤩", "🥳", "🫠", "🙃", "🙂", "🥲", "🥹", "😊",
            "☺️", "😌", "😔", "😴", "😏", "🤤", "😋", "😛",
            "😝", "😜", "🤪", "😒", "🥺", "😬", "😑", "😐"
        )
        val people = listOf("👋", "🤚", "✋", "🖖", "👌", "🤌", "✌️", "🤞", "🤟", "🤘", "🤙", "👍", "👎", "✊", "👊", "👏", "🙌", "🫶", "🤝", "🙏", "✍️", "💪", "🏃", "💃")
        val animals = listOf("🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈", "🙉", "🙊", "🐔", "🐧", "🐦", "🦅", "🦋", "🌸")
        val food = listOf("🍎", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍒", "🍕", "🍔", "🍟", "🌭", "🍿", "🥪", "🌮", "🌯", "🍜", "🍝", "🍣", "🍦", "🍩", "🍪", "🎂", "☕")
        val travel = listOf("🚗", "🚕", "🚙", "🚌", "🏎", "🚓", "🚑", "🚒", "✈️", "🚀", "🛸", "🚁", "⛵", "🏠", "🏢", "🏙", "💻", "📱", "⌚", "📷", "🔥", "❤️", "💯", "🎉")
        val kaomojis = listOf(":-)", ":-(", ";-)", ":-D", ":-P", "¯\\_(ツ)_/¯", "(^_^)", "(•_•)", "(⌐■_■)", "♥‿♥", "(づ｡◕‿‿◕｡)づ", "(☞ﾟヮﾟ)☞")

        val emojisToShow = when {
            emojiSearchQuery == "laugh" -> listOf("😀", "😄", "😆", "😅", "😂", "🤣", "😝", "😜")
            emojiSearchQuery == "love" -> listOf("😍", "🥰", "😘", "😗", "😚", "❤️", "🫶", "🤩")
            emojiSearchQuery == "sad" -> listOf("😭", "🥲", "🥹", "😔", "🥺", "😒", "😑", "😐")
            emojiSearchQuery == "fire" -> listOf("🔥", "💯", "🚀", "🎉", "🤩", "🥳", "💪", "👏")
            activeEmojiCat == 0 -> recentEmojis
            activeEmojiCat == 1 -> smileys
            activeEmojiCat == 2 -> people
            activeEmojiCat == 3 -> animals
            activeEmojiCat == 4 -> food
            activeEmojiCat == 5 -> travel
            else -> kaomojis
        }

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(6), dp(4))
        }
        val cols = if (activeEmojiCat == 6 && emojiSearchQuery.isEmpty()) 4 else 8
        for (i in emojisToShow.indices step cols) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38))
            }
            for (j in 0 until cols) {
                if (i + j < emojisToShow.size) {
                    val em = emojisToShow[i + j]
                    row.addView(TextView(this).apply {
                        text = em
                        textSize = if (cols == 4) 13f else 22f
                        setTextColor(colTextDark)
                        gravity = Gravity.CENTER
                        background = keyRippleBg(Color.TRANSPARENT, 10)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        setOnClickListener {
                            vibrateAndClick(this)
                            recentEmojis.remove(em)
                            recentEmojis.add(0, em)
                            commitStr(em)
                        }
                    })
                } else {
                    row.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    })
                }
            }
            grid.addView(row)
        }
        scroll.addView(grid)
        parent.addView(scroll)

        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(6))
        }
        fun footerChip(label: String, selected: Boolean = false, onClick: () -> Unit): View {
            return TextView(this).apply {
                text = label
                textSize = 14f
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (selected) Color.WHITE else colTextDark)
                gravity = Gravity.CENTER
                background = keyRippleBg(if (selected) colAccentMaroon else colKeyWhite, 20)
                val lp = LinearLayout.LayoutParams(0, dp(36), 1f)
                lp.setMargins(dp(4), 0, dp(4), 0)
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    onClick()
                }
            }
        }
        footer.addView(footerChip("ABC") { openPanel(ScreenMode.KEYBOARD) })
        footer.addView(footerChip("😊", selected = activeEmojiCat in 0..5) {
            activeEmojiCat = 1
            openPanel(ScreenMode.EMOJI)
        })
        footer.addView(footerChip("GIF") {
            activeEmojiCat = 5
            openPanel(ScreenMode.EMOJI)
        })
        footer.addView(footerChip("🖼") {
            activeEmojiCat = 3
            openPanel(ScreenMode.EMOJI)
        })
        footer.addView(footerChip(":-)", selected = activeEmojiCat == 6) {
            activeEmojiCat = 6
            openPanel(ScreenMode.EMOJI)
        })
        footer.addView(footerChip("⌫") { deleteOneChar() })
        parent.addView(footer)
    }

    // ========================================================================
    // INPUT CONNECTION & SMART TYPING HELPERS
    // ========================================================================
    fun handleShiftTap() {
        val now = SystemClock.uptimeMillis()
        if (now - lastShiftTapTime < 340L) {
            isCapsLocked = true
            isShifted = true
        } else if (isCapsLocked) {
            isCapsLocked = false
            isShifted = false
        } else {
            isShifted = !isShifted
        }
        lastShiftTapTime = now
    }

    fun handleSpacebarTap() {
        val now = SystemClock.uptimeMillis()
        val ic = currentInputConnection ?: return
        if (now - lastSpaceTapTime < 320L) {
            val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                currentWord.clear()
                isShifted = true
                canvasKeyboardView?.invalidate()
                updateSuggestionsFast()
                lastSpaceTapTime = 0L
                return
            }
        }
        lastSpaceTapTime = now
        commitStr(" ")
    }

    private fun checkAutoCapState() {
        if (isCapsLocked) return
        val before = currentInputConnection?.getTextBeforeCursor(3, 0)?.toString() ?: ""
        isShifted = before.isEmpty() || before.endsWith(". ") || before.endsWith("? ") || before.endsWith("! ") || before.endsWith("\n")
    }

    fun commitStr(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)
        undoStack.addLast(text)
        if (undoStack.size > 35) undoStack.removeFirst()

        if (text.length == 1 && text[0].isLetter()) {
            currentWord.append(text)
        } else {
            currentWord.clear()
        }

        if (isShifted && !isCapsLocked) {
            isShifted = false
            canvasKeyboardView?.invalidate()
        } else if (text == " " || text == "\n") {
            checkAutoCapState()
            canvasKeyboardView?.invalidate()
        }
        updateSuggestionsFast()
    }

    private fun replaceCurrentWordWith(newWord: String) {
        val ic = currentInputConnection ?: return
        val len = currentWord.length
        if (len > 0) ic.deleteSurroundingText(len, 0)
        ic.commitText(newWord, 1)
        undoStack.addLast(newWord)
        currentWord.clear()
        checkAutoCapState()
        canvasKeyboardView?.invalidate()
        updateSuggestionsFast()
    }

    fun deleteOneChar() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
            if (currentWord.isNotEmpty()) {
                currentWord.deleteCharAt(currentWord.length - 1)
            }
        }
        checkAutoCapState()
        canvasKeyboardView?.invalidate()
        updateSuggestionsFast()
    }

    fun deleteWholeWordBeforeCursor() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(45, 0)?.toString() ?: return
        if (before.isEmpty()) return
        val trimmed = before.trimEnd()
        val lastSpace = trimmed.lastIndexOf(' ')
        val deleteCount = if (lastSpace == -1) before.length else (before.length - lastSpace - 1)
        if (deleteCount > 0) {
            ic.deleteSurroundingText(deleteCount, 0)
            currentWord.clear()
            checkAutoCapState()
            canvasKeyboardView?.invalidate()
            updateSuggestionsFast()
        }
    }

    private fun performUndo() {
        val ic = currentInputConnection ?: return
        if (undoStack.isNotEmpty()) {
            val last = undoStack.removeLast()
            ic.deleteSurroundingText(last.length, 0)
            currentWord.clear()
            updateSuggestionsFast()
        }
    }

    fun sendKeyDownUp(keyCode: Int) {
        val ic = currentInputConnection ?: return
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    fun sendEnterOrSearch() {
        val ic = currentInputConnection ?: return
        currentWord.clear()
        updateSuggestionsFast()
        val info = currentInputEditorInfo
        if (info != null && (info.imeOptions and EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        } else {
            sendKeyDownUp(KeyEvent.KEYCODE_ENTER)
        }
        checkAutoCapState()
        canvasKeyboardView?.invalidate()
    }
}

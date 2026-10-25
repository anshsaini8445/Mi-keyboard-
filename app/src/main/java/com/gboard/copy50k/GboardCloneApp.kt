package com.gboard.copy50k

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
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
import java.util.UUID

// ============================================================================
// 1. LAUNCHER SETUP & 50,000 LINES TEST ACTIVITY
// ============================================================================
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F9ECEF"))
            setPadding(48, 64, 48, 48)
        }

        val title = TextView(this).apply {
            text = "Gboard 50K (Developer Edition)"
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F1A1C"))
        }
        root.addView(title)

        val sub = TextView(this).apply {
            text = "Exact Gboard UI + 50,000 Lines Copy-Paste Engine"
            textSize = 14f
            setTextColor(Color.parseColor("#513E44"))
            setPadding(0, 8, 0, 36)
        }
        root.addView(sub)

        fun makeBtn(label: String, onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#513E44"))
                    cornerRadius = 32f
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 24
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        root.addView(makeBtn("Step 1: Enable 'Gboard 50K' in Settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        root.addView(makeBtn("Step 2: Select 'Gboard 50K' as Active Keyboard") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        root.addView(makeBtn("Step 3: Copy 50,000 Lines Sample Code to Test") {
            val sb = StringBuilder()
            sb.append("package com.app.mediaplayer\n\nimport android.content.BroadcastReceiver\n")
            for (i in 4..50000) {
                sb.append("val lineNumber_$i = \"Developer code line $i\"\n")
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code50k", sb.toString()))
            Toast.makeText(this, "50,000 Lines Copied! Open Keyboard Clipboard to Paste.", Toast.LENGTH_LONG).show()
        })

        val testInput = EditText(this).apply {
            hint = "Yahan click karke Gboard 50K test karein..."
            gravity = Gravity.TOP or Gravity.START
            minLines = 8
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setBackgroundColor(Color.WHITE)
            setPadding(32, 32, 32, 32)
        }
        root.addView(testInput, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        setContentView(root)
    }
}

// ============================================================================
// 2. 50,000 LINES FILE-BACKED CLIPBOARD & CHUNKED PASTE ENGINE
// ============================================================================
data class ClipItem(
    val id: String,
    val preview: String,
    val lineCount: Int,
    val isLinkOrPkg: Boolean,
    val filePath: String
)

class ClipboardEngine50K(private val context: Context) {
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val clipFolder = File(context.filesDir, "clips_50k").apply { if (!exists()) mkdirs() }
    private val indexFile = File(context.filesDir, "clips_index.json")
    private var lastHash = 0
    var isEnabled = true

    companion object {
        const val MAX_LINES = 50_000
        private const val CHUNK_SIZE = 16_384
    }

    init {
        seedInitialClipsIfEmpty()
    }

    private fun seedInitialClipsIfEmpty() {
        if (!indexFile.exists()) {
            saveRawClip("android.content.Context")
            saveRawClip("android.content.BroadcastReceiver")
            saveRawClip("com.app.mediaplayer")
            saveRawClip("package com.app.mediaplayer\n\nimport android.content.BroadcastReceiver...")
        }
    }

    fun syncSystemClipboard() {
        if (!isEnabled) return
        try {
            val primary = clipboard.primaryClip ?: return
            if (primary.itemCount > 0) {
                val text = primary.getItemAt(0).coerceToText(context)?.toString() ?: return
                if (text.isNotBlank() && text.hashCode() != lastHash) {
                    lastHash = text.hashCode()
                    saveRawClip(text)
                }
            }
        } catch (_: Exception) {}
    }

    fun saveRawClip(rawText: String) {
        if (!isEnabled || rawText.isBlank()) return
        val lines = rawText.lineSequence().take(MAX_LINES).toList()
        val finalText = lines.joinToString("\n")
        val id = UUID.randomUUID().toString()
        val file = File(clipFolder, "$id.txt")
        file.writeText(finalText, Charsets.UTF_8)

        val trimmed = finalText.trim()
        val isPkg = !trimmed.contains("\n") && (trimmed.contains(".") && !trimmed.contains(" "))
        val preview = if (trimmed.length > 140) trimmed.take(140) + "..." else trimmed

        val arr = if (indexFile.exists()) JSONArray(indexFile.readText()) else JSONArray()
        val updated = JSONArray()

        // Duplicate preview check
        val newObj = JSONObject().apply {
            put("id", id)
            put("preview", preview)
            put("lineCount", lines.size)
            put("isLinkOrPkg", isPkg)
            put("filePath", file.absolutePath)
        }
        updated.put(newObj)

        for (i in 0 until minOf(arr.length(), 40)) {
            val old = arr.getJSONObject(i)
            if (old.optString("preview") != preview) {
                updated.put(old)
            }
        }
        indexFile.writeText(updated.toString(), Charsets.UTF_8)
    }

    fun getRecentClips(): List<ClipItem> {
        if (!indexFile.exists()) return emptyList()
        val arr = JSONArray(indexFile.readText(Charsets.UTF_8))
        val result = mutableListOf<ClipItem>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            result.add(
                ClipItem(
                    id = o.getString("id"),
                    preview = o.getString("preview"),
                    lineCount = o.optInt("lineCount", 1),
                    isLinkOrPkg = o.optBoolean("isLinkOrPkg", false),
                    filePath = o.getString("filePath")
                )
            )
        }
        return result
    }

    fun readFullClip(item: ClipItem): String {
        val f = File(item.filePath)
        return if (f.exists()) f.readText(Charsets.UTF_8) else item.preview
    }

    fun clearAll() {
        clipFolder.listFiles()?.forEach { it.delete() }
        if (indexFile.exists()) indexFile.delete()
        seedInitialClipsIfEmpty()
    }

    fun paste50K(ic: InputConnection?, fullText: String, scope: CoroutineScope, onInserted: (String) -> Unit) {
        if (ic == null || fullText.isEmpty()) return
        scope.launch(Dispatchers.Main) {
            if (fullText.length <= CHUNK_SIZE) {
                ic.commitText(fullText, 1)
                onInserted(fullText)
                return@launch
            }
            var offset = 0
            val total = fullText.length
            while (offset < total) {
                val end = minOf(offset + CHUNK_SIZE, total)
                val chunk = withContext(Dispatchers.Default) { fullText.substring(offset, end) }
                ic.beginBatchEdit()
                ic.commitText(chunk, 1)
                ic.endBatchEdit()
                offset = end
                delay(6L)
            }
            onInserted(fullText)
        }
    }
}

// ============================================================================
// 3. EXACT GBOARD UI & INPUT METHOD SERVICE (ALL 5 SCREENSHOTS)
// ============================================================================
class GboardService : InputMethodService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var clipEngine: ClipboardEngine50K
    private lateinit var rootContainer: FrameLayout

    private enum class ScreenMode {
        KEYBOARD, CLIPBOARD, TOOLS_PAGE_1, TOOLS_PAGE_2, EMOJI, SYMBOLS
    }

    private var currentMode = ScreenMode.KEYBOARD
    private var isShifted = false
    private var isUrdu = false
    private var isOneHanded = false
    private var isFloating = false
    private var keyboardHeightDp = 285
    private var lastCommittedText = ""

    // Exact Gboard Material You Rose/Pink Palette from Screenshots
    private var colBg = Color.parseColor("#F9ECEF")
    private val colKeyWhite = Color.parseColor("#FFFFFF")
    private val colKeyDark = Color.parseColor("#513E44")
    private val colTextDark = Color.parseColor("#1F1A1C")
    private val colTextMuted = Color.parseColor("#6E5E63")
    private val colAccentMaroon = Color.parseColor("#632B43")

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (::clipEngine.isInitialized) {
            clipEngine.syncSystemClipboard()
            if (currentMode == ScreenMode.CLIPBOARD) renderCurrentScreen()
        }
    }

    override fun onCreate() {
        super.onCreate()
        clipEngine = ClipboardEngine50K(this)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.addPrimaryClipChangedListener(clipListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.removePrimaryClipChangedListener(clipListener)
        serviceScope.cancel()
    }

    override fun onCreateInputView(): View {
        rootContainer = FrameLayout(this)
        renderCurrentScreen()
        return rootContainer
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        clipEngine.syncSystemClipboard()
        renderCurrentScreen()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun roundedBg(color: Int, radiusDp: Int, strokeColor: Int = Color.TRANSPARENT, strokeWidthDp: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            if (strokeWidthDp > 0) setStroke(dp(strokeWidthDp), strokeColor)
        }
    }

    private fun switchMode(mode: ScreenMode) {
        currentMode = mode
        renderCurrentScreen()
    }

    private fun renderCurrentScreen() {
        if (!::rootContainer.isInitialized) return
        rootContainer.removeAllViews()

        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBg)
            val width = if (isOneHanded) (resources.displayMetrics.widthPixels * 0.82).toInt() else ViewGroup.LayoutParams.MATCH_PARENT
            val lp = FrameLayout.LayoutParams(width, dp(keyboardHeightDp))
            lp.gravity = if (isOneHanded) Gravity.END else Gravity.CENTER_HORIZONTAL
            if (isFloating) {
                lp.setMargins(dp(16), dp(8), dp(16), dp(24))
                background = roundedBg(colBg, 20, colKeyDark, 1)
            }
            layoutParams = lp
        }

        when (currentMode) {
            ScreenMode.KEYBOARD -> buildMainKeyboard(wrapper)
            ScreenMode.SYMBOLS -> buildSymbolsKeyboard(wrapper)
            ScreenMode.CLIPBOARD -> buildClipboardScreen(wrapper)
            ScreenMode.TOOLS_PAGE_1 -> buildQuickToolsScreen(wrapper, page = 1)
            ScreenMode.TOOLS_PAGE_2 -> buildQuickToolsScreen(wrapper, page = 2)
            ScreenMode.EMOJI -> buildEmojiScreen(wrapper)
        }

        rootContainer.addView(wrapper)
    }

    // ========================================================================
    // SCREENSHOT 4: MAIN QWERTY KEYBOARD WITH SUPERSCRIPT NUMBERS & TOP STRIP
    // ========================================================================
    private fun buildMainKeyboard(parent: LinearLayout) {
        // Top Suggestion / Quick Strip (::, Add, اردو, ..., Mic)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(6))
        }

        // 4-dot Grid Icon (::) -> Opens Quick Tools Page 1
        val gridIcon = TextView(this).apply {
            text = "⊞"
            textSize = 22f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener { switchMode(ScreenMode.TOOLS_PAGE_1) }
        }
        topBar.addView(gridIcon)

        val centerStrip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val addLabel = TextView(this).apply {
            text = "Add"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            setPadding(dp(8), dp(6), dp(10), dp(6))
            setOnClickListener { switchMode(ScreenMode.CLIPBOARD) }
        }
        centerStrip.addView(addLabel)

        // Language Chip (⌨ اردو)
        val urduChip = TextView(this).apply {
            text = if (isUrdu) "⌨  English" else "⌨  اردو"
            textSize = 14f
            setTextColor(colTextDark)
            background = roundedBg(colKeyWhite, 20)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                isUrdu = !isUrdu
                renderCurrentScreen()
            }
        }
        centerStrip.addView(urduChip)

        // Three Dots (...) Pill -> Opens Quick Tools Page 2
        val dotsBtn = TextView(this).apply {
            text = "•••"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            background = roundedBg(colKeyWhite, 20)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(8)
            layoutParams = lp
            setOnClickListener { switchMode(ScreenMode.TOOLS_PAGE_2) }
        }
        centerStrip.addView(dotsBtn)
        topBar.addView(centerStrip)

        // Voice Mic Button
        val micBtn = TextView(this).apply {
            text = "🎤"
            textSize = 16f
            gravity = Gravity.CENTER
            background = roundedBg(colKeyWhite, 20)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener { triggerVoiceInput() }
        }
        topBar.addView(micBtn)
        parent.addView(topBar)

        // Row 1: q1 w2 e3 r4 t5 y6 u7 i8 o9 p0
        val row1Letters = if (isUrdu) listOf("ط", "ص", "ھ", "د", "ٹ", "پ", "ت", "ب", "ج", "ح")
        else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
        val row1Nums = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        for (i in row1Letters.indices) {
            val ch = if (isShifted) row1Letters[i].uppercase() else row1Letters[i]
            val num = row1Nums[i]
            row1.addView(createNumberedKey(ch, num))
        }
        parent.addView(row1)

        // Row 2: a s d f g h j k l
        val row2Letters = if (isUrdu) listOf("م", "ن", "ر", "ل", "ہ", "ا", "ک", "ی", "ق")
        else listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(18), dp(3), dp(18), dp(3))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        for (letter in row2Letters) {
            val ch = if (isShifted) letter.uppercase() else letter
            row2.addView(createStandardKey(ch, weight = 1f) { commitStr(ch) })
        }
        parent.addView(row2)

        // Row 3: Shift + z x c v b n m + Backspace
        val row3Letters = if (isUrdu) listOf("ز", "خ", "ش", "غ", "ف", "ے", "س")
        else listOf("z", "x", "c", "v", "b", "n", "m")
        val row3 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(3), dp(4), dp(3))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        row3.addView(createDarkKey("⇧", weight = 1.4f) {
            isShifted = !isShifted
            renderCurrentScreen()
        })
        for (letter in row3Letters) {
            val ch = if (isShifted) letter.uppercase() else letter
            row3.addView(createStandardKey(ch, weight = 1f) { commitStr(ch) })
        }
        row3.addView(createBackspaceKey(weight = 1.4f))
        parent.addView(row3)

        // Row 4: ?123, Emoji/Comma, Globe, English Spacebar, Period, Search/Enter
        val row4 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(3), dp(4), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.05f)
        }
        row4.addView(createDarkKey("?123", weight = 1.3f, radiusDp = 24) {
            switchMode(ScreenMode.SYMBOLS)
        })
        row4.addView(createDarkKey("☺\n,", weight = 0.9f, radiusDp = 10, smallText = true) {
            switchMode(ScreenMode.EMOJI)
        })
        row4.addView(createStandardKey("🌐", weight = 0.9f) {
            isUrdu = !isUrdu
            renderCurrentScreen()
        })
        row4.addView(createStandardKey(if (isUrdu) "اردو" else "English", weight = 3.8f, isMutedLabel = true) {
            commitStr(" ")
        })
        row4.addView(createDarkKey(".", weight = 0.9f, radiusDp = 10) {
            commitStr(".")
        })
        row4.addView(createDarkKey("🔍", weight = 1.3f, radiusDp = 24) {
            sendEnterOrSearch()
        })
        parent.addView(row4)
    }

    private fun createNumberedKey(letter: String, number: String): View {
        val box = FrameLayout(this).apply {
            background = roundedBg(colKeyWhite, 8)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            lp.setMargins(dp(3), dp(2), dp(3), dp(2))
            layoutParams = lp
            isClickable = true
            setOnClickListener { commitStr(letter) }
            setOnLongClickListener {
                commitStr(number)
                true
            }
        }
        val numTv = TextView(this).apply {
            text = number
            textSize = 10f
            setTextColor(colTextMuted)
            gravity = Gravity.TOP or Gravity.END
            setPadding(0, dp(3), dp(5), 0)
        }
        val charTv = TextView(this).apply {
            text = letter
            textSize = 20f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
        }
        box.addView(numTv)
        box.addView(charTv)
        return box
    }

    private fun createStandardKey(
        label: String,
        weight: Float = 1f,
        isMutedLabel: Boolean = false,
        onClick: () -> Unit
    ): View {
        return TextView(this).apply {
            text = label
            textSize = if (isMutedLabel) 14f else 20f
            setTextColor(if (isMutedLabel) colTextMuted else colTextDark)
            gravity = Gravity.CENTER
            background = roundedBg(colKeyWhite, 8)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            lp.setMargins(dp(3), dp(2), dp(3), dp(2))
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun createDarkKey(
        label: String,
        weight: Float = 1f,
        radiusDp: Int = 8,
        smallText: Boolean = false,
        onClick: () -> Unit
    ): View {
        return TextView(this).apply {
            text = label
            textSize = if (smallText) 12f else 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBg(colKeyDark, radiusDp)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            lp.setMargins(dp(3), dp(2), dp(3), dp(2))
            layoutParams = lp
            setOnClickListener { onClick() }
        }
    }

    private fun createBackspaceKey(weight: Float): View {
        return TextView(this).apply {
            text = "⌫"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBg(colKeyDark, 8)
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
            lp.setMargins(dp(3), dp(2), dp(3), dp(2))
            layoutParams = lp
            var repeatJob: Job? = null
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        deleteOneChar()
                        repeatJob = serviceScope.launch {
                            delay(350L)
                            while (isActive) {
                                deleteOneChar()
                                delay(55L)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        repeatJob?.cancel()
                        true
                    }
                    else -> false
                }
            }
        }
    }

    // ========================================================================
    // SYMBOLS KEYBOARD (?123)
    // ========================================================================
    private fun buildSymbolsKeyboard(parent: LinearLayout) {
        val rows = listOf(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/"),
            listOf("*", "\"", "'", ":", ";", "!", "?", "{", "}", "⌫"),
            listOf("ABC", ",", "(", ")", " ", ".", "🔍")
        )
        for (r in rows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(dp(4), dp(4), dp(4), dp(4))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (key in r) {
                when (key) {
                    "ABC" -> rowLayout.addView(createDarkKey("ABC", 1.5f, 20) { switchMode(ScreenMode.KEYBOARD) })
                    "⌫" -> rowLayout.addView(createBackspaceKey(1.3f))
                    "🔍" -> rowLayout.addView(createDarkKey("🔍", 1.5f, 20) { sendEnterOrSearch() })
                    " " -> rowLayout.addView(createStandardKey("50K Dev Space", 3.5f, true) { commitStr(" ") })
                    else -> rowLayout.addView(createStandardKey(key, 1f) { commitStr(key) })
                }
            }
            parent.addView(rowLayout)
        }
    }

    // ========================================================================
    // SCREENSHOT 1: CLIPBOARD SCREEN (2-COLUMN CARDS + 50,000 LINES PASTE)
    // ========================================================================
    private fun buildClipboardScreen(parent: LinearLayout) {
        clipEngine.syncSystemClipboard()

        // Top Header: ← Clipboard [Toggle] [Pencil]
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(6))
        }

        val backBtn = TextView(this).apply {
            text = "←"
            textSize = 22f
            setTextColor(colTextDark)
            setPadding(dp(8), dp(2), dp(16), dp(2))
            setOnClickListener { switchMode(ScreenMode.KEYBOARD) }
        }
        header.addView(backBtn)

        val titleTv = TextView(this).apply {
            text = "Clipboard"
            textSize = 20f
            setTextColor(colTextDark)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(titleTv)

        // Toggle Pill Switch (Matches Screenshot 1)
        val toggleBtn = TextView(this).apply {
            text = if (clipEngine.isEnabled) "●━" else "━○"
            textSize = 14f
            setTextColor(colTextDark)
            background = roundedBg(colKeyWhite, 20)
            setPadding(dp(14), dp(6), dp(14), dp(6))
            setOnClickListener {
                clipEngine.isEnabled = !clipEngine.isEnabled
                renderCurrentScreen()
            }
        }
        header.addView(toggleBtn)

        // Pencil Icon (Clears or syncs 50k clipboard)
        val editBtn = TextView(this).apply {
            text = "✎"
            textSize = 18f
            setTextColor(colTextDark)
            background = roundedBg(colKeyWhite, 20)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(8)
            layoutParams = lp
            setOnClickListener {
                Toast.makeText(this@GboardService, "Long press any card to clear history", Toast.LENGTH_SHORT).show()
            }
            setOnLongClickListener {
                clipEngine.clearAll()
                renderCurrentScreen()
                true
            }
        }
        header.addView(editBtn)
        parent.addView(header)

        // "Recent" label
        val recentLabel = TextView(this).apply {
            text = "Recent"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextMuted)
            setPadding(dp(16), dp(4), dp(16), dp(6))
        }
        parent.addView(recentLabel)

        // 2-Column Scrollable Grid of Clipboard Cards (Matches Screenshot 1)
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

        val clips = clipEngine.getRecentClips()
        clips.forEachIndexed { index, item ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = roundedBg(colKeyWhite, 14)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(dp(5), dp(5), dp(5), dp(5))
                layoutParams = lp
                setOnClickListener {
                    val full = clipEngine.readFullClip(item)
                    clipEngine.paste50K(currentInputConnection, full, serviceScope) {
                        lastCommittedText = it
                    }
                }
            }

            if (item.isLinkOrPkg) {
                val linkIcon = TextView(this).apply {
                    text = "⊶"
                    textSize = 14f
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(colTextDark)
                }
                card.addView(linkIcon)
            }

            val bodyTv = TextView(this).apply {
                text = item.preview
                textSize = 15f
                setTextColor(colTextDark)
                maxLines = 5
                ellipsize = TextUtils.TruncateAt.END
            }
            card.addView(bodyTv)

            if (item.lineCount > 1) {
                val badge = TextView(this).apply {
                    text = "${item.lineCount} lines (50K ready)"
                    textSize = 11f
                    setTextColor(colAccentMaroon)
                    setPadding(0, dp(4), 0, 0)
                }
                card.addView(badge)
            }

            if (index % 2 == 0) leftCol.addView(card) else rightCol.addView(card)
        }

        columnsContainer.addView(leftCol)
        columnsContainer.addView(rightCol)
        scroll.addView(columnsContainer)
        parent.addView(scroll)
    }

    // ========================================================================
    // SCREENSHOT 2 & 3: QUICK TOOLS PAGES 1 & 2
    // ========================================================================
    private fun buildQuickToolsScreen(parent: LinearLayout, page: Int) {
        // Top Strip: (X) Close, Clipboard, Translate, Settings, Mic
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = roundedBg(colKeyDark, 24)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { switchMode(ScreenMode.KEYBOARD) }
        }
        topBar.addView(closeBtn)

        fun topIcon(symbol: String, onClick: () -> Unit): View {
            return TextView(this).apply {
                text = symbol
                textSize = 18f
                setTextColor(colTextDark)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { onClick() }
            }
        }

        topBar.addView(topIcon("📋") { switchMode(ScreenMode.CLIPBOARD) })
        topBar.addView(topIcon("G文") {
            Toast.makeText(this, "Translate Active", Toast.LENGTH_SHORT).show()
        })
        topBar.addView(topIcon("⚙") {
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        })

        val micBtn = TextView(this).apply {
            text = "🎤"
            textSize = 16f
            gravity = Gravity.CENTER
            background = roundedBg(colKeyWhite, 24)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener { triggerVoiceInput() }
        }
        topBar.addView(micBtn)
        parent.addView(topBar)

        // 2-Column Pill Grid
        val gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        data class ToolOption(val icon: String, val title: String, val hasArrow: Boolean, val action: () -> Unit)

        val options = if (page == 1) {
            // Matches Screenshot 3
            listOf(
                ToolOption("🎨", "Theme", true) {
                    colBg = if (colBg == Color.parseColor("#F9ECEF")) Color.parseColor("#EFE5E8") else Color.parseColor("#F9ECEF")
                    renderCurrentScreen()
                },
                ToolOption("🖼", "Sticker", true) { switchMode(ScreenMode.EMOJI) },
                ToolOption("GIF", "GIF", true) { switchMode(ScreenMode.EMOJI) },
                ToolOption("😊", "Emoji", true) { switchMode(ScreenMode.EMOJI) },
                ToolOption("📱", "One-handed", false) {
                    isOneHanded = !isOneHanded
                    switchMode(ScreenMode.KEYBOARD)
                },
                ToolOption("◄I►", "Text", true) {
                    currentInputConnection?.performContextMenuAction(android.R.id.selectAll)
                }
            )
        } else {
            // Matches Screenshot 2
            listOf(
                ToolOption("🔗", "Share Gboard", false) {
                    commitStr("https://github.com/Gboard50K")
                    switchMode(ScreenMode.KEYBOARD)
                },
                ToolOption("⬚", "Resize", false) {
                    keyboardHeightDp = if (keyboardHeightDp == 285) 330 else 285
                    switchMode(ScreenMode.KEYBOARD)
                },
                ToolOption("⌨", "Floating", false) {
                    isFloating = !isFloating
                    switchMode(ScreenMode.KEYBOARD)
                },
                ToolOption("↶", "Undo", false) {
                    if (lastCommittedText.isNotEmpty()) {
                        currentInputConnection?.deleteSurroundingText(lastCommittedText.length, 0)
                        lastCommittedText = ""
                    }
                    switchMode(ScreenMode.KEYBOARD)
                },
                ToolOption("🌐", "Next language", false) {
                    isUrdu = !isUrdu
                    switchMode(ScreenMode.KEYBOARD)
                }
            )
        }

        for (i in options.indices step 2) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            for (j in 0..1) {
                if (i + j < options.size) {
                    val opt = options[i + j]
                    val pill = LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL
                        gravity = Gravity.CENTER_VERTICAL
                        background = roundedBg(colKeyWhite, 28)
                        setPadding(dp(16), dp(8), dp(16), dp(8))
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        lp.setMargins(dp(6), dp(5), dp(6), dp(5))
                        layoutParams = lp
                        setOnClickListener { opt.action() }
                    }
                    val iconTv = TextView(this).apply {
                        text = opt.icon
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(colTextDark)
                        setPadding(0, 0, dp(10), 0)
                    }
                    val labelTv = TextView(this).apply {
                        text = opt.title
                        textSize = 15f
                        setTextColor(colTextDark)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    pill.addView(iconTv)
                    pill.addView(labelTv)
                    if (opt.hasArrow) {
                        val arrowTv = TextView(this).apply {
                            text = "❯"
                            textSize = 13f
                            setTextColor(colTextMuted)
                        }
                        pill.addView(arrowTv)
                    }
                    row.addView(pill)
                } else {
                    val spacer = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                    }
                    row.addView(spacer)
                }
            }
            gridContainer.addView(row)
        }
        parent.addView(gridContainer)

        // Bottom Indicator Row: Page Dots (• •) + Oval Pencil Button
        val bottomRow = FrameLayout(this).apply {
            setPadding(dp(16), dp(4), dp(16), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val dotsTv = TextView(this).apply {
            text = if (page == 1) "●  ○" else "○  ●"
            textSize = 14f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            setOnClickListener {
                switchMode(if (page == 1) ScreenMode.TOOLS_PAGE_2 else ScreenMode.TOOLS_PAGE_1)
            }
        }
        bottomRow.addView(dotsTv)

        val editOval = TextView(this).apply {
            text = "✎"
            textSize = 16f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            background = roundedBg(Color.TRANSPARENT, 20, colKeyDark, 1)
            setPadding(dp(24), dp(6), dp(24), dp(6))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
            setOnClickListener {
                switchMode(if (page == 1) ScreenMode.TOOLS_PAGE_2 else ScreenMode.TOOLS_PAGE_1)
            }
        }
        bottomRow.addView(editOval)
        parent.addView(bottomRow)
    }

    // ========================================================================
    // SCREENSHOT 5: EMOJI SCREEN (SEARCH BAR, CATEGORIES, 8-COL GRID, FOOTER)
    // ========================================================================
    private fun buildEmojiScreen(parent: LinearLayout) {
        // Top Header: ← [🔍 Search] 🕒 (😊) 🏃 🐻 ☕ 🏙
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(6), dp(10), dp(4))
        }

        val backBtn = TextView(this).apply {
            text = "←"
            textSize = 20f
            setTextColor(colTextDark)
            setPadding(dp(6), dp(4), dp(10), dp(4))
            setOnClickListener { switchMode(ScreenMode.KEYBOARD) }
        }
        topBar.addView(backBtn)

        val searchPill = TextView(this).apply {
            text = "🔍 Search"
            textSize = 14f
            setTextColor(colTextDark)
            background = roundedBg(Color.parseColor("#EFE3E6"), 20)
            setPadding(dp(14), dp(6), dp(20), dp(6))
            setOnClickListener { switchMode(ScreenMode.KEYBOARD) }
        }
        topBar.addView(searchPill)

        val cats = listOf("🕒", "😊", "🏃", "🐻", "☕", "🏙")
        for (c in cats) {
            val catView = TextView(this).apply {
                text = c
                textSize = 14f
                gravity = Gravity.CENTER
                if (c == "😊") {
                    background = roundedBg(colAccentMaroon, 20)
                    setTextColor(Color.WHITE)
                }
                val lp = LinearLayout.LayoutParams(0, dp(34), 1f)
                lp.marginStart = dp(2)
                layoutParams = lp
            }
            topBar.addView(catView)
        }
        parent.addView(topBar)

        val subTitle = TextView(this).apply {
            text = "Smileys and emotions"
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextMuted)
            setPadding(dp(14), dp(2), dp(14), dp(4))
        }
        parent.addView(subTitle)

        // Exact Emojis from Screenshot 5 in 8-Column Grid
        val emojis = listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣",
            "😭", "😉", "😗", "😙", "😚", "😘", "🥰", "😍",
            "🤩", "🥳", "🫠", "🙃", "🙂", "🥲", "🥹", "😊",
            "☺️", "😌", "😔", "😴", "😏", "🤤", "😋", "😛",
            "😝", "😜", "🤪", "😒", "🥺", "😬", "😑", "😐",
            "🔥", "❤️", "👍", "🙏", "🎉", "💯", "🚀", "💻"
        )

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), 0, dp(6), dp(4))
        }

        for (i in emojis.indices step 8) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(38))
            }
            for (j in 0 until 8) {
                if (i + j < emojis.size) {
                    val em = emojis[i + j]
                    val tv = TextView(this).apply {
                        text = em
                        textSize = 22f
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        setOnClickListener { commitStr(em) }
                    }
                    row.addView(tv)
                }
            }
            grid.addView(row)
        }
        scroll.addView(grid)
        parent.addView(scroll)

        // Bottom Bar: ABC, (😊), GIF, Sticker, :-), Backspace
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
                background = roundedBg(if (selected) colAccentMaroon else Color.parseColor("#EFE3E6"), 20)
                val lp = LinearLayout.LayoutParams(0, dp(36), 1f)
                lp.setMargins(dp(4), 0, dp(4), 0)
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        footer.addView(footerChip("ABC") { switchMode(ScreenMode.KEYBOARD) })
        footer.addView(footerChip("😊", selected = true) {})
        footer.addView(footerChip("GIF") { commitStr("[GIF]") })
        footer.addView(footerChip("🖼") { commitStr("✨") })
        footer.addView(footerChip(":-)") { commitStr(":-)") })
        footer.addView(createBackspaceKey(1f))
        parent.addView(footer)
    }

    // ========================================================================
    // INPUT CONNECTION HELPERS
    // ========================================================================
    private fun commitStr(text: String) {
        currentInputConnection?.commitText(text, 1)
        lastCommittedText = text
        if (isShifted) {
            isShifted = false
            if (currentMode == ScreenMode.KEYBOARD) renderCurrentScreen()
        }
    }

    private fun deleteOneChar() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun sendEnterOrSearch() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (info != null && (info.imeOptions and EditorInfo.IME_MASK_ACTION) != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(info.imeOptions and EditorInfo.IME_MASK_ACTION)
        } else {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun triggerVoiceInput() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }
}

package com.gboard.copy50k

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.abs

// ============================================================================
// 1. सेटअप और 50,000 लाइन्स टेस्ट एक्टिविटी
// ============================================================================
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F9ECEF"))
            setPadding(44, 56, 44, 44)
        }

        val title = TextView(this).apply {
            text = "Gboard 50K Ultra (120FPS Canvas Engine)"
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1F1A1C"))
        }
        root.addView(title)

        val sub = TextView(this).apply {
            text = "Zero-Lag Multi-Touch • Key Popup Preview • Space Trackpad • 50,000 Lines RAM+Disk Engine"
            textSize = 13f
            setTextColor(Color.parseColor("#513E44"))
            setPadding(0, 8, 0, 28)
        }
        root.addView(sub)

        fun makeBtn(label: String, bgHex: String = "#513E44", onClick: () -> Unit): Button {
            return Button(this).apply {
                text = label
                isAllCaps = false
                setTextColor(Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = 28f
                }
                val lp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 18
                layoutParams = lp
                setOnClickListener { onClick() }
            }
        }

        root.addView(makeBtn("1. Enable 'Gboard 50K' in Settings") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        })

        root.addView(makeBtn("2. Select 'Gboard 50K' as Active Keyboard") {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        })

        root.addView(makeBtn("3. Copy 50,000 Lines Code to Test Clipboard", "#632B43") {
            val sb = StringBuilder(1_800_000)
            sb.append("package com.app.mediaplayer\n\nimport android.content.BroadcastReceiver\nimport android.content.Context\n\n")
            for (i in 6..50000) {
                sb.append("val fastLine_$i = \"Gboard 120FPS line $i\"\n")
            }
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("code50k", sb.toString()))
            Toast.makeText(this, "50,000 Lines Copied! Open Keyboard Clipboard.", Toast.LENGTH_SHORT).show()
        })

        val editor = EditText(this).apply {
            hint = "यहाँ दोनों अंगूठों से तेज़ टाइपिंग, की-पॉपअप, स्पेसबार ट्रैकपैड और 50K पेस्ट का असली मज़ा लें..."
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = 24f
            }
            setPadding(32, 32, 32, 32)
        }
        root.addView(
            editor,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        setContentView(root)
    }
}

// ============================================================================
// 2. ZERO-LAG RAM + ASYNC DISK 50,000 LINES CLIPBOARD ENGINE
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
    private val indexFile = File(context.filesDir, "clips_index_v3.json")
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

    // 0ms Instant RAM Read (No Main-Thread Disk Freeze!)
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
// 3. REAL GBOARD 120FPS SINGLE-CANVAS KEYBOARD VIEW (MULTI-TOUCH + KEY POPUP)
// ============================================================================
class GboardCanvasView(
    context: Context,
    private val service: GboardService
) : View(context) {

    enum class KeyKind {
        LETTER, SHIFT, BACKSPACE, SYMBOLS, EMOJI_COMMA, GLOBE, SPACE, DOT, ENTER
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

    private val rows = Array<MutableList<KeyCell>>(4) { mutableListOf() }
    private val allKeys = mutableListOf<KeyCell>()

    // Pre-allocated Paints for 0-allocation 120FPS onDraw
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

    private val shadowRect = RectF()
    private val popupRect = RectF()

    // Multi-touch pointer tracking & Gesture states
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

        if (service.isSymbolsMode) {
            val r0 = if (service.isNumbersPage2) listOf("~", "`", "|", "•", "√", "π", "÷", "×", "¶", "∆")
            else listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            val r1 = if (service.isNumbersPage2) listOf("£", "¢", "€", "¥", "^", "°", "=", "{", "}", "\\")
            else listOf("@", "#", "$", "_", "&", "-", "+", "(", ")", "/")
            val r2 = if (service.isNumbersPage2) listOf("?123", "%", "©", "®", "™", "✓", "[", "]")
            else listOf("=\\<", "*", "\"", "'", ":", ";", "!", "?")

            r0.forEach { rows[0].add(KeyCell(0, 1f, it)) }
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
            // Exact Screenshot 4 Layout
            val r0 = if (service.isUrdu) listOf("ط", "ص", "ھ", "د", "ٹ", "پ", "ت", "ب", "ج", "ح")
            else listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
            val nums = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
            for (i in r0.indices) {
                rows[0].add(KeyCell(0, 1f, r0[i], topNumber = nums[i], kind = KeyKind.LETTER))
            }

            val r1 = if (service.isUrdu) listOf("م", "ن", "ر", "ل", "ہ", "ا", "ک", "ی", "ق")
            else listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
            r1.forEach { rows[1].add(KeyCell(1, 1f, it, kind = KeyKind.LETTER)) }

            val r2 = if (service.isUrdu) listOf("ز", "خ", "ش", "غ", "ف", "ے", "س")
            else listOf("z", "x", "c", "v", "b", "n", "m")
            rows[2].add(KeyCell(2, 1.45f, "⇧", kind = KeyKind.SHIFT))
            r2.forEach { rows[2].add(KeyCell(2, 1f, it, kind = KeyKind.LETTER)) }
            rows[2].add(KeyCell(2, 1.45f, "⌫", kind = KeyKind.BACKSPACE))

            rows[3].add(KeyCell(3, 1.35f, "?123", kind = KeyKind.SYMBOLS))
            rows[3].add(KeyCell(3, 0.9f, "☺,", kind = KeyKind.EMOJI_COMMA))
            rows[3].add(KeyCell(3, 0.9f, "🌐", kind = KeyKind.GLOBE))
            rows[3].add(KeyCell(3, 3.8f, if (service.isUrdu) "اردو" else "English", kind = KeyKind.SPACE))
            rows[3].add(KeyCell(3, 0.9f, ".", kind = KeyKind.DOT))
            rows[3].add(KeyCell(3, 1.35f, "🔍", kind = KeyKind.ENTER))
        }

        for (r in rows) allKeys.addAll(r)
        computeKeyBounds(width, height)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeKeyBounds(w, h)
    }

    private fun computeKeyBounds(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val padH = dpF(3f)
        val padV = dpF(3.5f)
        val topMargin = dpF(2f)
        val bottomMargin = dpF(6f)
        val usableH = h - topMargin - bottomMargin
        val rowH = usableH / 4f

        for (rIdx in 0..3) {
            val rowList = rows[rIdx]
            if (rowList.isEmpty()) continue
            val sideInset = if (rIdx == 1 && !service.isSymbolsMode) dpF(18f) else dpF(4f)
            val rowWidth = w - (sideInset * 2f)
            val totalWeight = rowList.sumOf { it.weight.toDouble() }.toFloat()
            var curX = sideInset
            val topY = topMargin + rIdx * rowH

            for (key in rowList) {
                val kw = (key.weight / totalWeight) * rowWidth
                key.rect.set(
                    curX + padH,
                    topY + padV,
                    curX + kw - padH,
                    topY + rowH - padV
                )
                curX += kw
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        keyWhitePaint.color = service.colKeyWhite
        keyPressedPaint.color = Color.parseColor("#E4D4D9")
        keyDarkPaint.color = service.colKeyDark
        keyAccentPaint.color = service.colAccentMaroon
        shadowPaint.color = Color.parseColor("#22000000")
        popupBgPaint.color = service.colKeyWhite
        popupTextPaint.color = service.colTextDark

        val cornerStd = dpF(8f)
        val cornerPill = dpF(22f)
        val shadowOffset = dpF(1.3f)
        val upper = service.isShifted || service.isCapsLocked

        for (key in allKeys) {
            val isPill = key.kind == KeyKind.SYMBOLS || key.kind == KeyKind.ENTER
            val rad = if (isPill) cornerPill else cornerStd

            // Gboard subtle bottom key shadow
            shadowRect.set(key.rect.left, key.rect.top + shadowOffset, key.rect.right, key.rect.bottom + shadowOffset)
            canvas.drawRoundRect(shadowRect, rad, rad, shadowPaint)

            // Key surface color
            val bgPaint = when {
                key.kind == KeyKind.ENTER -> if (key.isPressed) keyAccentPaint else keyDarkPaint
                key.kind == KeyKind.SHIFT && service.isCapsLocked -> keyAccentPaint
                key.kind == KeyKind.SHIFT || key.kind == KeyKind.BACKSPACE ||
                    key.kind == KeyKind.SYMBOLS || key.kind == KeyKind.EMOJI_COMMA || key.kind == KeyKind.DOT ->
                    if (key.isPressed) keyAccentPaint else keyDarkPaint
                else -> if (key.isPressed) keyPressedPaint else keyWhitePaint
            }
            canvas.drawRoundRect(key.rect, rad, rad, bgPaint)

            // Key label rendering
            val cx = key.rect.centerX()
            val cy = key.rect.centerY()

            when (key.kind) {
                KeyKind.EMOJI_COMMA -> {
                    textMainPaint.color = Color.WHITE
                    textMainPaint.textSize = spF(13f)
                    canvas.drawText("☺", cx, cy - dpF(2f), textMainPaint)
                    canvas.drawText(",", cx, cy + dpF(11f), textMainPaint)
                }
                KeyKind.SPACE -> {
                    textMainPaint.color = service.colTextMuted
                    textMainPaint.textSize = spF(14f)
                    val label = if (isSpaceTrackpadActive) "◄   Space Trackpad   ►" else key.rawLabel
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(label, cx, textY, textMainPaint)
                }
                KeyKind.SHIFT -> {
                    textMainPaint.color = Color.WHITE
                    textMainPaint.textSize = spF(18f)
                    val sym = if ( service.isSymbolsMode) key.rawLabel else when {
                        service.isCapsLocked -> "⇪"
                        service.isShifted -> "⬆"
                        else -> "⇧"
                    }
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(sym, cx, textY, textMainPaint)
                }
                KeyKind.BACKSPACE, KeyKind.SYMBOLS, KeyKind.DOT, KeyKind.ENTER -> {
                    textMainPaint.color = Color.WHITE
                    textMainPaint.textSize = if (key.rawLabel.length > 2) spF(14f) else spF(18f)
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(key.rawLabel, cx, textY, textMainPaint)
                }
                else -> {
                    val outText = if (key.kind == KeyKind.LETTER && upper) key.rawLabel.uppercase() else key.rawLabel
                    textMainPaint.color = service.colTextDark
                    textMainPaint.textSize = if (outText.length > 1) spF(15f) else spF(21f)
                    val textY = cy - (textMainPaint.descent() + textMainPaint.ascent()) / 2f
                    canvas.drawText(outText, cx, textY, textMainPaint)

                    if (key.topNumber.isNotEmpty()) {
                        textSubPaint.color = service.colTextMuted
                        textSubPaint.textSize = spF(10f)
                        canvas.drawText(
                            key.topNumber,
                            key.rect.right - dpF(5f),
                            key.rect.top + dpF(11f),
                            textSubPaint
                        )
                    }
                }
            }
        }

        // Draw Floating Key Popup Preview Balloon (Just like real Gboard!)
        val popupKey = activePopupKey
        if (popupKey != null && popupKey.kind == KeyKind.LETTER) {
            val popW = popupKey.rect.width() * 1.28f
            val popH = popupKey.rect.height() * 1.35f
            val popLeft = (popupKey.rect.centerX() - popW / 2f).coerceIn(dpF(4f), width - popW - dpF(4f))
            val popBottom = (popupKey.rect.top - dpF(4f)).coerceAtLeast(popH + dpF(2f))
            val popTop = popBottom - popH

            popupRect.set(popLeft, popTop, popLeft + popW, popBottom)
            shadowRect.set(popLeft, popTop + dpF(2f), popLeft + popW, popBottom + dpF(3f))
            canvas.drawRoundRect(shadowRect, dpF(12f), dpF(12f), shadowPaint)
            canvas.drawRoundRect(popupRect, dpF(12f), dpF(12f), popupBgPaint)

            val upper = service.isShifted || service.isCapsLocked
            val popLabel = if (upper) popupKey.rawLabel.uppercase() else popupKey.rawLabel
            popupTextPaint.textSize = spF(28f)
            val py = popupRect.centerY() - (popupTextPaint.descent() + popupTextPaint.ascent()) / 2f
            canvas.drawText(popLabel, popupRect.centerX(), py, popupTextPaint)
        }
    }

    private fun findNearestKey(x: Float, y: Float): KeyCell? {
        for (k in allKeys) {
            if (k.rect.contains(x, y)) return k
        }
        // Forgiving touch hit-box: find closest key center within 28dp
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
                // Multi-Touch Chording: If another finger is already down on a letter, commit it immediately!
                if (action == MotionEvent.ACTION_POINTER_DOWN && pointerKeyMap.isNotEmpty()) {
                    val existingEntries = pointerKeyMap.entries.toList()
                    for ((oldPid, oldKey) in existingEntries) {
                        if (oldKey.kind == KeyKind.LETTER) {
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
                activePopupKey = if (hit.kind == KeyKind.LETTER) hit else null
                service.vibrateAndClick(this)
                invalidate()

                when (hit.kind) {
                    KeyKind.BACKSPACE -> {
                        backspaceDownX = x
                        service.deleteOneChar()
                        backspaceJob?.cancel()
                        backspaceJob = service.serviceScope.launch {
                            delay(300L)
                            var interval = 55L
                            while (isActive) {
                                service.deleteOneChar()
                                delay(interval)
                                if (interval > 20L) interval -= 4L
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
                                delay(310L)
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
                        if (backspaceDownX - px > dpF(38f)) {
                            backspaceJob?.cancel()
                            service.deleteWholeWordBeforeCursor()
                            service.vibrateTick(this)
                            backspaceDownX = px
                        }
                        continue
                    }

                    val newHit = findNearestKey(px, py)
                    if (newHit != null && newHit != currentKey && newHit.kind == KeyKind.LETTER) {
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
                    service.updateLanguageChipText()
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
// 4. MAIN INPUT METHOD SERVICE (ZERO-REBUILD SUGGESTION BAR + ALL PANELS)
// ============================================================================
class GboardService : InputMethodService() {

    val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var clipEngine: ClipboardEngine50K
    private lateinit var rootContainer: FrameLayout

    // Pre-built persistent Keyboard Screen (Never recreated on key press!)
    private var mainKeyboardWrapper: LinearLayout? = null
    private var canvasKeyboardView: GboardCanvasView? = null
    private var idleStripRow: LinearLayout? = null
    private var liveSuggestionRow: LinearLayout? = null
    private val suggestionTextViews = arrayOfNulls<TextView>(3)
    private var urduChipView: TextView? = null

    private var vibrator: Vibrator? = null
    private var audioManager: AudioManager? = null

    enum class ScreenMode {
        KEYBOARD, CLIPBOARD, TOOLS_PAGE_1, TOOLS_PAGE_2, TEXT_EDITING, EMOJI, TRANSLATE, RESIZE
    }

    var currentMode = ScreenMode.KEYBOARD
    var isShifted = true // Auto-cap at start
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
    private var keyboardHeightDp = 290

    private var activeEmojiCat = 1
    private var emojiSearchQuery = ""
    private val recentEmojis = mutableListOf("😂", "❤️", "🔥", "👍", "😭", "🙏", "😍", "🎉")

    private val currentWord = StringBuilder()
    private val undoStack = ArrayDeque<String>()

    var themeIndex = 0
    private val themes = listOf(
        // 0: Exact Screenshot Rose/Pink Material You
        intArrayOf(Color.parseColor("#F9ECEF"), Color.parseColor("#FFFFFF"), Color.parseColor("#513E44"), Color.parseColor("#1F1A1C"), Color.parseColor("#6E5E63"), Color.parseColor("#632B43")),
        // 1: Gboard Material Dark
        intArrayOf(Color.parseColor("#1B1B1F"), Color.parseColor("#2F3036"), Color.parseColor("#44474F"), Color.parseColor("#E3E2E6"), Color.parseColor("#90909A"), Color.parseColor("#A8C7FA")),
        // 2: Samsung OneUI Light Breeze
        intArrayOf(Color.parseColor("#ECEFF1"), Color.parseColor("#FFFFFF"), Color.parseColor("#455A64"), Color.parseColor("#202124"), Color.parseColor("#5F6368"), Color.parseColor("#1A73E8"))
    )

    val colBg get() = themes[themeIndex][0]
    val colKeyWhite get() = themes[themeIndex][1]
    val colKeyDark get() = themes[themeIndex][2]
    val colTextDark get() = themes[themeIndex][3]
    val colTextMuted get() = themes[themeIndex][4]
    val colAccentMaroon get() = themes[themeIndex][5]

    private val dictionaryWords = listOf(
        "package", "import", "android", "fun", "val", "var", "class", "override", "return",
        "context", "BroadcastReceiver", "mediaplayer", "the", "and", "you", "that", "for",
        "with", "have", "this", "from", "bhai", "kya", "hai", "acha", "theek", "code", "github",
        "gboard", "keyboard", "smooth", "performance", "developer"
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

    // Ultra-fast 0ms Haptic + Sound
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

    // Builds the main keyboard ONCE and keeps it cached in RAM for 0ms switching
    private fun buildPersistentKeyboardView() {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(colBg)
        }

        // Top Bar Container (Fixed height 44dp, no removeAllViews during typing!)
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
        }

        // Left 4-Dot Grid Button (⊞)
        val gridIcon = TextView(this).apply {
            text = "⊞"
            textSize = 21f
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            background = keyRippleBg(Color.TRANSPARENT, 20)
            setPadding(dp(10), dp(2), dp(10), dp(2))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.TOOLS_PAGE_1)
            }
        }
        topBar.addView(gridIcon)

        // Middle Frame holds BOTH Idle Strip (Add | اردو | •••) and 3 Fixed Suggestion Slots
        val middleFrame = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }

        // 1. Idle Strip (Screenshot 4)
        val idleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val addLabel = TextView(this).apply {
            text = "Add"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            background = keyRippleBg(Color.TRANSPARENT, 16)
            setPadding(dp(10), dp(4), dp(12), dp(4))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.CLIPBOARD)
            }
        }
        idleRow.addView(addLabel)

        val uChip = TextView(this).apply {
            text = if (isUrdu) "⌨  English" else "⌨  اردو"
            textSize = 14f
            setTextColor(colTextDark)
            background = keyRippleBg(colKeyWhite, 20)
            elevation = dp(1).toFloat()
            setPadding(dp(14), dp(4), dp(14), dp(4))
            setOnClickListener {
                vibrateAndClick(this)
                isUrdu = !isUrdu
                updateLanguageChipText()
                canvasKeyboardView?.rebuildKeyDefinitions()
            }
        }
        urduChipView = uChip
        idleRow.addView(uChip)

        val dotsBtn = TextView(this).apply {
            text = "•••"
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            background = keyRippleBg(colKeyWhite, 20)
            elevation = dp(1).toFloat()
            setPadding(dp(12), dp(4), dp(12), dp(4))
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginStart = dp(8)
            layoutParams = lp
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.TOOLS_PAGE_2)
            }
        }
        idleRow.addView(dotsBtn)
        idleStripRow = idleRow
        middleFrame.addView(idleRow)

        // 2. Live 3-Slot Suggestion Row (Pre-allocated, Zero Lag!)
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

        // Right Voice Mic Icon
        val micBtn = TextView(this).apply {
            text = "🎤"
            textSize = 15f
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyWhite, 20)
            elevation = dp(1).toFloat()
            setPadding(dp(10), dp(4), dp(10), dp(4))
            setOnClickListener {
                vibrateAndClick(this)
                triggerVoiceInput()
            }
        }
        topBar.addView(micBtn)
        wrapper.addView(topBar)

        // 120FPS Custom Canvas Key Grid
        val canvasView = GboardCanvasView(this, this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        canvasKeyboardView = canvasView
        wrapper.addView(canvasView)

        mainKeyboardWrapper = wrapper
    }

    fun updateLanguageChipText() {
        urduChipView?.text = if (isUrdu) "⌨  English" else "⌨  اردو"
    }

    // Updates suggestion text in <0.1ms without creating a single View object!
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
        currentMode = mode
        if (!::rootContainer.isInitialized) return
        rootContainer.removeAllViews()

        val width = if (isOneHanded) (resources.displayMetrics.widthPixels * 0.84).toInt() else ViewGroup.LayoutParams.MATCH_PARENT
        val lp = FrameLayout.LayoutParams(width, dp(keyboardHeightDp)).apply {
            gravity = if (isOneHanded) Gravity.END else Gravity.CENTER_HORIZONTAL
            if (isFloating) setMargins(dp(14), dp(6), dp(14), dp(22))
        }

        if (mode == ScreenMode.KEYBOARD) {
            if (mainKeyboardWrapper == null) buildPersistentKeyboardView()
            val kb = mainKeyboardWrapper!!
            (kb.parent as? ViewGroup)?.removeView(kb)
            kb.setBackgroundColor(colBg)
            kb.layoutParams = lp
            updateLanguageChipText()
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
            ScreenMode.TEXT_EDITING -> buildTextEditingScreen(panelWrapper)
            ScreenMode.EMOJI -> buildEmojiScreen(panelWrapper)
            ScreenMode.TRANSLATE -> buildTranslateScreen(panelWrapper)
            ScreenMode.RESIZE -> buildResizeScreen(panelWrapper)
            else -> {}
        }
        rootContainer.addView(panelWrapper)
    }

    // ========================================================================
    // SCREENSHOT 1: CLIPBOARD PANEL (INSTANT RAM READ + 50,000 LINES PASTE)
    // ========================================================================
    private fun buildClipboardScreen(parent: LinearLayout) {
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(4))
        }

        val backBtn = TextView(this).apply {
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
        }
        header.addView(backBtn)

        val statusTitle = TextView(this).apply {
            text = if (isClipEditMode) "Manage Clips" else "Clipboard"
            textSize = 19f
            setTextColor(colTextDark)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(statusTitle)

        val toggleBtn = TextView(this).apply {
            text = if (clipEngine.isEnabled) "●━" else "━○"
            textSize = 14f
            setTextColor(colTextDark)
            background = keyRippleBg(colKeyWhite, 20)
            elevation = dp(1).toFloat()
            setPadding(dp(14), dp(5), dp(14), dp(5))
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
            lp.marginStart = dp(8)
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
    // SCREENSHOT 2 & 3: QUICK TOOLS PAGES 1 & 2
    // ========================================================================
    private fun buildQuickToolsScreen(parent: LinearLayout, page: Int) {
        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(6))
        }

        topBar.addView(TextView(this).apply {
            text = "✕"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyDark, 24)
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(ScreenMode.KEYBOARD)
            }
        })

        fun topIcon(symbol: String, onClick: () -> Unit): View {
            return TextView(this).apply {
                text = symbol
                textSize = 18f
                setTextColor(colTextDark)
                gravity = Gravity.CENTER
                background = keyRippleBg(Color.TRANSPARENT, 20)
                layoutParams = LinearLayout.LayoutParams(0, dp(40), 1f)
                setOnClickListener {
                    vibrateAndClick(this)
                    onClick()
                }
            }
        }

        topBar.addView(topIcon("📋") { openPanel(ScreenMode.CLIPBOARD) })
        topBar.addView(topIcon("G文") { openPanel(ScreenMode.TRANSLATE) })
        topBar.addView(topIcon("⚙") {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })

        topBar.addView(TextView(this).apply {
            text = "🎤"
            textSize = 16f
            gravity = Gravity.CENTER
            background = keyRippleBg(colKeyWhite, 24)
            elevation = dp(1).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            setOnClickListener {
                vibrateAndClick(this)
                triggerVoiceInput()
            }
        })
        parent.addView(topBar)

        val gridContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }

        data class ToolOption(val icon: String, val title: String, val hasArrow: Boolean, val action: () -> Unit)

        val options = if (page == 1) {
            listOf(
                ToolOption("🎨", "Theme", true) {
                    themeIndex = (themeIndex + 1) % themes.size
                    openPanel(ScreenMode.TOOLS_PAGE_1)
                },
                ToolOption("🖼", "Sticker", true) {
                    activeEmojiCat = 3
                    openPanel(ScreenMode.EMOJI)
                },
                ToolOption("GIF", "GIF", true) {
                    activeEmojiCat = 5
                    openPanel(ScreenMode.EMOJI)
                },
                ToolOption("😊", "Emoji", true) {
                    activeEmojiCat = 1
                    openPanel(ScreenMode.EMOJI)
                },
                ToolOption("📱", "One-handed", false) {
                    isOneHanded = !isOneHanded
                    openPanel(ScreenMode.KEYBOARD)
                },
                ToolOption("◄I►", "Text", true) {
                    openPanel(ScreenMode.TEXT_EDITING)
                }
            )
        } else {
            listOf(
                ToolOption("🔗", "Share Gboard", false) {
                    commitStr("https://github.com/Gboard50K")
                    openPanel(ScreenMode.KEYBOARD)
                },
                ToolOption("⬚", "Resize", false) {
                    openPanel(ScreenMode.RESIZE)
                },
                ToolOption("⌨", "Floating", false) {
                    isFloating = !isFloating
                    openPanel(ScreenMode.KEYBOARD)
                },
                ToolOption("↶", "Undo", false) {
                    performUndo()
                    openPanel(ScreenMode.KEYBOARD)
                },
                ToolOption("🌐", "Next language", false) {
                    isUrdu = !isUrdu
                    openPanel(ScreenMode.KEYBOARD)
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
                        background = keyRippleBg(colKeyWhite, 28)
                        elevation = dp(1).toFloat()
                        setPadding(dp(16), dp(8), dp(16), dp(8))
                        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                        lp.setMargins(dp(6), dp(5), dp(6), dp(5))
                        layoutParams = lp
                        setOnClickListener {
                            vibrateAndClick(this)
                            opt.action()
                        }
                    }
                    pill.addView(TextView(this).apply {
                        text = opt.icon
                        textSize = 15f
                        setTypeface(null, Typeface.BOLD)
                        setTextColor(colTextDark)
                        setPadding(0, 0, dp(10), 0)
                    })
                    pill.addView(TextView(this).apply {
                        text = opt.title
                        textSize = 15f
                        setTextColor(colTextDark)
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    if (opt.hasArrow) {
                        pill.addView(TextView(this).apply {
                            text = "❯"
                            textSize = 13f
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
            setPadding(dp(24), dp(5), dp(24), dp(5))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
            setOnClickListener {
                vibrateAndClick(this)
                openPanel(if (page == 1) ScreenMode.TOOLS_PAGE_2 else ScreenMode.TOOLS_PAGE_1)
            }
        })
        parent.addView(bottomRow)
    }

    // ========================================================================
    // REAL GBOARD TEXT EDITING D-PAD PANEL (◄I► Text)
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
            text = "Text editing"
            textSize = 18f
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
                                    delay(280L)
                                    while (isActive) {
                                        moveCursorWithSelection(keyCode)
                                        delay(45L)
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
        dRow3.addView(dpadCell("All") {
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
                textSize = 14f
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
            text = "G文  Quick Translate (EN ⇄ HI/UR)"
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
        })
        parent.addView(header)

        val phrases = listOf(
            "Hello brother, how are you?" to "भाई कैसे हो आप? / بھائی کیسے ہو؟",
            "Now keyboard runs at 120FPS smooth!" to "अब कीबोर्ड 120FPS पर मक्खन जैसा चल रहा है!",
            "50,000 lines clipboard is ready" to "50,000 लाइन्स क्लिपबोर्ड तैयार है",
            "Thank you very much" to "बहुत-बहुत धन्यवाद / شکریہ"
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
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(colTextDark)
            })
            list.addView(card)
        }
        parent.addView(list)
    }

    private fun buildResizeScreen(parent: LinearLayout) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(16), dp(24), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        box.addView(TextView(this).apply {
            text = "⬚  Keyboard Height (${keyboardHeightDp}dp)"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(colTextDark)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        })

        val sizes = listOf("Compact" to 250, "Normal" to 290, "Tall" to 330, "Max" to 365)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        for ((name, h) in sizes) {
            row.addView(TextView(this).apply {
                text = name
                textSize = 14f
                setTextColor(if (keyboardHeightDp == h) Color.WHITE else colTextDark)
                background = keyRippleBg(if (keyboardHeightDp == h) colAccentMaroon else colKeyWhite, 20)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.setMargins(dp(5), 0, dp(5), 0)
                layoutParams = lp
                setOnClickListener {
                    vibrateAndClick(this)
                    keyboardHeightDp = h
                    openPanel(ScreenMode.RESIZE)
                }
            })
        }
        box.addView(row)

        box.addView(TextView(this).apply {
            text = "✓  Apply & Return"
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
            background = keyRippleBg(Color.parseColor("#EFE3E6"), 20)
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
                background = keyRippleBg(if (selected) colAccentMaroon else Color.parseColor("#EFE3E6"), 20)
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
    // FAST INPUT CONNECTION & SMART GBOARD TYPING HELPERS
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
        // Double-Space Period like Gboard (if previous char was a letter/digit followed by space)
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

    private fun triggerVoiceInput() {
        try {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        } catch (_: Exception) {}
    }
}

package com.xnlu.alutube

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File

class RecordEditActivity : AppCompatActivity() {
    private lateinit var cat: CategoryConfig.Cat
    private var id: Long = -1
    private val editors = mutableMapOf<String, TextInputEditText>()
    private lateinit var calcPreview: TextView
    private lateinit var thumbsRow: LinearLayout
    /** 缩略图解码线程池：照片多时不再一图一线程 */
    private val photoExecutor = java.util.concurrent.Executors.newFixedThreadPool(2)

    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && id > 0 && PhotoStore.add(this, id, uri)) {
            android.widget.Toast.makeText(this, "照片已添加", android.widget.Toast.LENGTH_SHORT).show()
            refreshThumbs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataDb.init(this)
        cat = CategoryConfig.byKey(intent.getStringExtra("category")!!)
        id = intent.getLongExtra("id", -1L)

        val scroll = android.widget.ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(96))
        }

        // 逐字段生成输入框
        for (field in cat.fields) {
            val layout = TextInputLayout(this).apply {
                hint = field
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, dp(10)) }
            }
            val et = TextInputEditText(this).apply {
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                isSingleLine = false
            }
            layout.addView(et)
            editors[field] = et
            root.addView(layout)
        }

        // 备尺/飞剪长度自动计算（客户产品参数）
        if (cat.lengthCalc) {
            calcPreview = TextView(this).apply {
                textSize = 15f
                setPadding(dp(4), dp(8), dp(4), dp(8))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(6), 0, dp(6)) }
            }
            root.addView(calcPreview)

            val watcher = object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) { updateCalcPreview() }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            }
            editors["成品长度(mm)"]?.addTextChangedListener(watcher)
            editors["裁切刀数"]?.addTextChangedListener(watcher)
        }

        // 现场照片（已有记录）
        if (id > 0) {
            root.addView(TextView(this).apply {
                text = "现场照片"
                textSize = 16f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setPadding(dp(4), dp(8), dp(4), 0)
            })
            val hs = android.widget.HorizontalScrollView(this)
            thumbsRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
            }
            hs.addView(thumbsRow)
            root.addView(hs)
            root.addView(com.google.android.material.button.MaterialButton(this).apply {
                text = "＋ 添加照片（相册选择）"
                setOnClickListener { pickImage.launch("image/*") }
            })
            refreshThumbs()
        } else {
            root.addView(TextView(this).apply {
                text = "提示：先保存这条记录，再进来即可添加现场照片"
                textSize = 13f
                setPadding(dp(4), dp(8), dp(4), 0)
            })
        }

        // 保存按钮
        val save = com.google.android.material.button.MaterialButton(this).apply {
            text = "保存"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(12), 0, 0) }
            setOnClickListener { save() }
        }
        root.addView(save)

        // 已有记录：显示删除按钮
        if (id > 0) {
            val del = com.google.android.material.button.MaterialButton(this).apply {
                text = "删除此条"
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(8), 0, 0) }
                setOnClickListener { confirmDelete() }
            }
            root.addView(del)
        }

        scroll.addView(root)
        setContentView(scroll)
        supportActionBar?.title = if (id > 0) "编辑 · ${cat.title}" else "新增 · ${cat.title}"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (id > 0) {
            DataDb.get(id)?.let { (_, map) ->
                for ((f, et) in editors) et.setText(map[f] ?: "")
            }
        }
        updateCalcPreview()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onDestroy() {
        photoExecutor.shutdownNow()
        super.onDestroy()
    }

    /** 备尺/飞剪长度实时预览；刀数留空时自动推荐 */
    private fun updateCalcPreview() {
        if (!cat.lengthCalc || !this::calcPreview.isInitialized) return
        val finished = editors["成品长度(mm)"]?.text?.toString()?.toDoubleOrNull()
        if (finished == null || finished <= 0) {
            calcPreview.text = "填写成品长度后，自动计算备尺和飞剪长度\n（备尺尽量在 ${LengthCalc.fmt(LengthCalc.TARGET_LOW)}-${LengthCalc.fmt(LengthCalc.TARGET_HIGH)} 之间）"
            calcPreview.setTextColor(0xFF888888.toInt())
            return
        }
        val cutsText = editors["裁切刀数"]?.text?.toString()?.trim()
        val autoCuts = LengthCalc.recommendCuts(finished)
        val cuts = cutsText?.toIntOrNull() ?: autoCuts
        val stock = LengthCalc.stockLength(finished, cuts)
        val inRange = LengthCalc.isStockInRange(stock)
        val sb = StringBuilder()
        sb.append(if (cutsText.isNullOrBlank()) "刀数（自动推荐）: $cuts\n" else "刀数: $cuts（自动推荐 $autoCuts）\n")
        sb.append("备尺: ${LengthCalc.fmt(stock)} mm")
        if (!inRange) sb.append("  ⚠ 不在 ${LengthCalc.fmt(LengthCalc.TARGET_LOW)}-${LengthCalc.fmt(LengthCalc.TARGET_HIGH)} 范围内")
        sb.append("\n飞剪长度: ${LengthCalc.fmt(LengthCalc.shearLow(stock))} - ${LengthCalc.fmt(LengthCalc.shearHigh(stock))} mm")
        calcPreview.text = sb.toString()
        calcPreview.setTextColor(if (inRange) 0xFF1565C0.toInt() else 0xFFC62828.toInt())
    }

    /** 刷新照片缩略图 */
    private fun refreshThumbs() {
        if (!this::thumbsRow.isInitialized) return
        thumbsRow.removeAllViews()
        val files = PhotoStore.list(this, id)
        if (files.isEmpty()) {
            thumbsRow.addView(TextView(this).apply {
                text = "暂无照片"
                textSize = 13f
                setPadding(0, dp(24), 0, dp(24))
            })
            return
        }
        for (f in files) {
            val iv = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(90), dp(90)).apply { setMargins(0, 0, dp(8), 0) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(0xFFE0E0E0.toInt())
            }
            photoExecutor.execute {
                val bmp = PhotoStore.decodeSampled(f, 256)
                if (bmp != null && !isFinishing && !isDestroyed) iv.post { iv.setImageBitmap(bmp) }
            }
            iv.setOnClickListener { showPhotoDialog(f) }
            iv.setOnLongClickListener { confirmDeletePhoto(f); true }
            thumbsRow.addView(iv)
        }
    }

    /** 全屏查看照片，可删除 */
    private fun showPhotoDialog(f: File) {
        val bmp = PhotoStore.decodeSampled(f, maxOf(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels))
        val iv = ImageView(this).apply {
            setImageBitmap(bmp)
            adjustViewBounds = true
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("照片")
            .setView(iv)
            .setPositiveButton("删除") { _, _ -> confirmDeletePhoto(f) }
            .setNegativeButton("关闭", null)
            .show()
    }

    private fun confirmDeletePhoto(f: File) {
        AlertDialog.Builder(this)
            .setTitle("删除照片")
            .setMessage("确定删除这张照片吗？")
            .setPositiveButton("删除") { _, _ ->
                PhotoStore.delete(f)
                refreshThumbs()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun save() {
        // 刀数留空时按推荐值保存
        if (cat.lengthCalc) {
            val finished = editors["成品长度(mm)"]?.text?.toString()?.toDoubleOrNull()
            val cutsEt = editors["裁切刀数"]
            if (finished != null && finished > 0 && cutsEt != null && cutsEt.text.isNullOrBlank()) {
                cutsEt.setText(LengthCalc.recommendCuts(finished).toString())
            }
        }
        val data = mutableMapOf<String, String>()
        for ((f, et) in editors) data[f] = et.text?.toString()?.trim() ?: ""
        val main = data[cat.titleField]
        if (main.isNullOrBlank()) {
            android.widget.Toast.makeText(this, "请填写「${cat.titleField}」", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        if (id > 0) DataDb.update(id, data) else DataDb.insert(cat.key, data)
        android.widget.Toast.makeText(this, "已保存", android.widget.Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("删除确认")
            .setMessage("确定删除这条记录吗？删除后不可恢复。")
            .setPositiveButton("删除") { _, _ ->
                DataDb.delete(id)
                android.widget.Toast.makeText(this, "已删除", android.widget.Toast.LENGTH_SHORT).show()
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

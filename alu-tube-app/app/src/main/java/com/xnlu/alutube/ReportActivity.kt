package com.xnlu.alutube

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 生产报表（按日期保存一份）
 *
 * 自动计算规则（2026-10-23 与用户确认）：
 *   当日累计合格品 = 合格品① + ② + ③ + ④
 *   废品总数 = 调试 + 穿料 + 爆破 + 边弯 + 麻点 + 涡流报警
 *   实际耗用材料 = 当日累计合格品 + 损耗 + 废品总数
 *   本班结存材料 = 上班转入 + 当班领用量 - 退库量 - 实际耗用
 */
class ReportActivity : AppCompatActivity() {

    private val inputs = mutableMapOf<String, TextInputEditText>()
    private lateinit var qualTotalView: TextView
    private lateinit var scrapTotalView: TextView
    private lateinit var recvTotalView: TextView
    private lateinit var actualUseView: TextView
    private lateinit var balanceView: TextView
    private lateinit var hourlyView: TextView
    private lateinit var dateText: TextView
    private var currentDate: String = ""

    private val qualFields = listOf("合格品①", "合格品②", "合格品③", "合格品④")
    private val scrapFields = listOf("调试", "穿料", "爆破", "边弯", "麻点", "涡流报警")
    private val recvFields = listOf("当班领用①", "当班领用②", "当班领用③")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataDb.init(this)

        currentDate = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Calendar.getInstance().time)

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(64))
        }
        scroll.addView(root)
        setContentView(scroll)
        supportActionBar?.title = "生产报表"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        // 日期选择行
        val dateRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
        val prev = MaterialButton(this).apply {
            text = "‹"
            setOnClickListener { shiftDate(-1) }
        }
        val next = MaterialButton(this).apply {
            text = "›"
            setOnClickListener { shiftDate(1) }
        }
        dateText = TextView(this).apply {
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener { pickDate() }
        }
        dateRow.addView(prev)
        dateRow.addView(dateText)
        dateRow.addView(next)
        dateRow.addView(TextView(this).apply {
            text = "（点日期可选）"
            textSize = 12f
            setPadding(dp(8), 0, 0, 0)
        })
        root.addView(dateRow)

        // ===== 一、原材料 =====
        root.addView(sectionTitle("一、原材料"))
        root.addView(numField("上班转入"))
        root.addView(numField("当班领用①"))
        root.addView(numField("当班领用②"))
        root.addView(numField("当班领用③"))
        root.addView(autoRow("当班领用合计（自动）", false).let { recvTotalView = it.second; it.first })
        root.addView(numField("退库量"))
        root.addView(autoRow("实际耗用（自动）", false).let { actualUseView = it.second; it.first })
        root.addView(autoRow("本班结存（自动）", true).let { balanceView = it.second; it.first })

        // ===== 二、合格品 =====
        root.addView(sectionTitle("二、合格品"))
        for (f in qualFields) {
            root.addView(numField(f))
        }
        root.addView(autoRow("当日累计合格品（自动）", false).let { qualTotalView = it.second; it.first })

        // ===== 三、损耗 =====
        root.addView(sectionTitle("三、损耗"))
        root.addView(numField("损耗"))

        // ===== 四、废品 =====
        root.addView(sectionTitle("四、废品"))
        for (f in scrapFields) {
            root.addView(numField(f))
        }
        root.addView(autoRow("废品总数（自动）", false).let { scrapTotalView = it.second; it.first })

        // ===== 五、开机时间与产量 =====
        root.addView(sectionTitle("五、开机时间与产量"))
        root.addView(numField("开机时间", unit = "小时"))
        root.addView(autoRow("小时产量（自动）", false).let { hourlyView = it.second; it.first })

        // 保存
        root.addView(MaterialButton(this).apply {
            text = "保存报表"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(16), 0, 0) }
            setOnClickListener { save() }
        })

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) { updateCalc() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        for ((_, et) in inputs) et.addTextChangedListener(watcher)

        loadDate(currentDate)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun sectionTitle(t: String) = TextView(this).apply {
        text = t
        textSize = 17f
        setTypeface(typeface, android.graphics.Typeface.BOLD)
        setPadding(dp(4), dp(16), dp(4), dp(6))
    }

    private fun numField(label: String, unit: String = "kg"): TextInputLayout {
        val layout = TextInputLayout(this).apply {
            hint = "$label（$unit）"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(8)) }
        }
        val et = TextInputEditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        layout.addView(et)
        inputs[label] = et
        return layout
    }

    private fun autoRow(label: String, highlight: Boolean): Pair<LinearLayout, TextView> {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(if (highlight) 0xFFE3F2FD.toInt() else 0xFFF5F5F5.toInt())
            setPadding(dp(12), dp(10), dp(12), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dp(6), 0, dp(6)) }
        }
        val tv = TextView(this).apply {
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            text = "0"
            setPadding(dp(8), 0, 0, 0)
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 14f
        })
        row.addView(tv)
        return row to tv
    }

    private fun dv(label: String): Double = inputs[label]?.text?.toString()?.toDoubleOrNull() ?: 0.0

    private fun updateCalc() {
        val recvTotal = recvFields.sumOf { dv(it) }
        val qualTotal = qualFields.sumOf { dv(it) }
        val scrapTotal = scrapFields.sumOf { dv(it) }
        val loss = dv("损耗")
        val actualUse = qualTotal + loss + scrapTotal
        val balance = dv("上班转入") + recvTotal - dv("退库量") - actualUse

        recvTotalView.text = fmt(recvTotal) + " kg"
        qualTotalView.text = fmt(qualTotal) + " kg"
        scrapTotalView.text = fmt(scrapTotal) + " kg"
        actualUseView.text = fmt(actualUse) + " kg"
        balanceView.text = fmt(balance) + " kg"
        balanceView.setTextColor(if (balance < 0) 0xFFC62828.toInt() else 0xFF1565C0.toInt())

        val hours = dv("开机时间")
        hourlyView.text = if (hours > 0) fmt(qualTotal / hours) + " kg/h" else "—（请填开机时间）"
    }

    private fun fmt(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else String.format("%.2f", v)

    private fun shiftDate(days: Int) {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        cal.time = sdf.parse(currentDate)!!
        cal.add(Calendar.DAY_OF_MONTH, days)
        loadDate(sdf.format(cal.time))
    }

    private fun pickDate() {
        val cal = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        cal.time = sdf.parse(currentDate)!!
        DatePickerDialog(this, { _, y, m, d ->
            loadDate(String.format(Locale.CHINA, "%04d-%02d-%02d", y, m + 1, d))
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun loadDate(date: String) {
        currentDate = date
        dateText.text = currentDate
        // 清空再载入
        for ((_, et) in inputs) et.setText("")
        DataDb.getReport(currentDate)?.let { map ->
            // 兼容 v1.9：旧的"当班领用量"转入①列
            if (map["当班领用①"].isNullOrBlank() && map["当班领用量"]?.isNotBlank() == true) {
                map["当班领用①"] = map["当班领用量"] ?: ""
            }
            for ((f, et) in inputs) et.setText(map[f] ?: "")
        }
        updateCalc()
    }

    private fun save() {
        val data = mutableMapOf<String, String>()
        for ((f, et) in inputs) data[f] = et.text?.toString()?.trim() ?: ""
        DataDb.saveReport(currentDate, data)
        AlertDialog.Builder(this)
            .setTitle("已保存")
            .setMessage("$currentDate 的生产报表已保存。")
            .setPositiveButton("好的", null)
            .show()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

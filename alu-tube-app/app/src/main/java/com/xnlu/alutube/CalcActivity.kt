package com.xnlu.alutube

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText

class CalcActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calc)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = "备尺快速计算器"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val finishedEt = findViewById<TextInputEditText>(R.id.calcFinished)
        val cutsEt = findViewById<TextInputEditText>(R.id.calcCuts)
        val result = findViewById<TextView>(R.id.calcResult)

        val watcher = object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                update(finishedEt, cutsEt, result)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        }
        finishedEt.addTextChangedListener(watcher)
        cutsEt.addTextChangedListener(watcher)
        update(finishedEt, cutsEt, result)
    }

    private fun update(finishedEt: TextInputEditText, cutsEt: TextInputEditText, result: TextView) {
        val finished = finishedEt.text?.toString()?.toDoubleOrNull()
        if (finished == null || finished <= 0) {
            result.text = "输入成品长度即可自动计算\n裁切刀数、备尺和飞剪长度"
            result.setTextColor(0xFF888888.toInt())
            return
        }
        val autoCuts = LengthCalc.recommendCuts(finished)
        val cutsText = cutsEt.text?.toString()?.trim()
        val cuts = cutsText?.toIntOrNull() ?: autoCuts
        val stock = LengthCalc.stockLength(finished, cuts)
        val inRange = LengthCalc.isStockInRange(stock)
        val sb = StringBuilder()
        sb.append(if (cutsText.isNullOrBlank()) "裁切刀数（自动推荐）: $cuts\n" else "裁切刀数: $cuts（自动推荐 $autoCuts）\n")
        sb.append("备尺: ${LengthCalc.fmt(stock)} mm\n")
        sb.append("飞剪长度: ${LengthCalc.fmt(LengthCalc.shearLow(stock))} - ${LengthCalc.fmt(LengthCalc.shearHigh(stock))} mm\n")
        sb.append(if (inRange) "✓ 备尺在目标范围内" else "⚠ 备尺超出 ${LengthCalc.fmt(LengthCalc.TARGET_LOW)}-${LengthCalc.fmt(LengthCalc.TARGET_HIGH)} 范围，请调整刀数")
        result.text = sb.toString()
        result.setTextColor(if (inRange) 0xFF1565C0.toInt() else 0xFFC62828.toInt())
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}

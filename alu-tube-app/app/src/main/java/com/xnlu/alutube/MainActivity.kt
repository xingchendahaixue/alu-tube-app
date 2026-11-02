package com.xnlu.alutube

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val catColors = mapOf(
        "customer_products" to 0xFF1E88E5,
        "size_standards" to 0xFF00897B,
        "molds" to 0xFF8E24AA,
        "weld_params" to 0xFFF4511E,
        "faults" to 0xFFE53935,
        "qc_standards" to 0xFF43A047
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataDb.init(this)
        DataDb.seedIfNeeded(this)

        // 加固：从最近任务恢复等路径直接进来时，未登录先回登录页
        if (!Auth.isLoggedIn(this)) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(24))
        }

        // ===== 渐变头部 =====
        val header = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(20)) }
            radius = dp(20).toFloat()
            cardElevation = dp(3).toFloat()
        }
        val headerInner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_header)
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        headerInner.addView(TextView(this).apply {
            text = "铝管工艺通"
            textSize = 26f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
        })
        headerInner.addView(TextView(this).apply {
            text = "高频焊铝管 · 工艺数据 · 备尺计算 · 生产报表"
            textSize = 13f
            setTextColor(0xFFD6E5FA.toInt())
            setPadding(0, dp(6), 0, 0)
        })
        header.addView(headerInner)
        root.addView(header)

        // ===== 工艺数据 =====
        root.addView(sectionLabel("工艺数据档案"))
        for (cat in CategoryConfig.all) {
            val color = catColors[cat.key] ?: 0xFF546E7A
            root.addView(rowCard(
                color = color,
                ch = cat.title.first().toString(),
                title = cat.title,
                desc = cat.desc
            ) {
                startActivity(
                    Intent(this@MainActivity, RecordListActivity::class.java)
                        .putExtra("category", cat.key)
                )
            })
        }

        // ===== 工具 =====
        root.addView(sectionLabel("常用工具"))
        root.addView(rowCard(0xFF3949AB, "算", "备尺快速计算器", "输入成品长度，自动算备尺和飞剪长度") {
            startActivity(Intent(this@MainActivity, CalcActivity::class.java))
        })
        root.addView(rowCard(0xFFFB8C00, "报", "生产报表", "原材料 / 损耗 / 废品 / 合格品 · 按日填写，自动汇总") {
            startActivity(Intent(this@MainActivity, ReportActivity::class.java))
        })
        root.addView(rowCard(0xFF546E7A, "备", "数据与账号", "备份导出 / 恢复 / 修改密码 · 换手机不丢数据") {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        })

        root.addView(TextView(this).apply {
            text = "数据来源：《高频焊铝管生产工艺数据档案》(2026-10-20 建档)\n" +
                "当前版本 ${AppInfo.label(this@MainActivity)} · 现场录入与报表保存在本机，可在「数据与账号」导出备份"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF90A4AE.toInt())
            setPadding(0, dp(20), 0, 0)
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF607D8B.toInt())
        setPadding(dp(6), dp(4), 0, dp(8))
    }

    private fun rowCard(color: Long, ch: String, title: String, desc: String, onClick: () -> Unit): com.google.android.material.card.MaterialCardView {
        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, dp(10)) }
            radius = dp(16).toFloat()
            cardElevation = dp(1).toFloat()
            setOnClickListener { onClick() }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        inner.addView(TextView(this).apply {
            text = ch
            textSize = 19f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(this@MainActivity, R.drawable.circle_bg)
            backgroundTintList = ColorStateList.valueOf(color.toInt())
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        inner.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(14), 0, dp(8), 0)
            }
            addView(TextView(this@MainActivity).apply {
                text = title
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(0xFF263238.toInt())
            })
            addView(TextView(this@MainActivity).apply {
                text = desc
                textSize = 12f
                setTextColor(0xFF78909C.toInt())
                setPadding(0, dp(2), 0, 0)
            })
        })
        inner.addView(TextView(this).apply {
            text = "›"
            textSize = 24f
            setTextColor(0xFFB0BEC5.toInt())
            gravity = Gravity.CENTER
        })
        card.addView(inner)
        return card
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

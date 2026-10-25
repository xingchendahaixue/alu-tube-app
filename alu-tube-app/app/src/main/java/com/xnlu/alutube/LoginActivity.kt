package com.xnlu.alutube

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 启动页：首次进入设置管理员账号，之后每次（未记住登录时）输密码进入。
 * 账号只存在本机，用于防止别人拿手机乱改工艺档案。
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var userEt: TextInputEditText
    private lateinit var pwdEt: TextInputEditText
    private lateinit var pwd2Et: TextInputEditText
    private lateinit var pwd2Layout: TextInputLayout
    private lateinit var actionBtn: MaterialButton

    private val isSetup get() = Auth.isSetup(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataDb.init(this)
        DataDb.seedIfNeeded(this)

        // 已登录（30 天内）直接进主界面
        if (Auth.isLoggedIn(this)) {
            goMain()
            return
        }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(0), dp(18), dp(24))
        }

        // ===== 头部 =====
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(this@LoginActivity, R.drawable.bg_header)
            setPadding(dp(22), dp(56), dp(22), dp(30))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(-dp(18), 0, -dp(18), 0) }
        }
        header.addView(TextView(this).apply {
            text = "铝管工艺通"
            textSize = 27f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFFFFFFFF.toInt())
        })
        header.addView(TextView(this).apply {
            text = if (isSetup) "请输入账号密码进入" else "首次使用，先设置一个管理员账号"
            textSize = 13f
            setTextColor(0xFFD6E5FA.toInt())
            setPadding(0, dp(8), 0, 0)
        })
        root.addView(header)

        // ===== 表单卡片 =====
        val card = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, -dp(18), 0, 0) }
            radius = dp(20).toFloat()
            cardElevation = dp(3).toFloat()
        }
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(20), dp(18), dp(18))
        }

        val userLayout = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox).apply {
            hint = "用户名"
            layoutParams = lp()
        }
        userEt = TextInputEditText(this).apply {
            setText(Auth.userName(this@LoginActivity))
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
        }
        userLayout.addView(userEt)
        form.addView(userLayout)

        val pwdLayout = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox).apply {
            hint = "密码"
            layoutParams = lp().apply { setMargins(0, dp(12), 0, 0) }
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
        }
        pwdEt = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        pwdLayout.addView(pwdEt)
        form.addView(pwdLayout)

        pwd2Layout = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox).apply {
            hint = "确认密码"
            layoutParams = lp().apply { setMargins(0, dp(12), 0, 0) }
            endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
            visibility = if (isSetup) android.view.View.GONE else android.view.View.VISIBLE
        }
        pwd2Et = TextInputEditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        pwd2Layout.addView(pwd2Et)
        form.addView(pwd2Layout)

        actionBtn = MaterialButton(this).apply {
            text = if (isSetup) "登 录" else "启用并进入"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { setMargins(0, dp(20), 0, 0) }
            setOnClickListener { onAction() }
        }
        form.addView(actionBtn)

        form.addView(TextView(this).apply {
            text = if (isSetup)
                "登录状态保留 30 天，之后需重新输入密码。"
            else
                "账号只保存在本机，不联网。密码用于防止他人误改工艺数据，忘记可在下方重置。"
            textSize = 11.5f
            setTextColor(0xFF90A4AE.toInt())
            setPadding(0, dp(12), 0, 0)
        })

        form.addView(TextView(this).apply {
            text = "忘记密码？重置账号（不删已录入数据）"
            textSize = 12.5f
            setTextColor(0xFF1E88E5.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(18), 0, dp(4))
            setOnClickListener { confirmReset() }
        })

        card.addView(form)
        root.addView(card)

        root.addView(TextView(this).apply {
            text = "数据来源：《高频焊铝管生产工艺数据档案》\n本机档案 ${totalRecords()} 条 · 报表 ${DataDb.reportCount()} 份"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF90A4AE.toInt())
            setPadding(dp(10), dp(26), dp(10), 0)
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun onAction() {
        val user = userEt.text?.toString()?.trim().orEmpty()
        val pwd = pwdEt.text?.toString().orEmpty()

        if (user.isBlank()) { toast("请输入用户名"); return }
        if (pwd.length < 4) { toast("密码至少 4 位"); return }

        if (!isSetup) {
            val pwd2 = pwd2Et.text?.toString().orEmpty()
            if (pwd != pwd2) { toast("两次输入的密码不一致"); return }
            Auth.setup(this, user, pwd)
            toast("账号已设置")
            goMain()
        } else {
            if (Auth.verify(this, user, pwd)) {
                Auth.keepLoggedIn(this)
                goMain()
            } else {
                pwdEt.setText("")
                toast("用户名或密码不对")
            }
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("重置账号")
            .setMessage("将清除本机密码，重新设置一个新账号。\n已录入的工艺数据和报表不受影响。")
            .setPositiveButton("重置") { _, _ ->
                getSharedPreferences("auth", MODE_PRIVATE).edit().clear().apply()
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun totalRecords(): Int = CategoryConfig.all.sumOf { DataDb.count(it.key) }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

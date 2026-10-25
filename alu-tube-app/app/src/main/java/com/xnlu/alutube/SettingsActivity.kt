package com.xnlu.alutube

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.io.File

/** 数据与账号：备份导出、从备份恢复、修改密码、退出登录 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var statView: TextView

    private val pickBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) doImport(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supportActionBar?.apply {
            title = "数据与账号"
            setDisplayHomeAsUpEnabled(true)
        }

        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(28))
        }

        // ===== 备份 =====
        root.addView(sectionLabel("数据备份与恢复"))
        val backupCard = card()
        val bf = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        statView = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFF546E7A.toInt())
            setLineSpacing(dp(3).toFloat(), 1f)
            text = statsText()
        }
        bf.addView(statView)
        bf.addView(TextView(this).apply {
            text = "备份是一个压缩包，含全部档案、报表和照片。可通过微信发给同事或自己，也可存到网盘。"
            textSize = 11.5f
            setTextColor(0xFF90A4AE.toInt())
            setPadding(0, dp(8), 0, dp(4))
        })
        bf.addView(primaryButton("导出备份并发送") { doExport() })
        bf.addView(outlineButton("从备份文件恢复") {
            pickBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*"))
        })
        bf.addView(TextView(this).apply {
            text = "恢复只补充本机缺少的记录，已有的不会重复、也不会被覆盖。"
            textSize = 11.5f
            setTextColor(0xFF90A4AE.toInt())
            setPadding(0, dp(10), 0, 0)
        })
        backupCard.addView(bf)
        root.addView(backupCard)

        // ===== 账号 =====
        root.addView(sectionLabel("账号"))
        val accCard = card()
        val af = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
        }
        af.addView(TextView(this).apply {
            text = "当前账号：" + Auth.userName(this@SettingsActivity)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF263238.toInt())
        })
        af.addView(TextView(this).apply {
            text = "账号仅保存在本机，用于防止他人误改工艺数据。"
            textSize = 11.5f
            setTextColor(0xFF90A4AE.toInt())
            setPadding(0, dp(6), 0, dp(4))
        })
        af.addView(outlineButton("修改密码") { changePassword() })
        af.addView(outlineButton("退出登录") {
            Auth.logout(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finishAffinity()
        })
        accCard.addView(af)
        root.addView(accCard)

        root.addView(TextView(this).apply {
            text = "多台手机之间自动同步需要云端服务，暂未开通；\n当前可在手机上导出备份，用微信传给其他手机导入。"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(0xFF9E9E9E.toInt())
            setPadding(dp(8), dp(24), dp(8), 0)
            setLineSpacing(dp(3).toFloat(), 1f)
        })

        scroll.addView(root)
        setContentView(scroll)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    // ===== 导出 =====
    private fun doExport() {
        try {
            val dir = File(cacheDir, "backup")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, Backup.suggestName())
            val r = Backup.export(this, file)
            val sizeKb = (file.length() + 1023) / 1024

            val uri: Uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, file.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            Toast.makeText(
                this,
                "已生成备份：${r.recordsAdded} 条记录 · ${r.reportsAdded} 份报表 · ${r.photosAdded} 张照片（${sizeKb}KB）",
                Toast.LENGTH_LONG
            ).show()
            startActivity(Intent.createChooser(send, "发送备份文件"))
        } catch (e: Exception) {
            AlertDialog.Builder(this).setTitle("导出失败").setMessage(e.message ?: "未知错误")
                .setPositiveButton("好", null).show()
        }
    }

    // ===== 导入 =====
    private fun doImport(uri: Uri) {
        AlertDialog.Builder(this)
            .setTitle("从备份恢复")
            .setMessage("将读取备份文件，把本机缺少的记录补进来。已有记录不会被覆盖。继续？")
            .setPositiveButton("开始恢复") { _, _ ->
                val loading = AlertDialog.Builder(this).setTitle("正在恢复…").setMessage("请稍候")
                    .setCancelable(false).create()
                loading.show()
                Thread {
                    val msg = try {
                        val r = Backup.import(this, uri)
                        "新增档案 ${r.recordsAdded} 条，跳过已有 ${r.recordsSkipped} 条\n" +
                            "新增报表 ${r.reportsAdded} 份，跳过已有 ${r.reportsSkipped} 份\n" +
                            "补充照片 ${r.photosAdded} 张"
                    } catch (e: Exception) {
                        "恢复失败：" + (e.message ?: "文件格式不对")
                    }
                    runOnUiThread {
                        loading.dismiss()
                        statView.text = statsText()
                        AlertDialog.Builder(this).setTitle("恢复结果").setMessage(msg)
                            .setPositiveButton("好", null).show()
                    }
                }.start()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ===== 修改密码 =====
    private fun changePassword() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun field(hint: String): TextInputLayout {
            val l = TextInputLayout(this, null, com.google.android.material.R.style.Widget_Material3_TextInputLayout_OutlinedBox).apply {
                this.hint = hint
                endIconMode = TextInputLayout.END_ICON_PASSWORD_TOGGLE
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, dp(6), 0, dp(6)) }
            }
            l.addView(TextInputEditText(this).apply {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                isSingleLine = true
            })
            box.addView(l)
            return l
        }
        val oldL = field("原密码")
        val newL = field("新密码（至少 4 位）")
        val new2L = field("确认新密码")

        AlertDialog.Builder(this)
            .setTitle("修改密码")
            .setView(box)
            .setPositiveButton("保存") { _, _ ->
                val old = (oldL.editText?.text?.toString() ?: "")
                val np = (newL.editText?.text?.toString() ?: "")
                val np2 = (new2L.editText?.text?.toString() ?: "")
                when {
                    !Auth.verify(this, Auth.userName(this), old) -> toast("原密码不对")
                    np.length < 4 -> toast("新密码至少 4 位")
                    np != np2 -> toast("两次输入的新密码不一致")
                    else -> {
                        Auth.changePassword(this, Auth.userName(this), np)
                        Auth.keepLoggedIn(this)
                        toast("密码已修改")
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ===== UI 小工具 =====
    private fun statsText(): String {
        val records = CategoryConfig.all.sumOf { DataDb.count(it.key) }
        var photos = 0
        File(filesDir, "photos").listFiles()?.forEach { d ->
            photos += d.listFiles()?.count { it.isFile } ?: 0
        }
        return "档案 $records 条 · 报表 ${DataDb.reportCount()} 份 · 照片 $photos 张"
    }

    private fun card() = MaterialCardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(0, 0, 0, dp(14)) }
        radius = dp(16).toFloat()
        cardElevation = dp(1).toFloat()
    }

    private fun primaryButton(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
        this.text = text
        textSize = 15f
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        ).apply { setMargins(0, dp(14), 0, 0) }
        setOnClickListener { onClick() }
    }

    private fun outlineButton(text: String, onClick: () -> Unit) =
        MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            this.text = text
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { setMargins(0, dp(10), 0, 0) }
            setOnClickListener { onClick() }
        }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(typeface, Typeface.BOLD)
        setTextColor(0xFF607D8B.toInt())
        setPadding(dp(6), dp(4), 0, dp(8))
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

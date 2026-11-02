package com.xnlu.alutube

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat

/**
 * 版本信息与下载地址（设置页与首页共用）
 *
 * 下载地址指向 GitHub Releases 的 latest，每次发版不用改代码。
 * 注意：仓库当前是私有的，未登录 GitHub 的手机打开该链接会 404，需要先登录账号。
 */
object AppInfo {

    const val DOWNLOAD_URL = "https://github.com/xingchendahaixue/alu-tube-app/releases/latest"
    const val REPO_URL = "https://github.com/xingchendahaixue/alu-tube-app"

    fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }

    fun versionCode(context: Context): Long = try {
        PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0)
        )
    } catch (e: Exception) {
        0L
    }

    /** 形如 v3.10（31） */
    fun label(context: Context): String = "v" + versionName(context) + "（" + versionCode(context) + "）"
}

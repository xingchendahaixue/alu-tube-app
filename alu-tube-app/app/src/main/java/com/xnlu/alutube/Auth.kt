package com.xnlu.alutube

import android.content.Context
import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 本机账号：一个管理员账号，密码加盐 SHA-256 存在 SharedPreferences。
 * 不联网，只用于防止别人拿手机乱改工艺档案。
 */
object Auth {
    private const val PREF = "auth"
    private const val K_USER = "user"
    private const val K_SALT = "salt"
    private const val K_HASH = "hash"
    private const val K_REMEMBER = "remember_until"

    /** 登录状态保持 30 天；设为 0 表示每次打开都要输密码 */
    private const val REMEMBER_DAYS = 30L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    private fun prefs(c: Context) = c.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    /** 是否已设置过账号 */
    fun isSetup(c: Context): Boolean = prefs(c).getString(K_HASH, null) != null

    fun userName(c: Context): String = prefs(c).getString(K_USER, "admin") ?: "admin"

    fun setup(c: Context, user: String, pwd: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs(c).edit()
            .putString(K_USER, user.ifBlank { "admin" })
            .putString(K_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(K_HASH, hash(pwd, salt))
            .putLong(K_REMEMBER, System.currentTimeMillis() + REMEMBER_DAYS * DAY_MS)
            .apply()
    }

    fun changePassword(c: Context, user: String, pwd: String) = setup(c, user, pwd)

    fun verify(c: Context, user: String, pwd: String): Boolean {
        val p = prefs(c)
        val saltStr = p.getString(K_SALT, null) ?: return false
        val saved = p.getString(K_HASH, null) ?: return false
        if (!user.equals(p.getString(K_USER, "admin"), ignoreCase = true)) return false
        val salt = Base64.decode(saltStr, Base64.NO_WRAP)
        return hash(pwd, salt) == saved
    }

    /** 已登录（记得登录且未过期） */
    fun isLoggedIn(c: Context): Boolean {
        if (!isSetup(c)) return false
        return prefs(c).getLong(K_REMEMBER, 0L) > System.currentTimeMillis()
    }

    fun keepLoggedIn(c: Context) {
        prefs(c).edit().putLong(K_REMEMBER, System.currentTimeMillis() + REMEMBER_DAYS * DAY_MS).apply()
    }

    fun logout(c: Context) {
        prefs(c).edit().putLong(K_REMEMBER, 0L).apply()
    }

    private fun hash(pwd: String, salt: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pwd.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(md.digest(), Base64.NO_WRAP)
    }
}

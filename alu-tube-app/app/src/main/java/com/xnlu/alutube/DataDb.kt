package com.xnlu.alutube

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

/**
 * 本地数据库：首次启动从 assets/app_data.json 灌入档案数据，
 * 之后所有录入/修改都保存在本地。
 */
object DataDb {
    private const val DB_NAME = "process_data.db"
    private const val VERSION = 3
    private lateinit var helper: DbHelper

    /** 一条记录的内存视图：searchText 预先转小写，避免每次按键重复拼串 */
    data class Row(val id: Long, val data: Map<String, String>, val searchText: String)

    private val cache = HashMap<String, List<Row>>()

    private fun invalidate(category: String? = null) {
        if (category == null) cache.clear() else cache.remove(category)
    }

    fun init(context: Context) {
        if (!DataDb::helper.isInitialized) {
            helper = DbHelper(context.applicationContext)
        }
    }

    private class DbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE records(" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "category TEXT NOT NULL," +
                    "sort INTEGER NOT NULL DEFAULT 0," +
                    "data TEXT NOT NULL)"
            )
            db.execSQL("CREATE TABLE IF NOT EXISTS reports(date TEXT PRIMARY KEY, data TEXT NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_category ON records(category, sort)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("CREATE TABLE IF NOT EXISTS reports(date TEXT PRIMARY KEY, data TEXT NOT NULL)")
            }
            if (oldVersion < 3) {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_records_category ON records(category, sort)")
            }
        }
    }

    /** 档案数据纠错版本：每次修正种子数据后 +1，升级后首次启动自动套用到手机已有数据 */
    private const val DATA_FIX_VERSION = 6
    /** v1：整名替换（识别错的客户名） */
    private val DATA_FIXES_V1 = mapOf("鲍昂" to "瀚昂", "雷迪" to "淮驰")

    /** 首次启动时从 assets 导入数据（只导入一次），之后套用数据纠错 */
    fun seedIfNeeded(context: Context) {
        val db = helper.writableDatabase
        var empty = false
        db.rawQuery("SELECT COUNT(*) FROM records", null).use { c ->
            empty = !(c.moveToFirst() && c.getInt(0) > 0)
        }
        if (empty) {
            val json = context.assets.open("app_data.json").bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            db.beginTransaction()
            try {
                for (cat in CategoryConfig.all) {
                    val arr = root.optJSONArray(cat.key) ?: continue
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val map = mutableMapOf<String, String>()
                        for (f in cat.fields) map[f] = obj.optString(f, "")
                        // 台账等表可能有"备注"以外的额外字段，也保留
                        for (key in obj.keys()) if (key !in map) map[key] = obj.optString(key, "")
                        insert(cat.key, map, i, db)
                    }
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            invalidate()
        }
        applyDataFixes(context)
    }

    /** 一次性数据纠错：把档案导入时识别错的内容改正（精确匹配，不影响用户其他修改） */
    private fun applyDataFixes(context: Context) {
        val prefs = context.getSharedPreferences("data_fix", Context.MODE_PRIVATE)
        val applied = prefs.getInt("version", 0)
        if (applied >= DATA_FIX_VERSION) return
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery(
                "SELECT id, data FROM records WHERE category='customer_products'", null
            ).use { c ->
                val updates = mutableListOf<Pair<Long, String>>()
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val o = JSONObject(c.getString(1))
                    var changed = false
                    // v1：客户名整名替换
                    if (applied < 1) {
                        val name = o.optString("客户名称", "").trim()
                        DATA_FIXES_V1[name]?.let {
                            o.put("客户名称", it); changed = true
                        }
                    }
                    // v2：淮驰的 R16 产品被错归到凯宏名下（16*2.0*0.3、压力 8.13）
                    if (applied < 2 &&
                        o.optString("客户名称", "").trim() == "凯宏" &&
                        o.optString("规格型号(mm)", "").trim() == "16*2.0*0.3" &&
                        o.optString("压力/备注(MPa)", "").contains("8.13")
                    ) {
                        o.put("客户名称", "淮驰"); changed = true
                    }
                    if (changed) updates.add(id to o.toString())
                }
                for ((id, data) in updates) {
                    db.execSQL("UPDATE records SET data=? WHERE id=?", arrayOf(data, id.toString()))
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        // v3/v4：德钢产品尺寸整段换新（客户新表 11 个规格，公差 A +0.03/-0.02、B ±0.05、C +0.03/-0.02）
        // v4 起规格命名统一为 R{主轴}×{末端}×{壁厚}
        if (applied < 4) {
            rebuildCustomer(context, db, "德钢")
            normalizeSpecNames(db, "customer_products", "规格型号(mm)")
        }
        // v5：产品尺寸标准（凯宏表3）也统一加 R 前缀，如 16×1.5 → R16×1.5
        if (applied < 5) {
            normalizeSpecNames(db, "size_standards", "规格/mm")
        }
        // v6：规格统一小数位——末端 C 一位小数（R16×2.0）、壁厚 T 两位小数（R16×2.0×0.30）
        if (applied < 6) {
            normalizeSpecNames(db, "customer_products", "规格型号(mm)")
            normalizeSpecNames(db, "size_standards", "规格/mm")
        }
        prefs.edit().putInt("version", DATA_FIX_VERSION).apply()
        invalidate()
    }

    /** 按 assets 种子数据整段重建某个客户的产品记录（旧规格记录删除，含其照片目录） */
    private fun rebuildCustomer(context: Context, db: SQLiteDatabase, customer: String) {
        val ids = mutableListOf<Long>()
        db.rawQuery("SELECT id, data FROM records WHERE category='customer_products'", null).use { c ->
            while (c.moveToNext()) {
                val o = JSONObject(c.getString(1))
                if (o.optString("客户名称", "").trim() == customer) ids.add(c.getLong(0))
            }
        }
        for (id in ids) {
            db.delete("records", "id=?", arrayOf(id.toString()))
            java.io.File(context.filesDir, "photos/$id").deleteRecursively()
        }
        val json = context.assets.open("app_data.json").bufferedReader().use { it.readText() }
        val arr = JSONObject(json).optJSONArray("customer_products") ?: return
        val cat = CategoryConfig.byKey("customer_products")
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            if (obj.optString("客户名称", "").trim() != customer) continue
            val map = mutableMapOf<String, String>()
            for (f in cat.fields) map[f] = obj.optString(f, "")
            for (key in obj.keys()) if (key !in map) map[key] = obj.optString(key, "")
            insert("customer_products", map, i, db)
        }
    }

    /**
     * 规格命名统一格式：R{主轴}×{末端}×{壁厚}
     *  - 主轴 A：原样（16、23.5、31.57）
     *  - 末端 C：至少 1 位小数（2 → 2.0；8.08 保留原精度）
     *  - 壁厚 T：至少 2 位小数，支持 0.28/0.30 这类复合值（0.3 → 0.30；0.285 保留）
     * 只处理 "数字×数字(×数字)" 形式的规格值，说明文字等其它内容原样保留。
     * （customer_products：16*1.5*0.3 → R16×1.5×0.30；size_standards：16×2 → R16×2.0）
     */
    private fun normalizeSpecNames(db: SQLiteDatabase, category: String, field: String) {
        val updates = mutableListOf<Pair<Long, String>>()
        db.rawQuery(
            "SELECT id, data FROM records WHERE category=?", arrayOf(category)
        ).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val o = JSONObject(c.getString(1))
                val spec = o.optString(field, "").trim()
                if (spec.isEmpty()) continue
                val body = spec.replace('*', '×').replace('x', '×').replace('X', '×').removePrefix("R")
                val parts = body.split("×").map { it.trim() }
                if (parts.size < 2) continue
                if (parts.any { p -> p.split("/").any { it.toDoubleOrNull() == null } }) continue
                val out = parts.toMutableList()
                out[1] = padDecimals(out[1], 1)
                for (i in 2 until out.size) {
                    out[i] = out[i].split("/").joinToString("/") { padDecimals(it, 2) }
                }
                val result = "R" + out.joinToString("×")
                if (result != spec) {
                    o.put(field, result)
                    updates.add(id to o.toString())
                }
            }
        }
        for ((id, data) in updates) {
            db.execSQL("UPDATE records SET data=? WHERE id=?", arrayOf(data, id.toString()))
        }
    }

    /** 数字至少补到 minDecimals 位小数（已有更多位则保留原精度） */
    private fun padDecimals(v: String, minDecimals: Int): String {
        val t = v.trim()
        if (t.toDoubleOrNull() == null) return t
        val dec = if (t.contains('.')) t.substringAfter('.').length else 0
        if (dec >= minDecimals) return t
        return if (dec == 0) t + "." + "0".repeat(minDecimals) else t + "0".repeat(minDecimals - dec)
    }

    private fun insert(category: String, data: Map<String, String>, sort: Int, db: SQLiteDatabase? = null): Long {
        val d = db ?: helper.writableDatabase
        val cv = android.content.ContentValues().apply {
            put("category", category)
            put("sort", sort)
            put("data", JSONObject(data).toString())
        }
        return d.insert("records", null, cv)
    }

    fun insert(category: String, data: Map<String, String>): Long {
        val id = insert(category, data, 0)
        invalidate(category)
        return id
    }

    fun update(id: Long, data: Map<String, String>) {
        val cv = android.content.ContentValues().apply {
            put("data", JSONObject(data).toString())
        }
        helper.writableDatabase.update("records", cv, "id=?", arrayOf(id.toString()))
        invalidate()
    }

    fun delete(id: Long) {
        helper.writableDatabase.delete("records", "id=?", arrayOf(id.toString()))
        invalidate()
    }

    fun get(id: Long): Pair<String, MutableMap<String, String>>? {
        helper.readableDatabase.rawQuery("SELECT category, data FROM records WHERE id=?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            val cat = c.getString(0)
            val map = JSONObject(c.getString(1)).let { o ->
                val m = mutableMapOf<String, String>()
                for (k in o.keys()) m[k] = o.optString(k, "")
                m
            }
            return cat to map
        }
    }

    /**
     * 读取某类目全部记录（带内存缓存）。首次查库时一次性解析 JSON 并预生成小写搜索文本，
     * 之后列表页的搜索/筛选都只走内存，不再重复查库和解析。
     */
    @Synchronized
    fun rows(category: String): List<Row> {
        cache[category]?.let { return it }
        val list = mutableListOf<Row>()
        helper.readableDatabase.rawQuery(
            "SELECT id, data FROM records WHERE category=? ORDER BY sort, id",
            arrayOf(category)
        ).use { c ->
            while (c.moveToNext()) {
                val o = JSONObject(c.getString(1))
                val map = LinkedHashMap<String, String>(o.length())
                for (k in o.keys()) map[k] = o.optString(k, "")
                list.add(Row(c.getLong(0), map, buildSearchText(category, map)))
            }
        }
        cache[category] = list
        return list
    }

    /** 搜索文本：全部字段 + 备尺/飞剪数值，统一小写，只算一次 */
    private fun buildSearchText(category: String, map: Map<String, String>): String {
        val hay = StringBuilder(map.values.joinToString("\n"))
        val calcEnabled = try { CategoryConfig.byKey(category).lengthCalc } catch (e: Exception) { false }
        if (calcEnabled) {
            val finished = map["成品长度(mm)"]?.toDoubleOrNull()
            if (finished != null && finished > 0) {
                val cuts = map["裁切刀数"]?.toIntOrNull() ?: LengthCalc.recommendCuts(finished)
                val stock = LengthCalc.stockLength(finished, cuts)
                hay.append('\n').append(LengthCalc.fmt(stock))
                    .append(' ').append(LengthCalc.fmt(LengthCalc.shearLow(stock)))
                    .append(' ').append(LengthCalc.fmt(LengthCalc.shearHigh(stock)))
            }
        }
        return hay.toString().lowercase()
    }

    /**
     * 查询 + 模糊搜索：多关键字用空格分隔，全部命中才算匹配（AND），不区分大小写。
     * 命中判断基于 rows() 的缓存，不再逐行解析 JSON。
     */
    fun query(category: String, filter: String = ""): List<Pair<Long, Map<String, String>>> {
        val all = rows(category)
        val kws = filter.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val matched = if (kws.isEmpty()) all else all.filter { row -> kws.all { row.searchText.contains(it) } }
        return matched.map { it.id to it.data }
    }

    /** 客户下拉去重（用于客户产品参数页筛选） */
    fun distinctValues(category: String, field: String): List<String> {
        val set = linkedSetOf<String>()
        query(category).forEach { (_, map) ->
            map[field]?.takeIf { it.isNotBlank() }?.let { set.add(it) }
        }
        return set.toList()
    }

    // ===== 生产报表 =====

    fun getReport(date: String): MutableMap<String, String>? {
        helper.readableDatabase.rawQuery("SELECT data FROM reports WHERE date=?", arrayOf(date)).use { c ->
            if (!c.moveToFirst()) return null
            val o = JSONObject(c.getString(0))
            val m = mutableMapOf<String, String>()
            for (k in o.keys()) m[k] = o.optString(k, "")
            return m
        }
    }

    fun saveReport(date: String, data: Map<String, String>) {
        helper.writableDatabase.insertWithOnConflict(
            "reports", null,
            android.content.ContentValues().apply {
                put("date", date)
                put("data", JSONObject(data).toString())
            },
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun reportDates(): List<String> {
        val out = mutableListOf<String>()
        helper.readableDatabase.rawQuery("SELECT date FROM reports ORDER BY date DESC", null).use { c ->
            while (c.moveToNext()) out.add(c.getString(0))
        }
        return out
    }
}

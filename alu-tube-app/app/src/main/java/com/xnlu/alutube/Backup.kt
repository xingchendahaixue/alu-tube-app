package com.xnlu.alutube

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 数据备份与恢复：把全部档案 + 报表 + 照片打包成一个 zip，
 * 用于换手机、发给同事、存档。导入时按业务主键去重合并，不覆盖本机已有内容。
 */
object Backup {

    private const val DATA_ENTRY = "data.json"
    private const val FORMAT_VERSION = 1

    data class Result(
        val recordsAdded: Int,
        val recordsSkipped: Int,
        val reportsAdded: Int,
        val reportsSkipped: Int,
        val photosAdded: Int
    )

    /** 备份文件名：铝管工艺通备份-20261025-1130.zip */
    fun suggestName(): String =
        "铝管工艺通备份-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.CHINA).format(Date()) + ".zip"

    /** 导出到指定文件，返回记录/照片数量统计 */
    fun export(context: Context, out: File): Result {
        val root = JSONObject()
        root.put("formatVersion", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("appVersion", appVersion(context))

        val recordsArr = JSONArray()
        var photoCount = 0
        for (cat in CategoryConfig.all) {
            for (row in DataDb.rows(cat.key)) {
                val o = JSONObject()
                o.put("id", row.id)
                o.put("category", cat.key)
                val dataObj = JSONObject()
                row.data.forEach { (k, v) -> dataObj.put(k, v) }
                o.put("data", dataObj)
                recordsArr.put(o)
            }
        }
        root.put("records", recordsArr)

        val reportsArr = JSONArray()
        for (d in DataDb.reportDates()) {
            val o = JSONObject()
            o.put("date", d)
            val dataObj = JSONObject()
            (DataDb.getReport(d) ?: mutableMapOf()).forEach { (k, v) -> dataObj.put(k, v) }
            o.put("data", dataObj)
            reportsArr.put(o)
        }
        root.put("reports", reportsArr)

        out.parentFile?.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zos ->
            zos.putNextEntry(ZipEntry(DATA_ENTRY))
            zos.write(root.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            val photosRoot = File(context.filesDir, "photos")
            photosRoot.listFiles()?.forEach { recDir ->
                if (!recDir.isDirectory) return@forEach
                recDir.listFiles()?.forEach { f ->
                    if (!f.isFile) return@forEach
                    zos.putNextEntry(ZipEntry("photos/${recDir.name}/${f.name}"))
                    f.inputStream().use { it.copyTo(zos) }
                    zos.closeEntry()
                    photoCount++
                }
            }
        }

        // 统计不参与返回语义（导出侧用不到），返回 0 占位由 UI 自行读取文件大小
        return Result(recordsArr.length(), 0, reportsArr.length(), 0, photoCount)
    }

    /** 从备份 zip 恢复：新增缺失的记录，已有业务键的记录跳过 */
    fun import(context: Context, uri: Uri): Result {
        val tmp = File(context.cacheDir, "import_tmp")
        tmp.deleteRecursively()
        tmp.mkdirs()

        var dataJson: String? = null
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input.buffered()).use { zis ->
                var e = zis.nextEntry
                while (e != null) {
                    val name = e.name
                    if (name == DATA_ENTRY) {
                        dataJson = zis.readBytes().toString(Charsets.UTF_8)
                    } else if (!e.isDirectory && name.startsWith("photos/")) {
                        val target = File(tmp, name)
                        target.parentFile?.mkdirs()
                        target.outputStream().use { out -> zis.copyTo(out) }
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
        }

        val json = dataJson ?: throw IllegalStateException("不是有效的备份文件")

        var added = 0
        var skipped = 0
        var reportsAdded = 0
        var reportsSkipped = 0
        var photosAdded = 0

        // 旧记录 id → 本机记录 id（照片要靠它挂回去）
        val idMap = HashMap<Long, Long>()

        val root = JSONObject(json)
        val records = root.optJSONArray("records") ?: JSONArray()
        for (i in 0 until records.length()) {
            val o = records.getJSONObject(i)
            val category = o.optString("category")
            val cat = try { CategoryConfig.byKey(category) } catch (e: Exception) { continue }
            val oldId = o.optLong("id")
            val dataObj = o.optJSONObject("data") ?: continue
            val map = LinkedHashMap<String, String>()
            for (key in dataObj.keys()) map[key] = dataObj.optString(key, "")

            val existing = DataDb.rows(cat.key)
            val key = businessKey(cat.key, map)
            val hit = existing.firstOrNull { businessKey(cat.key, it.data) == key }
            if (hit != null) {
                skipped++
                idMap[oldId] = hit.id
            } else {
                val newId = DataDb.insertWithSort(cat.key, map, i)
                added++
                idMap[oldId] = newId
            }
        }

        val reports = root.optJSONArray("reports") ?: JSONArray()
        val localDates = DataDb.reportDates().toHashSet()
        for (i in 0 until reports.length()) {
            val o = reports.getJSONObject(i)
            val date = o.optString("date")
            if (date.isBlank()) continue
            if (date in localDates) {
                reportsSkipped++
                continue
            }
            val dataObj = o.optJSONObject("data") ?: JSONObject()
            val map = LinkedHashMap<String, String>()
            for (key in dataObj.keys()) map[key] = dataObj.optString(key, "")
            DataDb.saveReport(date, map)
            reportsAdded++
        }

        // 照片：按旧 id 目录挂到新 id 目录，同名不重复
        val photosRoot = File(tmp, "photos")
        photosRoot.listFiles()?.forEach { oldDir ->
            val newId = idMap[oldDir.name.toLongOrNull() ?: -1L] ?: return@forEach
            val dest = PhotoStore.dir(context, newId)
            dest.mkdirs()
            oldDir.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                val target = File(dest, f.name)
                if (!target.exists()) {
                    f.copyTo(target, overwrite = false)
                    photosAdded++
                }
            }
        }

        tmp.deleteRecursively()
        return Result(added, skipped, reportsAdded, reportsSkipped, photosAdded)
    }

    /**
     * 业务主键：同一条数据在两台手机上应算出同一个键，用于导入去重。
     * 取该类别最能唯一确定一条记录的字段；字段缺失时退化为全部内容拼接。
     */
    private fun businessKey(category: String, d: Map<String, String>): String {
        fun v(f: String) = (d[f] ?: "").trim().replace(Regex("\\s+"), " ")
        val key = when (category) {
            "customer_products" -> "${v("客户名称")}|${v("规格型号(mm)")}"
            "size_standards" -> v("规格/mm")
            "molds" -> "${v("产品型号")}|${v("架次")}|${v("模具编号")}"
            "weld_params" -> v("参数项目")
            "faults" -> v("故障现象")
            "qc_standards" -> "${v("类别")}|${v("项目")}"
            else -> ""
        }
        return if (key.replace("|", "").isBlank()) {
            d.entries.sortedBy { it.key }.joinToString("|") { "${it.key}=${it.value}" }
        } else key
    }

    private fun appVersion(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}

package com.xnlu.alutube

/**
 * 备尺 / 飞剪长度计算
 *
 * 公式（2026-10-25 与用户确认，替换 10-23 版）：
 *   备尺 = (成品长度 + 2.5) × 裁切刀数 + 40（固定料头）
 *   飞剪长度 = 备尺 + 25 ~ 备尺 + 30（飞剪损耗）
 *   备尺尽量落在 5400-5900 之间；刀数自动推荐时取使备尺最接近区间中值 5650 的整数。
 *   验证：成品长度 222 → 刀数 25 → 备尺 (224.5)×25+40 = 5652.5 → 飞剪 5677.5-5682.5 ✓
 */
object LengthCalc {
    const val FIXED_MARGIN = 40.0      // 固定料头
    const val PER_CUT_ADD = 2.5        // 每刀附加
    const val TARGET_LOW = 5400.0      // 备尺目标区间下限
    const val TARGET_HIGH = 5900.0     // 备尺目标区间上限
    const val SHEAR_MIN = 25.0         // 飞剪损耗下限
    const val SHEAR_MAX = 30.0         // 飞剪损耗上限

    val targetMid = (TARGET_LOW + TARGET_HIGH) / 2.0   // 5650

    fun recommendCuts(finishedLength: Double): Int {
        if (finishedLength <= 0) return 0
        val n = Math.round((targetMid - FIXED_MARGIN) / (finishedLength + PER_CUT_ADD)).toInt()
        return n.coerceAtLeast(1)
    }

    fun stockLength(finishedLength: Double, cuts: Int): Double =
        (finishedLength + PER_CUT_ADD) * cuts + FIXED_MARGIN

    fun isStockInRange(stock: Double): Boolean =
        stock in TARGET_LOW..TARGET_HIGH

    fun shearLow(stock: Double) = stock + SHEAR_MIN
    fun shearHigh(stock: Double) = stock + SHEAR_MAX

    fun fmt(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else String.format("%.1f", v)
}

package com.xnlu.alutube

/**
 * 备尺 / 飞剪长度计算
 *
 * 公式（2026-10-23 与用户确认）：
 *   备尺 = (成品长度 + 3) × 裁切刀数 + 35（固定余量）
 *   飞剪长度 = 备尺 + 25 ~ 备尺 + 45（显示为区间）
 *   备尺尽量落在 5400-5900 之间；刀数自动推荐时取使备尺最接近区间中值 5650 的整数。
 *   验证：成品长度 222 → 刀数 25 → 备尺 (225)×25+35 = 5660 → 飞剪 5685-5710 ✓
 */
object LengthCalc {
    const val FIXED_MARGIN = 35.0      // 固定余量
    const val PER_CUT_ADD = 3.0        // 每刀附加
    const val TARGET_LOW = 5400.0      // 备尺目标区间下限
    const val TARGET_HIGH = 5900.0     // 备尺目标区间上限
    const val SHEAR_MIN = 25.0         // 飞剪长度下加
    const val SHEAR_MAX = 45.0         // 飞剪长度上加

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

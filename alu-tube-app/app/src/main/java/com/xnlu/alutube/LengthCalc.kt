package com.xnlu.alutube

/**
 * 备尺 / 飞剪长度计算
 *
 * 公式（2026-10-25 与用户确认）：
 *   备尺 = (成品长度 + 2.5) × 裁切刀数 + 40（固定料头）
 *   飞剪长度 = 备尺 + 25 ~ 备尺 + 30（飞剪损耗）
 *   备尺目标区间 5200-5950；刀数自动推荐时取区间内**最大**刀数（备尺靠近上限值）。
 *   验证：成品长度 222 → 刀数 26 → 备尺 224.5×26+40 = 5877 → 飞剪 5902-5907 ✓
 */
object LengthCalc {
    const val FIXED_MARGIN = 40.0      // 固定料头
    const val PER_CUT_ADD = 2.5        // 每刀附加
    const val TARGET_LOW = 5200.0      // 备尺目标区间下限
    const val TARGET_HIGH = 5950.0     // 备尺目标区间上限
    const val SHEAR_MIN = 25.0         // 飞剪损耗下限
    const val SHEAR_MAX = 30.0         // 飞剪损耗上限

    private const val EPS = 1e-9

    /**
     * 推荐刀数：优先取使备尺落在目标区间内的最大刀数（贴近上限）。
     * 若没有任何整数刀数能落进区间，则取备尺最接近上限的刀数（界面会提示超范围）。
     */
    fun recommendCuts(finishedLength: Double): Int {
        if (finishedLength <= 0) return 0
        val step = finishedLength + PER_CUT_ADD
        // 区间约束：TARGET_LOW <= step * n + FIXED_MARGIN <= TARGET_HIGH
        val nMin = Math.ceil((TARGET_LOW - FIXED_MARGIN) / step - EPS).toInt().coerceAtLeast(1)
        val nMax = Math.floor((TARGET_HIGH - FIXED_MARGIN) / step + EPS).toInt()
        if (nMax >= nMin) return nMax
        val n = Math.round((TARGET_HIGH - FIXED_MARGIN) / step).toInt()
        return n.coerceAtLeast(1)
    }

    fun stockLength(finishedLength: Double, cuts: Int): Double =
        (finishedLength + PER_CUT_ADD) * cuts + FIXED_MARGIN

    fun isStockInRange(stock: Double): Boolean =
        stock >= TARGET_LOW - EPS && stock <= TARGET_HIGH + EPS

    fun shearLow(stock: Double) = stock + SHEAR_MIN
    fun shearHigh(stock: Double) = stock + SHEAR_MAX

    fun fmt(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else String.format("%.1f", v)
}

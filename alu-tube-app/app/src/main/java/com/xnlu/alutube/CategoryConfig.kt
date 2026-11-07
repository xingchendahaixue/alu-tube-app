package com.xnlu.alutube

object CategoryConfig {
    /**
     * 下拉筛选配置
     * @param field 显示用的归属字段
     * @param groupByField 分组依据字段
     * @param numericGroup true=取该字段值的第一个数字作公称值并加R前缀（管型）；false=直接用原始文字值（如客户名）
     * @param segment 规格串分段取值：0=整串；1=主轴；2=末端；3=壁厚
     *
     * key 用于筛选状态与 UI 的唯一标识；不给时按 field 推导，
     * 同一字段挂多个下拉（如产品型号 → 主轴/末端）必须显式给 key，否则会互相覆盖。
     */
    data class Dropdown(
        val field: String,
        val allLabel: String,
        val groupByField: String = field,
        val numericGroup: Boolean = true,
        val segment: Int = 0,
        /** 筛选状态唯一标识，留空时取 field */
        val id: String = field
    )

    data class Cat(
        val key: String,
        val title: String,
        val desc: String,
        val titleField: String,
        val subFields: List<String>,
        val fields: List<String>,
        val lengthCalc: Boolean = false,   // 是否启用备尺/飞剪长度自动计算
        val dropdowns: List<Dropdown> = emptyList()   // 列表页下拉筛选（按字段去重）
    )

    val all = listOf(
        Cat(
            "customer_products", "客户产品参数", "各客户规格及尺寸范围、爆破压力、备尺计算",
            "规格型号(mm)",
            listOf("客户名称", "主轴范围(mm)", "短轴范围(mm)", "末端范围(mm)"),
            listOf("客户名称", "规格型号(mm)", "主轴范围(mm)", "短轴范围(mm)", "末端范围(mm)", "压力/备注(MPa)", "成品长度(mm)", "裁切刀数", "原料品牌", "原料规格", "核对状态", "备注"),
            lengthCalc = true,
            dropdowns = listOf(
                Dropdown("客户名称", "全部客户", numericGroup = false),
                Dropdown("主轴范围(mm)", "全部主轴型号", groupByField = "规格型号(mm)"),
                Dropdown("末端范围(mm)", "全部末端型号", groupByField = "规格型号(mm)", numericGroup = false, segment = 2, id = "tip"),
                Dropdown("压力/备注(MPa)", "全部壁厚", groupByField = "规格型号(mm)", numericGroup = false, segment = 3, id = "wall")
            )
        ),
        Cat(
            "size_standards", "产品尺寸标准", "管材尺寸及允许偏差（凯宏 表3）、备尺计算",
            "规格/mm",
            listOf("长轴A(±0.04)", "短轴B(±0.05)", "末端C(±0.04)", "R"),
            listOf("规格/mm", "长轴A(±0.04)", "短轴B(±0.05)", "末端C(±0.04)", "R", "成品长度(mm)", "裁切刀数", "原料品牌", "原料规格", "核对状态", "备注"),
            lengthCalc = true,
            dropdowns = listOf(
                Dropdown("长轴A(±0.04)", "全部长轴型号", groupByField = "规格/mm"),
                Dropdown("末端C(±0.04)", "全部末端型号", groupByField = "规格/mm", numericGroup = false, segment = 2, id = "tip")
            )
        ),
        Cat(
            "molds", "模具配置台账", "五套模具的架次-辊位-模具编号",
            "模具编号",
            listOf("产品型号", "架次", "辊位类型"),
            listOf("产品型号", "架次", "辊位类型", "模具编号", "备注"),
            dropdowns = listOf(
                Dropdown("产品型号", "全部产品型号", numericGroup = false),
                Dropdown("产品型号", "全部末端型号", numericGroup = false, segment = 2, id = "tip")
            )
        ),
        Cat(
            "weld_params", "焊接工艺参数", "高频焊关键工艺参数速查",
            "参数项目",
            listOf("关键数值/要点"),
            listOf("参数项目", "关键数值/要点", "备注")
        ),
        Cat(
            "faults", "常见故障处理", "故障现象、原因与处理方法",
            "故障现象",
            listOf("主要原因", "处理方法"),
            listOf("故障现象", "主要原因", "处理方法")
        ),
        Cat(
            "qc_standards", "质量检测标准", "尺寸公差、爆破压力、检测标准",
            "项目",
            listOf("类别", "标准要求"),
            listOf("类别", "项目", "标准要求", "备注")
        )
    )

    fun byKey(key: String): Cat = all.first { it.key == key }
}

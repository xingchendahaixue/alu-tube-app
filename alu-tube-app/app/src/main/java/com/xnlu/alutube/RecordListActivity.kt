package com.xnlu.alutube

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class RecordListActivity : AppCompatActivity() {
    private lateinit var cat: CategoryConfig.Cat
    private lateinit var adapter: RecordAdapter
    private var filter: String = ""
    private val filterValues = mutableMapOf<String, String>()   // 下拉筛选：下拉 id -> 选中值
    private val spinners = mutableListOf<Pair<CategoryConfig.Dropdown, androidx.appcompat.widget.AppCompatSpinner>>()
    /** 当前类目的全量数据（内存缓存），搜索/筛选都在这份数据上做，不再反复查库 */
    private var allRows: List<DataDb.Row> = emptyList()
    private lateinit var clearBtn: com.google.android.material.button.MaterialButton
    private val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingSearch: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataDb.init(this)
        cat = CategoryConfig.byKey(intent.getStringExtra("category")!!)
        setContentView(R.layout.activity_record_list)
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.title = cat.title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val search = findViewById<EditText>(R.id.searchBox)
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                filter = s?.toString() ?: ""
                // 防抖：连续输入只在停顿 180ms 后筛一次
                pendingSearch?.let { searchHandler.removeCallbacks(it) }
                val task = Runnable { applyFilter() }
                pendingSearch = task
                searchHandler.postDelayed(task, 180)
            }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        adapter = RecordAdapter()
        findViewById<RecyclerView>(R.id.list).apply {
            layoutManager = LinearLayoutManager(this@RecordListActivity)
            adapter = this@RecordListActivity.adapter
        }

        // 下拉筛选（按分类配置动态生成：客户 / 主轴型号 / 长轴型号…）
        val row = findViewById<LinearLayout>(R.id.spinnerRow)
        for (cfg in cat.dropdowns) {
            val sp = androidx.appcompat.widget.AppCompatSpinner(this)
            sp.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, dp(10), 0) }
            row.addView(sp)
            sp.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val newFilter = if (pos == 0) "" else sp.adapter?.getItem(pos)?.toString() ?: ""
                    if (newFilter != filterValues[cfg.id]) {
                        filterValues[cfg.id] = newFilter
                        applyFilter()
                    }
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
            spinners.add(cfg to sp)
        }

        clearBtn = com.google.android.material.button.MaterialButton(
            this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle
        ).apply {
            text = "清空"
            textSize = 12f
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(12), 0, dp(12), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)
            )
            visibility = View.GONE
            setOnClickListener {
                filterValues.clear()
                search.setText("")
                filter = ""
                for ((_, sp) in spinners) sp.setSelection(0, false)
                applyFilter()
            }
        }
        row.addView(clearBtn)

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.fab).setOnClickListener {
            startActivity(
                Intent(this, RecordEditActivity::class.java)
                    .putExtra("category", cat.key)
                    .putExtra("id", -1L)
            )
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    /** 进页面/从编辑页返回时刷新一次数据、下拉选项，然后套用当前筛选 */
    private fun loadData() {
        allRows = DataDb.rows(cat.key)
        refreshSpinners()
        applyFilter()
    }

    /** 纯内存筛选：搜索关键字（AND）+ 下拉筛选 */
    private fun applyFilter() {
        val kws = filter.trim().lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val items = allRows.asSequence()
            .filter { row -> kws.all { row.searchText.contains(it) } }
            .filter { row ->
                spinners.all { (cfg, _) ->
                    val v = filterValues[cfg.id]
                    v.isNullOrBlank() || recordKey(row.data, cfg) == v
                }
            }
            .map { it.id to it.data }
            .toList()
        adapter.submit(items)
        findViewById<TextView>(R.id.count).text = "共 ${items.size} 条"
        if (this::clearBtn.isInitialized) {
            val active = filter.isNotBlank() || filterValues.values.any { it.isNotBlank() }
            clearBtn.visibility = if (active) View.VISIBLE else View.GONE
        }
    }

    /**
     * 记录在某个下拉下的分组键。
     * segment>0 时从规格串里取第 N 段（1=主轴，2=末端，3=壁厚）；
     * 否则按 numericGroup 走「公称值+R前缀」或「原始文字」。
     */
    private fun recordKey(map: Map<String, String>, cfg: CategoryConfig.Dropdown): String? {
        val raw = map[cfg.groupByField]
        if (cfg.segment > 0) return specSegment(raw, cfg.segment)
        return if (cfg.numericGroup) tubeLabel(groupKey(raw)) else raw?.takeIf { it.isNotBlank() }
    }

    /**
     * 从规格串取第 n 段（1 起算）：水箱管16×1.5 / R16×2.0×0.3 / 16*1.5*0.26
     * 第 1 段必须是数字，否则视为非规格行（尺寸标准表里的「2. 同一批次…」这类说明），返回 null。
     */
    private fun specSegment(value: String?, n: Int): String? {
        if (value.isNullOrBlank()) return null
        val s = SPEC_PREFIX.replace(value.trim(), "")
        val parts = s.split(*SPEC_SEPS)
        if (parts.isEmpty() || parts[0].trim().toDoubleOrNull() == null) return null
        if (n - 1 >= parts.size) return null
        return parts[n - 1].trim().takeIf { it.isNotBlank() }
    }

    /** 复合值排序键：0.28/0.3 → 0.28；非数字排最后 */
    private fun sortKey(value: String): Double =
        value.substringBefore('/').toDoubleOrNull() ?: Double.MAX_VALUE

    /** 下拉选项来自已加载的全量数据，不再额外查库（原来每个下拉都会再全表查一次） */
    private fun refreshSpinners() {
        for ((cfg, sp) in spinners) {
            val keys = if (cfg.segment > 0) {
                allRows.mapNotNull { row -> specSegment(row.data[cfg.groupByField], cfg.segment) }
                    .distinct()
                    .sortedBy { sortKey(it) }
            } else if (cfg.numericGroup) {
                allRows.mapNotNull { row -> tubeLabel(groupKey(row.data[cfg.groupByField])) }
                    .distinct()
                    .sortedBy { it.removePrefix("R").toFloatOrNull() ?: Float.MAX_VALUE }
            } else {
                allRows.mapNotNull { row -> row.data[cfg.groupByField]?.takeIf { it.isNotBlank() } }
                    .distinct()
                    .sorted()
            }
            val options = mutableListOf(cfg.allLabel)
            options.addAll(keys)
            val adapter2 = android.widget.ArrayAdapter(
                this, android.R.layout.simple_spinner_item, options
            ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
            sp.adapter = adapter2
            val current = filterValues[cfg.id] ?: ""
            val sel = options.indexOf(current)
            if (sel > 0 && sp.selectedItemPosition != sel) {
                sp.setSelection(sel, false)
            }
        }
    }

    /**
     * 取公称值：规格串第 1 段（R16×1.5×0.3 / 16*1.5*0.3 / 水箱管26×2.0 → 16 / 26）。
     * 第 1 段不是数字的说明行返回 null，避免生成 R2、R3 这类假选项。
     */
    private fun groupKey(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val s = SPEC_PREFIX.replace(value.trim(), "")
        val head = s.split(*SPEC_SEPS).firstOrNull()?.trim() ?: return null
        return head.toDoubleOrNull()?.let { LengthCalc.fmt(it) }
    }

    /** 管型显示名：统一加 R 前缀（R12、R16、R23.5…，与模具编号习惯一致） */
    private fun tubeLabel(key: String?): String? {
        if (key == null) return null
        return "R$key"
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private companion object {
        /** 规格串前缀（水箱管 / R） */
        val SPEC_PREFIX = Regex("^(水箱管|R)")
        /** 规格串分隔符：× * x X */
        val SPEC_SEPS = charArrayOf('×', '*', 'x', 'X')
    }

    inner class RecordAdapter : RecyclerView.Adapter<RecordAdapter.VH>() {
        private val items = mutableListOf<Pair<Long, Map<String, String>>>()

        fun submit(list: List<Pair<Long, Map<String, String>>>) {
            items.clear()
            items.addAll(list)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val (id, map) = items[pos]
            h.title.text = map[cat.titleField]?.ifBlank { "(未填写)" } ?: "(未填写)"
            val parts = cat.subFields.mapNotNull { f ->
                map[f]?.takeIf { it.isNotBlank() }?.let { "$f: $it" }
            }.toMutableList()
            if (cat.lengthCalc) {
                val stock = calcStock(map)
                if (stock != null) {
                    parts.add("备尺 ${LengthCalc.fmt(stock.first)}${if (!stock.second) "⚠超范围" else ""} | 飞剪 ${LengthCalc.fmt(LengthCalc.shearLow(stock.first))}-${LengthCalc.fmt(LengthCalc.shearHigh(stock.first))}")
                }
            }
            h.sub.text = parts.joinToString("  |  ")
            h.itemView.setOnClickListener {
                startActivity(
                    Intent(this@RecordListActivity, RecordEditActivity::class.java)
                        .putExtra("category", cat.key)
                        .putExtra("id", id)
                )
            }
        }

        private fun calcStock(map: Map<String, String>): Pair<Double, Boolean>? {
            val finished = map["成品长度(mm)"]?.toDoubleOrNull() ?: return null
            val cuts = map["裁切刀数"]?.toIntOrNull() ?: LengthCalc.recommendCuts(finished)
            val stock = LengthCalc.stockLength(finished, cuts)
            return stock to LengthCalc.isStockInRange(stock)
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.itemTitle)
            val sub: TextView = v.findViewById(R.id.itemSub)
        }
    }
}

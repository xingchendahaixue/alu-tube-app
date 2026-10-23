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
    private val filterValues = mutableMapOf<String, String>()   // 下拉筛选：字段 -> 选中值
    private val spinners = mutableListOf<Pair<CategoryConfig.Dropdown, androidx.appcompat.widget.AppCompatSpinner>>()
    /** 当前类目的全量数据（内存缓存），搜索/筛选都在这份数据上做，不再反复查库 */
    private var allRows: List<DataDb.Row> = emptyList()
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
        val row = findViewById<LinearLayout>(R.id.filterRow)
        for (cfg in cat.dropdowns) {
            val sp = androidx.appcompat.widget.AppCompatSpinner(this)
            row.addView(sp, row.childCount - 1)   // 插在计数 TextView 之前
            sp.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    val newFilter = if (pos == 0) "" else sp.adapter?.getItem(pos)?.toString() ?: ""
                    if (newFilter != filterValues[cfg.field]) {
                        filterValues[cfg.field] = newFilter
                        applyFilter()
                    }
                }
                override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
            }
            spinners.add(cfg to sp)
        }

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
                    val v = filterValues[cfg.field]
                    v.isNullOrBlank() || recordKey(row.data, cfg) == v
                }
            }
            .map { it.id to it.data }
            .toList()
        adapter.submit(items)
        findViewById<TextView>(R.id.count).text = "共 ${items.size} 条"
    }

    /** 记录在某个下拉下的分组键：文字字段用原始值，管型字段用公称值+R前缀 */
    private fun recordKey(map: Map<String, String>, cfg: CategoryConfig.Dropdown): String? {
        val raw = map[cfg.groupByField]
        return if (cfg.numericGroup) tubeLabel(groupKey(raw)) else raw?.takeIf { it.isNotBlank() }
    }

    /** 下拉选项来自已加载的全量数据，不再额外查库（原来每个下拉都会再全表查一次） */
    private fun refreshSpinners() {
        for ((cfg, sp) in spinners) {
            val keys = if (cfg.numericGroup) {
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
            val current = filterValues[cfg.field] ?: ""
            val sel = options.indexOf(current)
            if (sel > 0 && sp.selectedItemPosition != sel) {
                sp.setSelection(sel, false)
            }
        }
    }

    /** 取公称值：字段值的第一个数字（16*1.5*0.3 → "16"） */
    private fun groupKey(value: String?): String? {
        if (value.isNullOrBlank()) return null
        return Regex("^\\s*(\\d+(?:\\.\\d+)?)").find(value)?.groupValues?.get(1)
    }

    /** 管型显示名：统一加 R 前缀（R12、R16、R23.5…，与模具编号习惯一致） */
    private fun tubeLabel(key: String?): String? {
        if (key == null) return null
        return "R$key"
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

# -*- coding: utf-8 -*-
"""从工艺档案 Excel 导出 APP 用的 JSON 数据"""
import openpyxl, json, sys, io

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
wb = openpyxl.load_workbook('高频焊铝管生产工艺数据档案.xlsx', data_only=True)

def rows(sheet, header_row=1, start=2):
    ws = wb[sheet]
    headers = [c.value for c in ws[header_row]]
    out = []
    for row in ws.iter_rows(min_row=start, values_only=True):
        if all(v is None or str(v).strip() == '' for v in row):
            continue
        rec = {}
        for h, v in zip(headers, row):
            if h is None:
                continue
            key = str(h).strip()
            rec[key] = '' if v is None else str(v).strip()
        out.append(rec)
    return out

data = {}
data['customer_products'] = rows('客户产品参数')      # 客户产品参数
data['size_standards'] = rows('产品尺寸标准', header_row=2, start=3)  # 第1行标题、第2行表头
data['molds'] = rows('模具配置台账')
data['weld_params'] = rows('焊接工艺参数')
data['faults'] = rows('常见故障处理')
data['qc_standards'] = rows('质量检测标准', start=3)   # 第2行起是"一、外形尺寸公差"下表头

# 质量检测标准表里有三个分段，手动处理
ws = wb['质量检测标准']
qc = []
section = ''
for row in ws.iter_rows(values_only=True):
    vals = ['' if v is None else str(v).strip() for v in row]
    if any(vals):
        joined = ' '.join(v for v in vals if v)
        if joined.startswith(('一、', '二、', '三、', '四、', '五、')):
            section = joined
            continue
        if joined.startswith(('项目', '管号', '检测项目')):
            continue
        if joined.startswith(('计算公式',)):
            qc.append({'类别': '公式', '项目': '爆破压力公式', '标准要求': joined, '备注': ''})
            continue
        if len(vals) >= 3 and vals[0]:
            qc.append({'类别': section, '项目': vals[0], '标准要求': vals[1] if len(vals) > 1 else '', '备注': vals[2] if len(vals) > 2 else ''})
data['qc_standards'] = qc

with open('app_data.json', 'w', encoding='utf-8') as f:
    json.dump(data, f, ensure_ascii=False, indent=1)

for k, v in data.items():
    print(k, len(v))

# 铝管工艺通（AluTubeProcess）

高频焊铝管车间现场使用的工艺数据速查 + 备尺计算 + 生产报表 App。

- 包名 `com.xnlu.alutube`，minSdk 26 / targetSdk 34，Kotlin
- 当前版本 **3.1**（versionCode 22）
- 数据来源：《高频焊铝管生产工艺数据档案》（2026-10-20 建档）

## 目录结构

```
生产数据APP/
├── alu-tube-app/                         Android 工程
│   ├── 高频焊铝管生产工艺数据档案.xlsx   数据源（唯一权威来源）
│   ├── export_data.py                   Excel → app_data.json 导出脚本
│   ├── gradle.properties                含 android.overridePathCheck=true（路径含中文）
│   └── app/
│       ├── build.gradle.kts
│       ├── proguard-rules.pro
│       └── src/main/
│           ├── assets/app_data.json     打包进 APK 的档案数据
│           └── java/com/xnlu/alutube/   业务代码
├── releases/                            交付 APK
└── _backup/                             改造前源码快照（可删）
```

## 构建

工程内自带 Gradle 8.7（`alu-tube-app/gradle-8.7/`），无需额外安装：

```powershell
cd alu-tube-app
.\gradle-8.7\bin\gradle.bat assembleRelease assembleDebug --offline
```

产物：

- `app/build/outputs/apk/release/app-release.apk` —— 正式包（R8 压缩，体积减半）
- `app/build/outputs/apk/debug/app-debug.apk` —— 调试包

> 两种包都使用 **debug 签名**。这是刻意保持的：历史 v1.0–v2.6 全部是 debug 签名，
> 换签名会导致已装设备无法覆盖升级，必须先卸载。

## 德钢规格表（2026-10-25 换新）

| 序号 | A 主轴(+0.03/−0.02) | B 短轴(±0.05) | C 末端(+0.03/−0.02) | T 壁厚 |
|---|---|---|---|---|
| 1 | 16 | 2.2 | 2 | 0.3 |
| 2 | 22.5 | 1.6 | 1.4 | 0.3 |
| 3 | 22.5 | 1.7 | 1.5 | 0.3 |
| 4 | 23.5 | 1.7 | 1.5 | 0.3 |
| 5 | 23.5 | 2.2 | 2 | 0.3 |
| 6 | 24 | 1.6 | 1.4 | 0.26 |
| 7 | 26 | 1.7 | 1.5 | 0.3 |
| 8 | 26 | 2.2 | 2 | 0.3 |
| 9 | 26 | 1.7 | 1.5 | 0.26 |
| 10 | 32 | 1.7 | 1.5 | 0.3 |
| 11 | 32 | 2.25 | 2 | 0.3 |

## 数据更新流程

改完 Excel 后：

```powershell
cd alu-tube-app
python export_data.py                                   # 生成 app_data.json
Copy-Item app_data.json app\src\main\assets\app_data.json -Force
.\gradle-8.7\bin\gradle.bat assembleRelease --offline
```

注意：`export_data.py` 只导出 6 张业务表，`目录` 与 `知识文档索引` 是说明页，不参与导出。

## 核心业务规则

| 规则 | 说明 |
|---|---|
| 规格命名 | `R{主轴A}×{末端C}×{壁厚T}`，例：R16×2.0×0.3（客户产品、尺寸标准全表统一带 R 前缀） |
| 备尺 | `(成品长度 + 2.5) × 裁切刀数 + 40`（40 = 固定料头） |
| 刀数推荐 | 取备尺落在 5200–5950 区间内的**最大**刀数（备尺靠近上限）；无整数解时取最接近上限的刀数并提示超范围 |
| 飞剪长度 | 备尺 + 25 ~ 备尺 + 30（飞剪损耗） |
| 示例 | 成品 222 → 刀数 26 → 备尺 5877 → 飞剪 5902–5907 |
| 报表·实际耗用 | 当日累计合格品 + 损耗 + 废品总数 |
| 报表·本班结存 | 上班转入 + 当班领用合计 − 退库量 − 实际耗用 |

## 数据存储

- 首启从 `assets/app_data.json` 灌入 SQLite（`process_data.db`），之后编辑只改本机数据库
- 照片存 `filesDir/photos/<记录id>/`
- `DataDb.DATA_FIX_VERSION`：修正档案数据后 +1，升级后首次启动自动套用到手机已有数据

## 版本历史

| 版本 | 说明 |
|---|---|
| 1.0 – 2.1 | 档案速查、备尺计算、报表 |
| 2.2 – 2.6 | 客户/管型下拉筛选、搜索、照片附件、数据纠错 |
| **2.7**（2026-10-24） | 列表性能优化 + APK 体积优化（5.57 MB → 1.64 MB） |
| **2.8**（2026-10-25） | 备尺公式更新为 `(成品长度+2.5)×刀数+40`，飞剪损耗区间改 25–30 |
| **2.9**（2026-10-25） | 德钢产品尺寸按客户新表整体换新（11 个规格，公差 A +0.03/−0.02、B ±0.05、C +0.03/−0.02） |
| **3.0**（2026-10-25） | 产品规格型号统一命名 `R{主轴}×{末端}×{壁厚}`；管型分组识别 R 前缀；DATA_FIX v4 自动改名 |
| **3.1**（2026-10-25） | 产品尺寸标准（凯宏表3）14 个规格也加 R 前缀（16×1.5 → R16×1.5，DATA_FIX v5）；备尺目标区间改为 5200–5950，刀数推荐改取区间内最大刀数（靠近上限） |

## 2.7 优化记录

1. **列表/搜索性能**：移除 N+1 查询（原来每次按键要查 3 次全表并重复解析 JSON），
   改为一次读取 + 内存缓存 + 预生成搜索文本；搜索增加 180ms 防抖；下拉选项不再触发查库。
2. **数据库**：`records(category, sort)` 建索引，DB 版本 2 → 3（自动迁移，不丢数据）。
3. **照片**：缩略图解码改为固定 2 线程池，避免一图一线程。
4. **包体积**：release 开启 R8 + 资源裁剪，APK **5.57 MB → 1.64 MB（-70.6%）**，dex 由 3 个合为 1 个。
5. **工程**：补 `.gitignore`、`proguard-rules.pro`、`android.overridePathCheck`（中文路径），release 关闭 lintVital 提速。

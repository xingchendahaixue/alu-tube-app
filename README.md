# 铝管工艺通（AluTubeProcess）

高频焊铝管车间现场使用的工艺数据速查 + 备尺计算 + 生产报表 App。

- 包名 `com.xnlu.alutube`，minSdk 26 / targetSdk 34，Kotlin
- 当前版本 **2.6**（versionCode 17）
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
| 备尺 | `(成品长度 + 3) × 裁切刀数 + 35` |
| 刀数推荐 | 取使备尺最接近 5650 的整数，备尺目标区间 5400–5900 |
| 飞剪长度 | 备尺 + 25 ~ 备尺 + 45 |
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

## 2.7 优化记录

1. **列表/搜索性能**：移除 N+1 查询（原来每次按键要查 3 次全表并重复解析 JSON），
   改为一次读取 + 内存缓存 + 预生成搜索文本；搜索增加 180ms 防抖；下拉选项不再触发查库。
2. **数据库**：`records(category, sort)` 建索引，DB 版本 2 → 3（自动迁移，不丢数据）。
3. **照片**：缩略图解码改为固定 2 线程池，避免一图一线程。
4. **包体积**：release 开启 R8 + 资源裁剪，APK **5.57 MB → 1.64 MB（-70.6%）**，dex 由 3 个合为 1 个。
5. **工程**：补 `.gitignore`、`proguard-rules.pro`、`android.overridePathCheck`（中文路径），release 关闭 lintVital 提速。

# 铝管工艺通 R8 规则
# 本应用不使用反射加载业务类，org.json / SQLite / AppCompat 均由平台或官方默认规则覆盖，
# 因此这里只做最小保留，避免误删。

# 保留 Activity 的类名（系统按 AndroidManifest 反射实例化）
-keep class com.xnlu.alutube.**Activity { *; }

# 保留行号，便于线上崩溃定位
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin 元数据（协程/反射类库有时需要）
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

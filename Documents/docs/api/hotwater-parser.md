---
title: HotWaterParser
---

# HotWaterParser

`HotWaterParser` 解析后勤平台热水信息页面的 HTML，提取各楼栋的温度与水位。

`cn.edu.shmtu.cas.parser.HotWaterParser`

## 构造函数

```kotlin
class HotWaterParser(htmlCode: String? = null)
```

构造时传 HTML 会自动调用 `getHotWaterUl`。

## 方法

### getHotWaterUl

```kotlin
fun getHotWaterUl(htmlCode: String): HotWaterParser
```

解析 HTML，定位 `#tab1 > div > div > ul` 下的所有 `li`，每个 `li` 代表一栋楼。

### getHotWaterList

```kotlin
fun getHotWaterList(): MutableList<Triple<Float, Float, Int>>
```

返回 `List<Triple<温度, 水位, 楼号>>`。

| 位置 | 类型 | 含义 |
|------|------|------|
| first | `Float` | 温度（℃） |
| second | `Float` | 水位百分比（0-100） |
| third | `Int` | 楼号 |

## 解析规则

每个 `li` 下查找 `div.bagreen`，子元素必须是 3 个：

1. 温度：`XX℃`，提取数字
2. 水位：`XX%水位` 或 `水位XX%`，提取数字
3. 楼号：`X号楼`，提取数字

任意一项解析失败则跳过该条（`NumberFormatException` 容忍）。

## 使用示例

```kotlin
val html = wechat.getHotWater().getOrThrow()
val list = HotWaterParser(html).getHotWaterList()

for ((temperature, waterLevel, building) in list) {
    println("$building 号楼: $temperature ℃, 水位 $waterLevel %")
}

// 或链式
HotWaterParser()
    .getHotWaterUl(html)
    .getHotWaterList()
```

## 字段顺序约定

`Triple` 的字段顺序是**温度、水位、楼号**。`getHotWaterList()` 的 Kotlin 解构就是 `for ((t, w, b) in list)`。

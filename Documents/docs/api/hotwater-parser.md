---
title: HotWaterParser API 参考
---

# HotWaterParser API 参考

`HotWaterParser` 用于解析后勤服务平台热水信息页面的 HTML，提取各楼栋的热水温度和水位数据。位于 `cn.edu.shmtu.cas.parser` 包。

## 构造函数

```kotlin
class HotWaterParser(htmlCode: String? = null)
```

### 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| htmlCode | String? | `null` | 热水页面 HTML 内容。传入时自动调用 `getHotWaterUl()` |

---

## getHotWaterUl

解析热水页面 HTML，提取热水信息列表元素。

```kotlin
fun getHotWaterUl(htmlCode: String): HotWaterParser
```

### 参数

| 参数 | 类型 | 说明 |
|------|------|------|
| htmlCode | String | 热水页面 HTML 内容 |

### 返回值

`HotWaterParser` - 返回自身，支持链式调用。

### 说明

- 使用 Jsoup 解析 HTML
- 查找 `#tab1 > div > div > ul` 下的所有 `li` 元素
- 每个 `li` 元素包含一个楼栋的热水信息

---

## getHotWaterList

获取解析后的热水信息列表。

```kotlin
fun getHotWaterList(): MutableList<Triple<Float, Float, Int>>
```

### 返回值

`MutableList<Triple<Float, Float, Int>>` - 热水信息列表。

### 三元组说明

| 位置 | 类型 | 说明 |
|------|------|------|
| first | Float | 热水温度（摄氏度） |
| second | Float | 水位百分比（0-100） |
| third | Int | 楼号 |

### 解析规则

每个 `li` 元素下查找 `div.bagreen`，其子元素结构为：

1. 温度：`XX℃`，提取数字部分
2. 水位：`XX%水位` 或 `水位XX%`，提取数字部分
3. 楼号：`X号楼`，提取数字部分

无法解析的数据会被跳过（`NumberFormatException` 时 continue）。

---

## 使用示例

```kotlin
// 方式一：构造时传入 HTML
val parser = HotWaterParser(htmlContent)
val list = parser.getHotWaterList()

// 方式二：链式调用
val list = HotWaterParser()
    .getHotWaterUl(htmlContent)
    .getHotWaterList()

// 遍历结果
for ((temperature, waterLevel, building) in list) {
    println("${building}号楼 - 温度: ${temperature}℃ - 水位: ${waterLevel}%")
}
```

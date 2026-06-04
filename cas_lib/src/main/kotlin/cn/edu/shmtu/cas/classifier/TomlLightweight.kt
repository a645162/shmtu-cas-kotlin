package cn.edu.shmtu.cas.classifier

// 极简 TOML 解析器 — 仅为对齐 Tauri 数据库 bill/*.toml 4 个文件的语法子集:
//   - 注释行以 '#' 开头
//   - 表格头: [a.b] / [a.b."key.with.dots"]
//   - 数组表格头: [[a]]
//   - 键值对: key = "string" / key = ["a", "b"] / key = 123 / key = true
//
// 解析后产物是一个 Map<String, Any?> 嵌套结构:
//   - String       → String
//   - 数字字面量    → String(原样保留,与 Tauri serde 行为一致避免精度问题)
//   - 布尔字面量    → String("true"/"false")
//   - 数组         → List<String>
//   - 子表         → Map<String, Any?>
//   - 数组表格     → List<Map<String, Any?>>
object TomlLightweight {

    @Suppress("unused")
    class TomlParseException(msg: String) : RuntimeException(msg)

    fun parse(text: String): Map<String, Any?> {
        val root = LinkedHashMap<String, Any?>()
        val stack = ArrayDeque<TableScope>()
        stack.addLast(PlainTableScope(root))

        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = stripComment(raw).trim()
            i++
            if (line.isEmpty()) continue

            when {
                line.startsWith("[[") && line.endsWith("]]") -> {
                    val path = parseDottedKey(line.substring(2, line.length - 2).trim())
                    val scope = pushArrayTable(stack, path)
                    stack.addLast(scope)
                }
                line.startsWith("[") && line.endsWith("]]") == false && line.endsWith("]") -> {
                    val path = parseDottedKey(line.substring(1, line.length - 1).trim())
                    val scope = pushTable(stack, path)
                    stack.addLast(scope)
                }
                line.contains("=") -> {
                    val eqIdx = line.indexOf('=')
                    val keyRaw = line.substring(0, eqIdx).trim()
                    val valueRaw = line.substring(eqIdx + 1).trim()
                    val key = parseKeySegment(keyRaw)
                    val value = parseValue(valueRaw)
                    val current = stack.last()
                    require(current !is ArrayTableScope || current.firstKey == null) {
                        "在 [[array]] 表内赋值的 key 必须是数组的第一个字段(line=$line)"
                    }
                    if (current is ArrayTableScope) current.firstKey = key
                    current.map[key] = value
                }
                else -> throw TomlParseException("无法识别的 TOML 行: $line")
            }
        }
        return root
    }

    private fun stripComment(line: String): String {
        var inStr = false
        var escape = false
        for ((idx, ch) in line.withIndex()) {
            if (escape) { escape = false; continue }
            if (ch == '\\') { escape = true; continue }
            if (ch == '"') { inStr = !inStr; continue }
            if (!inStr && ch == '#') return line.substring(0, idx)
        }
        return line
    }

    private fun parseDottedKey(dotted: String): List<String> {
        return dotted.split('.').map { parseKeySegment(it) }
    }

    /**
     * 解析单个 key 段:
     *   - 裸标识: a_b-c
     *   - 引用字符串: "a.b" / "with\"quote" / 'a.b'
     */
    private fun parseKeySegment(seg: String): String {
        val s = seg.trim()
        return when {
            s.startsWith("\"") && s.endsWith("\"") && s.length >= 2 -> unescape(s.substring(1, s.length - 1))
            s.startsWith("'") && s.endsWith("'") && s.length >= 2 -> s.substring(1, s.length - 1)
            else -> s
        }
    }

    private fun unescape(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    '"' -> sb.append('"')
                    '\\' -> sb.append('\\')
                    else -> { sb.append(c); sb.append(s[i + 1]) }
                }
                i += 2
            } else {
                sb.append(c); i++
            }
        }
        return sb.toString()
    }

    private fun parseValue(raw: String): Any {
        val s = raw.trim()
        return when {
            s.startsWith("[") && s.endsWith("]") -> parseArray(s)
            s.startsWith("\"") && s.endsWith("\"") -> unescape(s.substring(1, s.length - 1))
            s.startsWith("'") && s.endsWith("'") -> s.substring(1, s.length - 1)
            s == "true" || s == "false" -> s
            else -> s // 数字 / 其它 → 全部按字符串原样保留
        }
    }

    private fun parseArray(s: String): List<String> {
        val body = s.substring(1, s.length - 1)
        val items = mutableListOf<String>()
        var depth = 0
        var inStr = false
        var quoteCh = ' '
        var escape = false
        val cur = StringBuilder()
        for (ch in body) {
            if (escape) { cur.append(ch); escape = false; continue }
            if (ch == '\\' && inStr) { cur.append(ch); escape = true; continue }
            if (inStr) {
                if (ch == quoteCh) { cur.append(ch); inStr = false; continue }
                cur.append(ch); continue
            }
            when (ch) {
                '"', '\'' -> { inStr = true; quoteCh = ch; cur.append(ch) }
                '[' -> { depth++; cur.append(ch) }
                ']' -> { depth--; cur.append(ch) }
                ',' -> {
                    if (depth == 0) {
                        val t = cur.toString().trim()
                        if (t.isNotEmpty()) items.add(unquoteArrayItem(t))
                        cur.setLength(0)
                    } else cur.append(ch)
                }
                else -> cur.append(ch)
            }
        }
        val tail = cur.toString().trim()
        if (tail.isNotEmpty()) items.add(unquoteArrayItem(tail))
        return items
    }

    private fun unquoteArrayItem(t: String): String {
        val s = t.trim()
        return when {
            s.startsWith("\"") && s.endsWith("\"") && s.length >= 2 -> unescape(s.substring(1, s.length - 1))
            s.startsWith("'") && s.endsWith("'") && s.length >= 2 -> s.substring(1, s.length - 1)
            else -> s
        }
    }

    @Suppress("unused")
    private abstract class TableScope {
        abstract val map: LinkedHashMap<String, Any?>
    }

    @Suppress("unused")
    private class PlainTableScope(override val map: LinkedHashMap<String, Any?>) : TableScope()

    @Suppress("unused")
    private class ArrayTableScope(override val map: LinkedHashMap<String, Any?>) : TableScope() {
        var firstKey: String? = null
    }

    private fun pushTable(stack: ArrayDeque<TableScope>, path: List<String>): TableScope {
        var current: Any? = null
        var parentMap: LinkedHashMap<String, Any?>? = null
        val iter = stack.iterator()
        // 找到当前最内层 map 作为起点
        if (stack.isNotEmpty()) parentMap = stack.last().map
        for (seg in path) {
            current = parentMap?.get(seg)
            if (current == null) {
                val newMap = LinkedHashMap<String, Any?>()
                parentMap!![seg] = newMap
                current = newMap
            }
            parentMap = when (current) {
                is LinkedHashMap<*, *> -> @Suppress("UNCHECKED_CAST") (current as LinkedHashMap<String, Any?>)
                is ArrayList<*> -> {
                    // 进入数组表格的最后一个元素
                    val list = current as ArrayList<*>
                    @Suppress("UNCHECKED_CAST")
                    val last = list.last() as LinkedHashMap<String, Any?>
                    last
                }
                else -> throw TomlParseException("类型冲突,无法进入 $seg: $current")
            }
        }
        return PlainTableScope(parentMap!!)
    }

    private fun pushArrayTable(stack: ArrayDeque<TableScope>, path: List<String>): TableScope {
        var current: Any? = null
        var parentMap: LinkedHashMap<String, Any?>? = null
        if (stack.isNotEmpty()) parentMap = stack.last().map
        for ((idx, seg) in path.withIndex()) {
            val isLast = idx == path.size - 1
            if (isLast) {
                @Suppress("UNCHECKED_CAST")
                val arr = (parentMap!![seg] as? ArrayList<LinkedHashMap<String, Any?>>)
                    ?: ArrayList<LinkedHashMap<String, Any?>>().also { parentMap[seg] = it }
                val newTable = LinkedHashMap<String, Any?>()
                arr.add(newTable)
                return ArrayTableScope(newTable)
            } else {
                current = parentMap?.get(seg)
                if (current == null) {
                    val newMap = LinkedHashMap<String, Any?>()
                    parentMap!![seg] = newMap
                    current = newMap
                }
                parentMap = when (current) {
                    is LinkedHashMap<*, *> -> @Suppress("UNCHECKED_CAST") (current as LinkedHashMap<String, Any?>)
                    else -> throw TomlParseException("类型冲突,无法进入 $seg: $current")
                }
            }
        }
        throw TomlParseException("空数组表路径")
    }
}

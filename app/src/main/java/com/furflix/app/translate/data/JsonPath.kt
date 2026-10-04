package com.furflix.app.translate.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 迷你 JSONPath（蓝本 §5.1，约 60 行移植）。仅四个构件：
 *   `$`    根（可省略）
 *   `.key` 对象成员
 *   `[n]`  数组下标
 *   `[*]`  遍历数组，对每个元素求值后**无分隔符拼接**
 * 路径非法（缺键/类型不符/越界）抛 IllegalArgumentException，执行器转
 * "unexpected response format"。规则为空 = 整个响应体即译文（由调用方处理）。
 */
fun resolveJsonPath(root: JsonElement, path: String): String {
    val trimmed = path.trim().removePrefix("$")
    val tokens = parseTokens(trimmed, path)
    val sb = StringBuilder()
    evaluate(root, tokens, 0, sb)
    return sb.toString()
}

private sealed interface PathToken {
    data class Key(val name: String) : PathToken
    data class Index(val n: Int) : PathToken
    data object Wildcard : PathToken
}

private fun parseTokens(trimmed: String, original: String): List<PathToken> {
    val tokens = ArrayList<PathToken>()
    var i = 0
    while (i < trimmed.length) {
        when (trimmed[i]) {
            '.' -> {
                var j = i + 1
                while (j < trimmed.length && trimmed[j] != '.' && trimmed[j] != '[') j++
                if (j == i + 1) throw IllegalArgumentException("empty key in path: $original")
                tokens.add(PathToken.Key(trimmed.substring(i + 1, j)))
                i = j
            }
            '[' -> {
                val close = trimmed.indexOf(']', i)
                if (close < 0) throw IllegalArgumentException("unterminated '[' in path: $original")
                val inner = trimmed.substring(i + 1, close).trim()
                if (inner == "*") {
                    tokens.add(PathToken.Wildcard)
                } else {
                    val n = inner.toIntOrNull()
                        ?: throw IllegalArgumentException("bad index '$inner' in path: $original")
                    tokens.add(PathToken.Index(n))
                }
                i = close + 1
            }
            else -> {
                var j = i
                while (j < trimmed.length && trimmed[j] != '.' && trimmed[j] != '[') j++
                tokens.add(PathToken.Key(trimmed.substring(i, j)))
                i = j
            }
        }
    }
    return tokens
}

private fun evaluate(el: JsonElement, tokens: List<PathToken>, idx: Int, sb: StringBuilder) {
    if (idx == tokens.size) {
        when (el) {
            is JsonNull -> sb.append("null")
            is JsonPrimitive -> sb.append(el.content)
            else -> throw IllegalArgumentException("non-scalar value at end of path")
        }
        return
    }
    when (val t = tokens[idx]) {
        is PathToken.Key -> {
            val obj = el as? JsonObject
                ?: throw IllegalArgumentException("expected object for key '${t.name}'")
            val next = obj[t.name] ?: throw IllegalArgumentException("missing key '${t.name}'")
            evaluate(next, tokens, idx + 1, sb)
        }
        is PathToken.Index -> {
            val arr = el as? JsonArray
                ?: throw IllegalArgumentException("expected array for index ${t.n}")
            val next = arr.getOrNull(t.n)
                ?: throw IllegalArgumentException("index ${t.n} out of bounds")
            evaluate(next, tokens, idx + 1, sb)
        }
        is PathToken.Wildcard -> {
            val arr = el as? JsonArray ?: throw IllegalArgumentException("expected array for [*]")
            for (item in arr) evaluate(item, tokens, idx + 1, sb)
        }
    }
}

package com.furflix.app.data.model

import java.util.TreeMap

/**
 * Reply tree built from FA's flat comment list, where each row carries an
 * indentation level (0 = top-level) parsed from the container width style.
 * Pure Kotlin so the algorithm is unit-testable without Android framework.
 */
data class CommentNode(
    val comment: Comment,
    val answers: List<CommentNode>
)

/**
 * Builds the tree in one pass over the flat list: a TreeMap from indentation
 * level to the last comment seen at that level acts as a stack — anything
 * deeper than the current row closes its branch. The parent of a reply is the
 * deepest level strictly shallower than its own; a reply whose indentation
 * skips levels (or opens the list mid-thread) is degraded to a root instead of
 * dropped, mirroring the hidden-comment fallback in the parser.
 */
fun List<Comment>.buildTree(): List<CommentNode> {
    val lastAtIndent = TreeMap<Int, String>()
    val children = HashMap<String, MutableList<Comment>>()
    val roots = mutableListOf<Comment>()

    for (c in this) {
        lastAtIndent.tailMap(c.indentation, false).clear()
        val parent = lastAtIndent.floorEntry(c.indentation - 1)
        if (c.indentation <= 0 || parent == null) {
            roots += c
        } else {
            children.getOrPut(parent.value) { mutableListOf() } += c
        }
        lastAtIndent[c.indentation] = c.id
    }

    fun assemble(c: Comment): CommentNode =
        CommentNode(c, (children[c.id] ?: emptyList()).map { assemble(it) })

    return roots.map { assemble(it) }
}

fun List<CommentNode>.recursiveFirst(pred: (Comment) -> Boolean): CommentNode? =
    firstOrNull { pred(it.comment) } ?: firstNotNullOfOrNull { it.answers.recursiveFirst(pred) }

fun List<CommentNode>.recursiveCount(): Int = sumOf { 1 + it.answers.recursiveCount() }

/** Chain of nodes from a root down to [toCid], or null when absent. */
fun List<CommentNode>.recursivePath(toCid: String): List<CommentNode>? =
    firstNotNullOfOrNull { node ->
        if (node.comment.id == toCid) listOf(node)
        else node.answers.recursivePath(toCid)?.let { listOf(node) + it }
    }

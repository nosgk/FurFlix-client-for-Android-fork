package com.furflix.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommentTreeTest {

    private fun c(id: String, indent: Int) = Comment(id = id, indentation = indent, text = "t$id")

    @Test
    fun `builds nested tree from flat list`() {
        val tree = listOf(c("a", 0), c("b", 8), c("c", 16), c("d", 8)).buildTree()
        assertEquals(listOf("a"), tree.map { it.comment.id })
        assertEquals(listOf("b", "d"), tree[0].answers.map { it.comment.id })
        assertEquals(listOf("c"), tree[0].answers[0].answers.map { it.comment.id })
    }

    @Test
    fun `closes a branch when a shallower sibling appears`() {
        val tree = listOf(c("a", 0), c("b", 8), c("c", 8), c("e", 16)).buildTree()
        assertEquals(listOf("b", "c"), tree[0].answers.map { it.comment.id })
        assertEquals(listOf("e"), tree[0].answers[1].answers.map { it.comment.id })
    }

    @Test
    fun `attaches a level-skipped reply to the nearest shallower comment`() {
        val tree = listOf(c("a", 0), c("c", 16)).buildTree()
        assertEquals(listOf("a"), tree.map { it.comment.id })
        assertEquals(listOf("c"), tree[0].answers.map { it.comment.id })
    }

    @Test
    fun `degrades an orphaned leading reply to a root`() {
        val tree = listOf(c("b", 8), c("a", 0)).buildTree()
        assertEquals(listOf("b", "a"), tree.map { it.comment.id })
        assertEquals(0, tree[0].answers.size)
    }

    @Test
    fun `recursiveCount counts every descendant`() {
        val tree = listOf(c("a", 0), c("b", 8), c("c", 16), c("d", 8)).buildTree()
        assertEquals(4, tree.recursiveCount())
        assertEquals(2, tree[0].answers[0].answers.recursiveCount())
    }

    @Test
    fun `recursivePath returns the chain to the target`() {
        val tree = listOf(c("a", 0), c("b", 8), c("c", 16)).buildTree()
        val path = tree.recursivePath("c")!!
        assertEquals(listOf("a", "b", "c"), path.map { it.comment.id })
        assertNull(tree.recursivePath("missing"))
    }
}

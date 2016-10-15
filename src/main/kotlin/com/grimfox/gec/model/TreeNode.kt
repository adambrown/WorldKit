package com.grimfox.gec.model

import java.util.*

class TreeNode<T>(val value: T, var parent: TreeNode<T>? = null, val children: MutableList<TreeNode<T>> = ArrayList()): Sequence<T> {

    override fun iterator(): Iterator<T> = listOf(listOf(value).asSequence(), children.asSequence().flatten()).asSequence().flatten().iterator()

    fun nodeIterable() : Sequence<TreeNode<T>> {
        return object: Sequence<TreeNode<T>> {
            override fun iterator(): Iterator<TreeNode<T>> = listOf(listOf(this@TreeNode).asSequence(), children.map { it.nodeIterable() }.asSequence().flatten()).asSequence().flatten().iterator()
        }
    }
}

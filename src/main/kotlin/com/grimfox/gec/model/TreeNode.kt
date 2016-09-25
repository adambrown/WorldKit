package com.grimfox.gec.model

import java.util.*

class TreeNode<T>(val value: T, var parent: TreeNode<T>? = null, val children: MutableList<TreeNode<T>> = ArrayList()): Sequence<T> {

    override fun iterator(): Iterator<T> = listOf(listOf(value).asSequence(), children.asSequence().flatten()).asSequence().flatten().iterator()
}

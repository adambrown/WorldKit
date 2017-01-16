package com.grimfox.gec.ui.widgets

import com.grimfox.gec.*
import com.grimfox.gec.util.*
import org.lwjgl.glfw.GLFW
import org.lwjgl.nanovg.NVGColor
import java.util.*

class MenuBar(private val block: Block,
              private val menuLayer: Block,
              private val rowHeight: Float,
              private val textStyle: TextStyle,
              private val textColorInactive: NVGColor,
              private val activeMenu: MutableReference<Pair<Block, () -> Unit>?>,
              private val mouseDownOnActivator: MonitoredReference<Boolean>,
              private val mouseDownOnDeActivator: MonitoredReference<Boolean>,
              private val mouseOverActivator: MonitoredReference<Boolean>,
              private val mouseOverDeActivator: MonitoredReference<Boolean>) {

    fun menu(title: String, builder: DropdownList.() -> Unit): Block {
        return block.menu(title, menuLayer, activeMenu, mouseDownOnActivator, mouseDownOnDeActivator, mouseOverActivator, mouseOverDeActivator, rowHeight, textStyle, textColorInactive, builder)
    }
}

class DropdownList(private val block: Block, private val rowHeight: Float, private val textStyle: TextStyle, private val textColorInactive: NVGColor, private val mouseDownOnActivator: MonitoredReference<Boolean>, private val deactivate: () -> Unit, private val activeItem: MutableReference<Pair<Block, () -> Unit>?>) {

    private val shrinkGroup = hShrinkGroup()

    fun menuItem(text: String, hotKey: String? = null, glyph: Block.() -> Block = {block{}}, isActive: MonitoredReference<Boolean> = ref(true), onClick: () -> Unit): Block {
        return block.menuItem(text(text, textStyle), if (hotKey != null) { text(hotKey, textStyle) } else { NO_TEXT }, glyph, rowHeight, shrinkGroup, textColorInactive, isActive, mouseDownOnActivator, deactivate, activeItem, onClick)
    }

    fun subMenu(text: String, isActive: MonitoredReference<Boolean> = ref(true), builder: DropdownList.() -> Unit): Block {
        return block.subMenu(text(text, textStyle), rowHeight, textStyle, shrinkGroup, textColorInactive, isActive, mouseDownOnActivator, deactivate, activeItem, builder)
    }

    fun removeItem(block: Block) {
        this.block.layoutChildren.remove(block)
        this.block.renderChildren.remove(block)
        val activeItemValue = activeItem.value
        if (activeItemValue != null && activeItemValue.first == block) {
            activeItemValue.second()
            activeItem.value = null
        }
    }

    fun moveItemToIndex(block: Block, index: Int) {
        if (index >= this.block.layoutChildren.size) {
            throw IndexOutOfBoundsException("size: ${this.block.layoutChildren.size}, index: $index")
        }
        if (!this.block.layoutChildren.contains(block) || !this.block.renderChildren.contains(block)) {
            throw IllegalArgumentException("Item is not contained within drop down.")
        }
        this.block.layoutChildren.remove(block)
        this.block.renderChildren.remove(block)
        this.block.layoutChildren.add(index, block)
        this.block.renderChildren.add(index, block)
    }

    fun menuDivider(): Block {
        return block.menuDivider(3.0f, shrinkGroup)
    }
}

fun Block.menuBar(menuLayer: Block, rowHeight: Float, textStyle: TextStyle, textColorInactive: NVGColor, builder: MenuBar.() -> Unit): Block {
    val parent = this
    return block {
        parent.renderChildren.remove(this)
        menuLayer.renderChildren.add(this)
        val mouseDownOnActivator = ref(false)
        val mouseDownOnDeActivator = ref(false)
        val mouseOverActivator = ref(false)
        val mouseOverDeActivator = ref(false)
        val activeMenu = ref<Pair<Block, () -> Unit>?>(null)
        hSizing = Sizing.SHRINK
        vSizing = Sizing.STATIC
        height = rowHeight
        layout = Layout.HORIZONTAL
        isFallThrough = true
        MenuBar(this, menuLayer, rowHeight, textStyle, textColorInactive, activeMenu, mouseDownOnActivator, mouseDownOnDeActivator, mouseOverActivator, mouseOverDeActivator).builder()
    }
}

private fun Block.menu(
        title: String,
        menuLayer: Block,
        activeMenu: MutableReference<Pair<Block, () -> Unit>?>,
        mouseDownOnActivator: MonitoredReference<Boolean>,
        mouseDownOnDeActivator: MonitoredReference<Boolean>,
        mouseOverActivator: MonitoredReference<Boolean>,
        mouseOverDeActivator: MonitoredReference<Boolean>,
        rowHeight: Float,
        textStyle: TextStyle,
        textColorInactive: NVGColor,
        builder: DropdownList.() -> Unit): Block {
    val titleText = text(title, textStyle)
    return block {
        val menu = this
        var dropDown = NO_BLOCK
        var activator = NO_BLOCK
        hSizing = Sizing.SHRINK
        vSizing = Sizing.STATIC
        vAlign = VerticalAlignment.BOTTOM
        height = SMALL_ROW_HEIGHT
        isFallThrough = true
        layout = Layout.HORIZONTAL
        val activeItem: MutableReference<Pair<Block, () -> Unit>?> = mRef(null)
        val deactivateMenu = {
            activeItem.value?.second?.invoke()
            dropDown.isVisible = false
            dropDown.isMouseAware = false
            activator.isMouseAware = true
            menuLayer.isFallThrough = true
        }
        val deactivateMenuBar = {
            activeMenu.value?.second?.invoke()
            activeMenu.value = null
        }
        val activateMenu = {
            activeMenu.value?.second?.invoke()
            dropDown.isVisible = true
            dropDown.isMouseAware = true
            activator.isMouseAware = false
            menuLayer.isFallThrough = false
            activeMenu.value = Pair(menu, deactivateMenu)
        }
        activator = block {
            hSizing = Sizing.SHRINK
            vSizing = Sizing.STATIC
            height = SMALL_ROW_HEIGHT
            block {
                hSizing = Sizing.SHRINK
                vSizing = Sizing.SHRINK
                hAlign = HorizontalAlignment.CENTER
                vAlign = VerticalAlignment.MIDDLE
                padLeft = SMALL_SPACER_SIZE
                padRight = SMALL_SPACER_SIZE
                text = titleText
                isMouseAware = false
            }
        }
        dropDown = block {
            dropDown = this
            isVisible = true
            isMouseAware = true
            layout = Layout.ABSOLUTE
            yOffset = SMALL_ROW_HEIGHT - 1.0f
            xOffset = -1.0f
            hSizing = Sizing.STATIC
            vSizing = Sizing.STATIC
            width = 0.0f
            height = 0.0f
            canOverflow = true
            isFallThrough = true
            menuDropDownList(rowHeight, textStyle, textColorInactive, mouseDownOnActivator, deactivateMenuBar, activeItem, builder)
            block {
                layout = Layout.ABSOLUTE
                hSizing = Sizing.SHRINK
                vSizing = Sizing.STATIC
                height = SMALL_ROW_HEIGHT + 1
                yOffset = -SMALL_ROW_HEIGHT
                canOverflow = true
                shape = SHAPE_MENU_BORDER
                block {
                    hSizing = Sizing.SHRINK
                    vSizing = Sizing.STATIC
                    height = SMALL_ROW_HEIGHT + 2
                    padLeft = 1.0f
                    padRight = 1.0f
                    padTop = 1.0f
                    canOverflow = true
                    shape = SHAPE_MENU_BACKGROUND
                    isMouseAware = false
                    block {
                        hSizing = Sizing.SHRINK
                        vSizing = Sizing.SHRINK
                        hAlign = HorizontalAlignment.CENTER
                        vAlign = VerticalAlignment.MIDDLE
                        yOffset = -1.0f
                        padLeft = SMALL_SPACER_SIZE
                        padRight = SMALL_SPACER_SIZE
                        text = titleText
                    }
                }
                onMouseDown { button, x, y ->
                    mouseDownOnDeActivator.value = true
                }
                onMouseUp { button, x, y ->
                    if (mouseDownOnDeActivator.value) {
                        deactivateMenuBar()
                    }
                }
                onMouseRelease { button, x, y ->
                    mouseDownOnDeActivator.value = false
                }
                onMouseOver {
                    mouseOverDeActivator.value = true
                }
                onMouseOut {
                    mouseOverDeActivator.value = false
                }
            }
        }
        dropDown.isVisible = false
        dropDown.isMouseAware = false
        activator.onMouseOver {
            shape = SHAPE_BUTTON_MOUSE_OVER
            mouseOverActivator.value = true
            val active = activeMenu.value
            if (active != null && active.first != menu) {
                activateMenu()
            }
        }
        activator.onMouseOut {
            shape = NO_SHAPE
            mouseOverActivator.value = false
        }
        activator.onMouseDown { button, x, y ->
            mouseDownOnActivator.value = true
            activateMenu()
        }
        activator.onMouseRelease { button, x, y ->
            if (mouseDownOnActivator.value && !(mouseOverDeActivator.value || mouseOverActivator.value)) {
                deactivateMenuBar()
            }
            mouseDownOnActivator.value = false
        }
        menuLayer.onMouseDown { button, x, y ->
            deactivateMenuBar()
            reprocessTick()
        }
    }
}

private fun Block.menuItem(text: Text,
                           hotKey: Text,
                           glyph: Block.() -> Block,
                           height: Float,
                           shrinkGroup: ShrinkGroup,
                           textColorInactive: NVGColor,
                           isActive: MonitoredReference<Boolean>,
                           mouseDownOnActivator: Reference<Boolean>,
                           deactivate: () -> Unit,
                           activeItem: MutableReference<Pair<Block, () -> Unit>?>,
                           onClick: () -> Unit = {}): Block {
    return block {
        val thisItem = this
        var mouseDownOver = false
        var mouseOver = false
        hSizing = Sizing.SHRINK_GROUP
        hShrinkGroup = shrinkGroup
        vSizing = Sizing.STATIC
        this.height = height
        layout = Layout.VERTICAL
        block{
            hSizing = Sizing.STATIC
            width = 32.0f
            layout = Layout.HORIZONTAL
            isMouseAware = false
            block {
                this.hSizing = Sizing.SHRINK
                this.vSizing = Sizing.SHRINK
                this.hAlign = HorizontalAlignment.CENTER
                this.vAlign = VerticalAlignment.MIDDLE
                val glyphBlock = glyph()
                val subGlyphBlocks = glyphBlock.descendants
                val allTexts = arrayListOf(text, hotKey)
                allTexts.addAll(subGlyphBlocks.map { it.text })
                val inactiveColor = cRef(textColorInactive)
                val textStyles = allTexts.map {
                    Triple(it, it.style, TextStyle(it.style.size, it.style.font, inactiveColor))
                }
                isActive.listener { oldVal, newVal ->
                    if (oldVal != newVal) {
                        if (newVal) {
                            textStyles.forEach {
                                it.first.style = it.second
                            }
                        } else {
                            textStyles.forEach {
                                it.first.style = it.third
                            }
                        }
                    }
                }
                isActive.value = !isActive.value
                isActive.value = !isActive.value
            }
        }
        block {
            hSizing = Sizing.SHRINK
            vSizing = Sizing.SHRINK
            hAlign = HorizontalAlignment.LEFT
            vAlign = VerticalAlignment.MIDDLE
            layout = Layout.HORIZONTAL
            this.text = text
            isMouseAware = false
        }
        block {
            hSizing = Sizing.GROW
            layout = Layout.HORIZONTAL
            isMouseAware = false
        }
        block {
            hSizing = Sizing.STATIC
            layout = Layout.HORIZONTAL
            width = MEDIUM_SPACER_SIZE
            isMouseAware = false
        }
        block {
            hSizing = Sizing.SHRINK
            vSizing = Sizing.SHRINK
            hAlign = HorizontalAlignment.LEFT
            vAlign = VerticalAlignment.MIDDLE
            layout = Layout.HORIZONTAL
            this.text = hotKey
            isMouseAware = false
        }
        block {
            hSizing = Sizing.STATIC
            width = 22.0f
            layout = Layout.HORIZONTAL
            isMouseAware = false
        }
        onMouseOver {
            mouseOver = true
            if (!mouseDownOver && isActive.value) {
                if (mouseDownOnActivator.value) {
                    shape = SHAPE_BUTTON_MOUSE_DOWN
                } else {
                    shape = SHAPE_MENU_BORDER
                }
            }
            val activeItemValue = activeItem.value
            if (activeItemValue != null && activeItemValue.first != thisItem) {
                activeItemValue.second()
            }
            activeItem.value = Pair(thisItem, {})
        }
        onMouseOut {
            mouseOver = false
            if (!mouseDownOver) {
                shape = NO_SHAPE
            }
        }
        onMouseDown { button, x, y ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive.value) {
                mouseDownOver = true
                shape = SHAPE_BUTTON_MOUSE_DOWN
            }
        }
        onMouseUp { button, x, y ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOnActivator.value && isActive.value) {
                deactivate()
                onClick()
            }
        }
        onMouseRelease { button, x, y ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                shape = if (mouseOver) SHAPE_MENU_BORDER else NO_SHAPE
            }
        }
        onMouseClick { button, x, y ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive.value) {
                deactivate()
                onClick()
            }
        }
    }
}

private fun Block.subMenu(text: Text,
                          rowHeight: Float,
                          textStyle: TextStyle,
                          shrinkGroup: ShrinkGroup,
                          textColorInactive: NVGColor,
                          isActive: MonitoredReference<Boolean>,
                          mouseDownOnActivator: MonitoredReference<Boolean>,
                          deactivate: () -> Unit,
                          activeItem: MutableReference<Pair<Block, () -> Unit>?>,
                          builder: DropdownList.() -> Unit): Block {
    return block {
        val inactiveShape = ShapeTriangle(FillColor(textColorInactive), NO_STROKE, ShapeTriangle.Direction.EAST)
        val activeShape = ShapeTriangle(FillColor(textStyle.color.value), NO_STROKE, ShapeTriangle.Direction.EAST)
        val downShape = ShapeTriangle(FILL_BUTTON_MOUSE_DOWN, NO_STROKE, ShapeTriangle.Direction.EAST)
        var arrowBlock = NO_BLOCK
        val thisItem = this
        var mouseDownOver = false
        var mouseOver = false
        hSizing = Sizing.SHRINK_GROUP
        hShrinkGroup = shrinkGroup
        vSizing = Sizing.STATIC
        this.height = rowHeight
        layout = Layout.VERTICAL
        isFallThrough = true
        var opened = false
        val clickCatcher = block {
            block {
                hSizing = Sizing.STATIC
                width = 32.0f
                layout = Layout.HORIZONTAL
                isMouseAware = false
                block {
                    this.hSizing = Sizing.SHRINK
                    this.vSizing = Sizing.SHRINK
                    this.hAlign = HorizontalAlignment.CENTER
                    this.vAlign = VerticalAlignment.MIDDLE
                    val inactiveColor = cRef(textColorInactive)
                    val labelTextStyle = Triple(text, text.style, TextStyle(text.style.size, text.style.font, inactiveColor))
                    isActive.listener { oldVal, newVal ->
                        if (oldVal != newVal) {
                            if (newVal) {
                                labelTextStyle.first.style = labelTextStyle.second
                                if (!opened) {
                                    arrowBlock.shape = activeShape
                                }
                            } else {
                                labelTextStyle.first.style = labelTextStyle.third
                                if (!opened) {
                                    arrowBlock.shape = inactiveShape
                                }
                            }
                        }
                    }
                }
            }
            block {
                hSizing = Sizing.SHRINK
                vSizing = Sizing.SHRINK
                hAlign = HorizontalAlignment.LEFT
                vAlign = VerticalAlignment.MIDDLE
                layout = Layout.HORIZONTAL
                this.text = text
                isMouseAware = false
            }
            block {
                hSizing = Sizing.GROW
                layout = Layout.HORIZONTAL
                isMouseAware = false
            }
            block {
                hSizing = Sizing.STATIC
                layout = Layout.HORIZONTAL
                width = MEDIUM_SPACER_SIZE
                isMouseAware = false
            }
            block {
                hSizing = Sizing.STATIC
                width = 18.0f
                layout = Layout.HORIZONTAL
                isMouseAware = false
                arrowBlock = block {
                    hSizing = Sizing.STATIC
                    vSizing = Sizing.STATIC
                    hAlign = HorizontalAlignment.CENTER
                    vAlign = VerticalAlignment.MIDDLE
                    width = 8.0f
                    height = 8.0f
                    shape = activeShape
                }
            }
        }
        var dropDown = NO_BLOCK
        val subActiveItem: MutableReference<Pair<Block, () -> Unit>?> = mRef(null)
        val deActivateDropDown = {
            subActiveItem.value?.second?.invoke()
            opened = false
            dropDown.isVisible = false
            dropDown.isMouseAware = false
            arrowBlock.shape = if (isActive.value) {
                activeShape
            } else {
                inactiveShape
            }
        }
        val activateDropDown = {
            val graceStart = System.nanoTime()
            opened = true
            dropDown.isVisible = true
            dropDown.isMouseAware = true
            val dropDownList = dropDown.layoutChildren.first()
            inputOverride = dropDown
            val endOverride = {
                inputOverride = null
                dropDown.onMouseDown = null
                dropDown.onMouseUp = null
                dropDown.onTick = null
            }
            dropDown.onMouseDown = { button, x, y ->
                endOverride()
            }
            dropDown.onMouseUp = { button, x, y ->
                endOverride()
            }
            var lastX: Int? = null
            var lastY: Int? = null
            val leftX = dropDownList.x
            val topY = dropDownList.y
            val bottomY = topY + dropDownList.height
            dropDown.onTick = { x, y ->
                val lastXFinal = lastX
                val lastYFinal = lastY
                if (lastXFinal == null || lastYFinal == null) {
                    lastX = x
                    lastY = y
                } else if (x < lastXFinal || x >= leftX || y >= bottomY || y <= topY) {
                    endOverride()
                } else {
                    val deltaX = x - lastXFinal
                    val deltaY = Math.max(0.0f, y.toFloat() - lastYFinal)
                    val remainingY = Math.abs(bottomY - y)
                    val remainingX = Math.abs(leftX - x)
                    val remainingRatio = (remainingX / remainingY) * 0.4f
                    val deltaRatio = if (deltaY < 0.1f) {
                        Float.MAX_VALUE
                    } else {
                        deltaX / deltaY
                    }
                    if (deltaRatio < remainingRatio) {
                        endOverride()
                    } else {
                        val now = System.nanoTime()
                        if (now - graceStart > 2500000000) {
                            endOverride()
                        }
                    }
                }
            }
            arrowBlock.shape = downShape
            val activeItemValue = activeItem.value
            if (activeItemValue != null && activeItemValue.first != thisItem) {
                activeItemValue.second()
            }
            activeItem.value = Pair(thisItem, deActivateDropDown)
        }
        dropDown = block {
            dropDown = this
            isVisible = true
            isMouseAware = true
            layout = Layout.HORIZONTAL
            yOffset = -2.0f
            xOffset = 0.0f
            hSizing = Sizing.STATIC
            vSizing = Sizing.STATIC
            width = 0.0f
            this.height = 0.0f
            canOverflow = true
            isFallThrough = true
            menuDropDownList(rowHeight, textStyle, textColorInactive, mouseDownOnActivator, deactivate, subActiveItem, builder)
        }
        dropDown.isVisible = false
        dropDown.isMouseAware = false
        clickCatcher.onMouseOver {
            mouseOver = true
            if (!mouseDownOver && isActive.value) {
                if (mouseDownOnActivator.value) {
                    shape = SHAPE_BUTTON_MOUSE_DOWN
                } else {
                    shape = SHAPE_MENU_BORDER
                }
                var startX: Int? = null
                var startY: Int? = null
                val startTime: Long = System.nanoTime()
                var lastTime: Long = startTime
                onTick = { x, y ->
                    if (startX == null || startY == null) {
                        startX = x
                        startY = y
                        lastTime = System.nanoTime()
                    } else {
                        val deltaY = Math.abs(y - (startY ?: y))
                        if (deltaY < 5) {
                            val newTime = System.nanoTime()
                            val deltaTime = newTime - lastTime
                            if (!opened && deltaTime > 350000000) {
                                onTick = null
                                activateDropDown()
                            }
                        } else {
                            startX = x
                            startY = y
                            lastTime = System.nanoTime()
                        }
                    }
                }
            }
        }
        clickCatcher.onMouseOut {
            mouseOver = false
            if (!mouseDownOver) {
                shape = NO_SHAPE
            }
            onTick = null
        }
        clickCatcher.onMouseDown { button, x, y ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive.value) {
                mouseDownOver = true
                shape = SHAPE_BUTTON_MOUSE_DOWN
            }
        }
        clickCatcher.onMouseUp { button, x, y ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOnActivator.value && isActive.value) {
                if (!opened) {
                    onTick = null
                    activateDropDown()
                }
            }
        }
        clickCatcher.onMouseRelease { button, x, y ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                shape = if (mouseOver) SHAPE_MENU_BORDER else NO_SHAPE
            }
        }
        clickCatcher.onMouseClick { button, x, y ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive.value) {
                if (!opened) {
                    onTick = null
                    activateDropDown()
                }
            }
        }
        isActive.value = !isActive.value
        isActive.value = !isActive.value
    }
}

private fun Block.getDescendants(descendants: MutableList<Block> = ArrayList()): List<Block> {
    descendants.add(this)
    layoutChildren.forEach { it.getDescendants(descendants) }
    return descendants
}

private val Block.descendants: List<Block> get() = getDescendants()

private fun Block.menuDivider(height: Float, shrinkGroup: ShrinkGroup): Block {
    return block {
        hSizing = Sizing.SHRINK_GROUP
        hShrinkGroup = shrinkGroup
        vSizing = Sizing.STATIC
        this.height = height
        layout = Layout.VERTICAL
        block{
            hSizing = Sizing.STATIC
            width = 32.0f
            layout = Layout.HORIZONTAL
        }
        block {
            hSizing = Sizing.GROW
            layout = Layout.HORIZONTAL
            block {
                vSizing = Sizing.STATIC
                this.height = 1.0f
                hSizing = Sizing.RELATIVE
                width = -2.0f
                shape = SHAPE_MENU_BORDER
                hAlign = HorizontalAlignment.LEFT
                vAlign = VerticalAlignment.MIDDLE
            }
        }
    }
}

private fun Block.menuDropDownList(rowHeight: Float, textStyle: TextStyle, textColorInactive: NVGColor, mouseDownOnActivator: MonitoredReference<Boolean>, deactivate: () -> Unit, activeItem: MutableReference<Pair<Block, () -> Unit>?>, builder: DropdownList.() -> Unit): Block {
    return block {
        layout = Layout.ABSOLUTE
        hSizing = Sizing.SHRINK
        vSizing = Sizing.SHRINK
        canOverflow = true
        isFallThrough = true
        block {
            xOffset = 3.0f
            yOffset = 3.0f
            canOverflow = true
            shape = SHAPE_DROP_SHADOW
            isMouseAware = false
        }
        block {
            layout = Layout.ABSOLUTE
            hSizing = Sizing.SHRINK
            vSizing = Sizing.SHRINK
            canOverflow = true
            isFallThrough = true
            shape = SHAPE_MENU_BORDER
            block {
                hSizing = Sizing.SHRINK
                vSizing = Sizing.SHRINK
                padLeft = 1.0f
                padRight = 1.0f
                padTop = 1.0f
                padBottom = 1.0f
                shape = SHAPE_MENU_BACKGROUND
                isFallThrough = true
                block {
                    hSizing = Sizing.SHRINK
                    vSizing = Sizing.SHRINK
                    padLeft = 2.0f
                    padRight = 2.0f
                    padTop = 2.0f
                    padBottom = 2.0f
                    isFallThrough = true
                    DropdownList(this, rowHeight, textStyle, textColorInactive, mouseDownOnActivator, deactivate, activeItem).builder()
                }
            }
        }
    }
}

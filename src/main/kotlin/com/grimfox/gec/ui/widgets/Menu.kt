package com.grimfox.gec.ui.widgets

import com.grimfox.gec.*
import com.grimfox.gec.ui.nvgproxy.NPColor
import com.grimfox.gec.util.*
import org.lwjgl.glfw.GLFW
import java.util.*

class MenuBar(private val block: Block,
              private val menuLayer: Block,
              private val rowHeight: Float,
              private val textStyle: TextStyle,
              private val textColorInactive: NPColor,
              private val activeMenu: MutableReference<Pair<Block, () -> Unit>?>,
              private val mouseDownOnActivator: ObservableMutableReference<Boolean>,
              private val mouseDownOnDeActivator: ObservableMutableReference<Boolean>,
              private val mouseOverActivator: ObservableMutableReference<Boolean>,
              private val mouseOverDeActivator: ObservableMutableReference<Boolean>) {

    fun menu(title: String, builder: DropdownList.() -> Unit): Block =
            block.menu(
                    title = title,
                    menuLayer = menuLayer,
                    activeMenu = activeMenu,
                    mouseDownOnActivator = mouseDownOnActivator,
                    mouseDownOnDeActivator = mouseDownOnDeActivator,
                    mouseOverActivator = mouseOverActivator,
                    mouseOverDeActivator = mouseOverDeActivator,
                    rowHeight = rowHeight,
                    textStyle = textStyle,
                    textColorInactive = textColorInactive,
                    builder = builder)
}

class DropdownList(
        private val block: Block,
        private val rowHeight: Float,
        private val textStyle: TextStyle,
        private val textColorInactive: NPColor,
        private val mouseDownOnActivator: ObservableMutableReference<Boolean>,
        private val deactivate: () -> Unit,
        private val activeItem: MutableReference<Pair<Block, () -> Unit>?>,
        private val shrink: Boolean = true) {

    private val shrinkGroup = hShrinkGroup()

    fun menuItem(
            text: Text,
            hotKey: String? = null,
            glyph: Block.() -> Block = { block { } },
            isActive: ObservableMutableReference<Boolean> = ref(true),
            onClick: () -> Unit): Block =
            block.menuItem(
                    text = text,
                    hotKey = if (hotKey != null) text(hotKey, textStyle) else NO_TEXT,
                    glyph = glyph,
                    height = rowHeight,
                    shrinkGroup = shrinkGroup,
                    textColorInactive = textColorInactive,
                    isActive = isActive,
                    mouseDownOnActivator = mouseDownOnActivator,
                    deactivate = deactivate,
                    activeItem = activeItem,
                    shrink = shrink,
                    onClick = onClick)

    fun menuItem(
            text: String,
            hotKey: String? = null,
            glyph: Block.() -> Block = { block { } },
            isActive: ObservableMutableReference<Boolean> = ref(true),
            onClick: () -> Unit): Block =
            block.menuItem(
                    text = text(text, textStyle),
                    hotKey = if (hotKey != null) text(hotKey, textStyle) else NO_TEXT,
                    glyph = glyph,
                    height = rowHeight,
                    shrinkGroup = shrinkGroup,
                    textColorInactive = textColorInactive,
                    isActive = isActive,
                    mouseDownOnActivator = mouseDownOnActivator,
                    deactivate = deactivate,
                    activeItem = activeItem,
                    shrink = shrink,
                    onClick = onClick)

    fun subMenu(
            text: String,
            isActive: ObservableMutableReference<Boolean> = ref(true),
            builder: DropdownList.() -> Unit): Block =
            block.subMenu(
                    text = text(text, textStyle),
                    rowHeight = rowHeight,
                    textStyle = textStyle,
                    shrinkGroup = shrinkGroup,
                    textColorInactive = textColorInactive,
                    isActive = isActive,
                    mouseDownOnActivator = mouseDownOnActivator,
                    deactivate = deactivate,
                    activeItem = activeItem,
                    builder = builder)

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

    fun menuDivider(): Block = block.menuDivider(3.0f, shrinkGroup)
}

fun Block.menuBar(
        menuLayer: Block,
        rowHeight: Float,
        textStyle: TextStyle,
        textColorInactive: NPColor,
        builder: MenuBar.() -> Unit): Block {
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
        MenuBar(block = this,
                menuLayer = menuLayer,
                rowHeight = rowHeight,
                textStyle = textStyle,
                textColorInactive = textColorInactive,
                activeMenu = activeMenu,
                mouseDownOnActivator = mouseDownOnActivator,
                mouseDownOnDeActivator = mouseDownOnDeActivator,
                mouseOverActivator = mouseOverActivator,
                mouseOverDeActivator = mouseOverDeActivator).builder()
    }
}

fun Block.dropdown(
        title: Text,
        dropdownLayer: Block,
        buttonHeight: Float,
        itemHeight: Float,
        textStyle: TextStyle,
        textColorInactive: NPColor,
        builder: DropdownList.() -> Unit): Block =
        dropdownMenu(title, dropdownLayer, buttonHeight, itemHeight, textStyle, textColorInactive, builder)

private fun Block.menu(
        title: String,
        menuLayer: Block,
        activeMenu: MutableReference<Pair<Block, () -> Unit>?>,
        mouseDownOnActivator: ObservableMutableReference<Boolean>,
        mouseDownOnDeActivator: ObservableMutableReference<Boolean>,
        mouseOverActivator: ObservableMutableReference<Boolean>,
        mouseOverDeActivator: ObservableMutableReference<Boolean>,
        rowHeight: Float,
        textStyle: TextStyle,
        textColorInactive: NPColor,
        builder: DropdownList.() -> Unit): Block {
    val titleText = text(title, textStyle)
    return menu(titleText,
            menuLayer,
            activeMenu,
            mouseDownOnActivator,
            mouseDownOnDeActivator,
            mouseOverActivator,
            mouseOverDeActivator,
            rowHeight,
            textStyle,
            textColorInactive,
            builder)
}

private fun Block.menu(
        titleText: Text,
        menuLayer: Block,
        activeMenu: MutableReference<Pair<Block, () -> Unit>?>,
        mouseDownOnActivator: ObservableMutableReference<Boolean>,
        mouseDownOnDeActivator: ObservableMutableReference<Boolean>,
        mouseOverActivator: ObservableMutableReference<Boolean>,
        mouseOverDeActivator: ObservableMutableReference<Boolean>,
        rowHeight: Float,
        textStyle: TextStyle,
        textColorInactive: NPColor,
        builder: DropdownList.() -> Unit): Block {
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
            menuLayer.onMouseDown { _, _, _, _ -> }
        }
        val activateMenu = {
            activeMenu.value?.second?.invoke()
            dropDown.isVisible = true
            dropDown.isMouseAware = true
            activator.isMouseAware = false
            menuLayer.isFallThrough = false
            activeMenu.value = Pair(menu, deactivateMenu)
            menuLayer.onMouseDown { _, _, _, _ ->
                deactivateMenuBar()
                reprocessTick()
            }
        }
        activator = block {
            hSizing = Sizing.SHRINK
            vSizing = Sizing.STATIC
            height = SMALL_ROW_HEIGHT
            block {
                hSizing = Sizing.SHRINK
                vSizing = Sizing.STATIC
                height = SMALL_ROW_HEIGHT
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
            menuDropDownList(
                    rowHeight = rowHeight,
                    textStyle = textStyle,
                    textColorInactive = textColorInactive,
                    mouseDownOnActivator = mouseDownOnActivator,
                    deactivate = deactivateMenuBar,
                    activeItem = activeItem,
                    shrink = true,
                    builder = builder)
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
                    height = SMALL_ROW_HEIGHT + 1
                    padLeft = 1.0f
                    padRight = 1.0f
                    padTop = 1.0f
                    canOverflow = true
                    shape = SHAPE_MENU_BACKGROUND
                    isMouseAware = false
                    block {
                        hSizing = Sizing.SHRINK
                        vSizing = Sizing.STATIC
                        height = SMALL_ROW_HEIGHT
                        hAlign = HorizontalAlignment.CENTER
                        vAlign = VerticalAlignment.MIDDLE
                        yOffset = -0.5f
                        padLeft = SMALL_SPACER_SIZE
                        padRight = SMALL_SPACER_SIZE
                        text = titleText
                    }
                }
                onMouseDown { _, _, _, _ ->
                    mouseDownOnDeActivator.value = true
                }
                onMouseUp { _, _, _, _ ->
                    if (mouseDownOnDeActivator.value) {
                        deactivateMenuBar()
                    }
                }
                onMouseRelease { _, _, _, _ ->
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
        activator.onMouseDown { _, _, _, _ ->
            mouseDownOnActivator.value = true
            activateMenu()
        }
        activator.onMouseRelease { _, _, _, _ ->
            if (mouseDownOnActivator.value && !(mouseOverDeActivator.value || mouseOverActivator.value)) {
                deactivateMenuBar()
            }
            mouseDownOnActivator.value = false
        }
    }
}

private fun Block.dropdownMenu(
        titleText: Text,
        dropdownLayer: Block,
        buttonHeight: Float,
        itemHeight: Float,
        textStyle: TextStyle,
        textColorInactive: NPColor,
        builder: DropdownList.() -> Unit): Block {
    return block {
        val menu = this
        val mouseDownOnActivator = ref(false)
        val mouseOverActivator = ref(false)
        val mouseDownOnDeActivator = ref(false)
        val mouseOverDeActivator = ref(false)
        val activeMenu = ref<Pair<Block, () -> Unit>?>(null)
        var dropDown = NO_BLOCK
        var activator = NO_BLOCK
        var deactivator = NO_BLOCK
        var dropDownContainer = NO_BLOCK
        var dropDownList = NO_BLOCK
        vSizing = Sizing.STATIC
        vAlign = VerticalAlignment.BOTTOM
        height = buttonHeight
        isFallThrough = true
        layout = Layout.HORIZONTAL
        val activeItem: MutableReference<Pair<Block, () -> Unit>?> = mRef(null)
        val deactivateMenu = {
            activeItem.value?.second?.invoke()
            dropDown.isVisible = false
            dropDown.isMouseAware = false
            activator.isMouseAware = true
            dropdownLayer.isFallThrough = true
            activator.shape = NO_SHAPE
        }
        val deactivateMenuBar = {
            activeMenu.value?.second?.invoke()
            activeMenu.value = null
            dropdownLayer.onMouseDown { _, _, _, _ -> }
        }
        val activateMenu = {
            activator.shape = SHAPE_BUTTON_MOUSE_DOWN
            val currentWidth = activator.width
            deactivator.width = currentWidth
            dropDownContainer.width = currentWidth
            activeMenu.value?.second?.invoke()
            dropDown.isVisible = true
            dropDown.isMouseAware = true
            activator.isMouseAware = false
            dropdownLayer.isFallThrough = false
            activeMenu.value = Pair(menu, deactivateMenu)
            dropdownLayer.onMouseDown { _, _, _, _ ->
                deactivateMenuBar()
                reprocessTick()
            }
        }
        activator = block {
            vSizing = Sizing.STATIC
            height = buttonHeight
            block {
                hSizing = Sizing.SHRINK
                vSizing = Sizing.SHRINK
                hAlign = HorizontalAlignment.LEFT
                vAlign = VerticalAlignment.MIDDLE
                padLeft = SMALL_SPACER_SIZE
                padRight = SMALL_SPACER_SIZE
                text = titleText
                isMouseAware = false
            }
        }
        dropDown = block {
            parent.renderChildren.remove(this)
            dropdownLayer.renderChildren.add(this)
            dropDown = this
            isVisible = true
            isMouseAware = true
            layout = Layout.ABSOLUTE
            yOffset = buttonHeight
            hSizing = Sizing.STATIC
            vSizing = Sizing.STATIC
            width = 0.0f
            height = 0.0f
            canOverflow = true
            isFallThrough = true
            receiveChildEvents = true
            val dropDownPair = menuDropDownList(
                    rowHeight = itemHeight,
                    textStyle = textStyle,
                    textColorInactive = textColorInactive,
                    mouseDownOnActivator = mouseDownOnActivator,
                    deactivate = deactivateMenuBar,
                    activeItem = activeItem,
                    shrink = false,
                    builder = builder)
            dropDownContainer = dropDownPair.first
            dropDownList = dropDownPair.second
            deactivator = block {
                layout = Layout.ABSOLUTE
                hSizing = Sizing.STATIC
                vSizing = Sizing.STATIC
                height = buttonHeight
                yOffset = -buttonHeight
                canOverflow = true
                shape = NO_SHAPE
                onMouseDown { _, _, _, _ ->
                    mouseDownOnDeActivator.value = true
                }
                onMouseUp { _, _, _, _ ->
                    if (mouseDownOnDeActivator.value) {
                        deactivateMenuBar()
                    }
                }
                onMouseRelease { _, _, _, _ ->
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

        dropDown.onScroll { _, y ->
            var intY = y.toInt()
            if (intY < 0) {
                intY = -intY
                for (i in 0 until intY) {
                    if (dropDownList.layoutChildren.isNotEmpty()) {
                        val firstChild = dropDownList.layoutChildren.removeAt(0)
                        dropDownList.layoutChildren.add(firstChild)
                    }
                    if (dropDownList.renderChildren.isNotEmpty()) {
                        val firstChild = dropDownList.renderChildren.removeAt(0)
                        dropDownList.renderChildren.add(firstChild)
                    }
                }
            } else if (y > 0) {
                for (i in 0 until intY) {
                    if (dropDownList.layoutChildren.isNotEmpty()) {
                        val lastChild = dropDownList.layoutChildren.removeAt(dropDownList.layoutChildren.size - 1)
                        dropDownList.layoutChildren.add(0, lastChild)
                    }
                    if (dropDownList.renderChildren.isNotEmpty()) {
                        val lastChild = dropDownList.renderChildren.removeAt(dropDownList.renderChildren.size - 1)
                        dropDownList.renderChildren.add(0, lastChild)
                    }
                }
            }

        }

        activator.onMouseOver {
            shape = SHAPE_BUTTON_MOUSE_OVER
            mouseOverActivator.value = true
            val active = activeMenu.value
            if (active != null && active.first != menu) {
                activateMenu()
            }
        }
        activator.onMouseOut {
            if (activeMenu.value?.first != menu) {
                shape = NO_SHAPE
            }
            mouseOverActivator.value = false
        }
        activator.onMouseDown { _, _, _, _ ->
            mouseDownOnActivator.value = true
            activateMenu()
        }
        activator.onMouseRelease { _, _, _, _ ->
            if (mouseDownOnActivator.value && !(mouseOverDeActivator.value || mouseOverActivator.value)) {
                deactivateMenuBar()
            }
            mouseDownOnActivator.value = false
        }
    }
}

private fun Block.menuItem(
        text: Text,
        hotKey: Text,
        glyph: Block.() -> Block,
        height: Float,
        shrinkGroup: ShrinkGroup,
        textColorInactive: NPColor,
        isActive: ObservableMutableReference<Boolean>,
        mouseDownOnActivator: Reference<Boolean>,
        deactivate: () -> Unit,
        activeItem: MutableReference<Pair<Block, () -> Unit>?>,
        shrink: Boolean = true,
        onClick: () -> Unit = {}): Block {
    return block {
        val thisItem = this
        var mouseDownOver = false
        var mouseOver = false
        if (shrink) {
            hSizing = Sizing.SHRINK_GROUP
            hShrinkGroup = shrinkGroup
        }
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
                isActive.addListener { oldVal, newVal ->
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
                shape = if (mouseDownOnActivator.value) SHAPE_BUTTON_MOUSE_DOWN else SHAPE_MENU_BORDER
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
        onMouseDown { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive.value) {
                mouseDownOver = true
                shape = SHAPE_BUTTON_MOUSE_DOWN
            }
        }
        onMouseUp { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOnActivator.value && isActive.value) {
                deactivate()
                task {
                    onClick()
                }
            }
        }
        onMouseRelease { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                shape = if (mouseOver) SHAPE_MENU_BORDER else NO_SHAPE
            }
        }
        onMouseClick { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive.value) {
                deactivate()
                task {
                    onClick()
                }
            }
        }
    }
}

private fun Block.subMenu(
        text: Text,
        rowHeight: Float,
        textStyle: TextStyle,
        shrinkGroup: ShrinkGroup,
        textColorInactive: NPColor,
        isActive: ObservableMutableReference<Boolean>,
        mouseDownOnActivator: ObservableMutableReference<Boolean>,
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
                    isActive.addListener { oldVal, newVal ->
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
            dropDown.onMouseDown = { _, _, _, _ ->
                endOverride()
            }
            dropDown.onMouseUp = { _, _, _, _ ->
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
            menuDropDownList(
                    rowHeight = rowHeight,
                    textStyle = textStyle,
                    textColorInactive = textColorInactive,
                    mouseDownOnActivator = mouseDownOnActivator,
                    deactivate = deactivate,
                    activeItem = subActiveItem,
                    shrink = true,
                    builder = builder)
        }
        dropDown.isVisible = false
        dropDown.isMouseAware = false
        clickCatcher.onMouseOver {
            mouseOver = true
            if (!mouseDownOver && isActive.value) {
                shape = if (mouseDownOnActivator.value) SHAPE_BUTTON_MOUSE_DOWN else SHAPE_MENU_BORDER
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
        clickCatcher.onMouseDown { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && isActive.value) {
                mouseDownOver = true
                shape = SHAPE_BUTTON_MOUSE_DOWN
            }
        }
        clickCatcher.onMouseUp { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOnActivator.value && isActive.value) {
                if (!opened) {
                    onTick = null
                    activateDropDown()
                }
            }
        }
        clickCatcher.onMouseRelease { button, _, _, _ ->
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && mouseDownOver) {
                mouseDownOver = false
                shape = if (mouseOver) SHAPE_MENU_BORDER else NO_SHAPE
            }
        }
        clickCatcher.onMouseClick { button, _, _, _ ->
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

private fun Block.menuDropDownList(
        rowHeight: Float,
        textStyle: TextStyle,
        textColorInactive: NPColor,
        mouseDownOnActivator: ObservableMutableReference<Boolean>,
        deactivate: () -> Unit,
        activeItem: MutableReference<Pair<Block, () -> Unit>?>,
        shrink: Boolean = true,
        builder: DropdownList.() -> Unit): Pair<Block, Block> {
    var listBlock = NO_BLOCK
    val thisBlock = block {
        layout = Layout.ABSOLUTE
        hSizing = if (shrink) Sizing.SHRINK else Sizing.STATIC
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
            hSizing = if (shrink) Sizing.SHRINK else Sizing.RELATIVE
            vSizing = Sizing.SHRINK
            canOverflow = true
            isFallThrough = true
            shape = SHAPE_MENU_BORDER
            block {
                hSizing = if (shrink) Sizing.SHRINK else Sizing.RELATIVE
                vSizing = Sizing.SHRINK
                padLeft = 1.0f
                padRight = 1.0f
                padTop = 1.0f
                padBottom = 1.0f
                shape = SHAPE_MENU_BACKGROUND
                isFallThrough = true
                block {
                    listBlock = this
                    hSizing = if (shrink) Sizing.SHRINK else Sizing.RELATIVE
                    vSizing = Sizing.SHRINK
                    padLeft = 2.0f
                    padRight = 2.0f
                    padTop = 2.0f
                    padBottom = 2.0f
                    isFallThrough = true
                    DropdownList(
                            block = this,
                            rowHeight = rowHeight,
                            textStyle = textStyle,
                            textColorInactive = textColorInactive,
                            mouseDownOnActivator = mouseDownOnActivator,
                            deactivate = deactivate,
                            activeItem = activeItem,
                            shrink = shrink).builder()
                }
            }
        }
    }
    return thisBlock to listBlock
}

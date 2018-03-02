package com.grimfox.gec

import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.nvgproxy.bInt
import com.grimfox.gec.ui.nvgproxy.gInt
import com.grimfox.gec.ui.nvgproxy.rInt
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.*
import com.grimfox.gec.util.FileDialogs
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

fun exportPanel(ui: UserInterface) {
    val shrinkGroup = hShrinkGroup()
    val regionFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useRegionFile = ref(false)
    val biomeFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useBiomeFile = ref(false)
    val mapFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useMapFile = ref(false)
    val heightFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useHeightFile = ref(false)
    val objFile = DynamicTextReference("", 1024, TEXT_STYLE_NORMAL)
    val useObjFile = ref(false)
    fun pngExtensionFilter(textReference: DynamicTextReference): (String, String) -> Unit = { old, new ->
        if (old != new) {
            if (new.isNotBlank() && !new.endsWith(".png", true)) {
                textReference.reference.value = "$new.png"
            }
        }
    }
    fun objExtensionFilter(textReference: DynamicTextReference): (String, String) -> Unit = { old, new ->
        if (old != new) {
            if (new.isNotBlank() && !new.endsWith(".obj", true)) {
                textReference.reference.value = "$new.obj"
            }
        }
    }
    regionFile.reference.addListener(pngExtensionFilter(regionFile))
    biomeFile.reference.addListener(pngExtensionFilter(biomeFile))
    mapFile.reference.addListener(pngExtensionFilter(mapFile))
    heightFile.reference.addListener(pngExtensionFilter(heightFile))
    objFile.reference.addListener(objExtensionFilter(objFile))
    panelLayer {
        exportPanel = panel(650.0f) {
            vSizing = Sizing.SHRINK
            vSpacer(LARGE_SPACER_SIZE)
            vSaveFileRowWithToggle(regionFile, useRegionFile, LARGE_ROW_HEIGHT, text("Region file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(biomeFile, useBiomeFile, LARGE_ROW_HEIGHT, text("Biome file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(mapFile, useMapFile, LARGE_ROW_HEIGHT, text("Map file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(heightFile, useHeightFile, LARGE_ROW_HEIGHT, text("Height file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(objFile, useObjFile, LARGE_ROW_HEIGHT, text("Terrain mesh:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "obj")
            val outputSizes = arrayOf(256, 512, 1024, 2048, 4096)
            val outputSizesAsText = outputSizes.map { text("$it x $it px", TEXT_STYLE_BUTTON) }
            val selectedOutputSize: ObservableMutableReference<Int> = ref(0)
            block {
                vSizing = Sizing.STATIC
                this.height = LARGE_ROW_HEIGHT
                layout = Layout.VERTICAL
                label(text("Output size:"), shrinkGroup)
                hSpacer(MEDIUM_SPACER_SIZE)
                block {
                    hSizing = Sizing.GROW
                    layout = Layout.HORIZONTAL
                    val textRef = StaticTextReference(outputSizesAsText[selectedOutputSize.value])
                    dropdown(textRef, dialogDropdownLayer, SMALL_ROW_HEIGHT, MEDIUM_ROW_HEIGHT, TEXT_STYLE_BUTTON, COLOR_DISABLED_CLICKABLE) {
                        outputSizesAsText.forEachIndexed { i, value ->
                            menuItem(value) {
                                selectedOutputSize.value = i
                            }
                        }
                    }.with {
                        vAlign = VerticalAlignment.MIDDLE
                    }
                    selectedOutputSize.addListener { old, new ->
                        if (old != new) {
                            textRef.reference.value = outputSizesAsText[new]
                        }
                    }
                }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("Export"), DIALOG_BUTTON_STYLE) {
                    var outputSize = outputSizes[selectedOutputSize.value]
                    if (useRegionFile.value) {
                        val file = File(regionFile.reference.value)
                        val regionMask = currentState.regionMask.value
                        if (((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) && regionMask != null) {
                            val output = BufferedImage(regionMask.width, regionMask.width, BufferedImage.TYPE_4BYTE_ABGR)
                            val raster = output.raster
                            for (y in 0 until regionMask.width) {
                                for (x in 0 until regionMask.width) {
                                    val region = regionMask[x, y].toInt()
                                    val color = REGION_COLORS[region]
                                    raster.setPixel(x, y, intArrayOf(color.rInt, color.gInt, color.bInt, 255))
                                }
                            }
                            ImageIO.write(output, "png", file)
                        }
                    }
                    if (useBiomeFile.value) {
                        val file = File(biomeFile.reference.value)
                        val biomeMask = currentState.biomeMask.value
                        if (((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) && biomeMask != null) {
                            val output = BufferedImage(biomeMask.width, biomeMask.width, BufferedImage.TYPE_4BYTE_ABGR)
                            val raster = output.raster
                            for (y in 0 until biomeMask.width) {
                                for (x in 0 until biomeMask.width) {
                                    val biome = biomeMask[x, y].toInt()
                                    val color = BIOME_COLORS[biome]
                                    raster.setPixel(x, y, intArrayOf(color.rInt, color.gInt, color.bInt, 255))
                                }
                            }
                            ImageIO.write(output, "png", file)
                        }
                    }
                    if (useMapFile.value) {
                        if (DEMO_BUILD) {
                            outputSize = 256
                        }
                        val file = File(mapFile.reference.value)
                        val regionSplines = currentState.regionSplines.value
                        if (((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) && regionSplines != null) {
                            val textureId = TextureBuilder.renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints + regionSplines.customRiverPoints, regionSplines.mountainPoints + regionSplines.customMountainPoints, regionSplines.ignoredPoints + regionSplines.customIgnoredPoints)
                            val bytes = TextureBuilder.extractTextureRgbaByte(textureId, 4096)
                            val output = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_4BYTE_ABGR)
                            val raster = output.raster
                            for (y in 0 until outputSize) {
                                val yOff = Math.round((y / (outputSize - 1).toFloat()) * 4095.0f)
                                for (x in 0 until outputSize) {
                                    var offset = (yOff + Math.round((x / (outputSize - 1).toFloat()) * 4095.0f)) * 4
                                    val r = bytes[offset++]
                                    val g = bytes[offset++]
                                    val b = bytes[offset]
                                    raster.setPixel(x, y, intArrayOf(r.toInt() and 0xFF, g.toInt() and 0xFF, b.toInt() and 0xFF, 255))
                                }
                            }
                            ImageIO.write(output, "png", file)
                        }
                    }
                    if (useHeightFile.value) {
                        if (DEMO_BUILD) {
                            outputSize = 256
                        }
                        val file = File(heightFile.reference.value)
                        val heightMap = currentState.heightMapTexture.value
                        if (((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) && heightMap != null) {
                            val shorts = TextureBuilder.extractTextureRedShort(heightMap, 4096)
                            val output = BufferedImage(outputSize, outputSize, BufferedImage.TYPE_USHORT_GRAY)
                            val raster = output.raster
                            for (y in 0 until outputSize) {
                                val yOff = Math.round((y / (outputSize - 1).toFloat()) * 4095.0f) * 4096
                                for (x in 0 until outputSize) {
                                    val xOff = Math.round((x / (outputSize - 1).toFloat()) * 4095.0f)
                                    val offset = yOff + xOff
                                    raster.setSample(x, y, 0, shorts[offset].toInt() and 0xFFFF)
                                }
                            }
                            ImageIO.write(output, "png", file)
                        }
                    }
                    exportPanel.isVisible = false
                    panelLayer.isVisible = false
                }.with { width = 60.0f }
                hSpacer(SMALL_SPACER_SIZE)
                button(text("Close"), DIALOG_BUTTON_STYLE) {
                    exportPanel.isVisible = false
                    panelLayer.isVisible = false
                }.with { width = 60.0f }
            }
            vSpacer(MEDIUM_SPACER_SIZE)
        }
    }
}

package com.grimfox.gec

import com.grimfox.gec.ui.*
import com.grimfox.gec.ui.nvgproxy.bInt
import com.grimfox.gec.ui.nvgproxy.gInt
import com.grimfox.gec.ui.nvgproxy.rInt
import com.grimfox.gec.ui.widgets.*
import com.grimfox.gec.util.ref
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
    fun pngExtensionFilter(textReference: DynamicTextReference): (String, String) -> Unit = { old, new ->
        if (old != new) {
            if (new.isNotBlank() && !new.endsWith(".png", true)) {
                textReference.reference.value = "$new.png"
            }
        }
    }
    regionFile.reference.addListener(pngExtensionFilter(regionFile))
    biomeFile.reference.addListener(pngExtensionFilter(biomeFile))
    mapFile.reference.addListener(pngExtensionFilter(mapFile))
    heightFile.reference.addListener(pngExtensionFilter(heightFile))
    panelLayer {
        exportPanel = panel(650.0f) {
            vSizing = Sizing.SHRINK
            vSpacer(LARGE_SPACER_SIZE)
            vSaveFileRowWithToggle(regionFile, useRegionFile, LARGE_ROW_HEIGHT, text("Region file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(biomeFile, useBiomeFile, LARGE_ROW_HEIGHT, text("Biome file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(mapFile, useMapFile, LARGE_ROW_HEIGHT, text("Map file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSaveFileRowWithToggle(heightFile, useHeightFile, LARGE_ROW_HEIGHT, text("Height file:"), shrinkGroup, MEDIUM_SPACER_SIZE, dialogLayer, true, ui, "png")
            vSpacer(MEDIUM_SPACER_SIZE)
            vButtonRow(LARGE_ROW_HEIGHT) {
                button(text("Export"), DIALOG_BUTTON_STYLE) {
                    if (useRegionFile.value) {
                        val file = File(regionFile.reference.value)
                        val regionMask = currentState.regionMask.value
                        if (((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) && regionMask != null) {
                            val output = BufferedImage(regionMask.width, regionMask.width, BufferedImage.TYPE_4BYTE_ABGR)
                            val raster = output.raster
                            for (y in 0..regionMask.width - 1) {
                                for (x in 0..regionMask.width - 1) {
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
                            for (y in 0..biomeMask.width - 1) {
                                for (x in 0..biomeMask.width - 1) {
                                    val region = biomeMask[x, y].toInt()
                                    val color = REGION_COLORS[region]
                                    raster.setPixel(x, y, intArrayOf(color.rInt, color.gInt, color.bInt, 255))
                                }
                            }
                            ImageIO.write(output, "png", file)
                        }
                    }
                    if (useMapFile.value) {
                        val file = File(mapFile.reference.value)
                        val regionSplines = currentState.regionSplines.value
                        if (((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) && regionSplines != null) {
                            val textureId = TextureBuilder.renderMapImage(regionSplines.coastPoints, regionSplines.riverPoints + regionSplines.customRiverPoints, regionSplines.mountainPoints + regionSplines.customMountainPoints, regionSplines.ignoredPoints + regionSplines.customIgnoredPoints)
                            val bytes = TextureBuilder.extractTextureRgbaByte(textureId, 4096)
                            val output = BufferedImage(4096, 4096, BufferedImage.TYPE_4BYTE_ABGR)
                            val raster = output.raster
                            for (y in 0..4095) {
                                for (x in 0..4095) {
                                    var offset = (y * 4096 + x) * 4
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
                        val file = File(heightFile.reference.value)
                        val heightMap = currentState.heightMapTexture.value
                        if (((!file.exists() && file.parentFile.isDirectory && file.parentFile.canWrite()) || file.canWrite()) && heightMap != null) {
                            val shorts = TextureBuilder.extractTextureRedShort(heightMap, 4096)
                            val output = BufferedImage(4096, 4096, BufferedImage.TYPE_USHORT_GRAY)
                            val raster = output.raster
                            for (y in 0..4095) {
                                for (x in 0..4095) {
                                    val offset = y * 4096 + x
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

package com.grimfox.gec

import com.grimfox.gec.model.Graph
import com.grimfox.gec.model.Matrix
import com.grimfox.gec.ui.bInt
import com.grimfox.gec.ui.color
import com.grimfox.gec.ui.gInt
import com.grimfox.gec.ui.rInt
import com.grimfox.gec.ui.widgets.DynamicTextReference
import com.grimfox.gec.ui.widgets.MeshViewport3D
import com.grimfox.gec.ui.widgets.NO_BLOCK
import com.grimfox.gec.ui.widgets.TextureBuilder
import com.grimfox.gec.util.*
import org.lwjgl.nanovg.NVGColor
import java.util.*

val REGION_COLORS = arrayListOf(
        color(0.0f, 0.0f, 0.0f),
        color(0.28627452f, 0.93333334f, 0.28627452f),
        color(1.0f, 0.6392157f, 0.9411765f),
        color(1.0f, 1.0f, 0.5294118f),
        color(0.627451f, 0.24313726f, 0.8901961f),
        color(0.8392157f, 0.53333336f, 0.21960784f),
        color(0.41568628f, 0.41568628f, 1.0f),
        color(0.9647059f, 0.34509805f, 0.34509805f),
        color(0.1882353f, 0.8039216f, 0.80784315f),
        color(0.17254902f, 0.57254905f, 0.17254902f),
        color(0.7607843f, 0.16862746f, 0.65882355f),
        color(0.69803923f, 0.7019608f, 0.1764706f),
        color(0.38431373f, 0.18039216f, 0.5372549f),
        color(0.49411765f, 0.34117648f, 0.19215687f),
        color(0.16470589f, 0.16470589f, 0.6627451f),
        color(0.61960787f, 0.16078432f, 0.16078432f),
        color(0.20392157f, 0.45490196f, 0.45490196f)
) as List<NVGColor>

private fun colorToInt(id: Int): Int {
    val color = REGION_COLORS[id]
    return ((color.rInt and 0xFF) shl 16) + ((color.gInt and 0xFF) shl 8) + (color.bInt and 0xFF)
}

val REGION_COLOR_INTS = Array(REGION_COLORS.size) { i ->
    colorToInt(i)
}.toList()


val RANDOM = Random()
val DEFAULT_HEIGHT_SCALE = 50.0f
val MAX_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 10
val MIN_HEIGHT_SCALE = DEFAULT_HEIGHT_SCALE * 0
val HEIGHT_SCALE_CONST = DEFAULT_HEIGHT_SCALE * 0.968746f
val HEIGHT_SCALE_MULTIPLIER = DEFAULT_HEIGHT_SCALE * 32.00223

val heightScaleFunction = { scale: Float ->
    Math.min(MAX_HEIGHT_SCALE, Math.max(MIN_HEIGHT_SCALE, if (scale <= 0.5f) {
        scale * (2 * DEFAULT_HEIGHT_SCALE)
    } else {
        (HEIGHT_SCALE_CONST + (HEIGHT_SCALE_MULTIPLIER * Math.pow(scale - 0.46874918, 2.0))).toFloat()
    }))
}

val heightScaleFunctionInverse = { value: Float ->
    Math.min(1.0f, Math.max(0.0f, if (value <= DEFAULT_HEIGHT_SCALE) {
        value / (2 * DEFAULT_HEIGHT_SCALE)
    } else {
        (Math.sqrt((value - HEIGHT_SCALE_CONST) / HEIGHT_SCALE_MULTIPLIER) + 0.46874918).toFloat()
    }))
}

val heightMapScaleFactor = ref(DEFAULT_HEIGHT_SCALE)
val waterPlaneOn = ref(true)
val perspectiveOn = ref(true)
val rotateAroundCamera = ref(false)
val resetView = mRef(false)
val imageMode = ref(3)
val disableCursor = ref(false)
val hideCursor = ref(false)
val brushOn = ref(false)
val brushActive = ref(false)
val brushSize = ref(10.0f)
val regionEditBrushSize = ref(0.0859375f)
val biomeEditBrushSize = ref(0.0859375f)
val currentEditBrushSize = ref(regionEditBrushSize)
val brushListener = ref<MeshViewport3D.BrushListener?>(null)
val rememberWindowState = ref(preferences.rememberWindowState)
val projectDir = DynamicTextReference(preferences.projectDir.canonicalPath, 1024, TEXT_STYLE_NORMAL)
val tempDir = DynamicTextReference(preferences.tempDir.canonicalPath, 1024, TEXT_STYLE_NORMAL)

val meshViewport = MeshViewport3D(resetView, rotateAroundCamera, perspectiveOn, waterPlaneOn, heightMapScaleFactor, imageMode, disableCursor, hideCursor, brushOn, brushActive, brushListener, brushSize, currentEditBrushSize)

var mainLayer = NO_BLOCK
var panelLayer = NO_BLOCK
var menuLayer = NO_BLOCK
var dropdownLayer = NO_BLOCK
var dialogLayer = NO_BLOCK
var mouseOverlayLayer = NO_BLOCK
var brushShapeOuter = NO_BLOCK
var brushShapeInner = NO_BLOCK

var preferencesPanel = NO_BLOCK
var exportPanel = NO_BLOCK

class CurrentState(var parameters: BuildContinent.ParameterSet? = null,
                           var regionGraph: Graph? = null,
                           var regionMask: Matrix<Byte>? = null,
                           var regionSplines: BuildContinent.RegionSplines? = null,
                           var biomeGraph: Graph? = null,
                           var biomeMask: Matrix<Byte>? = null,
                           var biomes: List<Biomes.Biome>? = null,
                           var heightMapTexture: TextureBuilder.TextureId? = null,
                           var riverMapTexture: TextureBuilder.TextureId? = null,
                           var meshScale: Int? = null)

val currentState = CurrentState()

fun linearClampedScaleFunction(range: IntRange): (Float) -> Int {
    return { scale: Float ->
        clamp(Math.round(scale * (range.last - range.first)) + range.first, range.first, range.last)
    }
}

fun linearClampedScaleFunctionInverse(range: IntRange): (Int) -> Float {
    return { value: Int ->
        clamp(Math.abs((value - range.first).toFloat() / (range.last - range.first)), 0.0f, 1.0f)
    }
}

fun linearClampedScaleFunction(min: Float, max: Float): (Float) -> Float {
    return { scale: Float ->
        clamp((scale * (max - min)) + min, min, max)
    }
}

fun linearClampedScaleFunctionInverse(min: Float, max: Float): (Float) -> Float {
    return { value: Float ->
        clamp(Math.abs((value - min) / (max - min)), 0.0f, 1.0f)
    }
}

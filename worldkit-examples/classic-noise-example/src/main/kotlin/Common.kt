import wk.api.*

var projectPath: String by ref()

val mapScale by cRef(MapScale.MapScale20K)
val baseNoiseSize by cRef(1024)
val outputWidth by cRef(8192)

val ioPath by dir(::projectPath) { "$projectPath/io" }
val landShapesPath by dir(::ioPath) { "$ioPath/land-shapes" }
val islandShapesPath by dir(::landShapesPath) { "$landShapesPath/island" }
val terrainProfilesPath by dir(::ioPath) { "$ioPath/terrain-profiles" }
val inputsPath by dir(::terrainProfilesPath) { "$ioPath/inputs" }

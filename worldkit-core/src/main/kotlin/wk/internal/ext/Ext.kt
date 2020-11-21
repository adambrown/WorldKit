package wk.internal.ext

fun CharSequence.functionNameToText(): String {
    return replace("([A-Z][a-z]+)".toRegex(), " $1")
            .replace("([A-Z][A-Z]+)".toRegex(), " $1")
            .replace("([^A-Za-z]+)".toRegex(), " $1")
            .replace("([A-Z])$".toRegex(), " $1")
            .replace("([a-z])([A-Z])(\\s+)".toRegex(), "$1 $2$3")
            .trim()
            .replace("(\\s+)".toRegex(), " ")
            .toLowerCase()
}

fun fastFloor(x: Float): Float {
    val xi = x.toInt()
    return (if (x < xi) xi - 1 else xi).toFloat()
}

fun fastFloorI(x: Float): Int {
    val xi = x.toInt()
    return if (x < xi) xi - 1 else xi
}

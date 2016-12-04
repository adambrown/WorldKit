package com.grimfox.gec.opengl

import java.io.File

fun getPathForResource(resource: String): String {
    return File(Shaders::class.java.getResource(resource).toURI()).canonicalPath
}

package com.grimfox.gec.util

import org.lwjgl.opengl.GL11.GL_FALSE
import org.lwjgl.opengl.GL20.*

object Shaders

data class ShaderAttribute(val name: String) {
    internal var _location: Int = -1
    val location: Int
        get() = _location
}

data class ShaderUniform(val name: String) {
    internal var _location: Int = -1
    val location: Int
        get() = _location
}

fun loadShaderSource(sourcePath: String): String {
    return getResourceStream(sourcePath).bufferedReader().use { it.readText() }
}

fun compileShader(shaderType: Int, shaderSource: String): Int {
    var errorString = ""
    var shaderHandle = glCreateShader(shaderType)
    if (shaderHandle != 0) {
        glShaderSource(shaderHandle, shaderSource)
        glCompileShader(shaderHandle)
        val compileStatus = IntArray(1)
        glGetShaderiv(shaderHandle, GL_COMPILE_STATUS, compileStatus)
        if (compileStatus[0] == GL_FALSE) {
            errorString = " Compilation error: " + glGetShaderInfoLog(shaderHandle)
            glDeleteShader(shaderHandle)
            shaderHandle = 0
        }
    }
    if (shaderHandle == 0) {
        throw RuntimeException("Error creating shader." + errorString)
    }
    return shaderHandle
}

fun createAndLinkProgram(shaderHandles: List<Int>, attributes: List<ShaderAttribute>, uniforms: List<ShaderUniform>): Int {
    var errorString = ""
    var programHandle = glCreateProgram()
    if (programHandle != 0) {
        shaderHandles.forEach {
            glAttachShader(programHandle, it)
        }
        glLinkProgram(programHandle)
        val linkStatus = IntArray(1)
        glGetProgramiv(programHandle, GL_LINK_STATUS, linkStatus)
        if (linkStatus[0] == GL_FALSE) {
            errorString = " Compilation error: ${glGetProgramInfoLog(programHandle)}"
            glDeleteProgram(programHandle)
            programHandle = 0
        }
        if (programHandle != 0) {
            attributes.forEach {
                it._location = glGetAttribLocation(programHandle, it.name)
            }
            uniforms.forEach {
                it._location = glGetUniformLocation(programHandle, it.name)
            }
        }
    }
    if (programHandle == 0) {
        throw RuntimeException("Error creating program.$errorString")
    }
    return programHandle
}

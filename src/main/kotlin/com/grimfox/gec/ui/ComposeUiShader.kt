package com.grimfox.gec.ui

import com.grimfox.gec.ui.widgets.TextureBuilder.TextureId
import com.grimfox.gec.util.*
import org.joml.Matrix4f
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL13.*
import org.lwjgl.opengl.GL20.*

class ComposeUiShader(vertexShaderResource: String, fragmentShaderResource: String) {

    private val floatBuffer = BufferUtils.createFloatBuffer(16)
    private val projectionMatrix = Matrix4f()

    private val mvpMatrixUniform = ShaderUniform("modelViewProjectionMatrix")
    private val imageTextureUniform = ShaderUniform("imageTexture")
    private val positionAttribute = ShaderAttribute("position")
    private val uvAttribute = ShaderAttribute("uv")
    private val instancePositionAttribute = ShaderAttribute("instancePosition")

    private val vertexShader = compileShader(GL_VERTEX_SHADER, loadShaderSource(vertexShaderResource))
    private val fragmentShader = compileShader(GL_FRAGMENT_SHADER, loadShaderSource(fragmentShaderResource))

    private val shaderProgram = createAndLinkProgram(
            listOf(vertexShader, fragmentShader),
            listOf(positionAttribute, uvAttribute, instancePositionAttribute),
            listOf(mvpMatrixUniform, imageTextureUniform)
    )

    private val instanceRenderer = InstanceRenderer(1.0f, positionAttribute, uvAttribute, instancePositionAttribute)

    fun <T> use(block: ComposeUiShader.() -> T): T {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA)
        glEnable(GL_BLEND)
        glDisable(GL_CULL_FACE)
        glDisable(GL_DEPTH_TEST)
        glUseProgram(shaderProgram)
        try {
            return block()
        } finally {
            glUseProgram(0)
        }
    }

    fun render(width: Int, height: Int, instances: List<Pair<RenderableInstance, TextureId>>) {
        glViewport(0, 0, width, height)
        projectionMatrix.setOrtho(0.0f, width.toFloat(), height.toFloat(), 0.0f, 0.0f, 1000.0f)
        glUniformMatrix4fv(mvpMatrixUniform.location, false, projectionMatrix.get(0, floatBuffer))
        instances.forEach { (instance, textureId) ->
            glUniform1i(imageTextureUniform.location, 0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, textureId.id)
            instanceRenderer.render(listOf(instance))
        }
    }
}
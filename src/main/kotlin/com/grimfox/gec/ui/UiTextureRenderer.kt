package com.grimfox.gec.ui

import com.grimfox.gec.doOnMainThread
import org.lwjgl.opengl.GL11.*
import org.lwjgl.opengl.GL12.*
import org.lwjgl.opengl.GL20.glDrawBuffers
import org.lwjgl.opengl.GL30.*
import org.lwjgl.system.MemoryUtil

class UiTextureRenderer(val width: Int, val height: Int) {

    val msFboId = glGenFramebuffers()
    val fboId = glGenFramebuffers()
    val renderTextureId: Int
    val depthBufferId: Int
    val colorBufferId: Int

    init {
        glBindFramebuffer(GL_FRAMEBUFFER, msFboId)

        depthBufferId = glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER, depthBufferId)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_DEPTH24_STENCIL8, width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_STENCIL_ATTACHMENT, GL_RENDERBUFFER, depthBufferId)

        colorBufferId = glGenRenderbuffers()
        glBindRenderbuffer(GL_RENDERBUFFER, colorBufferId)
        glRenderbufferStorageMultisample(GL_RENDERBUFFER, 4, GL_RGBA8, width, height)
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_RENDERBUFFER, colorBufferId)

        glDrawBuffers(intArrayOf(GL_COLOR_ATTACHMENT0))
        var frameBufferStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (frameBufferStatus != GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not created successfully. Code: $frameBufferStatus")
        }

        glBindFramebuffer(GL_FRAMEBUFFER, fboId)

        renderTextureId = glGenTextures()
        glBindTexture(GL_TEXTURE_2D, renderTextureId)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, MemoryUtil.NULL)
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, renderTextureId, 0)

        glDrawBuffers(intArrayOf(GL_COLOR_ATTACHMENT0))
        frameBufferStatus = glCheckFramebufferStatus(GL_FRAMEBUFFER)
        if (frameBufferStatus != GL_FRAMEBUFFER_COMPLETE) {
            throw RuntimeException("Framebuffer not created successfully. Code: $frameBufferStatus")
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        glBindFramebuffer(GL_FRAMEBUFFER, msFboId)
        glViewport(0, 0, width, height)
    }

    fun unbind() {
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, msFboId)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, fboId)
        glBlitFramebuffer(0, 0, width, height, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_NEAREST)
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0)
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0)
    }

    @Suppress("unused")
    fun finalize() {
        doOnMainThread {
            glDeleteFramebuffers(fboId)
            glDeleteFramebuffers(msFboId)
            glDeleteRenderbuffers(depthBufferId)
            glDeleteRenderbuffers(colorBufferId)
            glDeleteTextures(renderTextureId)
        }
    }
}
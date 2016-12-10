package com.grimfox.gec.learning

import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.opengl.GL
import org.lwjgl.system.Platform

import org.lwjgl.glfw.Callbacks.*
import org.lwjgl.glfw.GLFW.*
import org.lwjgl.nanovg.NanoVG.*
import org.lwjgl.nanovg.NanoVGGL3.*
import org.lwjgl.opengl.GL11.*
import org.lwjgl.system.MemoryUtil.*

/**
 * OpenGL 3.0+ demo.

 *
 * This is a Java port of
 * [https://github.com/memononen/nanovg/blob/master/example/example_gl3.c](https://github.com/memononen/nanovg/blob/master/example/example_gl3.c).
 */
object ExampleGL3 {
    private var blowup: Boolean = false
    private var screenshot: Boolean = false
    private var premult: Boolean = false

    @JvmStatic fun main(args: Array<String>) {
        GLFWErrorCallback.createThrow().set()
        if (!glfwInit())
            throw RuntimeException("Failed to init GLFW.")

        val data = DemoData()
        val gpuTimer = GPUtimer()

        val fps = PerfGraph()
        val cpuGraph = PerfGraph()
        val gpuGraph = PerfGraph()

        initGraph(fps, GRAPH_RENDER_FPS, "Frame Time")
        initGraph(cpuGraph, GRAPH_RENDER_MS, "CPU Time")
        initGraph(gpuGraph, GRAPH_RENDER_MS, "GPU Time")

        if (Platform.get() === Platform.MACOSX) {
            glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
            glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 2)
            glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GLFW_TRUE)
            glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)
        }

        val DEMO_MSAA = args.isNotEmpty() && "msaa".equals(args[0], ignoreCase = true)
        if (DEMO_MSAA)
            glfwWindowHint(GLFW_SAMPLES, 4)

        val window = glfwCreateWindow(1000, 600, "NanoVG", NULL, NULL)
        //window = glfwCreateWindow(1000, 600, "NanoVG", glfwGetPrimaryMonitor(), NULL);
        if (window == NULL) {
            glfwTerminate()
            throw RuntimeException()
        }

        glfwSetKeyCallback(window) { windowHandle, keyCode, scancode, action, mods ->
            if (keyCode == GLFW_KEY_ESCAPE && action == GLFW_PRESS)
                glfwSetWindowShouldClose(windowHandle, true)
            if (keyCode == GLFW_KEY_SPACE && action == GLFW_PRESS)
                blowup = !blowup
            if (keyCode == GLFW_KEY_S && action == GLFW_PRESS)
                screenshot = true
            if (keyCode == GLFW_KEY_P && action == GLFW_PRESS)
                premult = !premult
        }

        glfwMakeContextCurrent(window)
        GL.createCapabilities()

        val vg = if (DEMO_MSAA)
            nvgCreate(NVG_STENCIL_STROKES or NVG_DEBUG)
        else
            nvgCreate(NVG_ANTIALIAS or NVG_STENCIL_STROKES or NVG_DEBUG)

        if (vg == NULL) {
            throw RuntimeException("Could not init nanovg.")
        }

        if (loadDemoData(vg, data) == -1)
            throw RuntimeException()

        glfwSwapInterval(0)

        initGPUTimer(gpuTimer)

        glfwSetTime(0.0)
        var prevt = glfwGetTime()

        while (!glfwWindowShouldClose(window)) {
            val t: Double
            val dt: Double
            val pxRatio: Float

            t = glfwGetTime()
            dt = t - prevt
            prevt = t
            updateGraph(fps, dt.toFloat())

            startGPUTimer(gpuTimer)

            glfwGetCursorPos(window, mx, my)
            glfwGetWindowSize(window, winWidth, winHeight)
            glfwGetFramebufferSize(window, fbWidth, fbHeight)

            // Calculate pixel ration for hi-dpi devices.
            pxRatio = fbWidth.get(0).toFloat() / winWidth.get(0).toFloat()

            // Update and render
            glViewport(0, 0, fbWidth.get(0), fbHeight.get(0))
            if (premult)
                glClearColor(0f, 0f, 0f, 0f)
            else
                glClearColor(0.3f, 0.3f, 0.32f, 1.0f)
            glClear(GL_COLOR_BUFFER_BIT or GL_DEPTH_BUFFER_BIT or GL_STENCIL_BUFFER_BIT)

            nvgBeginFrame(vg, winWidth.get(0), winHeight.get(0), pxRatio)

            renderDemo(vg, mx.get(0).toFloat(), my.get(0).toFloat(), winWidth.get(0).toFloat(), winHeight.get(0).toFloat(), t.toFloat(), blowup, data)
            renderGraph(vg, 5f, 5f, fps)
            renderGraph(vg, (5 + 200 + 5).toFloat(), 5f, cpuGraph)
            if (gpuTimer.supported)
                renderGraph(vg, (5 + 200 + 5 + 200 + 5).toFloat(), 5f, gpuGraph)

            nvgEndFrame(vg)

            // Measure the CPU time taken excluding swap buffers (as the swap may wait for GPU)
            val cpuTime = glfwGetTime() - t

            updateGraph(fps, dt.toFloat())
            updateGraph(cpuGraph, cpuTime.toFloat())

            // We may get multiple results.
            val n = stopGPUTimer(gpuTimer, gpuTimes, 3)
            for (i in 0..n - 1)
                updateGraph(gpuGraph, gpuTimes.get(i))

            if (screenshot) {
                screenshot = false
                saveScreenShot(fbWidth.get(0), fbHeight.get(0), premult, "dump.png")
            }

            glfwSwapBuffers(window)
            glfwPollEvents()
        }

        freeDemoData(vg, data)

        nvgDelete(vg)

        System.out.printf("Average Frame Time: %.2f ms\n", getGraphAverage(fps) * 1000.0f)
        System.out.printf("          CPU Time: %.2f ms\n", getGraphAverage(cpuGraph) * 1000.0f)
        System.out.printf("          GPU Time: %.2f ms\n", getGraphAverage(gpuGraph) * 1000.0f)

        glfwFreeCallbacks(window)
        glfwTerminate()
        glfwSetErrorCallback(null).free()
    }
}
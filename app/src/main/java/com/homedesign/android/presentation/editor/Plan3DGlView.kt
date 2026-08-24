package com.homedesign.android.presentation.editor

import android.content.Context
import android.graphics.Color
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.view.MotionEvent
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.render3d.HomeExtrusion
import com.homedesign.android.domain.render3d.HomeScene3D
import com.homedesign.android.domain.render3d.MeshTri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * OpenGL ES 2.0 extruded plan view — Tier 3 working 3D without Filament native deps.
 * Orbit with one finger; pinch-ish vertical drag adjusts distance.
 */
class Plan3DGlView(context: Context) : GLSurfaceView(context) {
    private val renderer = Plan3DRenderer()

    init {
        setEGLContextClientVersion(2)
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setHome(home: Home) {
        queueEvent { renderer.setHome(home) }
    }

    private var lastX = 0f
    private var lastY = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - lastX
                val dy = event.y - lastY
                lastX = event.x
                lastY = event.y
                if (event.pointerCount >= 2) {
                    renderer.distance *= (1f + dy * 0.005f)
                    renderer.distance = renderer.distance.coerceIn(2f, 80f)
                } else {
                    renderer.yawDeg += dx * 0.4f
                    renderer.pitchDeg = (renderer.pitchDeg + dy * 0.3f).coerceIn(15f, 85f)
                }
            }
        }
        return true
    }
}

private class Plan3DRenderer : GLSurfaceView.Renderer {
    @Volatile var yawDeg = 45f
    @Volatile var pitchDeg = 55f
    @Volatile var distance = 12f

    private var program = 0
    private var uMvp = 0
    private var uColor = 0
    private var aPos = 0
    private var scene: HomeScene3D? = null
    private var gpuMeshes: List<GpuMesh> = emptyList()

    private val view = FloatArray(16)
    private val proj = FloatArray(16)
    private val mvp = FloatArray(16)

    fun setHome(home: Home) {
        scene = HomeExtrusion.build(home)
        // Rebuild GPU buffers on next draw if context ready
        pendingRebuild = true
    }

    @Volatile private var pendingRebuild = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.96f, 0.94f, 0.91f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        program = buildProgram(VERT, FRAG)
        uMvp = GLES20.glGetUniformLocation(program, "uMvp")
        uColor = GLES20.glGetUniformLocation(program, "uColor")
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        pendingRebuild = true
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val aspect = width.toFloat() / max(height, 1)
        Matrix.perspectiveM(proj, 0, 45f, aspect, 0.1f, 200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (pendingRebuild) {
            pendingRebuild = false
            gpuMeshes = scene?.meshes?.map { upload(it) }.orEmpty()
        }
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = scene ?: return
        val yaw = Math.toRadians(yawDeg.toDouble())
        val pitch = Math.toRadians(pitchDeg.toDouble())
        val eyeX = s.centerX + (distance * cos(pitch) * sin(yaw)).toFloat()
        val eyeY = (distance * sin(pitch)).toFloat()
        val eyeZ = s.centerZ + (distance * cos(pitch) * cos(yaw)).toFloat()
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, s.centerX, 0.5f, s.centerZ, 0f, 1f, 0f)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)

        GLES20.glUseProgram(program)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        for (mesh in gpuMeshes) {
            val r = Color.red(mesh.color) / 255f
            val g = Color.green(mesh.color) / 255f
            val b = Color.blue(mesh.color) / 255f
            GLES20.glUniform4f(uColor, r, g, b, 1f)
            GLES20.glEnableVertexAttribArray(aPos)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, mesh.buf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
            GLES20.glDisableVertexAttribArray(aPos)
        }
    }

    private fun upload(mesh: MeshTri): GpuMesh {
        val buf = ByteBuffer.allocateDirect(mesh.positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(mesh.positions)
        buf.position(0)
        return GpuMesh(buf, mesh.positions.size / 3, mesh.colorArgb)
    }

    private data class GpuMesh(val buf: FloatBuffer, val vertexCount: Int, val color: Int)

    private fun buildProgram(vert: String, frag: String): Int {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vert)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, frag)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        return prog
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES20.glCreateShader(type)
        GLES20.glShaderSource(s, src)
        GLES20.glCompileShader(s)
        return s
    }

    companion object {
        private const val VERT = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            void main() {
              gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """
        private const val FRAG = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
              gl_FragColor = uColor;
            }
        """
    }
}

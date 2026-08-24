package com.homedesign.android.presentation.editor

import android.content.Context
import android.graphics.Color
import android.media.Image
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.view.MotionEvent
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.render3d.HomeExtrusion
import com.homedesign.android.domain.render3d.HomeScene3D
import com.homedesign.android.domain.render3d.MeshTri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.max

/**
 * ARCore + GLES3 view: camera feed, horizontal-plane hit-test, tap to place
 * the extruded home mesh (or footprint box). Uses Depth API for mesh occlusion
 * when supported; otherwise clips fragments below the detected plane.
 * Does not use Filament / SceneView.
 */
class ArHomeGlView(context: Context) : GLSurfaceView(context) {
    private val renderer = ArHomeRenderer(context)

    var onStatus: ((String) -> Unit)? = null
        set(value) {
            field = value
            renderer.onStatus = value
        }

    init {
        // GLES3 for DEPTH16 → RG8 upload (ARCore Depth API).
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    fun setHome(home: Home, soloFurnitureId: String? = null) {
        queueEvent { renderer.setHome(home, soloFurnitureId) }
    }

    /** Uniform model scale for the placed home / piece (1 = real size). */
    fun setModelScale(scale: Float) {
        queueEvent { renderer.modelScale = scale.coerceIn(0.15f, 3f) }
    }

    fun onHostResume() {
        // Session create / requestInstall must run on the UI thread.
        renderer.resumeSession()
        onResume()
    }

    fun onHostPause() {
        onPause()
        renderer.pauseSession()
    }

    fun destroySession() {
        renderer.closeSessionSync()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            renderer.queueTap(event.x, event.y)
        }
        return true
    }
}

private class ArHomeRenderer(private val context: Context) : GLSurfaceView.Renderer {
    var onStatus: ((String) -> Unit)? = null

    private var session: Session? = null
    private var installRequested = false
    private var viewportWidth = 1
    private var viewportHeight = 1
    private var displayRotation = 0
    private var viewportChanged = true

    private var cameraTexId = 0
    private var cameraProgram = 0
    private var meshProgram = 0
    private var uCamTexture = 0
    private var aCamPos = 0
    private var aCamUv = 0
    private var uMvp = 0
    private var uMv = 0
    private var uModel = 0
    private var uColor = 0
    private var uDepthTex = 0
    private var uUseDepth = 0
    private var uPlaneY = 0
    private var uPlaneClip = 0
    private var aMeshPos = 0

    private var depthTexId = 0
    private var depthReady = false
    private var isDepthSupported = false

    private val ndcQuad = floatArrayOf(
        -1f, -1f, +1f, -1f, -1f, +1f, +1f, +1f,
    )
    private var camVertices: FloatBuffer = emptyFloatBuffer(8)
    private var camUvs: FloatBuffer = emptyFloatBuffer(8)
    private val uvScratch = FloatArray(8)

    private var scene: HomeScene3D? = null
    private var gpuMeshes: List<GpuMesh> = emptyList()
    @Volatile private var pendingRebuild = false
    @Volatile private var soloMode = false
    @Volatile var modelScale: Float = 1f

    private var placedAnchor: Anchor? = null
    private val pendingTap = AtomicReference<Pair<Float, Float>?>(null)
    private var planeProgram = 0
    private var uPlaneMvp = 0
    private var uPlaneColor = 0
    private var aPlanePos = 0
    private var planeLineBuf: FloatBuffer = emptyFloatBuffer(6)

    private val viewMatrix = FloatArray(16)
    private val projMatrix = FloatArray(16)
    private val anchorMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val centerOffset = FloatArray(16)

    private val statusLock = Any()
    private var lastStatus: String? = null

    fun setHome(home: Home, soloFurnitureId: String? = null) {
        scene = if (soloFurnitureId != null) {
            HomeExtrusion.buildFurnitureOnly(home, soloFurnitureId)
        } else {
            HomeExtrusion.build(home)
        }
        soloMode = soloFurnitureId != null
        pendingRebuild = true
    }

    fun queueTap(x: Float, y: Float) {
        pendingTap.set(x to y)
    }

    fun resumeSession() {
        val s = ensureSession() ?: return
        try {
            s.resume()
            if (cameraTexId != 0) {
                s.setCameraTextureName(cameraTexId)
            }
        } catch (e: CameraNotAvailableException) {
            Log.e(TAG, "Camera not available", e)
            postStatus("Camera not available")
        }
    }

    fun pauseSession() {
        try {
            session?.pause()
        } catch (_: Throwable) {
        }
    }

    fun closeSessionSync() {
        try {
            placedAnchor?.detach()
        } catch (_: Throwable) {
        }
        placedAnchor = null
        try {
            session?.close()
        } catch (_: Throwable) {
        }
        session = null
    }

    private fun ensureSession(): Session? {
        session?.let { return it }
        val activity = context as? android.app.Activity
        if (activity == null) {
            postStatus("AR needs an Activity context")
            return null
        }
        return try {
            when (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    postStatus("Install Google Play Services for AR, then return")
                    return null
                }
                ArCoreApk.InstallStatus.INSTALLED -> Unit
            }
            val s = Session(activity)
            val config = Config(s)
            config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
            config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            config.lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
            isDepthSupported = s.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
            config.depthMode = if (isDepthSupported) {
                Config.DepthMode.AUTOMATIC
            } else {
                Config.DepthMode.DISABLED
            }
            s.configure(config)
            session = s
            val depthHint = if (isDepthSupported) " · depth occlusion on" else " · plane clip (no depth)"
            postStatus("Move phone to find a floor, then tap to place$depthHint")
            s
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to create AR session", t)
            postStatus("ARCore unavailable: ${t.javaClass.simpleName}")
            null
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        cameraTexId = genExternalTexture()
        cameraProgram = buildProgram(CAM_VERT, CAM_FRAG)
        uCamTexture = GLES20.glGetUniformLocation(cameraProgram, "uTexture")
        aCamPos = GLES20.glGetAttribLocation(cameraProgram, "aPosition")
        aCamUv = GLES20.glGetAttribLocation(cameraProgram, "aTexCoord")

        meshProgram = buildProgram(MESH_VERT, MESH_FRAG)
        uMvp = GLES20.glGetUniformLocation(meshProgram, "uMvp")
        uMv = GLES20.glGetUniformLocation(meshProgram, "uMv")
        uModel = GLES20.glGetUniformLocation(meshProgram, "uModel")
        uColor = GLES20.glGetUniformLocation(meshProgram, "uColor")
        uDepthTex = GLES20.glGetUniformLocation(meshProgram, "uDepth")
        uUseDepth = GLES20.glGetUniformLocation(meshProgram, "uUseDepth")
        uPlaneY = GLES20.glGetUniformLocation(meshProgram, "uPlaneY")
        uPlaneClip = GLES20.glGetUniformLocation(meshProgram, "uPlaneClip")
        aMeshPos = GLES20.glGetAttribLocation(meshProgram, "aPos")

        depthTexId = genDepthTexture()

        planeProgram = buildProgram(PLANE_VERT, PLANE_FRAG)
        uPlaneMvp = GLES20.glGetUniformLocation(planeProgram, "uMvp")
        uPlaneColor = GLES20.glGetUniformLocation(planeProgram, "uColor")
        aPlanePos = GLES20.glGetAttribLocation(planeProgram, "aPos")

        camVertices = floatBufferOf(*ndcQuad)
        camUvs = emptyFloatBuffer(8)
        pendingRebuild = true
        session?.setCameraTextureName(cameraTexId)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = max(width, 1)
        viewportHeight = max(height, 1)
        GLES20.glViewport(0, 0, viewportWidth, viewportHeight)
        viewportChanged = true
        displayRotation = (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.rotation
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val s = session ?: return

        if (viewportChanged) {
            s.setDisplayGeometry(displayRotation, viewportWidth, viewportHeight)
            viewportChanged = false
            s.setCameraTextureName(cameraTexId)
        }

        val frame: Frame = try {
            s.update()
        } catch (e: CameraNotAvailableException) {
            postStatus("Camera not available")
            return
        }

        if (pendingRebuild) {
            pendingRebuild = false
            gpuMeshes = scene?.meshes?.map { upload(it) }.orEmpty()
            if (gpuMeshes.isEmpty()) {
                // Fallback footprint box ~4×0.1×3 m if home empty
                gpuMeshes = listOf(upload(footprintBox(2f, 0.05f, 1.5f, 0xFFB85C3C.toInt())))
            }
        }

        drawCamera(frame)
        if (isDepthSupported) {
            updateDepthTexture(frame)
        }

        val camera = frame.camera
        if (camera.trackingState != TrackingState.TRACKING) {
            postStatus("Move phone slowly to start tracking…")
            return
        }

        val planeCount = s.getAllTrackables(Plane::class.java)
            .count { it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
        handleTap(frame)

        camera.getViewMatrix(viewMatrix, 0)
        camera.getProjectionMatrix(projMatrix, 0, 0.1f, 100f)

        // Detected horizontal plane extents (grid / outline) in world space.
        drawTrackedPlanes(s, viewMatrix, projMatrix)

        val anchor = placedAnchor
        if (anchor != null && anchor.trackingState == TrackingState.TRACKING) {
            anchor.pose.toMatrix(anchorMatrix, 0)
            val sc = scene
            val scale = modelScale.coerceIn(0.15f, 3f)
            // centerOffset = S * T(-center) so scale is about plan centre, then anchor.
            Matrix.setIdentityM(centerOffset, 0)
            Matrix.scaleM(centerOffset, 0, scale, scale, scale)
            if (sc != null) {
                Matrix.translateM(centerOffset, 0, -sc.centerX, 0f, -sc.centerZ)
            }
            Matrix.multiplyMM(modelMatrix, 0, anchorMatrix, 0, centerOffset, 0)
            Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
            Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvMatrix, 0)
            val planeY = anchorMatrix[13]
            drawMeshes(mvpMatrix, mvMatrix, modelMatrix, planeY)
            val label = if (soloMode) "Piece" else "Home"
            val scalePct = (scale * 100f).toInt()
            val occ = when {
                isDepthSupported && depthReady -> "depth occ"
                isDepthSupported -> "depth warming"
                else -> "plane clip"
            }
            postStatus("$label placed · ${scalePct}% · $occ · tap to move · $planeCount plane(s)")
        } else if (planeCount > 0) {
            postStatus(
                if (soloMode) "Tap a detected floor to place this piece"
                else "Tap a detected floor to place your home",
            )
        } else {
            postStatus("Scanning for a floor surface…")
        }
    }

    /** Upload DEPTH16 image as RG8 for fragment occlusion tests. */
    private fun updateDepthTexture(frame: Frame) {
        if (depthTexId == 0) return
        var image: Image? = null
        try {
            image = frame.acquireDepthImage16Bits()
            val plane = image.planes[0]
            val buf = plane.buffer
            // Row-stride may exceed width*2; upload tightly packed copy when needed.
            val width = image.width
            val height = image.height
            val rowStride = plane.rowStride
            val pixelStride = plane.pixelStride
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTexId)
            if (rowStride == width * pixelStride) {
                GLES30.glTexImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    GLES30.GL_RG8,
                    width,
                    height,
                    0,
                    GLES30.GL_RG,
                    GLES20.GL_UNSIGNED_BYTE,
                    buf,
                )
            } else {
                val packed = ByteBuffer.allocateDirect(width * height * 2)
                val row = ByteArray(rowStride)
                for (y in 0 until height) {
                    buf.position(y * rowStride)
                    buf.get(row, 0, minOf(rowStride, buf.remaining()))
                    packed.put(row, 0, width * 2)
                }
                packed.position(0)
                GLES30.glTexImage2D(
                    GLES20.GL_TEXTURE_2D,
                    0,
                    GLES30.GL_RG8,
                    width,
                    height,
                    0,
                    GLES30.GL_RG,
                    GLES20.GL_UNSIGNED_BYTE,
                    packed,
                )
            }
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            depthReady = true
        } catch (_: NotYetAvailableException) {
            // Depth map not ready yet — keep last texture / plane-clip fallback.
        } catch (t: Throwable) {
            Log.w(TAG, "Depth image unavailable", t)
            depthReady = false
        } finally {
            try {
                image?.close()
            } catch (_: Throwable) {
            }
        }
    }

    /** Draw ARCore plane polygons as a light grid outline on the floor. */
    private fun drawTrackedPlanes(session: Session, view: FloatArray, proj: FloatArray) {
        val verts = ArrayList<Float>(128)
        for (plane in session.getAllTrackables(Plane::class.java)) {
            if (plane.trackingState != TrackingState.TRACKING) continue
            if (plane.type != Plane.Type.HORIZONTAL_UPWARD_FACING) continue
            val polygon = plane.polygon ?: continue
            val n = polygon.limit() / 2
            if (n < 3) continue
            val pose = plane.centerPose
            val world = FloatArray(3)
            // Outer ring
            for (i in 0 until n) {
                val j = (i + 1) % n
                val x0 = polygon.get(i * 2)
                val z0 = polygon.get(i * 2 + 1)
                val x1 = polygon.get(j * 2)
                val z1 = polygon.get(j * 2 + 1)
                pose.transformPoint(floatArrayOf(x0, 0f, z0), 0, world, 0)
                verts.add(world[0]); verts.add(world[1] + 0.01f); verts.add(world[2])
                pose.transformPoint(floatArrayOf(x1, 0f, z1), 0, world, 0)
                verts.add(world[0]); verts.add(world[1] + 0.01f); verts.add(world[2])
            }
            // Simple cross through extent centre for a grid cue
            var minX = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var minZ = Float.POSITIVE_INFINITY
            var maxZ = Float.NEGATIVE_INFINITY
            polygon.position(0)
            for (i in 0 until n) {
                val x = polygon.get(i * 2)
                val z = polygon.get(i * 2 + 1)
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (z < minZ) minZ = z
                if (z > maxZ) maxZ = z
            }
            val midX = (minX + maxX) * 0.5f
            val midZ = (minZ + maxZ) * 0.5f
            fun addEdge(ax: Float, az: Float, bx: Float, bz: Float) {
                pose.transformPoint(floatArrayOf(ax, 0f, az), 0, world, 0)
                verts.add(world[0]); verts.add(world[1] + 0.01f); verts.add(world[2])
                pose.transformPoint(floatArrayOf(bx, 0f, bz), 0, world, 0)
                verts.add(world[0]); verts.add(world[1] + 0.01f); verts.add(world[2])
            }
            addEdge(minX, midZ, maxX, midZ)
            addEdge(midX, minZ, midX, maxZ)
            // Extra interior grid lines (~0.5 m local)
            var gx = minX + 0.5f
            while (gx < maxX - 0.05f) {
                addEdge(gx, minZ, gx, maxZ)
                gx += 0.5f
            }
            var gz = minZ + 0.5f
            while (gz < maxZ - 0.05f) {
                addEdge(minX, gz, maxX, gz)
                gz += 0.5f
            }
        }
        if (verts.isEmpty()) return
        val arr = verts.toFloatArray()
        if (planeLineBuf.capacity() < arr.size) {
            planeLineBuf = emptyFloatBuffer(arr.size)
        }
        planeLineBuf.position(0)
        planeLineBuf.put(arr)
        planeLineBuf.position(0)

        Matrix.multiplyMM(mvpMatrix, 0, proj, 0, view, 0)
        GLES20.glUseProgram(planeProgram)
        GLES20.glUniformMatrix4fv(uPlaneMvp, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(uPlaneColor, 0.55f, 0.78f, 1f, 0.55f)
        GLES20.glLineWidth(2f)
        GLES20.glEnableVertexAttribArray(aPlanePos)
        GLES20.glVertexAttribPointer(aPlanePos, 3, GLES20.GL_FLOAT, false, 0, planeLineBuf)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, arr.size / 3)
        GLES20.glDisableVertexAttribArray(aPlanePos)
    }

    private fun handleTap(frame: Frame) {
        val tap = pendingTap.getAndSet(null) ?: return
        if (frame.camera.trackingState != TrackingState.TRACKING) return
        val hits = frame.hitTest(tap.first, tap.second)
        for (hit in hits) {
            val trackable = hit.trackable
            if (trackable is Plane &&
                trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                trackable.isPoseInPolygon(hit.hitPose)
            ) {
                placedAnchor?.detach()
                placedAnchor = hit.createAnchor()
                postStatus("Placed on floor")
                return
            }
        }
        postStatus("Tap a highlighted floor area")
    }

    private fun drawCamera(frame: Frame) {
        frame.transformCoordinates2d(
            Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
            ndcQuad,
            Coordinates2d.TEXTURE_NORMALIZED,
            uvScratch,
        )
        camUvs.position(0)
        camUvs.put(uvScratch)
        camUvs.position(0)
        camVertices.position(0)

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glUseProgram(cameraProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexId)
        GLES20.glUniform1i(uCamTexture, 0)
        GLES20.glEnableVertexAttribArray(aCamPos)
        GLES20.glVertexAttribPointer(aCamPos, 2, GLES20.GL_FLOAT, false, 0, camVertices)
        GLES20.glEnableVertexAttribArray(aCamUv)
        GLES20.glVertexAttribPointer(aCamUv, 2, GLES20.GL_FLOAT, false, 0, camUvs)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aCamPos)
        GLES20.glDisableVertexAttribArray(aCamUv)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun drawMeshes(
        mvp: FloatArray,
        mv: FloatArray,
        model: FloatArray,
        planeY: Float,
    ) {
        GLES20.glUseProgram(meshProgram)
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0)
        GLES20.glUniformMatrix4fv(uMv, 1, false, mv, 0)
        GLES20.glUniformMatrix4fv(uModel, 1, false, model, 0)
        GLES20.glUniform1f(uPlaneY, planeY)
        // Always clip below the placement plane (hides through-floor bleed).
        GLES20.glUniform1f(uPlaneClip, 1f)
        val useDepth = isDepthSupported && depthReady
        GLES20.glUniform1f(uUseDepth, if (useDepth) 1f else 0f)
        if (useDepth) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, depthTexId)
            GLES20.glUniform1i(uDepthTex, 1)
        }
        for (mesh in gpuMeshes) {
            val r = Color.red(mesh.color) / 255f
            val g = Color.green(mesh.color) / 255f
            val b = Color.blue(mesh.color) / 255f
            GLES20.glUniform4f(uColor, r, g, b, 0.92f)
            GLES20.glEnableVertexAttribArray(aMeshPos)
            GLES20.glVertexAttribPointer(aMeshPos, 3, GLES20.GL_FLOAT, false, 0, mesh.buf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, mesh.vertexCount)
            GLES20.glDisableVertexAttribArray(aMeshPos)
        }
        if (useDepth) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        }
    }

    private fun postStatus(msg: String) {
        synchronized(statusLock) {
            if (msg == lastStatus) return
            lastStatus = msg
        }
        val cb = onStatus ?: return
        android.os.Handler(android.os.Looper.getMainLooper()).post { cb(msg) }
    }

    private data class GpuMesh(val buf: FloatBuffer, val vertexCount: Int, val color: Int)

    private fun upload(mesh: MeshTri): GpuMesh {
        val buf = ByteBuffer.allocateDirect(mesh.positions.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buf.put(mesh.positions)
        buf.position(0)
        return GpuMesh(buf, mesh.positions.size / 3, mesh.colorArgb)
    }

    companion object {
        private const val TAG = "ArHomeGlView"

        private val CAM_VERT = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
              gl_Position = aPosition;
              vTexCoord = aTexCoord;
            }
        """.trimIndent()

        private val CAM_FRAG = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTexCoord;
            uniform samplerExternalOES uTexture;
            void main() {
              gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        private val MESH_VERT = """
            uniform mat4 uMvp;
            uniform mat4 uMv;
            uniform mat4 uModel;
            attribute vec3 aPos;
            varying vec3 vViewPos;
            varying vec4 vClip;
            varying float vWorldY;
            void main() {
              vec4 wp = uModel * vec4(aPos, 1.0);
              vWorldY = wp.y;
              vViewPos = (uMv * vec4(aPos, 1.0)).xyz;
              vClip = uMvp * vec4(aPos, 1.0);
              gl_Position = vClip;
            }
        """.trimIndent()

        private val MESH_FRAG = """
            precision mediump float;
            uniform vec4 uColor;
            uniform sampler2D uDepth;
            uniform float uUseDepth;
            uniform float uPlaneY;
            uniform float uPlaneClip;
            varying vec3 vViewPos;
            varying vec4 vClip;
            varying float vWorldY;

            float depthMm(vec2 uv) {
              vec2 packed = texture2D(uDepth, uv).rg;
              return dot(packed, vec2(255.0, 256.0 * 255.0));
            }

            void main() {
              // Hide geometry below the detected floor (always-on soft clip).
              if (uPlaneClip > 0.5 && vWorldY < uPlaneY - 0.02) {
                discard;
              }
              if (uUseDepth > 0.5) {
                vec3 ndc = vClip.xyz / max(vClip.w, 1e-6);
                vec2 screenUv = ndc.xy * 0.5 + 0.5;
                if (screenUv.x >= 0.0 && screenUv.x <= 1.0 &&
                    screenUv.y >= 0.0 && screenUv.y <= 1.0) {
                  float realMm = depthMm(screenUv);
                  float virtMm = max(-vViewPos.z, 0.0) * 1000.0;
                  // Bias ~8 cm: prefer keeping virtual content visible at contact.
                  if (realMm > 50.0 && realMm + 80.0 < virtMm) {
                    discard;
                  }
                }
              }
              gl_FragColor = uColor;
            }
        """.trimIndent()

        private val PLANE_VERT = """
            uniform mat4 uMvp;
            attribute vec3 aPos;
            void main() {
              gl_Position = uMvp * vec4(aPos, 1.0);
            }
        """.trimIndent()

        private val PLANE_FRAG = """
            precision mediump float;
            uniform vec4 uColor;
            void main() {
              gl_FragColor = uColor;
            }
        """.trimIndent()

        private fun genExternalTexture(): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val id = ids[0]
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, id)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
            return id
        }

        private fun genDepthTexture(): Int {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            val id = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            return id
        }

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
            val sh = GLES20.glCreateShader(type)
            GLES20.glShaderSource(sh, src)
            GLES20.glCompileShader(sh)
            return sh
        }

        private fun emptyFloatBuffer(n: Int): FloatBuffer =
            ByteBuffer.allocateDirect(n * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()

        private fun floatBufferOf(vararg values: Float): FloatBuffer {
            val buf = emptyFloatBuffer(values.size)
            buf.put(values)
            buf.position(0)
            return buf
        }

        /** Axis-aligned box centered on origin (metres). */
        private fun footprintBox(hx: Float, hy: Float, hz: Float, color: Int): MeshTri {
            val positions = floatArrayOf(
                // top
                -hx, hy, -hz, hx, hy, -hz, hx, hy, hz,
                -hx, hy, -hz, hx, hy, hz, -hx, hy, hz,
                // bottom
                -hx, 0f, -hz, hx, 0f, hz, hx, 0f, -hz,
                -hx, 0f, -hz, -hx, 0f, hz, hx, 0f, hz,
                // +z
                -hx, 0f, hz, hx, hy, hz, hx, 0f, hz,
                -hx, 0f, hz, -hx, hy, hz, hx, hy, hz,
                // -z
                -hx, 0f, -hz, hx, 0f, -hz, hx, hy, -hz,
                -hx, 0f, -hz, hx, hy, -hz, -hx, hy, -hz,
                // +x
                hx, 0f, -hz, hx, hy, hz, hx, 0f, hz,
                hx, 0f, -hz, hx, hy, -hz, hx, hy, hz,
                // -x
                -hx, 0f, -hz, -hx, 0f, hz, -hx, hy, hz,
                -hx, 0f, -hz, -hx, hy, hz, -hx, hy, -hz,
            )
            return MeshTri(positions, FloatArray(positions.size), color)
        }
    }
}

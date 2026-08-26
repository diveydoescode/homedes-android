package com.homedesign.android.presentation.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.util.AttributeSet
import android.view.Choreographer
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceView
import com.google.android.filament.Box
import com.google.android.filament.Camera
import com.google.android.filament.Colors
import com.google.android.filament.Engine
import com.google.android.filament.Entity
import com.google.android.filament.EntityManager
import com.google.android.filament.Filament
import com.google.android.filament.IndexBuffer
import com.google.android.filament.LightManager
import com.google.android.filament.Material
import com.google.android.filament.MaterialInstance
import com.google.android.filament.MathUtils
import com.google.android.filament.RenderableManager
import com.google.android.filament.Renderer
import com.google.android.filament.Scene
import com.google.android.filament.Skybox
import com.google.android.filament.SwapChain
import com.google.android.filament.Texture
import com.google.android.filament.TextureSampler
import com.google.android.filament.VertexBuffer
import com.google.android.filament.View
import com.google.android.filament.Viewport
import com.google.android.filament.android.DisplayHelper
import com.google.android.filament.android.FilamentHelper
import com.google.android.filament.android.TextureHelper
import com.google.android.filament.android.UiHelper
import com.google.android.filament.filamat.MaterialBuilder
import com.homedesign.android.domain.model.Home
import com.homedesign.android.domain.render3d.DoorLeaf3D
import com.homedesign.android.domain.render3d.ExtrudeOptions
import com.homedesign.android.domain.render3d.HomeExtrusion
import com.homedesign.android.domain.render3d.HomeScene3D
import com.homedesign.android.domain.render3d.MeshTri
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

enum class Plan3DCameraMode {
    Orbit,
    Walk,
}

/** Walk eye pose for the minimap (metres, yaw degrees). */
data class WalkCameraSnapshot(
    val eyeX: Float,
    val eyeZ: Float,
    val yawDeg: Float,
    val centerX: Float,
    val centerZ: Float,
    val radius: Float,
)

/**
 * Filament SurfaceView that extrudes the current Home into a lit 3D scene.
 * Orbit: one-finger orbit, two-finger distance.
 * Walk: first-person at eye height; drag to look; joystick / WASD to move.
 */
class Plan3DSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : SurfaceView(context, attrs) {

    companion object {
        private const val EYE_HEIGHT_M = 1.70f
        private const val WALK_SPEED_M_S = 2.4f
        /** Player collision radius in metres (~22 cm, matches iOS Lab). */
        private const val WALK_RADIUS_M = 0.22f
        private const val DEFAULT_TIME_HOUR = 10f

        @Volatile private var filamentReady = false

        fun ensureFilament(): Boolean {
            if (filamentReady) return true
            return try {
                Filament.init()
                filamentReady = true
                true
            } catch (t: Throwable) {
                android.util.Log.e("Plan3D", "Filament.init failed", t)
                false
            }
        }

        /** Light travel direction + intensity for a simple day arc (hour in 0..24). */
        fun sunParams(hour: Float): SunParams {
            val h = ((hour % 24f) + 24f) % 24f
            val fromNoon = h - 12f
            val elev = (1f - kotlin.math.abs(fromNoon) / 12f).coerceIn(0f, 1f)
            val elevRad = (0.08f + elev * 1.35f).coerceIn(0.08f, 1.45f)
            val azRad = fromNoon / 12f * (Math.PI.toFloat() / 2f)
            val cosE = cos(elevRad)
            val dx = sin(azRad) * cosE
            val dy = -sin(elevRad)
            val dz = cos(azRad) * cosE
            val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f)
            val night = elev < 0.12f
            val intensity = if (night) 18_000f else (40_000f + elev * 70_000f)
            val cct = if (night) 8_000f else (3_800f + elev * 2_200f)
            return SunParams(dx / len, dy / len, dz / len, intensity, cct)
        }
    }

    data class SunParams(
        val dx: Float,
        val dy: Float,
        val dz: Float,
        val intensity: Float,
        val cct: Float,
    )

    private val choreographer = Choreographer.getInstance()
    private val displayHelper = DisplayHelper(context)
    private val uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK)

    private var engine: Engine? = null
    private var renderer: Renderer? = null
    private var scene: Scene? = null
    private var view: View? = null
    private var camera: Camera? = null
    private var swapChain: SwapChain? = null
    private var material: Material? = null
    private var texturedMaterial: Material? = null
    private var skybox: Skybox? = null

    @Entity private var sunLight = 0
    @Entity private var cameraEntity = 0

    private val meshEntities = ArrayList<Int>()
    private val vertexBuffers = ArrayList<VertexBuffer>()
    private val indexBuffers = ArrayList<IndexBuffer>()
    private val materialInstances = ArrayList<MaterialInstance>()
    private val textureCache = HashMap<String, Texture>()
    private val textureSampler = TextureSampler(
        TextureSampler.MinFilter.LINEAR_MIPMAP_LINEAR,
        TextureSampler.MagFilter.LINEAR,
        TextureSampler.WrapMode.REPEAT,
    )

    private data class DoorLeafEntity(
        val entity: Int,
        val leaf: DoorLeaf3D,
        var openFrac: Float = if (leaf.isOpen) 1f else 0f,
    )
    private val doorLeafEntities = ArrayList<DoorLeafEntity>()

    @Volatile var yawDeg = 45f
    @Volatile var pitchDeg = 55f
    @Volatile var distance = 12f

    @Volatile var cameraMode: Plan3DCameraMode = Plan3DCameraMode.Orbit
        private set

    /** Walk eye position (metres). */
    @Volatile private var eyeX = 0f
    @Volatile private var eyeY = EYE_HEIGHT_M
    @Volatile private var eyeZ = 0f
    /** Walk look: yaw 0 = +Z, pitch 0 = horizon. */
    @Volatile private var lookYawDeg = 0f
    @Volatile private var lookPitchDeg = 0f
    private var pendingWalkPose: WalkPose? = null
    private var walkEyeInitialized = false

    /** Joystick / keyboard: forward (+1) and strafe right (+1), each in [-1, 1]. */
    @Volatile private var moveForward = 0f
    @Volatile private var moveStrafe = 0f

    @Volatile private var timeOfDayHour = DEFAULT_TIME_HOUR
    @Volatile private var outdoorEnabled = true
    @Volatile private var roofsEnabled = true
    @Volatile private var fenceEnabled = false
    private var groundAdded = false


    private var sceneData: HomeScene3D? = null
    private var pendingHome: Home? = null
    private var lastHome: Home? = null
    private var ready = false
    private var attached = false
    private var viewWidth = 1
    private var viewHeight = 1
    private var lastFrameNanos = 0L

    private var lastX = 0f
    private var lastY = 0f

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            renderFrame(frameTimeNanos)
        }
    }

    init {
        uiHelper.renderCallback = object : UiHelper.RendererCallback {
            override fun onNativeWindowChanged(surface: Surface) {
                val eng = engine ?: return
                val ren = renderer ?: return
                swapChain?.let { eng.destroySwapChain(it) }
                swapChain = eng.createSwapChain(surface)
                displayHelper.attach(ren, display)
            }

            override fun onDetachedFromSurface() {
                displayHelper.detach()
                val eng = engine ?: return
                swapChain?.let {
                    eng.destroySwapChain(it)
                    eng.flushAndWait()
                    swapChain = null
                }
            }

            override fun onResized(width: Int, height: Int) {
                viewWidth = width.coerceAtLeast(1)
                viewHeight = height.coerceAtLeast(1)
                applyProjection()
                val eng = engine ?: return
                FilamentHelper.synchronizePendingFrames(eng)
            }
        }
        uiHelper.attachTo(this)
    }

    fun setHome(home: Home) {
        // Skip rebuild when the same Home instance is already on screen (slider ticks, etc.).
        if (ready && lastHome === home && sceneData != null && pendingHome == null) {
            return
        }
        lastHome = home
        pendingHome = home
        if (ready) {
            rebuildScene(home)
            pendingHome = null
        }
    }

    fun setCameraMode(mode: Plan3DCameraMode) {
        if (cameraMode == mode) return
        cameraMode = mode
        if (mode == Plan3DCameraMode.Walk) {
            walkEyeInitialized = false
            applyWalkEye()
            moveForward = 0f
            moveStrafe = 0f
        }
        applyProjection()
    }

    /** Pegman pose in plan cm; applied when entering Walk (or immediately if already walking). */
    fun setWalkPose(pose: WalkPose?) {
        if (pendingWalkPose == pose) return
        pendingWalkPose = pose
        if (cameraMode == Plan3DCameraMode.Walk) {
            walkEyeInitialized = false
            applyWalkEye()
        }
    }

    private fun applyWalkEye() {
        val pose = pendingWalkPose
        if (pose != null) {
            eyeX = pose.eyeXMeters
            eyeY = EYE_HEIGHT_M
            eyeZ = pose.eyeZMeters
            lookYawDeg = pose.yawDeg
            lookPitchDeg = 0f
            walkEyeInitialized = true
            return
        }
        val s = sceneData ?: return
        eyeX = s.centerX
        eyeY = EYE_HEIGHT_M
        eyeZ = s.centerZ
        lookYawDeg = yawDeg
        lookPitchDeg = 0f
        walkEyeInitialized = true
    }

    fun setTimeOfDay(hour: Float) {
        timeOfDayHour = hour.coerceIn(0f, 24f)
        applySun()
        applySkyForTime()
    }

    fun setOutdoorEnabled(enabled: Boolean) {
        if (outdoorEnabled == enabled) return
        outdoorEnabled = enabled
        val home = lastHome ?: return
        if (ready) rebuildScene(home)
    }

    fun setRoofsEnabled(enabled: Boolean) {
        if (roofsEnabled == enabled) return
        roofsEnabled = enabled
        val home = lastHome ?: return
        if (ready) rebuildScene(home)
    }

    fun setFenceEnabled(enabled: Boolean) {
        if (fenceEnabled == enabled) return
        fenceEnabled = enabled
        val home = lastHome ?: return
        if (ready) rebuildScene(home)
    }

    fun setWalkMove(forward: Float, strafe: Float) {
        moveForward = forward.coerceIn(-1f, 1f)
        moveStrafe = strafe.coerceIn(-1f, 1f)
    }

    fun walkSnapshot(): WalkCameraSnapshot? {
        val s = sceneData ?: return null
        return WalkCameraSnapshot(
            eyeX = eyeX,
            eyeZ = eyeZ,
            yawDeg = lookYawDeg,
            centerX = s.centerX,
            centerZ = s.centerZ,
            radius = s.radius,
        )
    }

    fun onHostResume() {
        if (!attached) {
            attached = true
            if (ensureFilament()) {
                setupEngine()
                choreographer.postFrameCallback(frameCallback)
            }
        } else {
            choreographer.postFrameCallback(frameCallback)
        }
    }

    fun onHostPause() {
        choreographer.removeFrameCallback(frameCallback)
        moveForward = 0f
        moveStrafe = 0f
    }

    fun destroy() {
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
        destroyMeshes()
        val eng = engine ?: return
        if (sunLight != 0) {
            eng.destroyEntity(sunLight)
            EntityManager.get().destroy(sunLight)
            sunLight = 0
        }
        material?.let { eng.destroyMaterial(it) }
        material = null
        texturedMaterial?.let { eng.destroyMaterial(it) }
        texturedMaterial = null
        for ((_, tex) in textureCache) eng.destroyTexture(tex)
        textureCache.clear()
        skybox?.let { eng.destroySkybox(it) }
        skybox = null
        view?.let { eng.destroyView(it) }
        view = null
        scene?.let { eng.destroyScene(it) }
        scene = null
        renderer?.let { eng.destroyRenderer(it) }
        renderer = null
        if (cameraEntity != 0) {
            eng.destroyCameraComponent(cameraEntity)
            EntityManager.get().destroy(cameraEntity)
            cameraEntity = 0
        }
        camera = null
        swapChain = null
        eng.destroy()
        engine = null
        ready = false
        attached = false
    }

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
                when (cameraMode) {
                    Plan3DCameraMode.Orbit -> {
                        if (event.pointerCount >= 2) {
                            distance = (distance * (1f + dy * 0.005f)).coerceIn(2f, 80f)
                        } else {
                            yawDeg += dx * 0.4f
                            pitchDeg = (pitchDeg + dy * 0.3f).coerceIn(15f, 85f)
                        }
                    }
                    Plan3DCameraMode.Walk -> {
                        lookYawDeg += dx * 0.18f
                        lookPitchDeg = (lookPitchDeg - dy * 0.15f).coerceIn(-80f, 80f)
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> Unit
        }
        return true
    }

    private fun setupEngine() {
        if (engine != null) return
        val eng = Engine.create()
        engine = eng
        renderer = eng.createRenderer()
        scene = eng.createScene()
        view = eng.createView().also {
            it.scene = scene
            it.isPostProcessingEnabled = true
        }
        cameraEntity = eng.entityManager.create()
        camera = eng.createCamera(cameraEntity).also {
            it.setExposure(16.0f, 1.0f / 125.0f, 100.0f)
            view?.camera = it
        }

        applySkyForTime()
        material = buildLitMaterial(eng)
        texturedMaterial = buildTexturedLitMaterial(eng)

        sunLight = EntityManager.get().create()
        applySun(create = true)
        scene?.addEntity(sunLight)

        ready = true
        pendingHome?.let {
            rebuildScene(it)
            pendingHome = null
        }
        applyProjection()
    }

    private fun applyProjection() {
        val cam = camera ?: return
        val v = view ?: return
        val aspect = viewWidth.toDouble() / viewHeight.toDouble()
        val fov = if (cameraMode == Plan3DCameraMode.Walk) 70.0 else 45.0
        cam.setProjection(fov, aspect, 0.05, 500.0, Camera.Fov.VERTICAL)
        v.viewport = Viewport(0, 0, viewWidth, viewHeight)
    }

    private fun applySun(create: Boolean = false) {
        val eng = engine ?: return
        if (sunLight == 0) return
        val p = sunParams(timeOfDayHour)
        val (r, g, b) = Colors.cct(p.cct)
        if (create) {
            LightManager.Builder(LightManager.Type.DIRECTIONAL)
                .color(r, g, b)
                .intensity(p.intensity)
                .direction(p.dx, p.dy, p.dz)
                .castShadows(false)
                .build(eng, sunLight)
        } else {
            val lm = eng.lightManager
            val inst = lm.getInstance(sunLight)
            if (inst != 0) {
                lm.setColor(inst, r, g, b)
                lm.setIntensity(inst, p.intensity)
                lm.setDirection(inst, p.dx, p.dy, p.dz)
            }
        }
    }

    private fun applySkyForTime() {
        val eng = engine ?: return
        val p = sunParams(timeOfDayHour)
        val elev = (-p.dy).coerceIn(0f, 1f)
        val night = elev < 0.15f
        skybox?.let { eng.destroySkybox(it) }
        skybox = if (night) {
            Skybox.Builder().color(0.08f, 0.10f, 0.16f, 1.0f).build(eng)
        } else {
            val warm = (1f - elev) * 0.15f
            Skybox.Builder()
                .color(0.78f + warm, 0.88f, 0.96f - warm * 0.3f, 1.0f)
                .build(eng)
        }
        scene?.skybox = skybox
    }

    private fun buildLitMaterial(eng: Engine): Material {
        MaterialBuilder.init()
        try {
            val matPackage = MaterialBuilder()
                .platform(MaterialBuilder.Platform.MOBILE)
                .name("HdOpaque")
                .shading(MaterialBuilder.Shading.LIT)
                .uniformParameter(MaterialBuilder.UniformType.FLOAT3, "baseColor")
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor.rgb = materialParams.baseColor;
                        material.roughness = materialParams.roughness;
                        material.metallic = 0.0;
                    }
                    """.trimIndent(),
                )
                .optimization(MaterialBuilder.Optimization.NONE)
                .build(eng)
            check(matPackage.isValid) { "MaterialBuilder produced invalid package" }
            val buffer = matPackage.buffer
            return Material.Builder().payload(buffer, buffer.remaining()).build(eng)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    /** Lit material sampling `albedo` via UV0 (floors with preset JPGs). */
    private fun buildTexturedLitMaterial(eng: Engine): Material {
        MaterialBuilder.init()
        try {
            val matPackage = MaterialBuilder()
                .platform(MaterialBuilder.Platform.MOBILE)
                .name("HdTextured")
                .shading(MaterialBuilder.Shading.LIT)
                .require(MaterialBuilder.VertexAttribute.UV0)
                .samplerParameter(
                    MaterialBuilder.SamplerType.SAMPLER_2D,
                    MaterialBuilder.SamplerFormat.FLOAT,
                    MaterialBuilder.ParameterPrecision.DEFAULT,
                    "albedo",
                )
                .uniformParameter(MaterialBuilder.UniformType.FLOAT, "roughness")
                .material(
                    """
                    void material(inout MaterialInputs material) {
                        prepareMaterial(material);
                        material.baseColor = texture(materialParams_albedo, getUV0());
                        material.roughness = materialParams.roughness;
                        material.metallic = 0.0;
                    }
                    """.trimIndent(),
                )
                .optimization(MaterialBuilder.Optimization.NONE)
                .build(eng)
            check(matPackage.isValid) { "Textured MaterialBuilder package invalid" }
            val buffer = matPackage.buffer
            return Material.Builder().payload(buffer, buffer.remaining()).build(eng)
        } finally {
            MaterialBuilder.shutdown()
        }
    }

    private fun loadAssetTexture(eng: Engine, assetPath: String): Texture? {
        textureCache[assetPath]?.let { return it }
        return try {
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = when {
                assetPath.startsWith("file:") ->
                    BitmapFactory.decodeFile(assetPath.removePrefix("file:"), opts)
                assetPath.startsWith("/") || assetPath.contains(":\\") ||
                    (assetPath.length > 2 && assetPath[1] == ':') ->
                    BitmapFactory.decodeFile(assetPath, opts)
                else ->
                    context.assets.open(assetPath).use { stream ->
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
            } ?: return null
            val bitmap = if (decoded.config == Bitmap.Config.ARGB_8888) {
                decoded
            } else {
                decoded.copy(Bitmap.Config.ARGB_8888, false).also { decoded.recycle() }
            }
            val levels = 1 + (31 - Integer.numberOfLeadingZeros(max(bitmap.width, bitmap.height)))
                .coerceAtLeast(0)
            val tex = Texture.Builder()
                .width(bitmap.width)
                .height(bitmap.height)
                .levels(levels.coerceIn(1, 8))
                .sampler(Texture.Sampler.SAMPLER_2D)
                .format(Texture.InternalFormat.SRGB8_A8)
                .build(eng)
            TextureHelper.setBitmap(eng, tex, 0, bitmap)
            if (levels > 1) tex.generateMipmaps(eng)
            if (bitmap !== decoded) bitmap.recycle() else decoded.recycle()
            textureCache[assetPath] = tex
            tex
        } catch (t: Throwable) {
            android.util.Log.w("Plan3D", "Failed to load texture $assetPath", t)
            null
        }
    }

    private fun rebuildScene(home: Home) {
        val eng = engine ?: return
        val scn = scene ?: return
        val mat = material ?: return
        destroyMeshes()
        groundAdded = false
        val built = HomeExtrusion.build(
            home,
            options = ExtrudeOptions(roofs = roofsEnabled, fence = fenceEnabled),
        )
        sceneData = built
        distance = (built.radius * 2.6f).coerceIn(4f, 60f)

        for (mesh in built.meshes) {
            if (mesh.positions.isEmpty()) continue
            addMeshEntity(eng, scn, mat, mesh)
        }

        if (outdoorEnabled) {
            val disc = HomeExtrusion.groundDisc(
                built.centerX,
                built.centerZ,
                (built.radius * 3.2f).coerceAtLeast(8f),
            )
            addMeshEntity(eng, scn, mat, disc, cull = false)
            groundAdded = true
        }

        for (leaf in built.doorLeaves) {
            addDoorLeafEntity(eng, scn, mat, leaf)
        }
        applyDoorTransforms()

        if (cameraMode == Plan3DCameraMode.Walk && !walkEyeInitialized) {
            applyWalkEye()
        }
    }

    private fun addDoorLeafEntity(
        eng: Engine,
        scn: Scene,
        mat: Material,
        leaf: DoorLeaf3D,
    ) {
        // Wide AABB so culling survives hinge rotation away from local origin.
        val entity = addMeshEntity(
            eng,
            scn,
            mat,
            leaf.localMesh,
            cull = false,
            aabbPad = 2.5f,
        )
        if (entity == 0) return
        val tcm = eng.transformManager
        if (!tcm.hasComponent(entity)) {
            tcm.create(entity)
        }
        doorLeafEntities.add(DoorLeafEntity(entity, leaf))
    }

    private fun applyDoorTransforms() {
        val eng = engine ?: return
        val tcm = eng.transformManager
        val m = FloatArray(16)
        for (item in doorLeafEntities) {
            val leaf = item.leaf
            val frac = item.openFrac.coerceIn(0f, 1f)
            val yaw = leaf.closedYawRad + leaf.swingRad * frac
            val c = cos(yaw.toDouble()).toFloat()
            val s = sin(yaw.toDouble()).toFloat()
            // Column-major: same basis as orientedBox (x' = c*lx - s*lz, z' = s*lx + c*lz).
            m[0] = c; m[1] = 0f; m[2] = s; m[3] = 0f
            m[4] = 0f; m[5] = 1f; m[6] = 0f; m[7] = 0f
            m[8] = -s; m[9] = 0f; m[10] = c; m[11] = 0f
            m[12] = leaf.hingeX; m[13] = leaf.hingeY; m[14] = leaf.hingeZ; m[15] = 1f
            val ti = tcm.getInstance(item.entity)
            if (ti != 0) tcm.setTransform(ti, m)
        }
    }

    private fun addMeshEntity(
        eng: Engine,
        scn: Scene,
        mat: Material,
        mesh: MeshTri,
        cull: Boolean = true,
        aabbPad: Float = 0f,
    ): Int {
        val vertexCount = mesh.positions.size / 3
        if (vertexCount < 3) return 0

        val texPath = mesh.textureAssetPath
        val uvs = mesh.uvs
        val useTexture = texPath != null &&
            uvs != null &&
            uvs.size >= vertexCount * 2 &&
            texturedMaterial != null
        val loadedTex = if (useTexture) loadAssetTexture(eng, texPath!!) else null
        val useUv = loadedTex != null

        val floatSize = 4
        val floatsPerVertex = if (useUv) 9 else 7
        val stride = floatsPerVertex * floatSize
        val vertexData = ByteBuffer.allocateDirect(vertexCount * stride)
            .order(ByteOrder.nativeOrder())
        val tangents = FloatArray(4)
        for (i in 0 until vertexCount) {
            val px = mesh.positions[i * 3]
            val py = mesh.positions[i * 3 + 1]
            val pz = mesh.positions[i * 3 + 2]
            val nx = mesh.normals.getOrElse(i * 3) { 0f }
            val ny = mesh.normals.getOrElse(i * 3 + 1) { 1f }
            val nz = mesh.normals.getOrElse(i * 3 + 2) { 0f }
            packTangentFromNormal(nx, ny, nz, tangents)
            vertexData.putFloat(px).putFloat(py).putFloat(pz)
            vertexData.putFloat(tangents[0]).putFloat(tangents[1])
                .putFloat(tangents[2]).putFloat(tangents[3])
            if (useUv) {
                vertexData.putFloat(uvs!![i * 2])
                vertexData.putFloat(uvs[i * 2 + 1])
            }
        }
        vertexData.flip()

        val vbBuilder = VertexBuffer.Builder()
            .bufferCount(1)
            .vertexCount(vertexCount)
            .attribute(
                VertexBuffer.VertexAttribute.POSITION,
                0,
                VertexBuffer.AttributeType.FLOAT3,
                0,
                stride,
            )
            .attribute(
                VertexBuffer.VertexAttribute.TANGENTS,
                0,
                VertexBuffer.AttributeType.FLOAT4,
                3 * floatSize,
                stride,
            )
        if (useUv) {
            vbBuilder.attribute(
                VertexBuffer.VertexAttribute.UV0,
                0,
                VertexBuffer.AttributeType.FLOAT2,
                7 * floatSize,
                stride,
            )
        }
        val vb = vbBuilder.build(eng)
        vb.setBufferAt(eng, 0, vertexData)
        vertexBuffers.add(vb)

        val indexData = ByteBuffer.allocateDirect(vertexCount * 4)
            .order(ByteOrder.nativeOrder())
        for (i in 0 until vertexCount) {
            indexData.putInt(i)
        }
        indexData.flip()
        val ib = IndexBuffer.Builder()
            .indexCount(vertexCount)
            .bufferType(IndexBuffer.Builder.IndexType.UINT)
            .build(eng)
        ib.setBuffer(eng, indexData)
        indexBuffers.add(ib)

        val instance = if (useUv && loadedTex != null) {
            val texMat = texturedMaterial ?: mat
            texMat.createInstance().also { mi ->
                mi.setParameter("albedo", loadedTex, textureSampler)
                mi.setParameter("roughness", mesh.roughness.coerceIn(0.04f, 1f))
            }
        } else {
            mat.createInstance().also { mi ->
                val r = Color.red(mesh.colorArgb) / 255f
                val g = Color.green(mesh.colorArgb) / 255f
                val b = Color.blue(mesh.colorArgb) / 255f
                mi.setParameter("baseColor", Colors.RgbType.SRGB, r, g, b)
                mi.setParameter("roughness", mesh.roughness.coerceIn(0.04f, 1f))
            }
        }
        materialInstances.add(instance)

        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY
        var i = 0
        while (i < mesh.positions.size) {
            val x = mesh.positions[i]
            val y = mesh.positions[i + 1]
            val z = mesh.positions[i + 2]
            if (x < minX) minX = x
            if (y < minY) minY = y
            if (z < minZ) minZ = z
            if (x > maxX) maxX = x
            if (y > maxY) maxY = y
            if (z > maxZ) maxZ = z
            i += 3
        }
        val cx = (minX + maxX) * 0.5f
        val cy = (minY + maxY) * 0.5f
        val cz = (minZ + maxZ) * 0.5f
        val hx = max((maxX - minX) * 0.5f, 0.01f) + aabbPad
        val hy = max((maxY - minY) * 0.5f, 0.01f) + aabbPad * 0.25f
        val hz = max((maxZ - minZ) * 0.5f, 0.01f) + aabbPad

        val entity = EntityManager.get().create()
        RenderableManager.Builder(1)
            .boundingBox(Box(cx, cy, cz, hx, hy, hz))
            .geometry(0, RenderableManager.PrimitiveType.TRIANGLES, vb, ib, 0, vertexCount)
            .material(0, instance)
            .culling(cull)
            .castShadows(false)
            .receiveShadows(true)
            .build(eng, entity)
        scn.addEntity(entity)
        meshEntities.add(entity)
        return entity
    }

    private fun destroyMeshes() {
        val eng = engine ?: return
        val scn = scene
        val em = EntityManager.get()
        doorLeafEntities.clear()
        for (e in meshEntities) {
            scn?.remove(e)
            eng.destroyEntity(e)
            em.destroy(e)
        }
        meshEntities.clear()
        for (vb in vertexBuffers) eng.destroyVertexBuffer(vb)
        vertexBuffers.clear()
        for (ib in indexBuffers) eng.destroyIndexBuffer(ib)
        indexBuffers.clear()
        for (mi in materialInstances) eng.destroyMaterialInstance(mi)
        materialInstances.clear()
        groundAdded = false
    }

    private fun integrateWalk(dt: Float) {
        if (cameraMode != Plan3DCameraMode.Walk) return
        val yaw = Math.toRadians(lookYawDeg.toDouble())
        val forwardX = sin(yaw).toFloat()
        val forwardZ = cos(yaw).toFloat()
        val rightX = cos(yaw).toFloat()
        val rightZ = -sin(yaw).toFloat()
        val speed = WALK_SPEED_M_S * dt
        val dx = (forwardX * moveForward + rightX * moveStrafe) * speed
        val dz = (forwardZ * moveForward + rightZ * moveStrafe) * speed
        if (dx == 0f && dz == 0f) {
            eyeY = EYE_HEIGHT_M
            return
        }
        val fromX = eyeX
        val fromZ = eyeZ
        val nx = fromX + dx
        val nz = fromZ + dz
        // Slide: try full move, then X-only, then Z-only (iOS Lab parity).
        when {
            !walkCollides(nx, nz) -> {
                eyeX = nx
                eyeZ = nz
            }
            !walkCollides(nx, fromZ) -> eyeX = nx
            !walkCollides(fromX, nz) -> eyeZ = nz
        }
        eyeY = EYE_HEIGHT_M
    }

    /** Circle vs wall segment distance in plan XZ; door discs cancel collision. */
    private fun walkCollides(x: Float, z: Float): Boolean {
        val s = sceneData ?: return false
        for (d in s.walkDoors) {
            val ddx = x - d.cx
            val ddz = z - d.cz
            if (ddx * ddx + ddz * ddz < d.radius * d.radius) return false
        }
        for (w in s.walkWalls) {
            val abx = w.bx - w.ax
            val abz = w.bz - w.az
            val len2 = abx * abx + abz * abz
            if (len2 < 1e-8f) continue
            var t = ((x - w.ax) * abx + (z - w.az) * abz) / len2
            t = t.coerceIn(0f, 1f)
            val px = w.ax + abx * t
            val pz = w.az + abz * t
            val dx = x - px
            val dz = z - pz
            val lim = w.halfThick + WALK_RADIUS_M
            if (dx * dx + dz * dz < lim * lim) return true
        }
        return false
    }

    private fun renderFrame(frameTimeNanos: Long) {
        if (!uiHelper.isReadyToRender) return
        val eng = engine ?: return
        val ren = renderer ?: return
        val v = view ?: return
        val cam = camera ?: return
        val chain = swapChain ?: return
        val s = sceneData

        val dt = if (lastFrameNanos == 0L) {
            1f / 60f
        } else {
            ((frameTimeNanos - lastFrameNanos).coerceIn(0L, 100_000_000L) / 1_000_000_000.0).toFloat()
        }
        lastFrameNanos = frameTimeNanos
        integrateWalk(dt)

        if (doorLeafEntities.isNotEmpty()) {
            // Ease each leaf toward open/closed from HomeDoorOrWindow.isOpen (sheet toggle).
            for (item in doorLeafEntities) {
                val target = if (item.leaf.isOpen) 1f else 0f
                item.openFrac += (target - item.openFrac) * (dt * 4f).coerceIn(0f, 1f)
            }
            applyDoorTransforms()
        }

        if (s != null) {
            when (cameraMode) {
                Plan3DCameraMode.Orbit -> {
                    val yaw = Math.toRadians(yawDeg.toDouble())
                    val pitch = Math.toRadians(pitchDeg.toDouble())
                    val eX = s.centerX + (distance * cos(pitch) * sin(yaw)).toFloat()
                    val eY = (distance * sin(pitch)).toFloat()
                    val eZ = s.centerZ + (distance * cos(pitch) * cos(yaw)).toFloat()
                    cam.lookAt(
                        eX.toDouble(),
                        eY.toDouble(),
                        eZ.toDouble(),
                        s.centerX.toDouble(),
                        0.6,
                        s.centerZ.toDouble(),
                        0.0,
                        1.0,
                        0.0,
                    )
                }
                Plan3DCameraMode.Walk -> {
                    val yaw = Math.toRadians(lookYawDeg.toDouble())
                    val pitch = Math.toRadians(lookPitchDeg.toDouble())
                    val cosPitch = cos(pitch)
                    val targetX = eyeX + (sin(yaw) * cosPitch).toFloat()
                    val targetY = eyeY + sin(pitch).toFloat()
                    val targetZ = eyeZ + (cos(yaw) * cosPitch).toFloat()
                    cam.lookAt(
                        eyeX.toDouble(),
                        eyeY.toDouble(),
                        eyeZ.toDouble(),
                        targetX.toDouble(),
                        targetY.toDouble(),
                        targetZ.toDouble(),
                        0.0,
                        1.0,
                        0.0,
                    )
                }
            }
        }

        if (ren.beginFrame(chain, frameTimeNanos)) {
            ren.render(v)
            ren.endFrame()
        }
    }

    private fun packTangentFromNormal(nx: Float, ny: Float, nz: Float, out: FloatArray) {
        val nlen = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-6f)
        val nnx = nx / nlen
        val nny = ny / nlen
        val nnz = nz / nlen
        val ax = if (kotlin.math.abs(nny) < 0.9f) 0f else 1f
        val ay = if (kotlin.math.abs(nny) < 0.9f) 1f else 0f
        val az = 0f
        var bx = nny * az - nnz * ay
        var by = nnz * ax - nnx * az
        var bz = nnx * ay - nny * ax
        val blen = sqrt(bx * bx + by * by + bz * bz).coerceAtLeast(1e-6f)
        bx /= blen; by /= blen; bz /= blen
        val tx = by * nnz - bz * nny
        val ty = bz * nnx - bx * nnz
        val tz = bx * nny - by * nnx
        MathUtils.packTangentFrame(tx, ty, tz, bx, by, bz, nnx, nny, nnz, out)
    }
}

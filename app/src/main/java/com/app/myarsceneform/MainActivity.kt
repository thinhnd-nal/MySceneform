package com.app.myarsceneform

import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import kotlin.math.abs

class MainActivity : AppCompatActivity() {
    private lateinit var arFragment: ArFragment
    private var arSession: Session? = null
    private var tigerRenderable: ModelRenderable? = null
    private var transformableNode: TransformableNode? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!checkIsSupportedDeviceOrFinish()) {
            return
        }

        ModelRenderable.builder()
            .setSource(this, Uri.parse("https://storage.googleapis.com/ar-answers-in-search-models/static/Tiger/model.glb"))
            .setIsFilamentGltf(true)
            .build()
            .thenAccept { modelRenderable ->
                tigerRenderable = modelRenderable
                logE("Renderable is ready")
            }
            .exceptionally { throwable ->
                logE("Unable to load renderable by " + throwable.message)
                null
            }

        arFragment = supportFragmentManager.findFragmentById(R.id.arFragment) as ArFragment
        arFragment.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            placeObject(hitResult, motionEvent)
        }
        arFragment.arSceneView.scene?.addOnUpdateListener {
            val frame = arFragment.arSceneView.arFrame ?: run {
                logE("OnUpdate without frame")
                return@addOnUpdateListener
            }
            val updatedPlanes = frame.getUpdatedTrackables(Plane::class.java)
            if (updatedPlanes.isNotEmpty()) {
                val vertical = updatedPlanes.filter { it.type == Plane.Type.VERTICAL }
                var msg = "Total plane = " + updatedPlanes.size + " with vertical = " + vertical.size
                if (vertical.size > 1) {
                    msg += " => Measure width ="
                    for (i in 0 until vertical.size - 1) {
                        msg += " " + abs(vertical[i].centerPose.distanceTo(vertical[i + 1].centerPose))
                    }
                }
            }
            val updatedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)
            if (updatedImages.isNotEmpty()) {
                logE("OnUpdate: " + updatedImages.joinToString(separator = " + ") { it.name })
            }
        }
    }

    override fun onPause() {
        super.onPause()
        arSession?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (arSession == null) {
            arSession = Session(applicationContext)
            val config = Config(arSession)
            try {
                val customImageDatabase = config.augmentedImageDatabase.takeIf {
                    it.numImages > 0
                } ?: AugmentedImageDatabase(arSession)
                imageModelList.forEach {
                    customImageDatabase.addImage(
                        it.name,
                        assets.open(it.url).use(BitmapFactory::decodeStream),
                        it.widthInMeters,
                    )
                }
                config.apply {
                    augmentedImageDatabase = customImageDatabase
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                    depthMode = Config.DepthMode.AUTOMATIC
                }
            } catch (exception: Exception) {
                logE(exception.message)
            }
            arSession?.configure(config)
            arFragment.arSceneView.setupSession(arSession)
        }
        arSession?.resume()
    }

    private fun placeObject(hitResult: HitResult, tap: MotionEvent?) {
//        val frame: Frame = arFragment.getArSceneView().getArFrame() ?: return
        // Calculate the ray from the user's device into the world.
//        val tap: MotionEvent = arFragment.getArSceneView().getArFrame().getAndroidHardwareBuffer().get()
        if (tap == null) return
        if (tigerRenderable == null) return
        if (transformableNode != null) return
        logE("Scene on touch")
//            val ray: Ray = arFragment.getArSceneView().getScene().getCamera().screenPointToRay(
//                tap.x,
//                tap.y,
//                displayMetrics.widthPixels,
//                displayMetrics.heightPixels
//            )
        // Get the point in the world where the ray intersected a plane.
//            val frame = arFragment.arSceneView.arFrame ?: return
//            val hitResultList = frame.hitTestInstantPlacement(tap.x, tap.y, 2f)
//            logE("Hit result list = " + hitResultList.size)
//            val hitResult = hitResultList.firstOrNull { hit ->
//                    when (val trackable = hit.trackable) {
//                        is Plane -> trackable.isPoseInPolygon(hit.hitPose) && calculateDistanceToPlane(hit.hitPose, frame.camera.pose) > 0
//                        is Point -> trackable.orientationMode == Point.OrientationMode.ESTIMATED_SURFACE_NORMAL
//                        is InstantPlacementPoint -> true
//                        // DepthPoints are only returned if Config.DepthMode is set to AUTOMATIC.
//                        is DepthPoint -> true
//                        else -> false
//                    }
//                } ?: return
        val anchorNode = AnchorNode(hitResult.createAnchor()).apply {
            localScale = Vector3(0.1f, 0.1f, 0.1f)
            setParent(arFragment.arSceneView.scene)
        }
        transformableNode = TransformableNode(arFragment.transformationSystem).apply {
            setParent(anchorNode)
            renderable = tigerRenderable
            select()
        }

//        val filamentAsset: FilamentAsset = model.getRenderableInstance().getFilamentAsset()
//        if (filamentAsset.animator.animationCount > 0) {
//            animators.add(com.google.ar.sceneform.samples.gltf.GltfActivity.AnimationInstance(filamentAsset.animator, 0, System.nanoTime()))
//        }
    }

    private fun checkIsSupportedDeviceOrFinish(): Boolean {
        val openGlVersionString = (getSystemService(ACTIVITY_SERVICE) as ActivityManager)
            .deviceConfigurationInfo
            .glEsVersion
        if (openGlVersionString.toDouble() < 3.0) {
            logE("Sceneform requires OpenGL ES 3.0 later")
            Toast.makeText(this, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG).show()
            finish()
            return false
        }
        return true
    }

    private fun logE(msg: String?) {
        Log.e("MyARScecefomrMainActivity", msg.toString())
    }

    private fun Pose.distanceTo(other: Pose): Float {
        val normal = FloatArray(3)
        // Get transformed Y axis of plane's coordinate system.
        getTransformedAxis(1, 1.0f, normal, 0)
        // Compute dot product of plane's normal with vector from camera to plane center.
        return (other.tx() - tx()) * normal[0] + (other.ty() - ty()) * normal[1] + (other.tz() - tz()) * normal[2]
    }

    private val imageModelList = listOf(
        AugmentedImageModel(
            name = "spoons",
            url = "augmentedimages/spoons.png",
            widthInMeters = 0.1f,
        ),
        AugmentedImageModel(
            name = "qrcode",
            url = "augmentedimages/qrcode.png",
            widthInMeters = 0.12f,
        ),
        AugmentedImageModel(
            name = "wallet",
            url = "augmentedimages/wallet.jpg",
            widthInMeters = 0.06f,
        ),
    )

    private data class AugmentedImageModel(
        val name: String,
        val widthInMeters: Float,
        val url: String,
    )
}
package com.app.myarsceneform

import android.app.ActivityManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
    private var tigerRenderable: ModelRenderable? = null

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
        arFragment.instructionsController.isEnabled = false
        arFragment.setOnTapArPlaneListener { hitResult, _, _ ->
            arFragment.arSceneView.scene.addChild(AnchorNode(hitResult.createAnchor()).apply {
                // Create the transformable model and add it to the anchor
                localScale = Vector3(0.5f, 0.5f, 0.5f)
                addChild(TransformableNode(arFragment.transformationSystem).apply {
                    renderable = tigerRenderable
                    renderableInstance.animate(true).start()
                    select()
                })
            })
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
        arFragment.setOnSessionConfigurationListener { session, config ->
            try {
                val customImageDatabase = config.augmentedImageDatabase.takeIf {
                    it.numImages > 0
                } ?: AugmentedImageDatabase(session)
                imageModelList.forEach {
                    customImageDatabase.addImage(
                        it.name,
                        assets.open(it.url).use(BitmapFactory::decodeStream),
                        it.widthInMeters,
                    )
                }
                config.apply {
                    augmentedImageDatabase = customImageDatabase
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        depthMode = Config.DepthMode.AUTOMATIC
                    }
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                }
            } catch (exception: Exception) {
                logE(exception.message)
            }
        }
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
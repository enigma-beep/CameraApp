//package com.example.fir
package com.example.cameraapp


import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.util.Pair
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.ml.common.FirebaseMLException
import com.google.firebase.ml.custom.*
import kotlinx.android.synthetic.main.activity_display.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.experimental.and
import kotlin.math.max
import kotlin.math.min


class display : AppCompatActivity() {

    /** Data structure holding pairs of <label, confidence> for each inference result */
    data class LabelConfidence(val label: String, val confidence: Float)

    /** Current image being displayed in our app's screen */
    private var selectedImage: Bitmap? = null

    /** List of JPG files in our assets folder */
    private val imagePaths by lazy {
        resources.assets.list("")!!.filter { it.endsWith(".jpg") }
    }

    /** Labels corresponding to the output of the vision model. */
    private val labelList by lazy {
        BufferedReader(InputStreamReader(resources.assets.open(LABEL_PATH))).lineSequence().toList()
    }



    /** Preallocated buffers for storing image data. */
    private val imageBuffer = IntArray(DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y)

    // Gets the targeted width / height.
    private val targetedWidthHeight: Pair<Int, Int>
        get() {
            val targetWidth: Int
            val targetHeight: Int
            val maxWidthForPortraitMode = image_view.width
            val maxHeightForPortraitMode = image_view.height
            targetWidth = maxWidthForPortraitMode
            targetHeight = maxHeightForPortraitMode
            return Pair(targetWidth, targetHeight)
        }


    val inputDims = arrayOf(DIM_BATCH_SIZE, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, DIM_PIXEL_SIZE)
    //    val outputDims = arrayOf(DIM_BATCH_SIZE, labelList.size)
    private val modelInputOutputOptions = FirebaseModelInputOutputOptions.Builder()
        .setInputFormat(0, FirebaseModelDataType.BYTE, intArrayOf(1, 224, 224, 3))
        .setOutputFormat(0, FirebaseModelDataType.BYTE, intArrayOf(1,1001) )
        .build()

    /** Firebase model interpreter used for the local model from assets */
    private lateinit var modelInterpreter: FirebaseModelInterpreter

    /** Initialize a local model interpreter from assets file */
    private fun createLocalModelInterpreter(): FirebaseModelInterpreter {
        // Select the first available .tflite file as our local model
        val localModelName = resources.assets.list("")?.firstOrNull { it.endsWith(".tflite") }
            ?: throw(RuntimeException("Don't forget to add the tflite file to your assets folder"))
        Log.d(TAG, "Local model found: $localModelName")

        // Create an interpreter with the local model asset
        val localModel =
            FirebaseCustomLocalModel.Builder().setAssetFilePath(localModelName).build()
        val localInterpreter = FirebaseModelInterpreter.getInstance(
            FirebaseModelInterpreterOptions.Builder(localModel).build())!!
        Log.d(TAG, "Local model interpreter initialized")

        // Return the interpreter
        return localInterpreter
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display)

        val storagePath = File(Environment.getExternalStorageDirectory(), "MyCameraApp")

        val myFile = File(storagePath, "IMG_1.jpg")
        val bitmap = BitmapFactory.decodeFile(myFile.absolutePath)


        fun Bitmap.rotate(degrees: Float): Bitmap {
            val matrix = Matrix().apply { postRotate(degrees) }
            return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
        }

        selectedImage = bitmap.rotate(90F) // value must be float

        image_view.setImageBitmap(selectedImage)
       // val bytearr=intent.getByteArrayExtra("bytearr");
       // selectedImage = BitmapFactory.decodeByteArray(bytearr, 0, bytearr.size)

//        if (selectedImage != null) {
//            // Get the dimensions of the View
//            val targetedSize = targetedWidthHeight
//
//            val targetWidth = targetedSize.first
//            val maxHeight = targetedSize.second
//
//            // Determine how much to scale down the image
//            val scaleFactor = max(
//                selectedImage!!.width.toFloat() / targetWidth.toFloat(),
//                selectedImage!!.height.toFloat() / maxHeight.toFloat())
//
//            val resizedBitmap = Bitmap.createScaledBitmap(
//                selectedImage!!,
//                (selectedImage!!.width / scaleFactor).toInt(),
//                (selectedImage!!.height / scaleFactor).toInt(),
//                true)
//
//            image_view.setImageBitmap(resizedBitmap)
//            selectedImage = resizedBitmap
//        }
        modelInterpreter = createLocalModelInterpreter()
        button_run.setOnClickListener {
            runModelInference()
        }
    }
    private fun runModelInference() = selectedImage?.let { image ->

        // Create input data.
        val imgData = convertBitmapToByteBuffer(image)

        try {
            // Create model inputs from our image data.
            val modelInputs = FirebaseModelInputs.Builder().add(imgData).build()

            // Perform inference using our model interpreter.
            modelInterpreter.run(modelInputs, modelInputOutputOptions).continueWith {
                val inferenceOutput = it.result?.getOutput<Array<ByteArray>>(0)!!

                // Display labels on the screen using an overlay
                val topLabels = getTopLabels(inferenceOutput)
                graphic_overlay.clear()
                graphic_overlay.add(LabelGraphic(graphic_overlay, topLabels))
                topLabels
            }


        } catch (exc: FirebaseMLException) {
            val msg = "Error running model inference"
            Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            Log.e(TAG, msg, exc)
        }
    }

    /** Gets the top labels in the results. */
    @Synchronized
    private fun getTopLabels(inferenceOutput: Array<ByteArray>): List<String> {
        // Since we ran inference on a single image, inference output will have a single row.
        val imageInference = inferenceOutput.first()

        // The columns of the image inference correspond to the confidence for each label.
        return labelList.mapIndexed { idx, label ->
            LabelConfidence(label, (imageInference[idx] and 0xFF.toByte()) / 255.0f)

            // Sort the results in decreasing order of confidence and return only top 3.
        }.sortedBy { it.confidence }.reversed().map { "${it.label}:${it.confidence}" }
            .subList(0, min(labelList.size, RESULTS_TO_SHOW))
    }

    /** Writes Image data into a `ByteBuffer`. */
    @Synchronized
    private fun convertBitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val imgData = ByteBuffer.allocateDirect(
            DIM_BATCH_SIZE * DIM_IMG_SIZE_X * DIM_IMG_SIZE_Y * DIM_PIXEL_SIZE).apply {
            order(ByteOrder.nativeOrder())
            rewind()
        }
        val scaledBitmap =
            Bitmap.createScaledBitmap(bitmap, DIM_IMG_SIZE_X, DIM_IMG_SIZE_Y, true)
        scaledBitmap.getPixels(
            imageBuffer, 0, scaledBitmap.width, 0, 0, scaledBitmap.width, scaledBitmap.height)
        // Convert the image to int points.
        var pixel = 0
        for (i in 0 until DIM_IMG_SIZE_X) {
            for (j in 0 until DIM_IMG_SIZE_Y) {
                val `val` = imageBuffer[pixel++]
                imgData.put((`val` shr 16 and 0xFF).toByte())
                imgData.put((`val` shr 8 and 0xFF).toByte())
                imgData.put((`val` and 0xFF).toByte())
            }
        }
        return imgData
    }


    companion object {
        private val TAG = MainActivity::class.java.simpleName

        /** Name of the label file stored in Assets. */
        private const val LABEL_PATH = "labels.txt"


        /** Number of results to show in the UI. */
        private const val RESULTS_TO_SHOW = 3

        /** Dimensions of inputs. */
        private const val DIM_BATCH_SIZE = 1
        private const val DIM_PIXEL_SIZE = 3
        private const val DIM_IMG_SIZE_X = 224
        private const val DIM_IMG_SIZE_Y = 224

        /** Utility function for loading and resizing images from app asset folder. */
        fun decodeBitmapAsset(context: Context, filePath: String): Bitmap =
            context.assets.open(filePath).let { BitmapFactory.decodeStream(it) }
    }
}



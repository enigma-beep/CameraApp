package com.example.cameraapp


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.hardware.Camera
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

import android.os.Environment.DIRECTORY_PICTURES

class MainActivity : AppCompatActivity() {
    /*private static final int CAMERA_REQUEST = 1888;
    private ImageView imageView;
    private static final int MY_CAMERA_PERMISSION_CODE = 100;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        this.imageView = (ImageView)this.findViewById(R.id.imageView1);
        Button photoButton = (Button) this.findViewById(R.id.button1);
        photoButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_PERMISSION_CODE);
                }
                else
                {
                    Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST);
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_PERMISSION_CODE)
        {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED)
            {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            }
            else
            {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK)
        {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            imageView.setImageBitmap(photo);
        }
    }*/
    private var mCamera: Camera? = null
    private var mCameraPreview: CameraPreview? = null
    private var storagePath: File? = null

    /**
     * Helper method to access the camera returns null if it cannot get the
     * camera or does not exist
     *
     * @return
     */
    private// cannot get camera or does not exist
    val cameraInstance: Camera?
        get() {
            var camera: Camera? = null
            try {
                camera = Camera.open()
            } catch (e: Exception) {
            }

            return camera
        }

    internal var mPicture: android.hardware.Camera.PictureCallback = Camera.PictureCallback { data, camera ->
        val pictureFile = outputMediaFile ?: return@PictureCallback
        try {
            val fos = FileOutputStream(pictureFile)
            fos.write(data)
            fos.close()
        } catch (e: FileNotFoundException) {

        } catch (e: IOException) {
        }
    }

    private//        File mediaStorageDir = new File(
    //
    //                Environment
    //                        .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
    //                "MyCameraApp");
    //        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "MYCAMERAAPP");
    //        String myfolder = Environment.getExternalStorageDirectory() + "/" + "Pictures";
    //        File f = new File(myfolder);
    //        if (!f.exists()) {
    //            if (!f.mkdirs()) {
    ////                Toast.makeText(getApplicationContext(), myfolder + " can't be created.", Toast.LENGTH_SHORT).show();
    //                Log.d("MyCameraApp", myfolder + " can't be created.");
    //
    //            }
    //        }
    //            mediaStorageDir.mkdirs();
    //                Toast.makeText(getApplicationContext(),)
    // Create a media file name
    val outputMediaFile: File?
        get() {
            val storagePath = File(Environment.getExternalStorageDirectory(), "MyCameraApp")

            if (!storagePath.exists()) {
                storagePath.mkdirs()
            }


            if (!storagePath.exists()) {
                if (!storagePath.mkdirs()) {
                    Log.d("MyCameraApp", "failed to create directory")
                    return null
                }
            }
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(Date())
            val mediaFile: File
            mediaFile = File(storagePath.path + File.separator
                    + "IMG_1.jpg")

            return mediaFile
        }

    /** Called when the activity is first created.  */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        storagePath = File(Environment.getExternalStorageDirectory(), "MyCameraApp")

        if (!storagePath!!.exists()) {
            storagePath!!.mkdirs()
        }


        if (!storagePath!!.exists()) {
            //            mediaStorageDir.mkdirs();
            if (!storagePath!!.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory")
                //                Toast.makeText(getApplicationContext(),)
                //                        return null;
            }
        }



        mCamera = cameraInstance
        mCameraPreview = CameraPreview(this, mCamera)
        val preview = findViewById<View>(R.id.camera_preview) as FrameLayout
        preview.addView(mCameraPreview)

        val captureButton = findViewById<View>(R.id.button_capture) as Button
        captureButton.setOnClickListener {
            mCamera!!.takePicture(null, null, mPicture)
            val i = Intent(this@MainActivity, display::class.java)
            startActivity(i)
        }
    }
}

package com.haxstar.snapkids;

/*
 * Copyright 2019 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.PixelCopy;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.ar.core.ArCoreApk;
import com.google.ar.core.AugmentedFace;
import com.google.ar.core.TrackingState;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.Scene;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.Renderable;
import com.google.ar.sceneform.rendering.Texture;
import com.google.ar.sceneform.utilities.ChangeId;
import com.google.ar.sceneform.ux.AugmentedFaceNode;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import androidx.appcompat.app.AppCompatActivity;

public class FaceArActivity extends AppCompatActivity {
    private static final String TAG = FaceArActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;
    private ArFaceFragment arFragment;
    private ModelRenderable faceRegionsRenderable;
    private Texture faceMeshTexture;
    private final ArrayList<ModelRenderable> filtersList = new ArrayList<>();
    private boolean changeModel = false;
    private ChangeId foxId;

    private final HashMap<AugmentedFace, AugmentedFaceNode> faceNodeMap = new HashMap<>();

    @Override
    @SuppressWarnings({"AndroidApiChecker", "FutureReturnValueIgnored"})
    // CompletableFuture requires api level 24
    // FutureReturnValueIgnored is not valid
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!checkIsSupportedDeviceOrFinish(this)) {
            return;
        }

        setContentView(R.layout.activity_main);
        arFragment = (ArFaceFragment) getSupportFragmentManager().findFragmentById(R.id.face_fragment);

        ImageButton galleryButton = findViewById(R.id.gallery_btn);
        ImageButton arButton = findViewById(R.id.ar_btn);
        ImageButton cameraButton = findViewById(R.id.camera_btn);
        ImageButton options = findViewById(R.id.face_filter_btn);

        ImageButton glasses = findViewById(R.id.glasses_filter_btn);
        ImageButton fox = findViewById(R.id.fox_filter_btn);
        ImageButton cat = findViewById(R.id.cat_filter_btn);
        ImageButton close = findViewById(R.id.face_filter_close);

        galleryButton.setOnClickListener(v -> {
            if (!galleryButton.isActivated()) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_VIEW);
                intent.setType("image/*");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });

        arButton.setOnClickListener(v -> {
            Intent sceneViewerIntent = new Intent(Intent.ACTION_VIEW);
            sceneViewerIntent.setData(Uri.parse("https://arvr.google.com/scene-viewer/1.1?file=https://poly.googleusercontent.com/downloads/c/fp/1587461923777301/2LCcq8vhqJ3/6S-eh-b-ESF/turtle.gltf"));
            sceneViewerIntent.setPackage("com.google.android.googlequicksearchbox");
            startActivity(sceneViewerIntent);
        });

        final MediaPlayer mp = MediaPlayer.create(this, R.raw.camera_snap);
        cameraButton.setOnClickListener(v -> {
            if (!cameraButton.isActivated()) {
                Toast.makeText(FaceArActivity.this, "Take Photo", Toast.LENGTH_SHORT).show();
                mp.start();
                takePhoto();
            }
        });

        //Show options of filters to choose from
        options.setOnClickListener((View v) -> {
                    options.setVisibility(View.GONE);
                    close.setVisibility(View.VISIBLE);
                    glasses.setVisibility(View.VISIBLE);
                    fox.setVisibility(View.VISIBLE);
                    cat.setVisibility(View.VISIBLE);
                }
        );

        //Close filter options
        close.setOnClickListener((View v) -> {
                    close.setVisibility(View.GONE);
                    options.setVisibility(View.VISIBLE);
                    glasses.setVisibility(View.GONE);
                    fox.setVisibility(View.GONE);
                    cat.setVisibility(View.GONE);
                }
        );

        //Apply glasses filter
        glasses.setOnClickListener((View v) -> {
                    close.setVisibility(View.GONE);
                    options.setVisibility(View.VISIBLE);
                    options.setImageResource(R.drawable.glasses_emoji);
                    glasses.setVisibility(View.GONE);
                    fox.setVisibility(View.GONE);
                    cat.setVisibility(View.GONE);
                    changeModel = !changeModel;
                    faceRegionsRenderable = filtersList.get(1);
                }
        );

        //Apply fox filter
        fox.setOnClickListener((View v) -> {
                    close.setVisibility(View.GONE);
                    options.setVisibility(View.VISIBLE);
                    options.setImageResource(R.drawable.fox_emoji);
                    glasses.setVisibility(View.GONE);
                    fox.setVisibility(View.GONE);
                    cat.setVisibility(View.GONE);
                    changeModel = !changeModel;
                    faceRegionsRenderable = filtersList.get(2);
                }
        );

        //Apply cat filter
        cat.setOnClickListener((View v) -> {
                    close.setVisibility(View.GONE);
                    options.setVisibility(View.VISIBLE);
                    options.setImageResource(R.drawable.cat_emoji);
                    glasses.setVisibility(View.GONE);
                    fox.setVisibility(View.GONE);
                    cat.setVisibility(View.GONE);
                    changeModel = !changeModel;
                    faceRegionsRenderable = filtersList.get(0);
                }
        );

        loadModels();

        ArSceneView sceneView = arFragment.getArSceneView();

        // This is important to make sure that the camera stream renders first so that
        // the face mesh occlusion works correctly.
        sceneView.setCameraStreamRenderPriority(Renderable.RENDER_PRIORITY_FIRST);

        Scene scene = sceneView.getScene();

        scene.addOnUpdateListener(
                (FrameTime frameTime) -> {
                    if (faceRegionsRenderable == null) {
                        return;
                    }

                    Collection<AugmentedFace> faceList =
                            Objects.requireNonNull(sceneView.getSession()).getAllTrackables(AugmentedFace.class);

                    // Make new AugmentedFaceNodes for any new faces.
                    for (AugmentedFace face : faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            AugmentedFaceNode faceNode = new AugmentedFaceNode(face);
                            faceNode.setParent(scene);
                            faceNode.setFaceRegionsRenderable(faceRegionsRenderable);
                            //If the fox filter is being loaded, load the texture as well
                            setFoxTexture(faceNode);
                            faceNodeMap.put(face, faceNode);
                        } else if (changeModel) {
                            Objects.requireNonNull(faceNodeMap.get(face)).setFaceRegionsRenderable(faceRegionsRenderable);
                            setFoxTexture(faceNodeMap.get(face));
                        }
                    }
                    changeModel = false;

                    // Remove any AugmentedFaceNodes associated with an AugmentedFace that stopped tracking.
                    Iterator<Map.Entry<AugmentedFace, AugmentedFaceNode>> iter =
                            faceNodeMap.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<AugmentedFace, AugmentedFaceNode> entry = iter.next();
                        AugmentedFace face = entry.getKey();
                        if (face.getTrackingState() == TrackingState.STOPPED) {
                            AugmentedFaceNode faceNode = entry.getValue();
                            faceNode.setParent(null);
                            iter.remove();
                        }
                    }
                });
    }

    /**
     * Loads each face regions renderables and textures
     * Face region renderables are skinned models that render 3D objects mapped to the regions of the augmented face.
     */
    private void loadModels() {
        ArrayList<String> resources = new ArrayList<>(Arrays.asList(
                "fox_face", "yellow_glasses", "cat"));

        //Load each models
        for (String res : resources) {
            ModelRenderable.builder()
                    .setSource(this, getResources().getIdentifier(res, "raw", "com.haxstar.snapkids"))
                    .build()
                    .thenAccept(
                            modelRenderable -> {
                                //Add filter to list of filters
                                filtersList.add(modelRenderable);
                                faceRegionsRenderable = modelRenderable;
                                modelRenderable.setShadowCaster(false);
                                modelRenderable.setShadowReceiver(false);

                                if (res.equals("fox_face")) {
                                    foxId = modelRenderable.getId();
                                }
                            });
        }

        // Load the fox face mesh texture.
        Texture.builder()
                .setSource(this, R.drawable.fox_face_mesh_texture)
                .build()
                .thenAccept(texture -> faceMeshTexture = texture);
    }

    /**
     * Sets the fox texture if the fox model is being rendered
     */
    private void setFoxTexture(AugmentedFaceNode node) {
        //If the fox filter is being loaded, load the texture as well
        if (faceRegionsRenderable.getId() == foxId) {
            node.setFaceMeshTexture(faceMeshTexture);
        } else {
            node.setFaceMeshTexture(null);
        }
    }

    /**
     * Returns false and displays an error message if Sceneform cannot run, true if Sceneform can run
     * on this device.
     *
     * <p>Sceneform requires Android N on the device as well as OpenGL 3.0 capabilities.
     *
     * <p>Finishes the activity if Sceneform can not run
     */
    private static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (ArCoreApk.getInstance().checkAvailability(activity)
                == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
            Log.e(TAG, "Augmented Faces requires ArCore.");
            Toast.makeText(activity, "Augmented Faces requires ArCore", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) Objects.requireNonNull(activity.getSystemService(Context.ACTIVITY_SERVICE)))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }

    private String generateFilename() {
        String date =
                new SimpleDateFormat("yyyyMMddHHmmss", java.util.Locale.getDefault()).format(new Date());
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).getAbsolutePath() +
                File.separator + "Camera/" + date + "_screenshot.jpg";
    }

    private void saveBitmapToDisk(Bitmap bitmap, String filename) throws IOException {

        File out = new File(filename);
        if (!Objects.requireNonNull(out.getParentFile()).exists()) {
            out.getParentFile().mkdirs();
        }
        try (FileOutputStream outputStream = new FileOutputStream(filename);
             ByteArrayOutputStream outputData = new ByteArrayOutputStream()) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputData);
            outputData.writeTo(outputStream);
            outputStream.flush();
            outputStream.close();

            Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri contentUri = Uri.fromFile(out);
            mediaScanIntent.setData(contentUri);
            this.sendBroadcast(mediaScanIntent);
        } catch (IOException ex) {
            throw new IOException("Failed to save bitmap to disk", ex);
        }
    }

    private void takePhoto() {
        final String filename = generateFilename();
        ArSceneView view = arFragment.getArSceneView();

        // Create a bitmap the size of the scene view.
        final Bitmap bitmap = Bitmap.createBitmap(view.getWidth(), view.getHeight(),
                Bitmap.Config.ARGB_8888);

        // Create a handler thread to offload the processing of the image.
        final HandlerThread handlerThread = new HandlerThread("PixelCopier");
        handlerThread.start();
        // Make the request to copy.
        PixelCopy.request(view, bitmap, (copyResult) -> {
            if (copyResult == PixelCopy.SUCCESS) {
                try {
                    saveBitmapToDisk(bitmap, filename);
                } catch (IOException e) {
                    Toast toast = Toast.makeText(FaceArActivity.this, e.toString(),
                            Toast.LENGTH_LONG);
                    toast.show();
                    return;
                }
            } else {
                Toast toast = Toast.makeText(FaceArActivity.this,
                        "Failed to copyPixels: " + copyResult, Toast.LENGTH_LONG);
                toast.show();
            }
            handlerThread.quitSafely();
        }, new Handler(handlerThread.getLooper()));
    }
}
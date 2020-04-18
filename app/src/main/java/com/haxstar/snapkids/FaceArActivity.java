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
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import androidx.appcompat.app.AppCompatActivity;

public class FaceArActivity extends AppCompatActivity {
    private static final String TAG = FaceArActivity.class.getSimpleName();

    private static final double MIN_OPENGL_VERSION = 3.0;

    private ArFaceFragment arFragment;

    private ModelRenderable faceRegionsRenderable;
    private Texture faceMeshTexture;
    private ArrayList<ModelRenderable> filtersList = new ArrayList<>();
    private boolean changeModel = false;
    private int filterIndex = 1;
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
        ImageButton cameraButton = findViewById(R.id.camera_btn);
        ImageButton nextButton = findViewById(R.id.face_filter_btn);

        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!galleryButton.isActivated()) {
                    Intent intent = new Intent();
                    intent.setAction(android.content.Intent.ACTION_VIEW);
                    intent.setType("image/*");
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
            }
        });

        final MediaPlayer mp = MediaPlayer.create(this, R.raw.camera_snap);
        cameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(!cameraButton.isActivated()) {
                    Toast.makeText(FaceArActivity.this, "Take Photo", Toast.LENGTH_SHORT).show();
                    mp.start();
                   
                }
            }
        });

        //Set the next button
        nextButton.setOnClickListener( (View v) -> {
                    changeModel = !changeModel;
                    filterIndex++;
                    if (filterIndex > filtersList.size() - 1) {
                        filterIndex = 0;
                    }
                    faceRegionsRenderable = filtersList.get(filterIndex);
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
                            sceneView.getSession().getAllTrackables(AugmentedFace.class);

                    // Make new AugmentedFaceNodes for any new faces.
                    for (AugmentedFace face : faceList) {
                        if (!faceNodeMap.containsKey(face)) {
                            AugmentedFaceNode faceNode = new AugmentedFaceNode(face);
                            faceNode.setParent(scene);
                            faceNode.setFaceRegionsRenderable(faceRegionsRenderable);
                            //If the fox filter is being loaded, load the texture as well
                            setFoxTexture(faceNode);
                            faceNodeMap.put(face, faceNode);
                        }else if(changeModel){
                            faceNodeMap.get(face).setFaceRegionsRenderable(faceRegionsRenderable);
                            setFoxTexture( faceNodeMap.get(face));
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
    public void loadModels(){
        ArrayList<String> resources = new ArrayList<>(Arrays.asList(
                "fox_face", "yellow_glasses", "cat"));

        //Load each models
        for(String res: resources){
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

                                if(res == "fox_face"){
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
    public void setFoxTexture(AugmentedFaceNode node){
        //If the fox filter is being loaded, load the texture as well
        if(faceRegionsRenderable.getId() == foxId){
            node.setFaceMeshTexture(faceMeshTexture);
        }else{
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
    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (ArCoreApk.getInstance().checkAvailability(activity)
                == ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE) {
            Log.e(TAG, "Augmented Faces requires ArCore.");
            Toast.makeText(activity, "Augmented Faces requires ArCore", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
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
}

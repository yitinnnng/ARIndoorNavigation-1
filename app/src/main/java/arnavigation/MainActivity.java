package arnavigation;


import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.math.Quaternion;
import com.google.ar.sceneform.math.Vector3;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.TransformableNode;
import com.ustglobal.arcloudanchors.R;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    private CloudAnchorFragment arFragment;
    private ArrayList anchorList;
    private String FROM, MODE;
    protected static final String HOSTED_ANCHOR_IDS = "anchor_ids";
    protected static final String HOSTED_ANCHOR_NAMES = "anchor_names";
    protected static final String HOSTED_ANCHOR_MINUTES = "anchor_minutes";
    private String cloudAnchorId;
    private TextView debugText;
    private TextView userMessageText;
    private SharedPreferences sharedPreferences;
    // Tap handling and UI.
    private GestureDetector gestureDetector;




    private enum AppAnchorState {
        NONE,
        HOSTING,
        HOSTED
    }

    private Anchor anchor;
    private AnchorNode anchorNode;
    private AppAnchorState appAnchorState = AppAnchorState.NONE;
    private String APARTMENT18 = "apartment18_DB";
    private String APARTMENT30 = "apartment18_DB";
    private String PACKENHAMHOUSE = "packenhamHouse_DB";
    private String FIREEXIT = "fireExit_DB";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        // Initialize UI components.
//        debugText = findViewById(R.id.debug_message);
//        userMessageText = findViewById(R.id.user_message);


        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            Bundle extras = getIntent().getExtras();
            if (extras == null) {
                FROM = null;
            } else {
                FROM = extras.getString(Home.FROM);
                MODE = extras.getString(Home.MODE);
            }
        }

        setContentView(R.layout.activity_main);
        anchorList = new ArrayList();
        // Context of the entire application is passed on to TinyDB
        Storage storage = new Storage(getApplicationContext());
        Button resolve = findViewById(R.id.resolve);

        arFragment = (CloudAnchorFragment) getSupportFragmentManager().findFragmentById(R.id.fragment);
        // This part of the code will be executed when the user taps on a plane
        arFragment.setOnTapArPlaneListener((hitResult, plane, motionEvent) -> {
            //Will be used in Admin Mode
            if(MODE.equalsIgnoreCase("admin"))
            {
                Log.d("HIT_RESULT:", hitResult.toString());
                // Used to render 3D model on the top of this anchor
                anchor = arFragment.getArSceneView().getSession().hostCloudAnchor(hitResult.createAnchor());
                appAnchorState = AppAnchorState.HOSTING;
                showToast("Adding Arrow to the scene");
                create3DModel(anchor);
            } else {
                showToast("3D model can be added in Admin mode only");
            }

        });

        arFragment.getArSceneView().getScene().addOnUpdateListener(frameTime -> {

            if (appAnchorState != AppAnchorState.HOSTING)
                return;
            Anchor.CloudAnchorState cloudAnchorState = anchor.getCloudAnchorState();

            if (cloudAnchorState.isError()) {
                showToast(cloudAnchorState.toString());
            } else if (cloudAnchorState == Anchor.CloudAnchorState.SUCCESS) {
                appAnchorState = AppAnchorState.HOSTED;

                String anchorId = anchor.getCloudAnchorId();
                anchorList.add(anchorId);

                if (FROM.equalsIgnoreCase(Home.APARTMENT18)) {
                    storage.addListString(APARTMENT18, anchorList);
                } else if (FROM.equalsIgnoreCase(Home.APARTMENT30)) {
                    storage.addListString(APARTMENT30, anchorList);
                } else if (FROM.equalsIgnoreCase(Home.PACKENHAMHOUSE)) {
                    storage.addListString(PACKENHAMHOUSE, anchorList);
                } else if (FROM.equalsIgnoreCase(Home.FIREEXIT)) {
                    storage.addListString(FIREEXIT, anchorList);
                }

                showToast("Anchor hosted successfully. Anchor Id: " + anchorId);
            }

        });


        resolve.setOnClickListener(view -> {
            ArrayList<String> stringArrayList = new ArrayList<>();
            if (FROM.equalsIgnoreCase(Home.APARTMENT18)) {
                stringArrayList = storage.getListString(APARTMENT18);
            } else if (FROM.equalsIgnoreCase(Home.APARTMENT30)) {
                stringArrayList = storage.getListString(APARTMENT30);
            } else if (FROM.equalsIgnoreCase(Home.PACKENHAMHOUSE)) {
                stringArrayList = storage.getListString(PACKENHAMHOUSE);
            } else if (FROM.equalsIgnoreCase(Home.FIREEXIT)) {
                stringArrayList = storage.getListString(FIREEXIT);
            }

            for (int i = 0; i < stringArrayList.size(); i++) {
                String anchorId = stringArrayList.get(i);
                if (anchorId.equals("null")) {
                    Toast.makeText(this, "No anchor Id found", Toast.LENGTH_LONG).show();
                    return;
                }

                Anchor resolvedAnchor = arFragment.getArSceneView().getSession().resolveCloudAnchor(anchorId);
                create3DModel(resolvedAnchor);
            }
        });
    }

    private void showToast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    /**
     * Used to build a 3D model
     * @param anchor
     */
    private void create3DModel(Anchor anchor) {
        ModelRenderable
                .builder()
                .setSource(this, Uri.parse("model-triangulated.sfb"))
                .build()
                .thenAccept(modelRenderable -> addModelToScene(anchor, modelRenderable))
                .exceptionally(throwable -> {
                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage(throwable.getMessage()).show();
                    return null;
                });

    }
    /**
     * Used to add the 3D model created in create3Dmodel to the scene
     * @param anchor
     * @param modelRenderable
     */
    private void addModelToScene(Anchor anchor, ModelRenderable modelRenderable) {
        // anchorNode will position itself based on anchor
        anchorNode = new AnchorNode(anchor);
        // AnchorNode cannot be zoomed in or moved so a TransformableNode is created where AnchorNode is the parent
        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
        // Setting the angle of 3D model
        transformableNode.setLocalRotation(Quaternion.axisAngle(new Vector3(0, 1f, 0), 180));
        transformableNode.setParent(anchorNode);
        //adding the model to the transformable node
        transformableNode.setRenderable(modelRenderable);
        //adding this to the scene
        arFragment.getArSceneView().getScene().addChild(anchorNode);
    }


    private static void saveAnchorToStorage(
            String anchorId, String anchorNickname, SharedPreferences anchorPreferences) {
        String hostedAnchorIds = anchorPreferences.getString(HOSTED_ANCHOR_IDS, "");
        String hostedAnchorNames = anchorPreferences.getString(HOSTED_ANCHOR_NAMES, "");
        String hostedAnchorMinutes = anchorPreferences.getString(HOSTED_ANCHOR_MINUTES, "");
        hostedAnchorIds += anchorId + ";";
        hostedAnchorNames += anchorNickname + ";";
        hostedAnchorMinutes += TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis()) + ";";
        anchorPreferences.edit().putString(HOSTED_ANCHOR_IDS, hostedAnchorIds).apply();
        anchorPreferences.edit().putString(HOSTED_ANCHOR_NAMES, hostedAnchorNames).apply();
        anchorPreferences.edit().putString(HOSTED_ANCHOR_MINUTES, hostedAnchorMinutes).apply();
    }
    private void onAnchorNameEntered(String anchorNickname) {
        saveAnchorToStorage(cloudAnchorId, anchorNickname, sharedPreferences);
        userMessageText.setVisibility(View.GONE);
  //      debugText.setText(getString(R.string.debug_hosting_success, cloudAnchorId));
        debugText.setText(cloudAnchorId);
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, cloudAnchorId);
        sendIntent.setType("text/plain");
        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);
    }

//    private void saveAnchorWithNickname() {
//        if (cloudAnchorId == null) {
//            return;
//        }
//        HostDialogFragment hostDialogFragment = new HostDialogFragment();
//        // Supply num input as an argument.
//        Bundle args = new Bundle();
//        args.putString(
//                "nickname", getString(R.string.nickname_default, getNumStoredAnchors(sharedPreferences)));
//        hostDialogFragment.setOkListener(this::onAnchorNameEntered);
//        hostDialogFragment.setArguments(args);
//        hostDialogFragment.show(getSupportFragmentManager(), "HostDialog");
//    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {

    }
}

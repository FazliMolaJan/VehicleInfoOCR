package com.example.ocr;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

import com.example.ocr.text_detection.*;
import com.example.ocr.camera.*;
import com.example.ocr.graphics.*;
import com.example.ocr.utils.*;
import com.example.ocr.webscraper.*;
import com.google.firebase.ml.vision.text.FirebaseVisionText;

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;


public class MainActivity extends AppCompatActivity implements AppEvents {
    // private DrawingArea drawingArea;
    private final int OK = 200;
    private final int SOCKET_TIMEOUT = 408;
    private final int CAPTCHA_LOAD_FAILED = 999;
    private final int TECHNICAL_DIFFICULTY = 888;
    private final String NUMPLATE_PATTERN = "[A-Z]{2}[0-9]{2}[A-Z]+[0-9]+";
    private final String PROCESSING_EMOJI = "\u23f3";
    private final String DONE_EMOJI = "\ud83d\udc4c";
    private final String SMILE_EMOJI = "\ud83d\ude00";
    private final String CRYING_EMOJI = "\ud83d\ude36⏳⏳⏳";
    private ViewGroup superContainer;
    //  ----- Instance Variables -----⏳
    // private EditTextPicker vehicleNumber;

    // the bottom sheet
    private EditText vehicleNumber;
    private ImageView captchaImageView;
    private Button searchBtn;
    private View bottomSheetView;
    private View fruitNinja;
    private ViewGroup platesView;
    private View drawerView;
    private CameraSource cameraSource = null;
    private CameraSourcePreview cameraPreview;
    private GraphicOverlay graphicOverlay;
    private EditText captchaInput;
    private ClipboardManager clipboard;
    private ImageButton imgbt;
    private WebScraper webScraper;
    private ImageBitmapGetter captchaBitmapGetter;
    public BitmapTextRecognizer bitmapProcessor;
    private SimplePermissions permHandler;
    private Button camBtn;
    private Button flashBtn;
    private Button drawerBtn;

    private boolean canDoTransitions;
    private Element eltCaptchaImage;
    private Element eltVehicleNumber;
    private Element eltCaptchaInput;
    private Element eltSubmitBtn;
    private static String TAG = "MainActivity";
    private final String BASE_URL = "https://vahan.nic.in";
    private final String VEHICLE_URL="/nrservices/faces/user/searchstatus.xhtml";
    private final String FULL_URL=BASE_URL+VEHICLE_URL;

    private Set<String> platesHash = new HashSet<>();
    private Queue<String> platesQueue = new LinkedList<>();
    private List<Button> buttons = new ArrayList<>();
    private int NUM_PLATES;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        canDoTransitions = getResources().getString(R.string.can_do_transitions).equals("true");
        bitmapProcessor = new BitmapTextRecognizer(MainActivity.this);
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        superContainer = findViewById(R.id.super_container);
        cameraPreview = findViewById(R.id.camera_source_preview);

        graphicOverlay = findViewById(R.id.graphics_overlay);
        captchaImageView = findViewById(R.id.captcha_img);
        drawerView = findViewById(R.id.drawer);
        platesView = findViewById(R.id.detected_plates);
        searchBtn = findViewById(R.id.search_btn);
        flashBtn = findViewById(R.id.flash_btn);
        drawerBtn = findViewById(R.id.drawer_btn);
        camBtn = findViewById(R.id.cam_btn);
        // drawerView.getBackground().setAlpha(242);
        setCaptchaInputListeners();
        setSearchButtonListeners();
        setVehicleNumListeners();

        webScraper = new WebScraper(this);
        captchaBitmapGetter = new ImageBitmapGetter(MainActivity.this);
        webScraper.setUserAgentToDesktop(true); //default: false
        webScraper.setLoadImages(true); //default: false

        LinearLayout layout = (LinearLayout)findViewById(R.id.webview);
        layout.addView(webScraper.getView());
        int[] plateids = {R.id.plate1,R.id.plate2,R.id.plate3,R.id.plate4,R.id.plate5};
        NUM_PLATES = plateids.length;
        for(int i=0;i<NUM_PLATES;i++){
            Button btn = findViewById(plateids[i]);
            buttons.add(btn);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vehicleNumber.setText(btn.getText());
                    drawerBtn.performClick();
                }
            });
        }
        // platesView.addView(searchBtn);
        // should usually be the last line in init
        getPermissionsAfterInit();
    }
    private void getPermissionsAfterInit(){
        permHandler = new SimplePermissions(this, new String[]{
                // android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_NETWORK_STATE
        });
        if(!permHandler.hasAllPermissions())
            Toast.makeText(MainActivity.this, "Checking permissions...", Toast.LENGTH_SHORT).show();
        permHandler.grantPermissions();
    }
    //    callback from ActivityCompat.requestPermissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String PermissionsList[], @NonNull int[] grantResults) {
        // https://stackoverflow.com/questions/34342816/android-6-0-multiple-PermissionsList
        if (permHandler.hasAllPermissions()) {
            Toast.makeText(MainActivity.this, "Permissions granted", Toast.LENGTH_SHORT).show();
            setCamButtonListeners();
            setFlashListeners();
            // start the camera
            camBtn.performClick();
            startLoadingCaptcha();
        }
        else {
            Toast.makeText(MainActivity.this, "Please enable the permissions from settings. Exiting App!", Toast.LENGTH_LONG).show();
            MainActivity.this.finish();
        }
    }
    private String captchaFilter(String s){
        // NOTE: small j and small i replaced if at 3rd pos!
        String filtered = s.replaceAll("[^a-zA-Z0-9]","");
        if(filtered.length() > 2 && filtered.charAt(2)=='j'){
            filtered = filtered.substring(0,2)+'J'+filtered.substring(3);
        }
        if(filtered.length() > 2 && filtered.charAt(2)=='i'){
            filtered = filtered.substring(0,2)+'I'+filtered.substring(3);
        }
        return filtered;
    }

    private void confirmVehicleNumber() {
        if (cameraSource != null && cameraSource.frameProcessor.textBlocks != null) {
            vehicleNumber.setText(cameraSource.frameProcessor.majorText);
            // show vehicleNum draw interface here
            List<FirebaseVisionText.TextBlock> textBlocks = cameraSource.frameProcessor.textBlocks;
            for (int i = 0; i < textBlocks.size(); i++) {
                // find and club nearby boxes?!
            }
        }
    }
    public static Bitmap resizeBitmap(Bitmap image, int maxWidth, int maxHeight) {
        if (maxHeight > 0 && maxWidth > 0) {
            int width = image.getWidth();
            int height = image.getHeight();
            float ratioBitmap = (float) width / (float) height;
            float ratioMax = (float) maxWidth / (float) maxHeight;

            int finalWidth = maxWidth;
            int finalHeight = maxHeight;
            if (ratioMax > 1) {
                finalWidth = (int) ((float) maxHeight * ratioBitmap);
            } else {
                finalHeight = (int) ((float) maxWidth / ratioBitmap);
            }

            image = Bitmap.createScaledBitmap(image, finalWidth, finalHeight, true);
            return image;
        } else {
            return image;
        }
    }

    private int colorVal(int pixel){
        return (int)Math.sqrt(Math.pow(Color.red(pixel),2) +Math.pow(Color.green(pixel),2) +Math.pow(Color.blue(pixel),2));
    }
    public Bitmap pad(Bitmap Src, int padding_x, int padding_y) {
        Bitmap outputimage = Bitmap.createBitmap(Src.getWidth() + padding_x,Src.getHeight() + padding_y, Bitmap.Config.ARGB_8888);
        Canvas can = new Canvas(outputimage);
        can.drawARGB(0xFF,0xFF,0xFF,0xFF); //This represents White color
        can.drawBitmap(Src, padding_x, padding_y, null);
        return outputimage;
    }
    private Bitmap dilateBinaryBitmap(Bitmap binaryBitmap,int KX,int KY) {
        Bitmap copyBitmap = binaryBitmap.copy(Bitmap.Config.ARGB_8888,true);
        int currp, maxp,w = binaryBitmap.getWidth(), h = binaryBitmap.getHeight(), maxi,maxj;
        // revolve kernel
        for(int i=0;i<w;i++){
            for(int j=0;j<h;j++){
                maxp = colorVal(binaryBitmap.getPixel(i,j));
                maxi = i;
                maxj = j;
                for(int ki=i-KX/2;ki<=i+KX/2;ki++){
                    for(int kj=j-KY/2;kj<=j+KY/2;kj++){
                        if(ki > -1 && kj > -1 && ki < w && kj < h){
                            currp = colorVal(binaryBitmap.getPixel(ki,kj));
                            if(maxp < currp){
                                maxp = currp;
                                maxi = ki;
                                maxj = kj;
                            }
                        }
                    }
                }
                copyBitmap.setPixel(i,j, binaryBitmap.getPixel(maxi,maxj));
            }
        }
        Log.d(TAG,"Dilated: "+copyBitmap.getWidth());
        return copyBitmap;
    }



    public boolean checkInternetConnection() {
        ConnectivityManager con_manager = (ConnectivityManager)
                MainActivity.this.getSystemService(MainActivity.CONNECTIVITY_SERVICE);

        if(con_manager.getActiveNetworkInfo() != null && con_manager.getActiveNetworkInfo().isAvailable() && con_manager.getActiveNetworkInfo().isConnected()){
            return true;
        }
        else{
            logToast(CRYING_EMOJI+"  No internet available!");
            return false;
        }
    }

    private void startLoadingCaptcha() {
        if(checkInternetConnection()) {
            // bottomSheetOn();

            captchaInput.setText("");
            // vehicleDetails.setVisibility(View.INVISIBLE);
            // vehicleDetails.setVisibility(View.GONE);
            webScraper.setOnPageLoadedListener(new WebScraper.onPageLoadedListener(){
                @Override
                public void loaded(String URL) {
                    Log.d(TAG, "Loaded !");
                    eltCaptchaImage = webScraper.findElementByClassName("captcha-image", 0);
                    eltVehicleNumber = webScraper.findElementById("regn_no1_exact");
                    eltCaptchaInput = webScraper.findElementById("txt_ALPHA_NUMERIC");
                    eltSubmitBtn = webScraper.findElementByClassName("ui-button ui-widget ui-state-default ui-corner-all ui-button-text-only", 0);
                    webScraper.setOnPageLoadedListener(null);
                    String focusScript=
                            "var children = document.getElementById('wrapper').children; "+
                                    "for (var i=0; i<children.length; i++) {children[i].style.display='none';} "+
                                    "document.getElementById('page-wrapper').style.display='block'; "+
                                    "var children = document.getElementsByClassName('container')[2].children; "+
                                    "for (var i=0; i<children.length; i++) {children[i].style.display='none';}"+
                                    "document.getElementsByClassName('row bottom-space')[0].style.display='block';"+
                                    "document.getElementsByClassName('logo-header-section display-print-none')[0].style.display='block';";
                    webScraper.loadURL("javascript:{" + focusScript + "}void(0);");
                    Log.d(TAG, "Got image from: " + eltCaptchaImage.getAttribute("src"));
                    eltCaptchaImage.callImageBitmapGetter(captchaBitmapGetter);
                }
            });
            logToast("Loading Captcha");
            webScraper.loadURL(FULL_URL);
        }
    }
    public class ImageBitmapGetter implements Img2Bitmap
    {
        private AppEvents listener;
        ImageBitmapGetter(AppEvents listener){
            this.listener = listener;
        }
        public void processAndRead(Bitmap bitmap){
            // The image is about 120x40
            Bitmap resizedCaptcha  =  resizeBitmap(bitmap, 90, 30);
            // Blur it out by resizing
            Bitmap captchaImage = resizeBitmap(resizedCaptcha, 195, 65);
            Bitmap dilated = dilateBinaryBitmap(captchaImage,1,5);
            Bitmap padded = pad(dilated,30,30);
            resizedCaptcha.recycle();
            captchaImage.recycle();
            dilated.recycle();
            listener.onBitmapProcessed(padded);
        }

        @Override
        public void onConvertComplete(final byte[] mDecodedImage)
        {
            if (mDecodedImage == null || mDecodedImage.length == 0) {
                return;
            }
            final Bitmap bitmap = BitmapFactory.decodeByteArray(mDecodedImage, 0, mDecodedImage.length);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    captchaImageView.setImageBitmap(bitmap);
                }

            });
            logToast(PROCESSING_EMOJI + " Processing Captcha");
            new Thread(new Runnable() {
                public void run() {
                    processAndRead(bitmap);
                }
            }).start();
        }
    }

    @Override
    public void onBitmapProcessed(final Bitmap processedCaptchaImage){
        bitmapProcessor.processBitmap(processedCaptchaImage);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                captchaImageView.setImageBitmap(processedCaptchaImage);
            }

        });
        // processedCaptchaImage.recycle();
    }

    private void createCameraSource() {
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
            cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
        }

        cameraSource.setMachineLearningFrameProcessor(new TextRecognitionProcessor(MainActivity.this));
    }
    @Override
    public void onCaptchaUpdate(String detectedCaptcha){
        captchaInput.setText(captchaFilter(detectedCaptcha));
        logToast(SMILE_EMOJI + " Captcha read successful!");
    }
    @Override
    public void onMajorTextUpdate(String majorText){
        if(platesHash.contains(majorText))
            return;
        platesHash.add(majorText);
        platesQueue.add(majorText);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                platesHash.remove(platesQueue.remove());
                updatePlates();
            }
        },10000);
        if(platesQueue.size() > NUM_PLATES) {
            platesHash.remove(platesQueue.remove());
        }
        updatePlates();
        if(vehicleNumber.getText().equals(""))
            vehicleNumber.setText(majorText);
        Log.d(TAG,"Updated majorText: "+majorText);
    }

    private Fade mFade;

    @TargetApi(19)
    public void updatePlates(){
        if(canDoTransitions) {
            // Get the root view and create a transition
            mFade = new Fade(Fade.IN);
            mFade.setDuration(1000);
            // Start recording changes to the view hierarchy
            TransitionManager.beginDelayedTransition(superContainer, mFade);
        }
        int i = 0;
        for(String plate : platesQueue){
            buttons.get(i).setText(plate);
            buttons.get(i).setVisibility(VISIBLE);
            i++;
        }
        while(i<NUM_PLATES){
            buttons.get(i).setVisibility(INVISIBLE);
            i++;
        }
    }

    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (cameraPreview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                cameraPreview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                stopCameraSource();
            }
        }
    }
    private void setCamButtonListeners(){
        camBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(cameraSource==null) {
                    createCameraSource();
                    startCameraSource();
                    camBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.bubble2));
                    // drawingArea.setVisibility(View.INVISIBLE);
                }
                else {
                    flashBtn.animate().scaleX(1/1.1f).scaleY(1/1.1f).start();
                    flashBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.flash_off));
                    confirmVehicleNumber();
                    stopCameraSource();
                    camBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.bubble_pop2));
                    // drawingArea.setVisibility(View.VISIBLE);
                    // camBtn.animate().setDuration(600).rotation(camBtn.getRotation() + 360).start();
                }
            }
        });
    }

    private boolean flashOn=false;
    private void setFlashListeners() {
        flashBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(cameraSource!=null){
                    if(flashOn) {
                        cameraSource.turnOffTheFlash();
                        flashBtn.animate().scaleX(1/1.1f).scaleY(1/1.1f).start();
                        flashBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.flash_off));
                    }
                    else {
                        cameraSource.turnOnTheFlash();
                        flashBtn.animate().scaleX(1.1f).scaleY(1.1f).start();
                        flashBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.flash_on));
                    }
                    flashOn = !flashOn;
                }
            }
        });
    }
    private void setCaptchaInputListeners() {
        captchaInput = findViewById(R.id.captcha_input);
        // submit on press enter
        captchaInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if((event!=null&&(event.getKeyCode()==KeyEvent.KEYCODE_ENTER))||(actionId== EditorInfo.IME_ACTION_DONE)){
                    searchBtn.performClick();
                }
                return false;
            }
        });
    }
    private void logToast(String msg) {
        Log.d(TAG,msg);
        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
    }
    private void setSearchButtonListeners() {
        searchBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "Searching...", Toast.LENGTH_SHORT).show();
                String result = vehicleNumber.getText().toString();
                eltVehicleNumber.setText(vehicleNumber.getText().toString());
                eltVehicleNumber.setAttribute("style","background-color:lightgreen !important");
                eltCaptchaInput.setText(captchaInput.getText().toString());
                eltCaptchaInput.setAttribute("style","background-color:lightgreen !important");
                if(checkInternetConnection()) {
                    eltSubmitBtn.click();

                    String tableScript = "var table = document.getElementsByClassName(\"table\")[0];" +
                            " for (var i = 0, row; row = table.rows[i]; i++) {" +
                            " row.style = \"display: table;  width:100%; word-break:break-all;\";" +
                            " for (var j = 0, col; col = row.cells[j]; j++) {" +
                            " col.style=\"display: table-row;\"" +
                            " }" +
                            " }";
                    webScraper.loadURL("javascript:{" + tableScript + "}void(0)");
                }
            }
        });
    }
    //  drawerBtn connected via onclick in xml
    public void onDrawerButtonClicked(View v) {
        if(drawerView.getVisibility()== VISIBLE){
            drawerView.setVisibility(INVISIBLE);
        }
        else{
            drawerView.setVisibility(VISIBLE);
        }
        // TransitionManager.beginDelayedTransition(superContainer);
        // camBtn.performClick();
    }
    private void setVehicleNumListeners() {
        vehicleNumber = findViewById(R.id.vehicle_plate);
        vehicleNumber.setFilters(new InputFilter[] {new InputFilter.AllCaps()});
        vehicleNumber.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
            @Override public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
            @Override public void afterTextChanged(Editable et) {
                searchBtn.setEnabled(et.toString().matches(NUMPLATE_PATTERN));
            }
        });
        searchBtn.setEnabled(vehicleNumber.getText().toString().matches(NUMPLATE_PATTERN));
    }

    public void copyNumber(View view) {
        String vehicleNum = vehicleNumber.getText().toString();
        Log.d(TAG,"Copying : "+vehicleNum);
        if(!vehicleNum.equals("")) {
            ClipData clip = ClipData.newPlainText("Vehicle Number", vehicleNum);
            clipboard.setPrimaryClip(clip);
            logToast(SMILE_EMOJI+" Text Copied:  "+vehicleNum);
        }
        else{
            logToast(CRYING_EMOJI+" Empty text");
        }
    }

    private boolean warningBack = false;
    // collapse bottom sheet when back button pressed
    @Override
    public void onBackPressed() {
        if (drawerView.getVisibility() != VISIBLE){
            if(cameraSource != null){
                if(warningBack)
                    super.onBackPressed();
                else{
                    Toast.makeText(MainActivity.this, "Press back again to exit", Toast.LENGTH_SHORT).show();
                    warningBack = true;
                    // reset after delay
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            warningBack = false;
                        }
                    },2500);
                }
            }
            else{
                camBtn.performClick();
            }
        }
        else
            drawerView.setVisibility(INVISIBLE);
    }

    private void initDrawingArea() {
        // if (drawingArea == null) {
        //     drawingArea = (DrawingArea) findViewById(R.id.drawing_area);
        //     drawingArea.initTrailDrawer();
        // }
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        initDrawingArea();
        startCameraSource();
    }

    /** Stops the camera. */
    @Override
    protected void onPause() {
        super.onPause();
        // drawingArea.trimMemory();
        cameraPreview.stop();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(bitmapProcessor!=null)
            bitmapProcessor.stop();
        if (cameraSource != null) {
            cameraSource.release();
        }
    }
    private void stopCameraSource() {
        if (cameraSource != null) {
            cameraSource.release();
            cameraSource = null;
        }
    }
}

/*
* private Bitmap thresholdBitmap(Bitmap orig, int THR ){
        Bitmap bitmap = orig.copy(Bitmap.Config.ARGB_8888,true);
        for(int i=0;i<bitmap.getWidth();i++){
            for(int j=0;j<bitmap.getHeight();j++){
                if(colorVal(bitmap.getPixel(i,j)) < THR)
                    bitmap.setPixel(i,j, Color.BLACK);
                else
                    bitmap.setPixel(i,j, Color.WHITE);
            }
        }
        return bitmap;
    }
    private Bitmap openBinaryBitmap(Bitmap binaryBitmap,int KX,int KY) {
        Bitmap copyBitmap1  = dilateBinaryBitmap(binaryBitmap,KX,KY);
        // binaryBitmap.recycle();
        Bitmap copyBitmap2 = erodeBinaryBitmap(copyBitmap1,KX,KY);
        copyBitmap1.recycle();
        return copyBitmap2;
    }
    private Bitmap erodeBinaryBitmap(Bitmap binaryBitmap,int KX,int KY) {
        Bitmap copyBitmap = binaryBitmap.copy(Bitmap.Config.ARGB_8888,true);
        int currp, minp,w = binaryBitmap.getWidth(), h = binaryBitmap.getHeight(), mini,minj;
        // revolve kernel
        for(int i=0;i<w;i++){
            for(int j=0;j<h;j++){
                minp = colorVal(binaryBitmap.getPixel(i,j));
                mini = i;
                minj = j;
                for(int ki=i-KX/2;ki<=i+KX/2;ki++){
                    for(int kj=j-KY/2;kj<=j+KY/2;kj++){
                        if(ki > -1 && kj > -1 && ki < w && kj < h){
                            currp = colorVal(binaryBitmap.getPixel(ki,kj));
                            if(minp > currp){
                                minp = currp;
                                mini = ki;
                                minj = kj;
                            }
                        }
                    }
                }
                copyBitmap.setPixel(i,j, binaryBitmap.getPixel(mini,minj));
            }
        }
        Log.d(TAG,"Eroded: "+copyBitmap.getWidth());
        return copyBitmap;
    }
public void copyDetails(View view) {
        TextView t1,t2,t3,t4,t5,t6,t7,t8;
        t1 = findViewById(R.id.vehicle_name);
        t2 = findViewById(R.id.vehicle_owner);
        t3 = findViewById(R.id.vehicle_fuel);
        t4 = findViewById(R.id.vehicle_cc);
        t5 = findViewById(R.id.vehicle_engine);
        t6 = findViewById(R.id.vehicle_chasis);
        t7 = findViewById(R.id.vehicle_location);
        t8 = findViewById(R.id.vehicle_expiry);
        String s="Vehicle Number : "+vehicleNumber.getText().toString()+"\n Vehicle Name : "+t1.getText().toString()+"\n Owner Name : "+t2.getText().toString()+"\n Fuel Type : "+t3.getText().toString()+"\n Displacement : "+t4.getText().toString()+"\n Engine Number : "+t5.getText().toString()+"\n Chasis Number : "+t6.getText().toString()+"\n Location : "+t7.getText().toString()+"\n Expiry On : "+t8.getText().toString();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Vehicle details", s);
        clipboard.setPrimaryClip(clip);
        logToast("Text Copied! "+SMILE_EMOJI);
    }

    */
package com.example.ocr;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.ocr.Jsoup.GetCaptcha;
import com.example.ocr.Jsoup.Vehicle;

import java.io.IOException;
import java.util.List;

import com.example.ocr.text_detection.*;
import com.example.ocr.camera.*;
import com.example.ocr.graphics.*;
import com.example.ocr.utils.*;
import com.example.ocr.webscraper.*;
import com.google.firebase.ml.vision.text.FirebaseVisionText;

import br.vince.owlbottomsheet.OwlBottomSheet;

public class MainActivity extends AppCompatActivity {
    private final int OK = 200;
    private final int SOCKET_TIMEOUT = 408;
    private final int CAPTCHA_LOAD_FAILED = 999;
    private final int TECHNICAL_DIFFICULTY = 888;
    public final String NUMPLATE_PATTERN = "[A-Z]{2}[0-9]{2}[A-Z]+[0-9]{3}[0-9]+";

    //  ----- Instance Variables -----
    // private EditTextPicker vehicleNumber;

    // the bottom sheet
    private OwlBottomSheet mBottomSheet;
    private EditText vehicleNumber;
    private ImageView captchaImageView;
    private Button searchBtn;
    private View bottomSheetView;
    private View vehicleDetails;
    private CameraSource cameraSource = null;
    private CameraSourcePreview preview;
    private GraphicOverlay graphicOverlay;
    private Button camBtn;
    private EditText captchaInput;
    private ClipboardManager clipboard;
    private ImageButton imgbt;
    private WebScraper webScraper;
    private ImageBitmapGetter captchaBitmapGetter;
    public BitmapTextRecognizer bitmapProcessor;
    private SimplePermissions permHandler;

    private Element eltCaptchaImage;
    private Element eltVehicleNumber;
    private Element eltCaptchaInput;
    private Element eltSubmitBtn;
    private static String TAG = "MainActivity";
    private final String BASE_URL = "https://vahan.nic.in";
    private final String VEHICLE_URL="/nrservices/faces/user/searchstatus.xhtml";
    private final String FULL_URL=BASE_URL+VEHICLE_URL;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setBottomSheetView();

        bitmapProcessor = new BitmapTextRecognizer();
        clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);

        preview = findViewById(R.id.camera_source_preview);
        graphicOverlay = findViewById(R.id.graphics_overlay);
        captchaImageView = bottomSheetView.findViewById(R.id.captcha_img);
        vehicleDetails = bottomSheetView.findViewById(R.id.vehicle_details);
        searchBtn = bottomSheetView.findViewById(R.id.search_btn);
        camBtn = findViewById(R.id.cam_btn);
        setCaptchaInputListeners();
        setSearchButtonListeners();
        setVehicleNumListeners();

        webScraper = new WebScraper(this);
        captchaBitmapGetter = new ImageBitmapGetter();
        webScraper.setUserAgentToDesktop(true); //default: false
        webScraper.setLoadImages(true); //default: false

        // LinearLayout layout = (LinearLayout)mBottomSheet.findViewById(R.id.webview);
        // layout.addView(webScraper.getView());

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

            camBtn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if(cameraSource==null) {
                        startLoadingCaptcha();
                        createCameraSource();
                        startCameraSource();
                        camBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.bubble2));
                    }
                    else {
                        confirmVehicleNumber();
                        stopCameraSource();
                        camBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.bubble_pop2));
                    }
                    // camBtn.animate().setDuration(600).rotation(camBtn.getRotation() + 360).start();
                }
            });

            setFlashListeners();
            // default is start the camera
            camBtn.performClick();
        }
        else {
            Toast.makeText(MainActivity.this, "Please enable the permissions from settings. Exiting App!", Toast.LENGTH_LONG).show();
            MainActivity.this.finish();
        }
    }
    // basic usage
    private void setBottomSheetView() {
        mBottomSheet = findViewById(R.id.owl_bottom_sheet);
        //used to calculate some animations. it's required
        mBottomSheet.setActivityView(this);

        //icon to show in collapsed sheet
        mBottomSheet.setIcon(R.drawable.bubble2);

        //bottom sheet color
        mBottomSheet.setBottomSheetColor(ContextCompat.getColor(this,R.color.colorPrimaryDark));

        //view shown in bottom sheet
        mBottomSheet.attachContentView(R.layout.main_content);

        bottomSheetView = mBottomSheet.getContentView();
        //getting close button from view shown
        bottomSheetView.findViewById(R.id.vehicle_details_close_button)
                .setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        mBottomSheet.collapse();
                    }});
        mBottomSheet.performClick();
    }
    private String numPlateFilter(String s){
        return s.toUpperCase().replaceAll("[^A-Z0-9]","");
    }
    private String captchaFilter(String s){
        return s.replaceAll("[^a-zA-Z0-9]","");
    }

    private void bottomSheetOn() {
        mBottomSheet.setIcon(R.drawable.bubble2);
    }
    private void bottomSheetOff() {
        mBottomSheet.setIcon(R.drawable.bubble_pop2);
    }
    private void confirmVehicleNumber() {
        bottomSheetOn();
        if (cameraSource != null && cameraSource.frameProcessor.textBlocks != null) {
            vehicleNumber.setText(numPlateFilter(cameraSource.frameProcessor.majorText));
            List<FirebaseVisionText.TextBlock> textBlocks = cameraSource.frameProcessor.textBlocks;
            for (int i = 0; i < textBlocks.size(); i++) {
                // set vehicleNum here
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


    public class ImageBitmapGetter implements Img2Bitmap
    {
        @Override
        public void onConvertComplete(final byte[] mDecodedImage)
        {
            if (mDecodedImage == null || mDecodedImage.length == 0)
                return;
            final Bitmap bitmap = BitmapFactory.decodeByteArray(mDecodedImage, 0, mDecodedImage.length);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    captchaImageView.setImageBitmap(bitmap);
                }
            });
            // The image is about 120x40
            Bitmap captchaImage  =  resizeBitmap(bitmap, 84, 28);
            // Blur it out by resizing
            captchaImage = resizeBitmap(captchaImage, 400, 130);
            captchaImage = dilateBinaryBitmap(captchaImage,3,7); //ksize be odd
            // captchaImage = thresholdBitmap(captchaImage  ,300);
            final Bitmap processedCaptchaImage = resizeBitmap(captchaImage, 120, 40);
                // make listener for this.
            new Thread(new Runnable() {
                @Override
                public void run() {
                    bitmapProcessor.processBitmap(processedCaptchaImage);
                    Log.d(TAG,"Setting captcha text: "+bitmapProcessor.allText);
                    String detectedCaptcha = captchaFilter(bitmapProcessor.allText);
                    // if(captchaInput.getText().toString().equals(""))
                    captchaInput.setText(detectedCaptcha);
                    // processedCaptchaImage.recycle();
                }
            }).start();
            bottomSheetOff();
            logToast("Captcha Handle completed...");
        }
    }

    private int colorVal(int pixel){
        return (int)Math.sqrt(Math.pow(Color.red(pixel),2) +Math.pow(Color.green(pixel),2) +Math.pow(Color.blue(pixel),2));
    }
    private Bitmap thresholdBitmap(Bitmap orig, int THR ){
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


    private void startLoadingCaptcha() {
        bottomSheetOn();

        captchaInput.setText("");
        vehicleDetails.setVisibility(View.GONE);

        Log.d(TAG,"Started Loading Captcha");
        webScraper.loadURL(FULL_URL);
        webScraper.setOnPageLoadedListener(new WebScraper.onPageLoadedListener() {
            @Override
            public void loaded(String URL) {
                Log.d(TAG,"Loading page: "+URL);
                // GetCaptcha.bigLog( TAG, webScraper.getHtml());
                eltCaptchaImage = webScraper.findElementByClassName("captcha-image",0);
                eltVehicleNumber = webScraper.findElementById("regn_no1_exact");
                eltCaptchaInput = webScraper.findElementById("txt_ALPHA_NUMERIC");
                eltSubmitBtn = webScraper.findElementByClassName("ui-button ui-widget ui-state-default ui-corner-all ui-button-text-only",0);
                Log.d(TAG,"Loaded image from: "+eltCaptchaImage.getAttribute("src"));
                // webScraper.web.evaluateJavascript();
                eltCaptchaImage.callImageBitmapGetter(captchaBitmapGetter);
            }
        });
    }
    private void showVehicleDetails(Vehicle vehicle) {

        ((TextView)findViewById(R.id.vehicle_name)).setText(vehicle.getName());
        ((TextView)findViewById(R.id.vehicle_fuel)).setText(vehicle.getFuel());
        ((TextView)findViewById(R.id.vehicle_cc)).setText(vehicle.getCc());
        ((TextView)findViewById(R.id.vehicle_engine)).setText(vehicle.getEngine());
        ((TextView)findViewById(R.id.vehicle_chasis)).setText(vehicle.getChassis());
        ((TextView)findViewById(R.id.vehicle_owner)).setText(vehicle.getOwner());
        ((TextView)findViewById(R.id.vehicle_location)).setText(vehicle.getLocation());
        ((TextView)findViewById(R.id.vehicle_expiry)).setText(vehicle.getExpiry());
    }
    private void createCameraSource() {
        if (cameraSource == null) {
            cameraSource = new CameraSource(this, graphicOverlay);
            cameraSource.setFacing(CameraSource.CAMERA_FACING_BACK);
        }
        cameraSource.setMachineLearningFrameProcessor(new TextRecognitionProcessor());

        // THIS CAUSES INPUTS TO DESTROY AND CAUSE POSSIBLE CRASH
        // final Handler handler = new Handler();
        // Runnable runnable = new Runnable(){
        //     @Override
        //     public void run(){
        //         if (cameraSource != null) {
        //             vehicleNumber.setText(cameraSource.frameProcessor.majorText);
        //             handler.postDelayed(this,1000);
        // show drawer edge-
        // if(!captchaInput.getText().toString().equals(""))
        // drawerEdge.setVisible(true)

        //         }
        //     }
        // };
        // handler.post(runnable);
    }

    private void startCameraSource() {
        if (cameraSource != null) {
            try {
                if (preview == null) {
                    Log.d(TAG, "resume: Preview is null");
                }
                if (graphicOverlay == null) {
                    Log.d(TAG, "resume: graphOverlay is null");
                }
                preview.start(cameraSource, graphicOverlay);
            } catch (IOException e) {
                Log.e(TAG, "Unable to start camera source.", e);
                stopCameraSource();
            }
        }
    }
    private boolean flashOn=false;
    private void setFlashListeners(

    ) {
        Button flashBtn = findViewById(R.id.flash_btn);
        flashBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(flashOn) {
                    flashBtn.animate().scaleX(1/1.1f).scaleY(1/1.1f).start();
                    cameraSource.turnOffTheFlash();
                    flashBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.flash_off));
                }
                else {
                    flashBtn.animate().scaleX(1.1f).scaleY(1.1f).start();
                    cameraSource.turnOnTheFlash();
                    flashBtn.setBackground(ContextCompat.getDrawable(MainActivity.this,R.drawable.flash_on));
                }
                flashOn = !flashOn;
            }
        });
    }
    private void setCaptchaInputListeners() {
        captchaInput = bottomSheetView.findViewById(R.id.captcha_input);
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
                eltCaptchaInput.setText(captchaInput.getText().toString());
                // executes some js : and submits form
                eltSubmitBtn.click();
                // this listener is called on onPageFinished listener
                webScraper.setOnPageLoadedListener(new WebScraper.onPageLoadedListener() {
                    @Override
                    public void loaded(String URL) {
                        Log.d(TAG,"Done loading2 : \n\n\t"+URL);
                        //vehicleDetails
                        Toast.makeText(MainActivity.this, "Search completed...", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    private void setVehicleNumListeners() {

        vehicleNumber = bottomSheetView.findViewById(R.id.vehicle_plate);
        vehicleNumber.setFilters(new InputFilter[] {new InputFilter.AllCaps()});
        vehicleNumber.addTextChangedListener(new TextWatcher() {
            @Override public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
            @Override public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {}
            @Override public void afterTextChanged(Editable et) {
                searchBtn.setEnabled(et.toString().matches(NUMPLATE_PATTERN));

            }
        });
        searchBtn.setEnabled(vehicleNumber.getText().toString().matches(NUMPLATE_PATTERN));
        vehicleNumber.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(!hasFocus)
                    vehicleNumber.setText(numPlateFilter(vehicleNumber.getText().toString()));
            }
        });
    }

    public void copyNumber(View view) {
        String vehicleNum = numPlateFilter(vehicleNumber.getText().toString());
        Log.d(TAG,"Copying : "+vehicleNum);
        if(!vehicleNum.equals("")) {
            ClipData clip = ClipData.newPlainText("Vehicle Number", vehicleNum);
            clipboard.setPrimaryClip(clip);
            logToast("\ud83d\ude00 Text Copied:  "+vehicleNum);
        }
        else{
            logToast("\ud83d\ude36 Empty text");
        }
    }

    public void copyDetails(View view) {
        TextView t1,t2,t3,t4,t5,t6,t7,t8;
        t1 = bottomSheetView.findViewById(R.id.vehicle_name);
        t2 = bottomSheetView.findViewById(R.id.vehicle_owner);
        t3 = bottomSheetView.findViewById(R.id.vehicle_fuel);
        t4 = bottomSheetView.findViewById(R.id.vehicle_cc);
        t5 = bottomSheetView.findViewById(R.id.vehicle_engine);
        t6 = bottomSheetView.findViewById(R.id.vehicle_chasis);
        t7 = bottomSheetView.findViewById(R.id.vehicle_location);
        t8 = bottomSheetView.findViewById(R.id.vehicle_expiry);
        String s="Vehicle Number : "+vehicleNumber.getText().toString()+"\n Vehicle Name : "+t1.getText().toString()+"\n Owner Name : "+t2.getText().toString()+"\n Fuel Type : "+t3.getText().toString()+"\n Displacement : "+t4.getText().toString()+"\n Engine Number : "+t5.getText().toString()+"\n Chasis Number : "+t6.getText().toString()+"\n Location : "+t7.getText().toString()+"\n Expiry On : "+t8.getText().toString();

        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Vehicle details", s);
        clipboard.setPrimaryClip(clip);
        logToast("Text Copied! "+("\ud83d\ude00"));
    }

    private boolean warningBack = false;
    // collapse bottom sheet when back button pressed
    @Override
    public void onBackPressed() {
        if (!mBottomSheet.isExpanded()){
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
            mBottomSheet.collapse();
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        startCameraSource();
    }

    /** Stops the camera. */
    @Override
    protected void onPause() {
        super.onPause();
        preview.stop();
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
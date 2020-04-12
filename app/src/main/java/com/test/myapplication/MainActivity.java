package com.test.myapplication;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.Activity;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends AppCompatActivity {

    private WebView webView;

    // For save last URL
    private SharedPreferences preferences;

    // Using for upload images
    private ValueCallback<Uri[]> mFilePathCallback;
    private String mCameraPhotoPath;
    private static final int INPUT_FILE_REQUEST_CODE = 1;

    private static final String START_URL = "https://imgbb.com/";
    private static final String LAST_URL_KEY = "LAST_URL";
    private static final String TASK_URL_KEY = "TASK_URL";

    // Strings from database
    private String secret;
    private String splash_url;
    private String task_url;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        preferences = getPreferences(MODE_PRIVATE);

        setWebViewSettings();

        if(isHavingDefTask()){
            // Load last URL or deeplink URL
            webView.loadUrl(getUrl());
            showCurrentUrl(webView.getUrl());
        } else {
            databaseActions();
        }
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (requestCode != INPUT_FILE_REQUEST_CODE || mFilePathCallback == null) {
                super.onActivityResult(requestCode, resultCode, data);
                return;
            }
            Uri[] results = null;
            // Check that the response is a good one
            if (resultCode == Activity.RESULT_OK) {
                if (data == null) {
                    // If there is not data, then we may have taken a photo
                    if (mCameraPhotoPath != null) {
                        results = new Uri[]{Uri.parse(mCameraPhotoPath)};
                    }
                } else {
                    String dataString = data.getDataString();
                    if (dataString != null) {
                        results = new Uri[]{Uri.parse(dataString)};
                    }
                }
            }
            mFilePathCallback.onReceiveValue(results);
            mFilePathCallback = null;
        }
    }


    @Override
    protected void onPause() {
        super.onPause();
        saveUrl(webView.getUrl());
    }


    @Override
    public void onBackPressed() {
        if(webView.canGoBack()){
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }


    private void setWebViewSettings(){
        webView = findViewById(R.id.webview);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().supportZoom();

        webView.setWebViewClient(new WebViewClient(){
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Catching redirect

                showCurrentUrl(url);

                if(url.contains("main")){
                    // Open second activity
                    openTextActivity(secret);
                } else if(url.contains("money")){
                    // Save default task_url
                    saveTaskUrl(task_url);

                    // Check deeplink
                    if(getIntent() != null && getIntent().getData() != null) {
                        task_url = getTaskUrl();
                    }
                    webView.loadUrl(task_url);
                    showCurrentUrl(webView.getUrl());
                }

                return false;
            }
        });

        webView.setWebChromeClient(new WebChromeClient(){
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> filePath, WebChromeClient.FileChooserParams fileChooserParams) {
                // Checking that we don't have any existing callbacks
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePath;

                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    // Create the photoFile where the photo should go
                    File photoFile = null;
                    try {
                        photoFile = createImageFile();
                        takePictureIntent.putExtra("PhotoPath", mCameraPhotoPath);
                    } catch (IOException ex) {
                        // Error occurred while creating the photoFile
                    }

                    // Continue only if the photoFile was successfully created
                    if (photoFile != null) {
                        mCameraPhotoPath = "file:" + photoFile.getAbsolutePath();
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(photoFile));
                    } else {
                        takePictureIntent = null;
                    }
                }

                Intent contentSelectionIntent = new Intent(Intent.ACTION_GET_CONTENT);
                contentSelectionIntent.addCategory(Intent.CATEGORY_OPENABLE);
                contentSelectionIntent.setType("image/*");

                Intent[] intentArray;
                if (takePictureIntent != null) {
                    intentArray = new Intent[] {
                            takePictureIntent
                    };
                } else {
                    intentArray = new Intent[0];
                }

                Intent chooserIntent = new Intent(Intent.ACTION_CHOOSER);
                chooserIntent.putExtra(Intent.EXTRA_INTENT, contentSelectionIntent);
                chooserIntent.putExtra(Intent.EXTRA_TITLE, R.string.to_do_chooser);
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, intentArray);
                startActivityForResult(chooserIntent, INPUT_FILE_REQUEST_CODE);
                return true;
            }
        });
    }


    private File createImageFile() throws IOException {
        // Create image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp;
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);

        // Create image file
        File imageFile = File.createTempFile(imageFileName, ".jpg", storageDir);

        return imageFile;
    }


    private void saveUrl(String url){
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(LAST_URL_KEY, url);
        editor.apply();
    }


    private String getUrl(){
        String url = "";

        // Check deeplink
        if(getIntent() != null && getIntent().getData() != null) {
            // Get taskUrl from database with variable from deeplink
            url = getTaskUrl();
        } else {
            // Checking last url
            if(preferences.contains(LAST_URL_KEY)){
                url = preferences.getString(LAST_URL_KEY, "");
            } else {
                url = START_URL;
            }
        }

        return url;
    }


    private String getTaskUrl(){
        String task_url = "";

        if(preferences.contains(TASK_URL_KEY)) {
            // We having task url and can continue
            task_url = preferences.getString(TASK_URL_KEY, "");

            // Replace url-parameter with new value from deeplink
            Uri uri = getIntent().getData();
            String newDeep = uri.getQueryParameter("deep");
            task_url = task_url.replace("{deep}", newDeep);
        } else {
            // Error
            task_url = START_URL;
        }

        return  task_url;
    }


    private boolean isHavingDefTask(){
        boolean isHaving = false;
        if(preferences.contains(TASK_URL_KEY)) {
            isHaving = true;
        }
        return isHaving;

    }


    private void openTextActivity(String text){
        Intent intent = new Intent(this, TextActivity.class);

        // Transfer text from database to the Text Activity
        intent.putExtra("TEXT_TO_SET", text);

        startActivity(intent);
    }


    // Make some actions with database
    private void databaseActions(){
        DatabaseReference database = FirebaseDatabase.getInstance().getReference();
        database.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                // Get strings from database
                secret = dataSnapshot.child("secret").getValue().toString();
                splash_url = dataSnapshot.child("splash_url").getValue().toString();
                task_url = dataSnapshot.child("task_url").getValue().toString();

                // Redirecting to splash_url
                webView.loadUrl(splash_url);
                showCurrentUrl(webView.getUrl());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(MainActivity.this, "Database are unavailable", Toast.LENGTH_LONG).show();
            }
        });
    }


    private void saveTaskUrl(String task_url){
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(TASK_URL_KEY, task_url);
        editor.apply();
    }


    // Show toast with current url
    private void showCurrentUrl(String currentUrl){
        String text = getString(R.string.current_url) + " " + currentUrl;
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }


}
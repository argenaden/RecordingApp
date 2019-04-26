package com.example.testingjavacv;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.error.VolleyError;
import com.android.volley.request.SimpleMultiPartRequest;

import java.util.ArrayList;
import java.util.List;


public class SelectionActivity extends AppCompatActivity {
    public static String login_name;
    private Button recordBtn, btnUpload, btnChoose;
    private ImageView imageView;
    private List<String> list;
    private AlphaAnimation buttonClick = new AlphaAnimation(1F, 0.7F);
    static final int PICK_IMAGE_REQUEST = 1;
    String filePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selection);
        //Intent  intent = getIntent();
        //login_name = intent.getStringExtra("returnData");
        recordBtn = (Button) findViewById(R.id.record);
        btnUpload = (Button) findViewById(R.id.upload);
        btnChoose = (Button) findViewById(R.id.select);
        imageView = (ImageView) findViewById(R.id.img);

        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(buttonClick);
                startActivity(new Intent(SelectionActivity.this, MainActivity.class));

            }
        });



        btnChoose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                imageBrowse();
            }
        });

        btnUpload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (filePath != null) {
                    imageUpload(filePath);
                } else {
                    Toast.makeText(getApplicationContext(), "File not selected!", Toast.LENGTH_LONG).show();
                }

            }
        });
    }

    private void imageBrowse() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        // Start the Intent
        startActivityForResult(galleryIntent, PICK_IMAGE_REQUEST);
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == -1) {

            if(requestCode == PICK_IMAGE_REQUEST){
                Uri picUri = data.getData();

                filePath = getPath(picUri);

                Log.d("picUri", picUri.toString());
                Log.d("filePath", filePath);

                imageView.setImageURI(picUri);

            }

        }

    }

    private void imageUpload(final String imagePath) {

        String BASE_URL = "http://10.0.2.2:9090/api/files/uploadFile";
        SimpleMultiPartRequest smr = new SimpleMultiPartRequest(Request.Method.POST, BASE_URL,
                new Response.Listener<String>() {
                    @Override
                    public void onResponse(String response) {
                        Log.d("Response", response);

                    }
                }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                //  Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        smr.addFile("upload", imagePath);
        MyApplication.getInstance().addToRequestQueue(smr);

    }

    private String getPath(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        CursorLoader loader = new CursorLoader(getApplicationContext(), contentUri, proj, null, null, null);
        Cursor cursor = loader.loadInBackground();
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String result = cursor.getString(column_index);
        cursor.close();
        return result;
    }

    /*@Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == Activity.RESULT_OK) {
            if(data.getClipData() != null) {
                list = new ArrayList<>();
                int count = data.getClipData().getItemCount();
                int currentItem = 0;
                while(currentItem < count) {
                    Uri imageUri = data.getClipData().getItemAt(currentItem).getUri();
                    Uri selectedFiles = data.getData();
                    list.add(String.valueOf(selectedFiles));
                    currentItem = currentItem + 1;
                }
                Intent intent = new Intent(SelectionActivity.this, UploadFiles.class);
                intent.putStringArrayListExtra("files", (ArrayList<String>) list);
                startActivity(intent);

            } else if(data.getData() != null) {
                list = new ArrayList<>();
                String imagePath = data.getData().getPath();
                list.add(String.valueOf(imagePath));
                Intent intent = new Intent(SelectionActivity.this, UploadFiles.class);
                intent.putStringArrayListExtra("files", (ArrayList<String>) list);
                startActivity(intent);
            }
        }
    }*/
}

package com.example.testingjavacv;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class SelectionActivity extends AppCompatActivity {

    private AlphaAnimation buttonClick = new AlphaAnimation(1F, 0.6F);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_selection);

        Button recordBtn = (Button) findViewById(R.id.record);
        Button uploadButton = (Button) findViewById(R.id.upload);

        recordBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(buttonClick);

                startActivity(new Intent(SelectionActivity.this, MainActivity.class));
            }
        });

        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                v.startAnimation(buttonClick);
                Intent chooser = new Intent(Intent.ACTION_GET_CONTENT);
                Uri uri = Uri.parse(Environment.getDownloadCacheDirectory().getPath().toString());
                chooser.addCategory(Intent.CATEGORY_OPENABLE);
                chooser.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                chooser.setDataAndType(uri, "*/*");
                try {
                    startActivityForResult(chooser, 12);

                }
                catch (android.content.ActivityNotFoundException ex) {
                    Toast.makeText(SelectionActivity.this, "Please install a File Manager.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode == Activity.RESULT_OK) {
            List<String> list;
            if(data.getClipData() != null) {
                list = new ArrayList<>();
                int count = data.getClipData().getItemCount();
                int currentItem = 0;
                while(currentItem < count) {
                    Uri imageUri = data.getClipData().getItemAt(currentItem).getUri();
                    list.add(String.valueOf(imageUri));
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
    }
}

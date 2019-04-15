package com.example.testingjavacv;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

public class UploadFiles extends AppCompatActivity {
    private ArrayAdapter<String> adapter;


    private ListView listView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload_files);

        listView = (ListView) findViewById(R.id.listView);

        ArrayList<String> list = getIntent().getStringArrayListExtra("files");

        adapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_list_item_1, list);
        listView.setAdapter(adapter);
    }
}

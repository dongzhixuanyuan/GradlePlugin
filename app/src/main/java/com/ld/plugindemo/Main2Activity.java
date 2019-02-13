package com.ld.plugindemo;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.CharacterPickerDialog;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.ld.lib.Demo2;
import com.ld.lib.Test;

public class Main2Activity extends AppCompatActivity {

    private TextView view;

    public boolean jarTest = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main2);
        view = findViewById(R.id.tv_haha);
        view.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                Log.d("liudong", "haha");
            }
        });
        new Demo2().logTest();
        handleSomething();
    }

    public void handleSomething(){
        Log.d("liudong", "fffff");
    }
}

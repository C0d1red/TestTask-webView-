package com.test.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.EditText;

public class TextActivity extends AppCompatActivity {

    private EditText textField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text);

        textField = findViewById(R.id.editText);
        textField.setText(getIntent().getStringExtra("TEXT_TO_SET"));
    }
}

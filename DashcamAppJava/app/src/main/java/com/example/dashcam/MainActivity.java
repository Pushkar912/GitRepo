package com.example.dashcam;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    Button btnRecord, btnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRecord = findViewById(R.id.btnRecord);
        btnStop = findViewById(R.id.btnStop);

        btnRecord.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, DashcamService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
            btnRecord.setEnabled(false);
            btnStop.setEnabled(true);
        });

        btnStop.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, DashcamService.class);
            stopService(serviceIntent);
            btnRecord.setEnabled(true);
            btnStop.setEnabled(false);
        });
    }
}

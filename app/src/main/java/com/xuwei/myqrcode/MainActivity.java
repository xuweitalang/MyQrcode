package com.xuwei.myqrcode;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.xuwei.myqrcode.utils.Utils;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_SCAN = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    private void init() {
        findViewById(R.id.ll_scan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getRuntimePermission();
            }
        });
    }

    private void getRuntimePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = new String[]{Manifest.permission.CAMERA,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            if (checkSelfPermission(permissions[0]) == PackageManager.PERMISSION_DENIED ||
                    checkSelfPermission(permissions[1]) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(permissions, 200);
            } else {
                jumpScanPage();
            }
        }
    }

    //跳到扫码页面
    private void jumpScanPage() {
        startActivityForResult(new Intent(this, CaptureActivity.class), REQUEST_SCAN);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SCAN && resultCode == RESULT_OK) {
            Toast.makeText(this, data.getStringExtra(Utils.BAR_CODE), Toast.LENGTH_LONG).show();
        }
    }
}

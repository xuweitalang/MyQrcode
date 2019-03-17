package com.xuwei.myqrcode;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.google.zxing.Result;
import com.xuwei.myqrcode.utils.BeepManager;
import com.xuwei.myqrcode.utils.Utils;
import com.xuwei.qrcode_library.CaptureCallback;
import com.xuwei.qrcode_library.camera.CameraManager;
import com.xuwei.qrcode_library.decode.DecodeThread;
import com.xuwei.qrcode_library.utils.CaptureActivityHandler;
import com.xuwei.qrcode_library.utils.InactivityTimer;

public class CaptureActivity extends AppCompatActivity implements SurfaceHolder.Callback, CaptureCallback {
    private Context context; // 上下文
    private Activity activity;
    private TextView tvLight; // 开灯/关灯
    private SurfaceView scanPreview; // SurfaceView控件
    private RelativeLayout scanContainer; // 布局容器
    private RelativeLayout scanCropView; // 布局中的扫描框

    private boolean isPause; // 是否暂停
    private CaptureActivityHandler handler;
    private Rect mCropRect; // 矩形
    private CameraManager cameraManager; // 相机管理类
    private InactivityTimer inactivityTimer; // 计时器
    private BeepManager beepManager; // 蜂鸣器
    private ObjectAnimator objectAnimator; // 属性动画
    private boolean isHasSurface; // SurfaceView控件是否存在，surfaceCreated

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //保持屏幕常亮
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_capture);
        context = this;
        activity = this;
        initView();
        initScan();
    }

    private void initView() {
        tvLight = findViewById(R.id.tv_light);
        ToggleButton tbLight = findViewById(R.id.tb_light);
        //闪光灯控制
        tbLight.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Utils.openFlashlight(cameraManager);
                    tvLight.setText("关灯");
                } else {
                    Utils.closeFlashlight();
                    tvLight.setText("开灯");
                }
            }
        });

        findViewById(R.id.ll_album).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //打开相册，要做权限判断
                Utils.openAlbum(activity);
            }
        });
    }

    //扫描初始化
    private void initScan() {
        scanPreview = findViewById(R.id.capture_preview);
        scanContainer = findViewById(R.id.capture_container);
        scanCropView = findViewById(R.id.capture_crop_view);
        ImageView scanLine = findViewById(R.id.scan_line);

        //线性扫描动画（属性动画可暂停）
        float curTranslationY = scanLine.getTranslationY();
        objectAnimator = ObjectAnimator.ofFloat(scanLine, "translationY", curTranslationY,
                Utils.dp2px(this, 170));
        objectAnimator.setDuration(4000); //动画持续时间
        objectAnimator.setInterpolator(new LinearInterpolator()); //线性动画LinearInterpolator 匀速
        objectAnimator.setRepeatCount(ObjectAnimator.INFINITE); //动画执行次数
        objectAnimator.setRepeatMode(ValueAnimator.RESTART); //动画如何重复

    }

    @Override
    protected void onResume() {
        super.onResume();
        startScan();
    }

    //开始扫描
    private void startScan() {
        inactivityTimer = new InactivityTimer(this);
        beepManager = new BeepManager(this);
        if (isPause) {
            //如果是暂停，扫描动画应该要暂停
            objectAnimator.resume(); //objectAnimator.resume():继续一个暂停的动画，它会让动画从暂停时的点继续。
            isPause = false;
        } else {
            objectAnimator.start(); //扫描动画开始
        }

        //初始化相机管理类
        cameraManager = new CameraManager(context);
        handler = null; //重置handler（因为之前可能坐过一些扫描的回调，这些需要把之前携带的对象置空还原）
        if (isHasSurface) {
            initCamera(scanPreview.getHolder());
        } else {
            //等待surfaceCreated来初始化相机
            scanPreview.getHolder().addCallback(this);
        }
        //开启计时器
        inactivityTimer.onResume();
    }

    @Override
    protected void onPause() {
        pauseScan();
        super.onPause();

    }

    //暂停扫描
    private void pauseScan() {
        if (handler != null) {
            //退出同步并置空
            handler.quitSynchronously();
            handler = null;
        }
        //计时器暂停
        inactivityTimer.onPause();
        //关闭蜂鸣器
        beepManager.close();
        //关闭相机管理器的驱动
        cameraManager.closeDriver();
        if (!isHasSurface) {
            //remove 等待
            scanPreview.getHolder().removeCallback(this);
        }
        //暂停动画
        objectAnimator.pause();
        isPause = true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        if (holder == null) {
            Log.e("qrcode>>> ", "surfaceHolder is null");
        }
        if (!isHasSurface) {
            isHasSurface = true;
            initCamera(holder);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        isHasSurface = false;
    }

    @Override
    public Rect getCropRect() {
        return mCropRect;
    }

    @Override
    public Handler getHandler() {
        return handler;
    }

    @Override
    public CameraManager getCameraManager() {
        return cameraManager;
    }

    @Override
    public void handleDecode(Result result, Bundle bundle) {
        //扫描成功后的回调
        inactivityTimer.onActivity();
        beepManager.playBeepSoundAndVibrate(); //播放蜂鸣声
        //将扫描的结果返回
        Intent intent = new Intent();
        intent.putExtra(Utils.BAR_CODE, result.getText());
        setResult(RESULT_OK, intent);
        finish();
    }

    //初始化相机
    private void initCamera(SurfaceHolder surfaceHolder) {
        if (surfaceHolder == null) {
            throw new IllegalStateException("SurfaceHolder is null");
        }
        if (cameraManager.isOpen()) {
            Log.e("qrcode>>> ", "camera is open");
            return;
        }

        try {
            cameraManager.openDriver(surfaceHolder);
            if (handler == null) {
                handler = new CaptureActivityHandler(this, cameraManager, DecodeThread.ALL_MODE);
            }
            initCrop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //初始化截取的矩形区域
    private void initCrop() {
        //获取相机的宽高
        int cameraWidth = cameraManager.getCameraResolution().x;
        int cameraHeight = cameraManager.getCameraResolution().y;
        //获取布局中扫描框的位置信息
        int[] location = new int[2];
        scanPreview.getLocationInWindow(location);
        int cropLeft = location[0];
        int cropTop = location[1] - Utils.getStatusBarHeight(context);

        //获取截取的宽高
        int cropWidth = scanCropView.getWidth();
        int cropHeight = scanCropView.getHeight();

        //获取布局容器的宽高
        int containerWidth = scanContainer.getWidth();
        int containerHeight = scanContainer.getHeight();

        // 计算最终截取的矩形的左上角顶点x坐标
        int x = cropLeft * cameraWidth / containerWidth;
        // 计算最终截取的矩形的左上角顶点y坐标
        int y = cropTop * cameraHeight / containerHeight;

        // 计算最终截取的矩形的宽度
        int width = cropWidth * cameraWidth / containerWidth;
        // 计算最终截取的矩形的高度
        int height = cropHeight * cameraHeight / containerHeight;

        // 生成最终的截取的矩形
        mCropRect = new Rect(x, y, width + x, height + y);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        //相册返回
        if (requestCode == Utils.SELECT_PIC_KITKAT && resultCode == RESULT_OK) {
            Uri uri = data.getData();
            String path = Utils.getPath(this, uri);
            Result result = Utils.scanningImage(path);
            if (result == null) {
                Toast.makeText(context, "未发现二维码/条形码", Toast.LENGTH_LONG).show();
            } else {
                // 数据返回
                String recode = Utils.recode(result.toString());
                Toast.makeText(context, recode, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        // 关闭计时器
        inactivityTimer.shutdown();
        if (objectAnimator != null) {
            // 结束动画
            objectAnimator.end();
        }
        super.onDestroy();
    }

}

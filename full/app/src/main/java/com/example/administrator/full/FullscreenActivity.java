package com.example.administrator.full;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.annotation.IdRes;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View.OnClickListener;

import android.util.Log;

import com.example.x6.serial.SerialPort;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Time;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    int count = 1;
    int u16Month = 5;
    int u16Day = 20;
    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private SerialPort serialttyS1;
    private InputStream ttyS1InputStream;
    private OutputStream ttyS1OutputStream;
    int index = 1;

    /*
    * 注意 16进制数组必是两位，且要大写
    *
    **/
    public static byte[] hex2byte(String hex) {
        String digital = "0123456789ABCDEF";
        String hex1 = hex.replace(" ", "");
        char[] hex2char = hex1.toCharArray();
        byte[] bytes = new byte[hex1.length() / 2];
        byte temp;
        for (int p = 0; p < bytes.length; p++) {
            temp = (byte) (digital.indexOf(hex2char[2 * p]) * 16);
            temp += digital.indexOf(hex2char[2 * p + 1]);
            bytes[p] = (byte) (temp & 0xff);
        }
        return bytes;
    }

    /*
        * 将数组封装成帧
        * 每一个数据帧由以下几个部分组成
        * 1)数据包头部 head 0XAA
        * 2)数据包命令 CMD  0X01  星座选择
        * 3)数据个数     length of data 3   arg 0 ~ 3
        * 4)校验和         H8/L8 byte of  check sum(取8位)
        * 5)数据结尾标志 tail OX30
        * 6)可采用线程进行获取当前时间
        */
    public byte[] makeStringtoFramePackage()
    {
        //在时间byte[]前后添加一些package校验信息
        int dataLength   = 8;
        byte CheckSum     = 0;
        byte[] terimalPackage=new byte[dataLength];

        //装填信息
        //时间数据包之前的信息
        terimalPackage[0] = (byte)0xAA;
        terimalPackage[1] = (byte)0x02;         //cmd 命令 01 上升 02 是下降
        terimalPackage[2] = (byte)0x03;         //包体大小
        terimalPackage[3] = (byte)index;       //arg0  代表第几个星座
        terimalPackage[4] = (byte)0x00;         //arg1
        terimalPackage[5] = (byte)0x00;         //arg2 默认

        //计算校验和
        //转化为无符号进行校验
        for(int dataIndex = 1; dataIndex < terimalPackage.length - 2; dataIndex++)
        {
            CheckSum += terimalPackage[dataIndex];
        }
        terimalPackage[6] = CheckSum;
        //数据包结尾
        terimalPackage[7]=0X55;
        return terimalPackage;
    }

    private String getAstro(int month, int day) {

        String[] astro = new String[]{"摩羯座","水瓶座","双鱼座","白羊座","金牛座","双子座","巨蟹座","狮子座","处女座","天秤座","天蝎座","射手座"};
        int[] arr = new int[]       {    20,      19,       21,         20,     21,      22,      23,      23,        23,      24,       23,       22};// 两个星座分割日
        // 所查询日期在分割日之前，索引-1，否则不变
        index = month;
        if (day < arr[month - 1]) {
            index = month - 1;
        }
        if(index == 12)
        {
            index = 0;
        }

/* 打开串口 */
        try {
            serialttyS1 = new SerialPort(new File("/dev/ttyS0"),9600,0);
            ttyS1InputStream = serialttyS1.getInputStream();
            ttyS1OutputStream = serialttyS1.getOutputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }

        byte [] SendPackData = makeStringtoFramePackage();
/* 串口发送字节 */
        try {
             ttyS1OutputStream.write(SendPackData);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return astro[index];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_fullscreen);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);

        final int image[] = new int[]{
                R.drawable.d1,
                R.drawable.d2,
                R.drawable.d3,
                R.drawable.d4,
                R.drawable.d5,
                R.drawable.d6,
                R.drawable.d7,
                R.drawable.d8,
                R.drawable.d9,
                R.drawable.d10,
                R.drawable.d11,
                R.drawable.d12,
        };
        final ImageView imageView = (ImageView) findViewById(R.id.imageView);

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        Button sanButton = (Button) findViewById(R.id.button2);

        Bundle bundle = this.getIntent().getExtras();
        index = bundle.getInt("index");
        imageView.setImageResource(image[index]);

        sanButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                TimerTask task = new TimerTask() {
                    @Override
                    public void run() {
                        /**
                         *要执行的操作
                         */
                        Intent intent = new Intent(FullscreenActivity.this, FullscreenActivity0.class);
                        Bundle bundle = new Bundle();
                        bundle.putInt("index", index);
                        intent.putExtras(bundle);
                        startActivity(intent);
                        finish();
                    }
                };
                getAstro(1 , 1); //发送下降命令
                Timer timer = new Timer();
                timer.schedule(task, 6000);//3秒后执行TimeTask的run方法
            }
        });
        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        hide();
    }

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        //mHideHandler.removeCallbacks(mHidePart2Runnable);
        // mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }



    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }



}

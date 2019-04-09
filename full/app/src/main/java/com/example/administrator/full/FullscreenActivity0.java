package com.example.administrator.full;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.x6.serial.SerialPort;
import com.itheima.wheelpicker.WheelPicker;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity0 extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //隐藏标题栏以及状态栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        /**标题是属于View的，所以窗口所有的修饰部分被隐藏后标题依然有效,需要去掉标题**/

        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_fullscreen0);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);


        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        findViewById(R.id.dummy_button).setOnTouchListener(mDelayHideTouchListener);
    }

    private SerialPort serialttyS1;
    private InputStream ttyS1InputStream;
    private OutputStream ttyS1OutputStream;
    int index = 1;
    int count = 1;
    int u16Month = 8;
    int u16Day = 8;
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
        terimalPackage[1] = (byte)0x01;         //cmd 命令
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
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        List<Integer> listData = new ArrayList<Integer>();
        List<Integer> listMonth = new ArrayList<Integer>();
        listData.add(1);
        listData.add(2);
        listData.add(3);
        listData.add(4);
        listData.add(5);
        listData.add(6);
        listData.add(7);
        listData.add(8);
        listData.add(9);
        listData.add(10);
        listData.add(11);
        listData.add(12);
        listData.add(13);
        listData.add(14);
        listData.add(15);
        listData.add(16);
        listData.add(17);
        listData.add(18);
        listData.add(19);
        listData.add(20);
        listData.add(21);
        listData.add(22);
        listData.add(23);
        listData.add(24);
        listData.add(25);
        listData.add(26);
        listData.add(27);
        listData.add(28);
        listData.add(29);
        listData.add(30);
        listData.add(31);
        //月
        listMonth.add(1);
        listMonth.add(2);
        listMonth.add(3);
        listMonth.add(4);
        listMonth.add(5);
        listMonth.add(6);
        listMonth.add(7);
        listMonth.add(8);
        listMonth.add(9);
        listMonth.add(10);
        listMonth.add(11);
        listMonth.add(12);

        //com.itheima.wheelpicker.WheelPicker
        final WheelPicker pickMonth = (WheelPicker)findViewById(R.id.wheelPickMonth);
        pickMonth.setData(listMonth);
        pickMonth.setItemTextSize(70);
        pickMonth.setSelectedItemPosition(u16Month -1);

        final WheelPicker pickData = (WheelPicker)findViewById(R.id.wheelPickData);
        pickData.setData(listData);
        pickData.setItemTextSize(70);
        pickData.setSelectedItemPosition(u16Day - 1);

        Button sanButton = (Button) findViewById(R.id.screen0_entry);
        sanButton.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v){
                u16Month = pickMonth.getCurrentItemPosition() + 1;
                u16Day   = pickData.getCurrentItemPosition() + 1;
                getAstro(u16Month, u16Day);
                Intent intent = new Intent(FullscreenActivity0.this, FullscreenActivity.class);
                Bundle bundle = new Bundle();
                bundle.putInt("index", index);
                intent.putExtras(bundle);
                startActivity(intent);
                finish();
            }
        });

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
       // mHideHandler.removeCallbacks(mHidePart2Runnable);
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

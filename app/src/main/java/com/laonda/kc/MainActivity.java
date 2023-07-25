package com.laonda.kc;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.JsonReader;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.laonda.kckit.KCConfig;
import com.laonda.kckit.KCSessionManager;
import com.laonda.kckit.KCType;
import com.laonda.kckit.KCUser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private String TEST_ROOM_ID = "shawn";

    private Boolean isPublish = false;
    private Boolean isJoined = false;
    private Boolean isSpeaker = false;

    private KCConfig config = KCConfig.getInstance();

    private KCSessionManager kcSessionManager = KCSessionManager.getInstance();

    private Map<String, View> renders = new HashMap<String, View>();

    private KCSessionManager.KCManagerListener listener = new KCSessionManager.KCManagerListener() {
        @Override
        public void onAddStream(KCSessionManager manager, String roomId, String userId, int mediaType) {
            System.out.println("onAddStream");
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    if (mediaType > KCType.KCMediaType.KC_AUDIO) {
                        View view = (View) manager.getRender(TEST_ROOM_ID, userId);
                        renders.put(userId, view);
                        alignRender();
                    }
                }
            });
        }

        @Override
        public void onRemoveStream(KCSessionManager manager, String roomId, String userId) {
            System.out.println("didLeaveRoom");
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    removeRender(userId);
                }
            });
        }

        @Override
        public void didOccurError(KCSessionManager manager, int errorCode, String errorMessage) {
            System.out.println("didOccurError" + errorCode + " " + errorMessage);
            if (errorCode == KCType.KCError.KC_ERROR_NEED_REFRESH_PUB) {
                MainActivity.this.runOnUiThread(new Runnable() {
                    public void run() {
                        LinearLayout mainLayout = (LinearLayout) findViewById(R.id.scrollLinear);
                        mainLayout.removeView(renders.get(config.user));
                        renders.remove(config.user);

                        alignRender();
                        kcSessionManager.stopPreview(TEST_ROOM_ID);
                        kcSessionManager.unpublish(TEST_ROOM_ID, config.user);
                        isPublish = false;
                    }
                });
            }
        }

        @Override
        public void didRecvRoomInfo(KCSessionManager manager, String roomId, ArrayList<KCUser> users) {

            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    ArrayList<String> arPresenter = new ArrayList<String>();

                    for (KCUser user : users) {
                        Log.d(TAG, "didRecvRoomInfo] room:"+ roomId + "user:" + user.userId + "userType:" + user.userType+ "stMic:" + user.stMic + "stCam:" + user.stCam + "stScreen:" + user.stScreen + "stData:" + user.stData);

                        if (user.userId.equals(config.user) && (user.userType == KCType.KCUserType.KC_USER_VIEWER)) {
                            if (isJoined == false) {
                                isJoined = true;
                            }
                        }

                        if ((user.userType == KCType.KCUserType.KC_USER_PRESENTER) && !(user.userId.equals(config.user)))
                        {
                            arPresenter.add(user.userId);
                        }
                    }

                    if(arPresenter.size() > 0)
                    {
                        manager.subscribeUsers(TEST_ROOM_ID, arPresenter);
                    }
                }
            });
        }

        @Override
        public void didChangeAudioLevel(KCSessionManager manager, String userId, double audioLevel) {
//            System.out.println("didChangeAudioLevel:"+userId+" "+audioLevel);
        }

        @Override
        public void didChangeVideoSize(KCSessionManager manager, String userId, int width, int height) {
            System.out.println("didChangeVideoSize:" + userId + " " + width + " " + height);
            MainActivity.this.runOnUiThread(new Runnable() {
                public void run() {
                    alignRender();
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setPermission();

        config.countryCode = getApplicationContext().getResources().getConfiguration().locale.getCountry();
        config.appID = "432e409445f9ab18a74d5c604c97addf";
        config.apiToken = "klang";
        config.sessionNode = "ws://3.37.135.207:7780/ws";
        config.user = "shawnAndroid";
        config.context = getApplicationContext();
        config.captureWidth = 640;
        config.captureHeight = 480;
        config.captureFps = 15;
        config.isFrontCamera = true;

        config.enableHWCodec = true;
        config.enableAEC = true;
        config.enableAGC = true;
        config.enableNS = true;
        config.enableHPF = true;
        config.maxBitrate = 500;

//        getServiceInfo();

        kcSessionManager.initWithConfig(config, listener);

        Button.OnClickListener onClickListener = new Button.OnClickListener() {
            @Override
            public void onClick(View view) {

                switch (view.getId()) {
                    case R.id.btnJoin:
                        getToken();
                        break ;
                    case R.id.btnLeave:
                        kcSessionManager.stopPreview(TEST_ROOM_ID);
                        kcSessionManager.leaveRoom(TEST_ROOM_ID);
                        isJoined = false;
                        clearRender();
                        break ;
                    case R.id.btnUnPub:

                        if (isPublish == true) {
                            LinearLayout mainLayout = (LinearLayout) findViewById(R.id.scrollLinear);
                            mainLayout.removeView(renders.get(config.user));
                            renders.remove(config.user);

                            alignRender();
                            kcSessionManager.stopPreview(TEST_ROOM_ID);
                            kcSessionManager.unpublish(TEST_ROOM_ID, config.user);
                            isPublish = false;

                        } else {
                            kcSessionManager.publish(TEST_ROOM_ID, KCType.KCMediaType.KC_AUDIO_VIDEO);
                            isPublish = true;

                            View localrender = (View) kcSessionManager.getRender(TEST_ROOM_ID, config.user);
                            renders.put(config.user, localrender);
                            alignRender();

                            kcSessionManager.startPreview(TEST_ROOM_ID);
                            kcSessionManager.resumeRecording(TEST_ROOM_ID, KCType.KCMediaType.KC_AUDIO);
                            kcSessionManager.resumeRecording(TEST_ROOM_ID, KCType.KCMediaType.KC_VIDEO);
                        }
                        break ;
                    case R.id.btnUnsubscribe:
                        ArrayList<String> arPresenter = new ArrayList<String>();
                        for (String key : renders.keySet()) {
                            if (!key.equals(config.user))
                                arPresenter.add(key);
                        }
                        kcSessionManager.unsubscribeUsers(TEST_ROOM_ID, arPresenter);
                        break ;

                    case R.id.btnCamera:
                        kcSessionManager.switchCamera(TEST_ROOM_ID);
                        break ;

                    case R.id.btnSpeaker:
                        if (isSpeaker == false) {
                            kcSessionManager.enableSpeaker();
                            isSpeaker = true;
                        } else {
                            kcSessionManager.disableSpeaker();
                            isSpeaker = false;
                        }
                        break ;
                }
            }
        } ;

        Button btnJoin = (Button) findViewById(R.id.btnJoin) ;
        btnJoin.setOnClickListener(onClickListener) ;
        Button btnLeave = (Button) findViewById(R.id.btnLeave) ;
        btnLeave.setOnClickListener(onClickListener) ;
        Button btnUnsub = (Button) findViewById(R.id.btnUnsubscribe) ;
        btnUnsub.setOnClickListener(onClickListener) ;
        Button btnPub = (Button) findViewById(R.id.btnUnPub) ;
        btnPub.setOnClickListener(onClickListener) ;
        Button btnCamera = (Button) findViewById(R.id.btnCamera) ;
        btnCamera.setOnClickListener(onClickListener) ;
        Button btnSpeaker = (Button) findViewById(R.id.btnSpeaker) ;
        btnSpeaker.setOnClickListener(onClickListener) ;
    }

    private void setPermission() {
        ArrayList deniedPermissions = new ArrayList();
        String[] permissions;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = new String[] {
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.CAMERA
            };
        } else {
            permissions = new String[] {
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.CAMERA
            };
        }

        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_DENIED) {
                deniedPermissions.add(permission);
            }
        }

        if (deniedPermissions.size() > 0) {
            ActivityCompat.requestPermissions(this, (String[]) deniedPermissions.toArray(new String[deniedPermissions.size()]), 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        for (int result : grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(getApplicationContext(), "필수 권한이 허용되지 않아 앱을 종료합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void alignRender() {
        LinearLayout mainLayout = (LinearLayout) findViewById(R.id.scrollLinear);
        for (String key : renders.keySet()) {
            renders.get(key).setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                    1200));

            mainLayout.removeView(renders.get(key));
            mainLayout.addView(renders.get(key));

            if (key.equals(config.user)) {
            }
        }
    }

    private void removeRender(String userId) {
        LinearLayout mainLayout = (LinearLayout) findViewById(R.id.scrollLinear);
        mainLayout.removeView(renders.get(userId));
        renders.remove(userId);
        alignRender();
    }

    private void clearRender() {
        LinearLayout mainLayout = (LinearLayout) findViewById(R.id.scrollLinear);
        for (String key : renders.keySet()) {
            mainLayout.removeView(renders.get(key));
        }
        renders.clear();
    }

    private void getToken() {

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String urlstr = "https://jwt.klang.network:8008/rtc-token?room_id="+TEST_ROOM_ID+"&user_id="+config.user;
                    URL url =  new URL(urlstr);
                    HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type", "application/json");
                    conn.setRequestProperty("Accept", "application/json");
                    conn.setDoOutput(true);
                    conn.setDoInput(true);

//                    JSONObject json = new JSONObject();
//                    json.put("app_id", TEST_APP_ID);
//                    json.put("secret_key", TEST_SECRET_KEY);
//                    json.put("room_id", TEST_ROOM_ID);
//                    json.put("login_id", config.user);
//
//                    OutputStream outputStream;
//                    outputStream = conn.getOutputStream();
//                    outputStream.write(json.toString().getBytes());
//                    outputStream.flush();

                    // 실제 서버로 Request 요청 하는 부분 (응답 코드를 받음, 200은 성공, 나머지 에러)
                    int response = conn.getResponseCode();
                    String responseMessage =  conn.getResponseMessage();

                    InputStream responseBody = conn.getInputStream();
                    InputStreamReader responseBodyReader = new InputStreamReader(responseBody, "UTF-8");
                    JsonReader jsonReader = new JsonReader(responseBodyReader);
                    jsonReader.beginObject(); // Start processing the JSON object
                    while (jsonReader.hasNext()) { // Loop through all keys
                        String key = jsonReader.nextName(); // Fetch the next key
                        if (key.equals("jwt")) { // Check if desired key
                            config.apiToken = jsonReader.nextString();
                        } else {
                            jsonReader.skipValue(); // Skip values of other keys
                        }
                    }
                    conn.disconnect();

                    kcSessionManager.initWithConfig(config, listener);
                    kcSessionManager.joinRoomByToken(config.apiToken,TEST_ROOM_ID, config.user, KCType.KCMediaType.KC_AUDIO_VIDEO);
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }
}
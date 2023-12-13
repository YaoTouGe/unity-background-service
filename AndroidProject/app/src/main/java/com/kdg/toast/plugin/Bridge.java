package com.kdg.toast.plugin;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.HardwareBuffer;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.PowerManager;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;
import java.util.Arrays;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import static android.Manifest.permission.ACTIVITY_RECOGNITION;
import static androidx.core.app.ActivityCompat.requestPermissions;

public final class Bridge extends Application {
    static int summarySteps;
    static int steps;
    static int initialSteps;
    static Activity myActivity;
    static Context appContext;
    static ServiceConnection aidlConn;
    static RenderServiceResources aidlInterface;
    Date currentDate;
    static final String STEPS="steps";
    static final String SUMMARY_STEPS="summarySteps";
    static final String DATE="currentDate";
    static final String INIT_DATE="initialDate";

    static HardwareBuffer colorBuffer = null;
    static int colorWidth;
    static int colorHeight;
    public static final Intent[] POWERMANAGER_INTENTS = new Intent[]{
            new Intent().setComponent(new ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
            new Intent().setComponent(new ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity")),
            new Intent().setComponent(new ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity")),
            new Intent().setComponent(new ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager")),
            new Intent().setComponent(new ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
            new Intent().setComponent(new ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
            new Intent().setComponent(new ComponentName("com.htc.pitroad", "com.htc.pitroad.landingpage.activity.LandingPageActivity")),
            new Intent().setComponent(new ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.MainActivity"))
    };

    private static native int HardwareBufferToGLTexture(HardwareBuffer buffer);

    public static int GetTextureNativeHandle()
    {
        if (colorBuffer == null)
        {
            //Log.e("[render service]", "hardware buffer is null!");
            return -1;
        }
        return HardwareBufferToGLTexture(colorBuffer);
    }
    public static void ReceiveActivityInstance(Activity tempActivity) {
        myActivity = tempActivity;
        String[] perms= new String[1];
        perms[0]=Manifest.permission.ACTIVITY_RECOGNITION;
        if (ContextCompat.checkSelfPermission(myActivity, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.i("PEDOMETER", "Permision isnt granted!");
            ActivityCompat.requestPermissions(Bridge.myActivity,
                    perms,
                    1);
        }
    }

    public static void StartService() {
        if (myActivity != null) {
            final SharedPreferences sharedPreferences = myActivity.getSharedPreferences("service_settings", MODE_PRIVATE);
            if (!sharedPreferences.getBoolean("auto_start", false)) {
                for (final Intent intent : POWERMANAGER_INTENTS) {
                    if (myActivity.getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null) {
                        AlertDialog alertDialog = new AlertDialog.Builder(myActivity).create();
                        alertDialog.setTitle("Auto start is required");
                        alertDialog.setMessage("Please enable auto start to provide correct work");
                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        sharedPreferences.edit().putBoolean("auto_start", true).apply();
                                        myActivity.startActivity(intent);
                                    }
                                });
                        alertDialog.show();
                        break;
                    }
                }
            }
            start();
        }
        else{
            start();
        }
    }

    private static HardwareBufferCallback callback = new HardwareBufferCallback.Stub() {
        @Override
        public void ObtainHardwareBuffer(HardwareBuffer hwb, int width, int height) throws RemoteException {
            colorBuffer = hwb;
            colorWidth = width;
            colorHeight = height;
            Log.e("[render service]", "Receive call back hardware buffer to client " + colorBuffer + " size " + colorWidth + " " + colorHeight);
        }
    };

    private static void start(){
        //myActivity.startForegroundService(new Intent(myActivity, PedometerService.class));
        // pass the shared resource to the other process using parcelable
        Intent newIntent = new Intent(myActivity, RenderService.class);
        newIntent.putExtra(Intent.EXTRA_TEXT, "start");
        if (aidlConn == null)
        {
            aidlConn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d("[render service]", "onServiceConnected");
                    aidlInterface = RenderServiceResources.Stub.asInterface(service);
                    try {
                        aidlInterface.RegisterHardwareBufferCallback(callback);
                    }
                    catch (RemoteException e)
                    {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d("[render service]", "onServiceDisconnected");
                }
            };
        }
        /*GLResources resource = new GLResources(
                colorBuffer,
                RenderService.colorWidth,
                RenderService.colorHeight);*/

        if (isMyServiceRunning(RenderService.class))
        {
            myActivity.bindService(newIntent, aidlConn, BIND_AUTO_CREATE | BIND_IMPORTANT);
            Toast.makeText(myActivity, "RenderService already running!", Toast.LENGTH_SHORT).show();
        }
        else
        {
            myActivity.bindService(newIntent, aidlConn, BIND_AUTO_CREATE | BIND_IMPORTANT);
            myActivity.startForegroundService(newIntent);
            //myActivity.startService(newIntent);
        }
    }

    private static boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) myActivity.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static void StopService(){
        //Intent serviceIntent = new Intent(myActivity, PedometerService.class);
        aidlInterface = null;
        myActivity.unbindService(aidlConn);
        aidlConn = null;

        Intent serviceIntent = new Intent(myActivity, RenderService.class);
        serviceIntent.putExtra(Intent.EXTRA_TEXT, "stop");
        myActivity.startForegroundService(serviceIntent);

    }
    public static int GetCurrentSteps(){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        Date currentDate = Calendar.getInstance().getTime();
        editor.putString(DATE, currentDate.toString());
        int walkedSteps = sharedPreferences.getInt(STEPS, 0);
        int allSteps = sharedPreferences.getInt(SUMMARY_STEPS,0);
        summarySteps=walkedSteps+allSteps;
        Log.i("PEDOMETER", "FROM BRIDGE CLASS - GetCurrentSteps:"+summarySteps);
        return summarySteps;
    }
    public static String SyncData(){
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(appContext);
        int stepsToSend=GetCurrentSteps();
        String firstDate = sharedPreferences.getString(INIT_DATE,"");
        String lastDate = sharedPreferences.getString(DATE,"");
        String data = firstDate+'#'+lastDate+'#'+stepsToSend;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt(STEPS,0);
        editor.putInt(SUMMARY_STEPS,0);
        steps=0;
        summarySteps=0;
        initialSteps=0;
        Date currentDate = Calendar.getInstance().getTime();
        editor.putString(INIT_DATE,currentDate.toString());
        editor.putString(DATE,currentDate.toString());
        editor.apply();
        Log.i("PEDOMETER", "SyncData: "+steps+' '+summarySteps+data);
        return data;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        System.loadLibrary("EGLNative");
        Bridge.appContext=getApplicationContext();

    }
}

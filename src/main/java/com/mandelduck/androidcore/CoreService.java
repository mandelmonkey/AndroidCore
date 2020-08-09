package com.mandelduck.androidcore;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.os.Bundle;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.Timer;

import java.util.Arrays;

import java.util.List;

public class CoreService extends Service {

    private final static String TAG = CoreService.class.getName();
    private final static int NOTIFICATION_ID = 922430164;
    private static final String PARAM_OUT_MSG = "rpccore";
    private Process mProcess;
    Timer timer;

    private static void removeNotification(final Context c) {
        ((NotificationManager) c.getSystemService(NOTIFICATION_SERVICE)).cancel(NOTIFICATION_ID);
        final Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MainController.RPCResponseReceiver.ACTION_RESP);
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        broadcastIntent.putExtra(PARAM_OUT_MSG, "exception");
        broadcastIntent.putExtra("exception", "");
        c.sendBroadcast(broadcastIntent);
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return null;
    }


    private void setupNotificationAndMoveToForeground() {

        final Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(MainController.RPCResponseReceiver.ACTION_RESP);
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        broadcastIntent.putExtra(PARAM_OUT_MSG, "OK");
        sendBroadcast(broadcastIntent);

        startForegroundNotif();

        MainController.postStart();

        try {
            JSONObject json = new JSONObject();
            json.put("error", false);
            json.put("response", "starting");

            MainController.sendMessage(json.toString());

        } catch (Exception e2) {
            Log.e(TAG, "err1:" + e2.toString());
        }
    }


    public void startForegroundNotif() {
        Context cont = getBaseContext();


        final Intent i = new Intent(cont, MainController.class);

        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TASK);

        final PendingIntent pI;
        pI = PendingIntent.getActivity(cont, 0, i, PendingIntent.FLAG_ONE_SHOT);
        final NotificationManager nM = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(cont);
        final String version = "0.18.0";

        final Notification.Builder b = new Notification.Builder(cont)
                .setContentTitle("Nayuta Core Is Running")
                .setContentIntent(pI)
                .setContentText(String.format("Version %s", version))
                .setSmallIcon(R.drawable.ic_nayuta_icon)
                .setOngoing(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final int importance = NotificationManager.IMPORTANCE_LOW;

            final NotificationChannel mChannel = new NotificationChannel("channel_00", "Nayuta Core, Bitcoind", importance);
            mChannel.setDescription(String.format("Version %s", version));
            mChannel.enableLights(true);
            mChannel.enableVibration(true);
            mChannel.setVibrationPattern(new long[]{100, 200, 300, 400, 500, 400, 300, 200, 400});
            nM.createNotificationChannel(mChannel);
            b.setChannelId("channel_00");
        }else{
            Log.i(TAG,"Background mode not supported");
        }


        final Notification n = b.build();

        startForeground(NOTIFICATION_ID, n);
    }

    @Override
    public int onStartCommand(final Intent intent, final int flags, final int startId) {

        Context cont = getBaseContext();
        if (Utils.isDaemonInstalled(cont) == false) {
            Log.i(TAG, "bitcoind not installed");
            return START_STICKY;
        }
        Log.i(TAG, "started 2");
        if (mProcess != null) {

            Log.i(TAG, "mProcess is not null");
            return START_STICKY;
        }

        if (intent == null) {
            Log.i(TAG, "intent is null");
            return START_STICKY;
        }

        Log.i(TAG, "Core service msg");

        try {

            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
            final String path = getNoBackupFilesDir().getCanonicalPath();

            final ProcessLogger.OnError er = new ProcessLogger.OnError() {
                @Override
                public void onError(final String[] error) {
                    mProcess = null;

                    final StringBuilder bf = new StringBuilder();
                    for (final String e : error)
                        if (!TextUtils.isEmpty(e))
                            bf.append(String.format("%s%s", e, System.getProperty("line.separator")));

                    Log.i(TAG, "err2:" + bf.toString());

                    Log.i(TAG, "stopping service");
                    try {
                        JSONObject json = new JSONObject();
                        json.put("error", true);
                        json.put("response", bf.toString());

                        MainController.sendMessage(json.toString());
                    } catch (Exception e2) {
                        Log.e(TAG, e2.toString());
                    }

                    stopSelf();
                }
            };

            final String daemon = "bitcoind";

            List<String> array = null;


            Bundle extras = intent.getExtras();
            boolean reindex = false;
            if (extras == null) {
                Log.i("Service", "null");
            } else {
                Log.i("Service", "not null");
                reindex = (boolean) extras.get("reindex");


            }

            array = Arrays.asList(String.format("%s/%s", path, daemon),
                    "--server=1",
                    String.format("--datadir=%s", Utils.getDataDir(cont)),
                    String.format("--conf=%s", Utils.getBitcoinConf(cont)));
            if (reindex) {

                Log.i("Service", "starting with reindex");
                array = Arrays.asList(String.format("%s/%s", path, daemon),
                        "--server=1",
                        "-reindex",
                        String.format("--datadir=%s", Utils.getDataDir(cont)),
                        String.format("--conf=%s", Utils.getBitcoinConf(cont)));
            }


            final ProcessBuilder pb = new ProcessBuilder(array);

            pb.directory(new File(path));

            mProcess = pb.start();

            final ProcessLogger errorGobbler = new ProcessLogger(mProcess.getErrorStream(), er);
            final ProcessLogger outputGobbler = new ProcessLogger(mProcess.getInputStream(), er);

            errorGobbler.start();
            outputGobbler.start();


            try {


                if (extras == null) {
                    Log.i("Service", "extras are null");
                } else {
                    Log.i("Service", "extras are not null");
                    boolean runInBackGround = (boolean) extras.get("startForeground");
                    if (runInBackGround) {
                        setupNotificationAndMoveToForeground();
                    } else {
                        Log.i(TAG, "dont run in background");
                    }

                }

            } catch (Exception e) {
                e.printStackTrace();
                Log.i(TAG, "app is not running so don't promote to foreground");
            }

        } catch (final IOException e) {
            Log.i(TAG, "Native exception!");
            Log.i(TAG, e.getMessage());

            Log.i(TAG, e.getLocalizedMessage());
            removeNotification(cont);
            mProcess = null;

            e.printStackTrace();
        }
        Log.i(TAG, "background Task finished");

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        final Intent i = new Intent(getBaseContext(), RPCIntentService.class);
        i.putExtra("CONSOLE_REQUEST", "stop");
        MainController.thisContext.startService(i);
        if (timer != null) {
            timer.cancel();
        }
        Log.i(TAG, "destroying core service");

        if (mProcess != null) {
            mProcess.destroy();
            mProcess = null;
        }

    }
}
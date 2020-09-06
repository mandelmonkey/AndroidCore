package com.mandelduck.androidcore;

import android.Manifest;
import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Debug;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.app.ActivityManager;

import android.app.ActivityManager.RunningServiceInfo;

import org.apache.commons.compress.utils.IOUtils;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Timer;

public class MainController {
    public static boolean HAS_BEEN_STARTED = false;
    private static Timer mTimer;
    static Context thisContext;
    static String thisConfig;
    static Activity thisActivity;
    static Handler handler;
    static CallbackInterface thisCallback;
    public static String mBlockHex;
    private final static int NOTIFICATION_ID = 922430164;

    private static final String PARAM_OUT_MSG = "rpccore";
    private static RPCResponseReceiver mRpcResponseReceiver;
    private static Process mProcessTor;

    public MainController() {

    }

    private static void deleteRF(final File f) {

        Log.v(TAG, "Deleting " + f.getAbsolutePath() + "/" + f.getName());
        if (f.isDirectory()) for (File child : f.listFiles()) deleteRF(child);

        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    public static void deleteCore() {

        final File dir = Utils.getDir(thisActivity);
        deleteRF(new File(dir, "shachecks"));
        deleteRF(new File(dir, "bitcoind"));

        deleteRF(new File(dir, "liquidd"));

        String pathMainnet = thisActivity.getApplicationContext().getNoBackupFilesDir().getPath() + "/mainnet.zip";
        String pathTestnet = thisActivity.getApplicationContext().getNoBackupFilesDir().getPath() + "/testnet.zip";

        String pathTestnet3 = thisActivity.getApplicationContext().getNoBackupFilesDir().getPath() + "/testnet3.zip";

        File file = new File(pathMainnet);
        if (file.exists()) {
            file.delete();
        }

        file = new File(pathTestnet);
        if (file.exists()) {
            file.delete();
        }

        file = new File(pathTestnet3);
        if (file.exists()) {
            file.delete();
        }

    }

    public static void deleteData() {

        deleteRF(new File(Utils.getDataDir(thisActivity)));

    }

    public static boolean isTestnet() {
        return Utils.isTestnet(thisContext);
    }

    public static void onPause() {

        Log.i(TAG, "on pause");

        if (mRpcResponseReceiver != null && thisContext != null) {
            thisContext.unregisterReceiver(mRpcResponseReceiver);
            mRpcResponseReceiver = null;
        }
        if (mTimer != null) {
            mTimer.cancel();
            mTimer.purge();
        }
    }

    public static String readConf(Context ctx) {

        try {

            final InputStream f = new FileInputStream(Utils.getBitcoinConf(ctx));
            return new String(IOUtils.toByteArray(f));

        } catch (final IOException e) {
            Log.i(TAG, e.getMessage());
        }
        return "";
    }

    public static void saveConf(String conf, Activity act, Context ctx) {

        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED && !ActivityCompat.shouldShowRequestPermissionRationale(act, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            ActivityCompat.requestPermissions(act, new String[]{
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                    },
                    0);

        // save file
        OutputStream f = null;
        try {
            f = new FileOutputStream(Utils.getBitcoinConf(ctx));
            IOUtils.copy(new ByteArrayInputStream(conf.getBytes(StandardCharsets.UTF_8)), f);

        } catch (final IOException e) {
            Log.i(TAG, e.getMessage());
        } finally {
            IOUtils.closeQuietly(f);
        }
    }

    public static void onResume() {

        Log.i(TAG, "on resume");

        final IntentFilter rpcFilter = new IntentFilter(RPCResponseReceiver.ACTION_RESP);
        if (mRpcResponseReceiver == null) {
            mRpcResponseReceiver = new RPCResponseReceiver();
        }
        rpcFilter.addCategory(Intent.CATEGORY_DEFAULT);
        if (thisContext != null) {
            thisContext.registerReceiver(mRpcResponseReceiver, rpcFilter);

            thisContext.startService(new Intent(thisContext, RPCIntentService.class));

        }

    }

    public static void localNotification() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(thisContext, "2")
                //  .setSmallIcon(R.drawable.notification_icon)
                .setContentTitle("sds").setContentText("sdsdsdsds").setPriority(NotificationCompat.PRIORITY_DEFAULT);
    }

    public static void stopService() {
        thisContext.stopService(new Intent(thisContext, RPCIntentService.class));
    }

    public static void stopCore() {
        final Intent i = new Intent(thisActivity, RPCIntentService.class);
        i.putExtra("CONSOLE_REQUEST", "stop");
        thisContext.startService(i);

    }

    private static final String TAG = MainController.class.getName();

    private static boolean isUnpacked(final String sha, final File outputDir) {
        final File shadir = new File(outputDir, "shachecks");
        return new File(shadir, sha).exists();
    }

    public static void callRPC(String command) {
        Log.i(TAG, "calling " + command);
        final Intent i = new Intent(thisContext, RPCIntentService.class);
        i.putExtra("CONSOLE_REQUEST", command);
        thisContext.startService(i);
    }

    public static void getBlockchainInfo() {
        final Intent i = new Intent(thisContext, RPCIntentService.class);
        i.putExtra("CONSOLE_REQUEST", "getblockchaininfo");
        thisContext.startService(i);
    }

    public static void getBlockStats(int height) {
        final Intent i = new Intent(thisContext, RPCIntentService.class);
        String val = "getblockstats " + height + "";
        Log.i(TAG, val);
        i.putExtra("CONSOLE_REQUEST", val);
        thisContext.startService(i);
    }

    public static void sendCommand(String command) {
        final Intent i = new Intent(thisContext, RPCIntentService.class);
        i.putExtra("CONSOLE_REQUEST", command);
        thisContext.startService(i);
    }

    public static void submitBlock(String blockHex) {
        mBlockHex = blockHex;
        final Intent i = new Intent(thisContext, RPCIntentService.class);
        i.putExtra("CONSOLE_REQUEST", "submitblock");
        thisContext.startService(i);
    }

    public static void generateBlock() {
        final Intent i = new Intent(thisContext, RPCIntentService.class);
        i.putExtra("CONSOLE_REQUEST", "generate 101");
        thisContext.startService(i);
    }

    public static String getDataDir() {
        return Utils.getDir(thisContext).getAbsolutePath();
    }

    public static void configureCore(final Context c) throws IOException {

        final File coreConf = new File(Utils.getBitcoinConf(c));

        // if(coreConf.exists())
        //return;
        coreConf.getParentFile().mkdirs();

        FileOutputStream outputStream;


        try {

            outputStream = new FileOutputStream(coreConf);
            outputStream.write(thisConfig.getBytes());

            for (final File f : c.getExternalFilesDirs(null))
                outputStream.write(String.format("# for external storage try: %s\n", f.getCanonicalPath()).getBytes());

            outputStream.write(String.format("datadir=%s\n", String.format("%s/bitcoinDirec", Utils.getDir(c).getAbsolutePath())).getBytes());

            IOUtils.closeQuietly(outputStream);
        } catch (final IOException e) {
            Log.e(TAG, "make dir error");
            e.printStackTrace();
            throw e;
        }

    }

    private static void refresh() {
        final Intent i = new Intent(thisContext, RPCIntentService.class);
        i.putExtra("REQUEST", "localonion");
        Log.i(TAG, "requesting local oninon");
        thisContext.startService(i);
    }

    public static void sendMessage(String message) {
        thisCallback.eventFired(message);
    }

    public static boolean checkIfServiceIsRunning(String serviceClassName) {

        final ActivityManager activityManager = (ActivityManager) thisContext.getSystemService(Context.ACTIVITY_SERVICE);

        final List<RunningServiceInfo> services = activityManager.getRunningServices(Integer.MAX_VALUE);

        for (RunningServiceInfo runningServiceInfo : services) {
            Log.i(TAG, "services " + runningServiceInfo.service.getClassName());
            if (runningServiceInfo.service.getClassName().equals(serviceClassName)) {

                return true;
            }
        }

        return false;
    }

    public static void stopTorHiddenService() {

        mProcessTor.destroy();
        mProcessTor = null;

    }

    public static void LogFilesIn(String path) {
        Log.i(TAG, "logging files in " + path);
        File directory = new File(path);

        directory.mkdirs();

        File[] fList = directory.listFiles();

        //get all the files from a directory
        for (File file : fList) {
            if (file.isFile()) {
                Log.i("FILE", file.getName());
            } else if (file.isDirectory()) {
                Log.i("Direc", file.getName());
            } else {
                Log.i("else", file.getName());
            }
        }
    }

    public static void startTorHiddenService(Context context) {

        try {
            Log.i(TAG, "Try Starting Tor");
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);


            try {

                String daemonName = "libtor.so";


                final String path = context.getNoBackupFilesDir().getAbsolutePath();
                String nativeP = thisContext.getPackageManager().getApplicationInfo("com.nayuta.core", PackageManager.GET_SHARED_LIBRARY_FILES).nativeLibraryDir;
                LogFilesIn(nativeP);

                String tpath = nativeP + "/" + daemonName;


                File f = new File(tpath);
                if (!f.exists()) {
                    tpath = nativeP + "/tor";
                }

                Log.i(TAG, tpath);
                ProcessBuilder pb = new ProcessBuilder(tpath, "DataDirectory", path + "/tordata", "SOCKSPort", "9050", "ControlPort", "9051", "CookieAuthentication", "1", "HiddenServiceDir", path + "/tordata", "HiddenServiceVersion", "3", "HiddenServicePort", "8080 127.0.0.1:8080");
                mProcessTor = pb.start();

                Log.i("start tor", "start tor");

                final ProcessLogger.OnError er = new ProcessLogger.OnError() {
                    @Override
                    public void onError(final String[] error) {
                        boolean ignoreError = false;
                        for (String
                                var : error) {
                            if (var != null) {
                                if (var.indexOf("Could not bind to 127.0.0.1:9051: Address already in use. Is Tor already running?") != -1) {
                                    ignoreError = true;
                                } else {
                                    Log.e(TAG, "Tor error " +
                                            var);
                                }
                            } else {
                                ignoreError = true;
                                Log.e(TAG, "error is null ");
                            }

                        }

                        if (ignoreError == false) {
                            Log.e(TAG, "Tor errors " + error.length);
                            try {
                                JSONObject json = new JSONObject();
                                json.put("error", true);
                                json.put("response", "hidden service error, make sure orbot is closed");

                                thisCallback.eventFired(json.toString());
                            } catch (Exception e2) {
                                thisCallback.eventFired("");
                            }

                            mProcessTor = null;
                        }
                    }
                };

                final ProcessLogger torErrorGobbler = new ProcessLogger(mProcessTor.getErrorStream(), er);
                final ProcessLogger torOutputGobbler = new ProcessLogger(mProcessTor.getInputStream(), er);
                Log.i(TAG, "Starting Tor");
                torErrorGobbler.start();

                torOutputGobbler.start();


                try {
                    JSONObject json = new JSONObject();
                    json.put("error", false);
                    json.put("response", "hidden service started");

                    thisCallback.eventFired(json.toString());
                } catch (Exception e2) {
                    e2.printStackTrace();
                    thisCallback.eventFired("");
                }


            } catch (Exception e) {
                Log.e("name error", "werr");
                Log.i(TAG, e.getLocalizedMessage());

                try {
                    JSONObject json = new JSONObject();
                    json.put("error", true);
                    json.put("response", "hidden service error");

                    thisCallback.eventFired(json.toString());
                } catch (Exception e2) {
                    thisCallback.eventFired("");
                }
                e.printStackTrace();
            }


        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "Tor error Native exception!");
            Log.i(TAG, e.getMessage());

            Log.i(TAG, e.getLocalizedMessage());

            try {
                JSONObject json = new JSONObject();
                json.put("error", true);
                json.put("response", "hidden service error, make sure orbot is closed");

                thisCallback.eventFired(json.toString());
            } catch (Exception e2) {
                thisCallback.eventFired("");
            }

            mProcessTor = null;
            e.printStackTrace();
        }

    }

    public static void getProgress() {
        final Intent i = new Intent(thisContext, RPCIntentService.class);
        i.putExtra("REQUEST", "progress");
        thisContext.startService(i);
    }

    public static void startCore(boolean reindex) {

        Intent serviceIntent = new Intent(thisContext, CoreService.class);
        serviceIntent.putExtra("startForeground", false);

        if (reindex) {
            serviceIntent.putExtra("reindex", true);

        } else {
            serviceIntent.putExtra("reindex", false);

        }

        thisContext.startService(serviceIntent);

        try {
            JSONObject json = new JSONObject();
            json.put("error", false);
            json.put("response", "starting");

            thisCallback.eventFired(json.toString());
        } catch (Exception e2) {
            thisCallback.eventFired("");
        }

    }

    public static void setUp(final Context context, final String config, final Activity activity, final CallbackInterface callback) {
        thisContext = context;
        thisActivity = activity;
        thisCallback = callback;
        Log.i(TAG, "config " + config);
        thisConfig = config;

        final String arch = Utils.getArch();
        Log.i(TAG, "arch is " + arch);

        onResume();
        try {
            configureCore(thisContext);
        } catch (Exception e) {
            e.printStackTrace();
        }

        Log.i(TAG, "checking blockchain...1");
        final Handler handler = new Handler();
        final int delay = 20000; //milliseconds
        handler.postDelayed(new Runnable() {
                                public void run() {
                                    Log.i(TAG, "cont checking blockchain...");
                                    //do something
                                    getBlockchainInfo();
                                    handler.postDelayed(this, delay);
                                }
                            },
                delay);

    }

    public static void cancelForeground() {

        thisContext.stopService(new Intent(thisContext, CoreService.class));

    }

    public static void scheduleJob(boolean limitedMode) {

        if (thisContext == null) {
            Log.i(TAG, "no context");
            return;
        }

        ComponentName componentName = new ComponentName(thisContext, SyncJobService.class);
        JobInfo info = null;

        if (limitedMode) {
            Log.i(TAG, "limited mode");
            info = new JobInfo.Builder(123, componentName).setRequiresCharging(false).setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED).setPersisted(true).setPeriodic(15 * 60 * 1000).build();

        } else {
            Log.i(TAG, "unlimited mode");
            info = new JobInfo.Builder(123, componentName).setPersisted(true).setPeriodic(15 * 60 * 1000).build();

        }

        Log.i(TAG, "schedule job mode");
        JobScheduler scheduler = (JobScheduler) thisContext.getSystemService(thisContext.JOB_SCHEDULER_SERVICE);
        int resultCode = scheduler.schedule(info);
        if (resultCode == JobScheduler.RESULT_SUCCESS) {
            Log.i(TAG, "Job scheduled limited=" + limitedMode);
        } else {
            Log.i(TAG, "Job scheduling failed");
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {

            Intent serviceIntent = new Intent(thisContext, CoreService.class);
            serviceIntent.putExtra("startForeground", true);
            serviceIntent.putExtra("reindex", false);

            thisContext.startService(serviceIntent);
        }

    }

    public static void cancelJob() {

        if(handler != null){
            handler.removeCallbacks(checkWifi);
        }
        JobScheduler scheduler = (JobScheduler) thisContext.getSystemService(thisContext.JOB_SCHEDULER_SERVICE);
        scheduler.cancel(123);
        scheduler.cancelAll();
        Log.i(TAG, "Job cancelled");

    }

    public static void registerBackgroundSync(boolean limited) {

        Log.i(TAG, "scheduling job");
        scheduleJob(limited);

    }

    public static long getRAM() {

        ActivityManager actManager = (ActivityManager) thisContext.getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo memInfo = new ActivityManager.MemoryInfo();
        actManager.getMemoryInfo(memInfo);
        return memInfo.totalMem;
    }

    public static boolean checkIfDownloaded() {
        return Utils.isDaemonInstalled(thisContext);
    }

    public static void startDownload() {

        HAS_BEEN_STARTED = true;

        final File dir = Utils.getDir(thisContext);
        Log.i(TAG, dir.getAbsolutePath());
        final String arch = Utils.getArch();

        Log.i(TAG, "getting binary for arc " + arch);

        //Utils.copyBitcoind(thisContext, Utils.getArch() + "_bitcoin.tar.xz");

        Log.i(TAG, "copied and extracted " + arch);

        HAS_BEEN_STARTED = false;
        try {
            JSONObject json = new JSONObject();
            json.put("error", false);
            json.put("response", "downloaded");

            thisCallback.eventFired(json.toString());
        } catch (Exception e2) {
            thisCallback.eventFired("");
        }

        try {

            configureCore(thisContext);

        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }

        return;

    }

    public static void onionMessage(String message) {
        Log.i(TAG, "oniony " + message);

    }

    public static Runnable checkWifi = new Runnable() {


        @Override
        public void run() {

            Log.i(TAG, "checking WIFI");

            ConnectivityManager connManager = (ConnectivityManager) thisContext.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo activeNetwork = connManager.getActiveNetworkInfo();
            if (activeNetwork != null) {
                Log.i(TAG, "connected");
                // connected to the internet
                String resType = "wifi off";
                if (activeNetwork.getType() == ConnectivityManager.TYPE_WIFI) {

                    resType = "wifi on";

                } else {

                    stopService();
                    stopCore();
                }

                try {
                    JSONObject json = new JSONObject();
                    json.put("error", false);
                    json.put("response", resType);
                    json.put("debug", "network: " + activeNetwork.getType() + " wifi is:" + ConnectivityManager.TYPE_WIFI);

                    MainController.sendMessage(json.toString());
                } catch (Exception e2) {
                    Log.e(TAG, e2.toString());
                }


            }

            handler.postDelayed(checkWifi, 2000);

        }
    };

    public static void StartWifiCheck() {

        Log.i(TAG, "start check wifi");
        if (handler == null) {
            handler = new Handler();
        }
        Log.i(TAG, "cont check wifi");

        handler.removeCallbacks(checkWifi);

        checkWifi.run();

    }

    public static void postStart() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(thisContext);
        final String useDistribution = prefs.getString("usedistribution", "core");
        Log.i(TAG, "post start");
        StartWifiCheck();

        try {
            JSONObject json = new JSONObject();
            json.put("error", false);
            json.put("response", "already started");
            json.put("distribution", useDistribution);

            MainController.sendMessage(json.toString());
        } catch (Exception e2) {
            Log.e(TAG, e2.toString());
        }
    }

    // And From your main() method or any other method
    public static class RPCResponseReceiver extends BroadcastReceiver {
        public static final String ACTION_RESP = "com.greenaddress.intent.action.RPC_PROCESSED";

        @Override
        public void onReceive(final Context context, final Intent intent) {
            final String text = intent.getStringExtra(RPCIntentService.PARAM_OUT_MSG);
            Log.i(TAG, "did receive " + text);
            switch (text) {
                case "OK":

                    break;
                case "exception":
                    final String exe = intent.getStringExtra("exception");
                    if (exe != null) Log.i(TAG, exe);
                    //postConfigure();
                    break;
                case "localonion":

                    if (!intent.hasExtra(RPCIntentService.PARAM_ONION_MSG)) return;
                    final String onion = intent.getStringExtra(RPCIntentService.PARAM_ONION_MSG);
                    Log.d("onion", onion);
                    if (onion == null) return;
            }
        }
    }

    private static String getLastLines(final File file, final int lines) {
        RandomAccessFile fileHandler = null;
        try {
            fileHandler = new RandomAccessFile(file, "r");
            final long fileLength = fileHandler.length() - 1;
            final StringBuilder sb = new StringBuilder();
            int line = 0;

            for (long filePointer = fileLength; filePointer != -1; --filePointer) {
                fileHandler.seek(filePointer);
                int readByte = fileHandler.readByte();

                if (readByte == 0xA) {
                    if (filePointer < fileLength) ++line;
                } else if (readByte == 0xD) {
                    if (filePointer < fileLength - 1) ++line;
                }

                if (line >= lines) break;
                sb.append((char) readByte);
            }

            return sb.reverse().toString();
        } catch (final IOException e) {
            e.printStackTrace();
            return null;
        } finally {
            if (fileHandler != null) try {
                fileHandler.close();
            } catch (final IOException ignored) {
            }
        }
    }

    public static void getLogs(final CallbackInterface logsCallback) {

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(thisContext);
        final String useDistribution = prefs.getString("usedistribution", "core");
        final String daemon = "/debug.log";

        final File f = new File(Utils.getDataDir(thisContext) + (Utils.isTestnet(thisContext) ? "/testnet3/debug.log" : daemon));
        if (!f.exists()) {
            Log.i(TAG, "No debug file exists yet");
            return;
        }

        for (int lines = 10; lines > 0; --lines) {
            final String txt = getLastLines(f, lines);
            if (txt != null) {
                try {
                    JSONObject json = new JSONObject();
                    json.put("error", false);
                    json.put("response", "logs");
                    json.put("res", txt);

                    logsCallback.eventFired(json.toString());
                } catch (Exception e2) {
                    logsCallback.eventFired("");
                }
            }
        }
    }

}
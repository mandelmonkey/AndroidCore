package com.mandelduck.androidcore;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.apache.commons.compress.utils.IOUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import wf.bitcoin.javabitcoindrpcclient.BitcoinJSONRPCClient;
import wf.bitcoin.javabitcoindrpcclient.BitcoinRPCException;
import wf.bitcoin.javabitcoindrpcclient.BitcoindRpcClient;

import android.content.SharedPreferences.Editor;

import static java.lang.Integer.parseInt;

public class RPCIntentService extends IntentService {
    public static final String NOTIFICATION_CHANNEL_ID = "10001" ;
    public static final String PARAM_OUT_MSG = "rpccore";
    public static final String PARAM_OUT_INFO = "rpccoreinfo";
    public static final String PARAM_ONION_MSG = "onionaddr";

    private static final String TAG = RPCIntentService.class.getName();

    public RPCIntentService() {
        super(RPCIntentService.class.getName());
    }

    private Properties getBitcoinConf() throws IOException {
        final Properties p = new Properties();
        final InputStream i = new BufferedInputStream(new FileInputStream(Utils.getBitcoinConf(this)));
        try {
            p.load(i);
        } finally {
            IOUtils.closeQuietly(i);
        }
        return p;
    }

    private String getRpcUrl() throws IOException {
        final Properties p = getBitcoinConf();
        String user = p.getProperty("rpcuser");
        String password = p.getProperty("rpcpassword");
        final String testnet = p.getProperty("testnet");
        final String nonMainnet = testnet == null || !testnet.equals("1") ? p.getProperty("regtest") : testnet;
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        final String useDistribution = prefs.getString("usedistribution", "core");
        if (user == null || password == null) {
            final String cookie = String.format("%s/%s", p.getProperty("datadir"), ".cookie");
            final String cookieTestnet = String.format("%s/%s", p.getProperty("datadir"), "testnet3/.cookie");

            final String daemon =  cookie;

            final String fCookie = nonMainnet == null || !nonMainnet.equals("1") ? daemon : cookieTestnet;
            final File file = new File(fCookie);

            final StringBuilder text = new StringBuilder();

            try {
                final BufferedReader br = new BufferedReader(new FileReader(file));
                String line;

                while ((line = br.readLine()) != null) {
                    text.append(line);
                }
                br.close();
            } catch (final IOException ignored) {
            }
            final String cookie_content = text.toString();
            user = "__cookie__";
            if (cookie_content.length() > user.length() + 2)
                password = cookie_content.substring(user.length() + 1);
        }
        final String host = p.getProperty("rpcconnect", "127.0.0.1");
        final String port = p.getProperty("rpcport");
        final String url = "http://" + user + ':' + password + "@" + host + ":" + (port == null ? "8332" : port) + "/";
        final String testUrl = "http://" + user + ':' + password + "@" + host + ":" + (port == null ? "18332" : port) + "/";
        Log.i(TAG,"rpc url "+url);

        final String mainUrl =  url;
        return !"1".equals(nonMainnet) ? mainUrl : testUrl;
    }

    private BitcoindRpcClient getRpc() throws IOException {
        return new BitcoinJSONRPCClient(getRpcUrl());
    }


    private void broadcastProgress() throws IOException {
        final BitcoindRpcClient bitcoin = getRpc();



        final BitcoindRpcClient.BlockChainInfo info = bitcoin.getBlockChainInfo();
        Log.i(TAG,"sync "+info.verificationProgress().multiply(BigDecimal.valueOf(100)));
        Log.i(TAG,"blocks "+info.blocks());

        Log.i(TAG,info.toString());


        try {
            JSONObject json = new JSONObject();
            json.put("error", false);
            json.put("response", "progress");
            json.put("sync", info.verificationProgress().multiply(BigDecimal.valueOf(100)));
            json.put("blocks", info.blocks());


            MainController.sendMessage(json.toString());
        } catch (Exception e2) {
            Log.e(TAG,e2.toString());
        }


    }

    private void broadcastNetwork() throws IOException {
        final BitcoindRpcClient bitcoin = getRpc();


        final BitcoindRpcClient.NetworkInfo info = bitcoin.getNetworkInfo();
        for (final Object addrs : info.localAddresses()) {
            final Map data = (Map) addrs;
            final String host = (String) data.get("address");
            if (host != null && host.endsWith(".onion")) {
                final Long port =  (Long) data.get("port");
                String onion = "bitcoin-p2p://" + host;
                if (port != null && 8333 != port) {
                    onion += ":" + port;
                }

                MainController.onionMessage(onion);
                break;
            }
        }
    }

    private void broadcastError(final Exception e) {
        Log.e(TAG, e.getClass().getName());

    }

    void MakeNotification(JSONObject obj){

        try {

            AudioManager am = (AudioManager)getSystemService(Context.AUDIO_SERVICE);

             switch (am.getRingerMode()) {
                case AudioManager.RINGER_MODE_SILENT:
                    Log.i("MyApp","Silent mode");
                    break;
                case AudioManager.RINGER_MODE_VIBRATE:
                    Log.i("MyApp","Vibrate mode");
                    break;
                case AudioManager.RINGER_MODE_NORMAL:
                    Log.i("MyApp","Normal mode");
                    break;
            }


            Log.i(TAG,obj.toString());
            Intent intent = new Intent(getBaseContext(), NotificationPub.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(getBaseContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
            long epoch = obj.getInt("time");
            Log.i(TAG,"epoch "+ epoch);

            epoch = epoch * 1000;
            Log.i(TAG,"epoch2 "+ epoch);

            DecimalFormat df2 = new DecimalFormat("#.##");


            String date = new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(new java.util.Date (epoch));

            double totalSATS = (double)obj.getLong("total_out");

            double totalBTC = totalSATS / 100000000;


            double totalSizeBytes = (double)obj.getDouble("total_size");
            double totalSizeMB = totalSizeBytes / 1000000;
            Uri soundUri = null;

            if(am.getRingerMode() == AudioManager.RINGER_MODE_NORMAL) {
                Log.i(TAG,"setting donk sound");
                soundUri = Uri.parse("android.resource://" + getBaseContext().getPackageName() + "/" + R.raw.donk);

            }

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(getBaseContext(), NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_nayuta_icon)
                    .setContentTitle("block: " + obj.getInt("height") + "")
                    .setContentText(date + ", " + obj.getInt("txs") + " txs, " + df2.format(totalBTC) + " BTC, " + df2.format(totalSizeMB) + " MB")
                    .setAutoCancel(true)
                    .setSound(null)
                    .setContentIntent(pendingIntent);



            NotificationManager mNotificationManager = (NotificationManager) getBaseContext().getSystemService(Context.NOTIFICATION_SERVICE);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {


                        // Changing Default mode of notification
                        notificationBuilder.setDefaults(Notification.DEFAULT_VIBRATE);
                        // Creating an Audio Attribute
                        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .setUsage(AudioAttributes.USAGE_ALARM)
                                .build();

                        // Creating Channel
                        NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, "Testing_Audio", NotificationManager.IMPORTANCE_HIGH);

                if(soundUri == null) {
                    notificationChannel.setSound(null, null);

                }else{
                    notificationChannel.setSound(soundUri, audioAttributes);
                }



                        mNotificationManager.createNotificationChannel(notificationChannel);
                        Log.e("ABCORE", "Created Notification Channel");


            }
            mNotificationManager.notify(0, notificationBuilder.build());

        }catch(Exception e){
            e.printStackTrace();
        }

    }

    @Override
    protected void onHandleIntent(final Intent intent) {




        String console_request = intent.getStringExtra("CONSOLE_REQUEST");



        if (console_request != null) {

            if (console_request.equals("localonion")) {

                Log.i(TAG,"handle intentlocal onion");
                try {
                    broadcastNetwork();
                }
                catch (Exception e){
                    Log.e(TAG,e.getLocalizedMessage());
                }
                return;

            }

            try {
                Log.i(TAG,"getting rpc ");

                String rpcUrl = getRpcUrl();

                Log.i(TAG,"rpc url is "+rpcUrl);

                final BitcoinJSONRPCClient bitcoin = new BitcoinJSONRPCClient(rpcUrl );

                Log.v(TAG, console_request);

                try {
                    Gson gson = new Gson();
                    final String[] array = console_request.split(" ");

                    if(console_request.contains("getblockstats")){
                        String[] params = {"txs","total_out","height","blockhash","total_size","time","swtxs"};
                        Object res = bitcoin.query(array[0],
                                parseInt(array[1]),params);


                        Log.i(TAG, "res is array here  "+ res.toString());
                        try {
                            JSONObject json = new JSONObject();
                            json.put("error", false);
                            json.put("response", "rpc");
                            json.put("command", array[0]);
                            json.put("res", gson.toJson(res));


                            MainController.sendMessage(json.toString());
                        } catch (Exception e2) {
                            Log.e(TAG, "heret "+ e2.toString());
                        }

                    }
                    else if (array.length > 1) {
                        Log.i(TAG, "has params "+ array.length);

                        Object res = bitcoin.query(array[0],
                                (Object[]) Arrays.copyOfRange(array, 1, array.length));



                        Log.i(TAG, "res is array here  "+ res.toString());
                        try {
                            JSONObject json = new JSONObject();
                            json.put("error", false);
                            json.put("response", "rpc");
                            json.put("command", array[0]);
                            json.put("res", gson.toJson(res));


                            MainController.sendMessage(json.toString());
                        } catch (Exception e2) {
                            Log.e(TAG, "heret "+ e2.toString());
                        }

                    }else {
                        Log.i(TAG, "no params");
                        Object res = bitcoin.query(console_request);
                        try {
                            Log.i(TAG,"res is object "+res.toString());
                            JSONObject json1 = new JSONObject();
                            json1.put("error", false);
                            json1.put("response", "rpc");
                            json1.put("command", console_request);
                            json1.put("res", gson.toJson(res) );


                            MainController.sendMessage(json1.toString());
                            if(res != null) {

                                JSONObject obj = new JSONObject( gson.toJson(res));

                                if (obj.has("bestblockhash")) {

                                    String bestBlockHash = obj.getString("bestblockhash");
                                    String blockHeight = obj.getInt("blocks")+"";
                                    Log.i(TAG, "saving best blockhash getblockchaininfo "+blockHeight+" "+bestBlockHash);

                                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

                                    if (settings != null) {

                                        String bbHashes = settings.getString("bestBlockHashesV1", "{}");

                                        JSONObject bbhObject = new JSONObject(bbHashes);



                                        if(!bbhObject.has(blockHeight)) {

                                            bbhObject.put(blockHeight, bestBlockHash);


                                            JSONArray keys = bbhObject.names();
                                            int startInt = 0;
                                            int endInt = keys.length();

                                            if(endInt > 100){

                                                startInt = endInt - 100;
                                            }

                                            JSONObject bbhObjectNew = new JSONObject();
                                            for(int i = startInt;i<endInt;i++){
                                                String key = keys.getString(i);
                                                bbhObjectNew.put(key,bbhObject.getString(key));
                                            }


                                            String json = bbhObject.toString();
                                            Editor edit = settings.edit();
                                            edit.putString("bestBlockHashesV1", json);
                                            edit.apply();

                                            if(obj.getBoolean("initialblockdownload") == false && obj.getDouble("verificationprogress") > 0.99999) {

                                                String[] params = {"txs", "total_out", "height", "blockhash", "total_size", "time", "swtxs"};
                                                Object res2 = bitcoin.query("getblockstats",
                                                        parseInt(blockHeight + ""), params);

                                                JSONObject obj2 = new JSONObject(gson.toJson(res2));


                                                MakeNotification(obj2);

                                            }
                                        }
                                    }


                                }


                            }


                        } catch (Exception e2) {
                            Log.e(TAG, "here3 "+ e2.toString());
                        }
                    }

                } catch (final BitcoinRPCException e) {

                    Log.i(TAG,"error4 "+e.getRPCError().getMessage());
                    try {
                        JSONObject json = new JSONObject();
                        json.put("error", true);
                        json.put("response", "rpc");
                        json.put("res", e.getRPCError().getMessage());


                        MainController.sendMessage(json.toString());
                    } catch (Exception e2) {
                        Log.e(TAG, "here"+ e2.toString());
                    }

                }

            } catch (final IOException e) {

                Log.i(TAG,"error2 "+e.getLocalizedMessage());
                try {
                    JSONObject json = new JSONObject();
                    json.put("error", true);
                    json.put("response", "rpc");
                    json.put("res", "failed");


                    MainController.sendMessage(json.toString());
                } catch (Exception e2) {
                    Log.e(TAG, "here"+ e2.toString());
                }
            } catch (final NullPointerException e) {

                Log.i(TAG,"error3 "+e.getLocalizedMessage());
                try {
                    JSONObject json = new JSONObject();
                    json.put("error", true);
                    json.put("response", "rpc");
                    json.put("res", "no value");


                    MainController.sendMessage(json.toString());
                } catch (Exception e2) {
                    Log.e(TAG, "here"+ e2.toString());
                }
            }



            return;
        }



    }
}

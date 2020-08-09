package com.mandelduck.androidcore;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;

class Utils {

    private final static String TAG = Utils.class.getSimpleName();

    static void extractTarXz(final File input, final File outputDir) throws IOException {
        TarArchiveInputStream in = null;
        try {

            in = new TarArchiveInputStream(new BufferedInputStream(new XZCompressorInputStream(new BufferedInputStream(new FileInputStream(input)))));

            ArchiveEntry entry;

            while ((entry = in.getNextEntry()) != null) {

                final String name = entry.getName();

                Log.v(TAG, "Extracting " + name);

                final File f = new File(outputDir, name);

                OutputStream out = null;
                try {
                    out = new FileOutputStream(f);
                    IOUtils.copy(in, out);
                } finally {
                    IOUtils.closeQuietly(out);
                }

                final int mode = ((TarArchiveEntry) entry).getMode();
                //noinspection ResultOfMethodCallIgnored
                f.setExecutable(true, (mode & 1) == 0);
            }

        } finally {
            IOUtils.closeQuietly(in);
        }
        //noinspection ResultOfMethodCallIgnored
        input.delete();
    }

    private static String sha256Hex(final String filePath) throws NoSuchAlgorithmException, IOException {
        final InputStream fis = new BufferedInputStream(new FileInputStream(filePath));
        final MessageDigest md = MessageDigest.getInstance("SHA-256");

        final byte[] dataBytes = new byte[1024];

        int nread;
        while ((nread = fis.read(dataBytes)) != -1)
            md.update(dataBytes, 0, nread);
        final byte[] mdbytes = md.digest();

        final StringBuilder sb = new StringBuilder();
        for (final byte b : mdbytes)
            sb.append(Integer.toString((b & 0xff) + 0x100, 16).substring(1));

        return sb.toString();
    }

    static void downloadFile(final String url, final String filePath) throws IOException {
        downloadFile(url, filePath, null);
    }

    static void downloadFile(final String url, final String filePath, final OnDownloadUpdate odsc) throws IOException {
try {
    final FileOutputStream fos = new FileOutputStream(filePath);
    final long start_download_time = System.currentTimeMillis();

    final DataInputStream dis = new DataInputStream(new BufferedInputStream(new URL(url).openStream()));

    final byte[] buffer = new byte[1024];
    int length;

    long lastUpdate = 0;

    int totalBytesDownloaded = 0;
    while ((length = dis.read(buffer)) > 0) {
        fos.write(buffer, 0, length);
        if (odsc != null) {
            totalBytesDownloaded += length;
            final long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdate > 200) {
                final long ms = currentTime - start_download_time;
                final int rate = (int) (totalBytesDownloaded / (ms / 1000.0));
                odsc.update(rate, totalBytesDownloaded);
                lastUpdate = currentTime;
            }
        }
    }

    Log.i(TAG, "Downloading fin");
    IOUtils.closeQuietly(fos);

    Log.i(TAG, "Downloading fin2");
    IOUtils.closeQuietly(dis);
}
catch (Exception e){

    Log.i(TAG, "Downloading error"+e.getLocalizedMessage());
    e.printStackTrace();

    try {
        JSONObject json = new JSONObject();
        json.put("error", true);
        json.put("response", "download");
        json.put("res","error downloading bitcoind");


        MainController.sendMessage(json.toString());
    } catch (Exception e2) {
        Log.e(TAG,e2.toString());
    }
}
    }

  static void copyBitcoind(Context context,String filename) {
        Log.i(TAG,"bitcoind stuff");
        AssetManager assetManager = context.getAssets();
        String[] files = null;
        try {
            files = assetManager.list("binaries");
        } catch (IOException e) {
            Log.e("tag", "Failed to get asset file list.", e);
        }


                InputStream in = null;
                OutputStream out = null;
                try {
                    in = assetManager.open("binaries/" + filename);
                    File outFile = new File( Utils.getDir(context).getAbsolutePath()+"/", filename);
                    out = new FileOutputStream(outFile);
                    copyFile(in, out);
                    in.close();
                    in = null;
                    out.flush();
                    out.close();
                    out = null;

                    extractTarXz(new File(Utils.getDir(context).getAbsolutePath()+"/", filename), getDir(context));
                } catch(IOException e) {
                    Log.e("tag", "Failed to copy asset file: " + filename, e);
                }


    }

    static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }
    static void moveFile(Context context, String inputPath, String outputPath, String directory) {

        InputStream in = null;
        OutputStream out = null;
        try {
            Log.d("filemove", "moving file " + inputPath);
            //create output directory if it doesn't exist
            File dir = new File(outputPath + "/" + directory);
            if (!dir.exists()) {

                dir.mkdirs();

            }
            File file = new File(outputPath + "/" + inputPath);
            if (!file.exists()) {

                in = context.getAssets().open(inputPath);
                out = new FileOutputStream(outputPath + "/" + inputPath);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                in.close();
                in = null;
                // write the output file
                out.flush();
                out.close();
                out = null;
                Log.d("filemove", "moved file " + inputPath);
                // delete the original file
                new File(inputPath).delete();
            }


        } catch (FileNotFoundException fnfe1) {
            Log.e("FILE NOT FOUND", fnfe1.getMessage());
        } catch (Exception e) {
            Log.e("tag", e.getMessage());
        }

    }
    static String getArch() {
        for (final String abi : Build.SUPPORTED_ABIS) {
            switch (abi) {
                case "armeabi-v7a":
                    return "arm-linux-androideabi";
                case "arm64-v8a":
                    return "aarch64-linux-android";
                case "x86":
                    return "i686-linux-android";
                case "x86_64":
                    return "x86_64-linux-android";
            }
        }
        throw new ABIsUnsupported();
    }

    static File getDir(final Context c) {
        return c.getNoBackupFilesDir();
    }

    static String getBitcoinConf(final Context c) {
        Log.i(TAG,String.format("%s/bitcoinDirec/bitcoin.conf", getDir(c).getAbsolutePath()));
        return String.format("%s/bitcoinDirec/bitcoin.conf", getDir(c).getAbsolutePath());
    }

    static Boolean bitcoinConfExists(final Context c) {

        File tempFile = new File(String.format("%s/bitcoinDirec/bitcoin.conf", getDir(c).getAbsolutePath()));
        return tempFile.exists();
    }

    static String getDataDir(final Context c) {
        final String defaultDataDir = String.format("%s/bitcoinDirec", getDir(c).getAbsolutePath());
        try {
            final Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(getBitcoinConf(c))));
            return p.getProperty("datadir", defaultDataDir);
        } catch (final IOException e) {
            return defaultDataDir;
        }
    }

    static boolean isTestnet(final Context c) {
        try {
            final Properties p = new Properties();
            p.load(new BufferedInputStream(new FileInputStream(getBitcoinConf(c))));
            return p.getProperty("testnet", p.getProperty("regtest", "0")).equals("1");
        } catch (final IOException e) {
            return false;
        }
    }

    static String getFilePathFromUrl(final Context c, final String url) {
        return getDir(c).getAbsoluteFile() + "/" + url.substring(url.lastIndexOf("/") + 1);
    }

    static String isSha256Different(final String arch, final String sha256raw, final String filePath) throws IOException, NoSuchAlgorithmException {
        final String hash = Utils.sha256Hex(filePath);
        final String sha256hash = sha256raw.substring(sha256raw.indexOf(arch) + arch.length());
        Log.d(TAG, hash);
        return sha256hash.equals(hash) ? null : hash;
    }

    static void validateSha256sum(final String arch, final String sha256raw, final String filePath) throws IOException, NoSuchAlgorithmException {
        final String diff = isSha256Different(arch, sha256raw, filePath);
        if (diff != null)
            throw new ValidationFailure(String.format("File %s doesn't match sha256sum %s", filePath, diff));
    }

    static boolean isDaemonInstalled(final Context c) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(c);
        final String useDistribution = prefs.getString("usedistribution", "core");
        final String daemon = "bitcoind";


        return new File(Utils.getDir(c).getAbsolutePath() + "/" + daemon).exists()
                && new File(Utils.getDir(c).getAbsolutePath() + "/tor").exists();
    }

    interface OnDownloadUpdate {
        void update(final int bytesPerSecond, final int bytesDownloaded);
    }

    static class ABIsUnsupported extends RuntimeException {
        ABIsUnsupported() {
            super(ABIsUnsupported.class.getName());
        }
    }

    static class ValidationFailure extends RuntimeException {
        ValidationFailure(final String s) {
            super(s);
        }
    }
}

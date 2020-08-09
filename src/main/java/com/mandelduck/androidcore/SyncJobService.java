package com.mandelduck.androidcore;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Intent;
import android.os.Build;
import android.util.Log;


public class SyncJobService extends JobService {
    private static final String TAG = "SyncJobService";
    private boolean jobCancelled = false;

    @Override
    public boolean onStartJob(JobParameters params) {

        Log.i(TAG, "Sync Job started");



        Intent serviceIntent = new Intent(this, CoreService.class);
        serviceIntent.putExtra("startForeground", true);
        serviceIntent.putExtra("reindex", false);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Log.i(TAG, "start");


           startForegroundService(serviceIntent);

        } else {
            startService(serviceIntent);
        }

        return true;
    }



    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job cancelled before completion");
        jobCancelled = true;
        return true;
    }
}
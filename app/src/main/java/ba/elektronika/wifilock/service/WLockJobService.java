package ba.elektronika.wifilock.service;

import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiManager;
import android.os.PersistableBundle;
import android.util.Log;

import java.util.List;

import ba.elektronika.wifilock.WLock;
import ba.elektronika.wifilock.database.DataSource;

/**
 * Created by Trax on 13/12/2017.
 */

public class WLockJobService extends JobService {
    private final String TAG = WLockJobService.class.getSimpleName();

    public static final int MANUAL_WLOCK_JOB_ID = 1;
    public static final int AUTOMATIC_WLOCK_JOB_ID = 2;

    public static final String STARTUP_TASK = "ba.elektronika.wifilock.intent.extra.STARTUP_TASK";
    public static final int STARTUP_TASK_UNLOCK = 1;
    public static final String STARTUP_TASK_UNLOCK_BSSID = "ba.elektronika.wifilock.intent.extra.STARTUP_TASK_UNLOCK_BSSID";

    private volatile boolean mInitialized = false;
    private WifiManager wifi = null;
    private Thread mWLockJobProcessorThread = null;
    private WLockJobServiceProcessor mWLockJobServiceProcessor = null;
    private WifiEventReceiver mWifiEventReceiver = null;
    private DataSource mDataSource = null;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "onCreate() " + this);

        //android.os.Debug.waitForDebugger();

        // "open" the database
        mDataSource = DataSource.getInstance(this);

        wifi = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifi.isWifiEnabled() == false) {
            try {
                wifi.setWifiEnabled(true);
            }
            catch (RuntimeException re) {
            }
        }

        // thread runnable
        mWLockJobServiceProcessor = new WLockJobServiceProcessor(this, wifi);

        // broadcast listener of wifi events
        IntentFilter filter = new IntentFilter();
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION);

        mWifiEventReceiver = new WifiEventReceiver();
        registerReceiver(mWifiEventReceiver, filter);
    }

    /**
     * Receiving WiFi events.
     */
    private class WifiEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // wifi finished scanning
            if(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION.equals(intent.getAction())) {
                mWLockJobServiceProcessor.sendScanResults(wifi.getScanResults());
            }
            // wifi changed state
            else if(WifiManager.SUPPLICANT_STATE_CHANGED_ACTION.equals(intent.getAction())) {
                mWLockJobServiceProcessor.sendSupplicantState((SupplicantState)intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE));
                Log.i(TAG, "SUPPLICANT STATE: " + (SupplicantState)intent.getParcelableExtra(WifiManager.EXTRA_NEW_STATE));
            }
        }
    }

    /**
     * Receiving tasks from Activities.
     */
    private class TaskEventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // ovdje cemo okidati recimo mWLockJobServiceProcessor.manualUnlock(BSSID); i tako to
        }
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy() " + this);

        unregisterReceiver(mWifiEventReceiver);

        super.onDestroy();
    }

    @Override
    public boolean onStartJob(final JobParameters jobParameters) {
        boolean isAutomatic = false;
        if (jobParameters.getJobId() == MANUAL_WLOCK_JOB_ID) {
            Log.i(TAG, "onStartJob(), for manual job. " + this);
        }
        else {
            Log.i(TAG, "onStartJob(), for automatic job. " + this);
            isAutomatic = true;
        }

        if(!mInitialized) {
            mInitialized = true;
            Log.i(TAG, "onStartJob(), creating and starting the thread process...");

            final Context ctx = this;
            mWLockJobServiceProcessor.setProcessorFinishedCallback(new WLockJobServiceProcessor.ProcessorFinishedCallback() {
                @Override
                public void call() {
                    WLockJobService.this.jobFinished(jobParameters, false);

                    // bug fix https://stackoverflow.com/a/36060742
                    JobScheduler jobScheduler = (JobScheduler) ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE);
                    jobScheduler.cancelAll();
                }
            });
            mWLockJobProcessorThread = new Thread(mWLockJobServiceProcessor);
            mWLockJobProcessorThread.start();
        }
        else
        {
            // this code executes only in Android version Oreo and later
            Log.i(TAG, "onStartJob(), thread process already running, just returning from here...");
            WLockJobService.this.jobFinished(jobParameters , false);
        }

        if(isAutomatic) {
            mWLockJobServiceProcessor.continueAutomaticMode();
        }
        else
        {
            PersistableBundle extra = jobParameters.getExtras();
            if(extra.containsKey(STARTUP_TASK)) {
                if(extra.getInt(STARTUP_TASK, -1) == STARTUP_TASK_UNLOCK) {

                    WLock wlock;
                    //wlock = mDataSource.getLock(extra.getString(STARTUP_TASK_UNLOCK_BSSID));

                    // rucno punjenje, ne izclavimo iz baze
                    wlock = new WLock("ba:75:d5:3d:a8:58", "WISPI.AP5a", "yourwifipass", "WPA-PSK/WPA2-PSK", "0000", 0, 0);

                    mWLockJobServiceProcessor.manualUnlock(wlock);
                }
            }
        }

        Log.i(TAG, "onStartJob(), returning from onStartJob(), should not end this process just yet!");

        return true; // tell OS that we have another process still working (in new Thread). within that Thread we will call jobFinished() once we are done
    }

    @Override
    public boolean onStopJob(JobParameters jobParameters) {
        if (jobParameters.getJobId() == MANUAL_WLOCK_JOB_ID) {
            Log.i(TAG, "onStopJob(), for manual job. " + this);
        }
        else {
            Log.i(TAG, "onStopJob(), for automatic job. " + this);
        }

        mInitialized = false;

        // prekini posao?
        //mWLockJobProcessorThread.interrupt();

        return false;
    }
}

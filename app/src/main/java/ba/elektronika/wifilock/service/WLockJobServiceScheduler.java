package ba.elektronika.wifilock.service;

import android.app.ActivityManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.util.Log;

/**
 * Created by Trax on 13/12/2017.
 *
 * as seen in: https://github.com/AltBeacon/android-beacon-library/blob/master/src/main/java/org/altbeacon/beacon/service/ScanJobScheduler.java
 */

public class WLockJobServiceScheduler {
    private static final String TAG = WLockJobServiceScheduler.class.getSimpleName();

    private static final Object SINGLETON_LOCK = new Object();
    private static volatile WLockJobServiceScheduler sInstance = null;

    public static WLockJobServiceScheduler getInstance() {
        WLockJobServiceScheduler instance = sInstance;
        if (instance == null) {
            synchronized (SINGLETON_LOCK) {
                instance = sInstance;
                if (instance == null) {
                    sInstance = instance = new WLockJobServiceScheduler();
                }
            }
        }
        return instance;
    }

    private WLockJobServiceScheduler() {
    }

    /**
     * Schedule a WLock Job to execute immediatelly or after some time.
     * @param context
     * @param startAfterMs
     * @param delayedStartMs
     * @return Returns JobScheduler.RESULT_FAILURE or JobScheduler.RESULT_SUCCESS
     */
    public int schedule(Context context, long startAfterMs, long delayedStartMs, PersistableBundle extra) {
        // Pre-OREO versions, bug fix
        // https://stackoverflow.com/questions/33235754/jobscheduler-posting-jobs-twice-not-expected/33293101#33293101
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            if (isWLockJobServiceAlreadyRunning(context)) {
                Log.i(TAG, "Service already running, not starting it again!");
                return JobScheduler.RESULT_FAILURE;
            }
        }

        ComponentName serviceComponent = new ComponentName(context, WLockJobService.class);

        int jobId = (startAfterMs <= 0) ? WLockJobService.MANUAL_WLOCK_JOB_ID : WLockJobService.AUTOMATIC_WLOCK_JOB_ID;

        JobInfo.Builder builder = new JobInfo.Builder(jobId, serviceComponent);
        builder.setOverrideDeadline(startAfterMs); // maximum delay
        builder.setMinimumLatency(delayedStartMs); // wait at least
        builder.setRequiresDeviceIdle(false); // don't care about the device state
        builder.setRequiresCharging(false); // don't care about the charger
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY); // don't care about the network
        builder.setPersisted(true); // this does not work, or what?

        if(extra != null) {
            builder.setExtras(extra);
        }

        JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        return jobScheduler.schedule(builder.build());
    }

    /**
     * Cancel particular or all scheduled jobs for this app.
     * @param context
     * @param jobId if 0 or less, cancels all jobs by this app.
     */
    public void cancel(Context context, int jobId) {
        JobScheduler jobScheduler = (JobScheduler)context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if(jobId <= 0) {
            jobScheduler.cancelAll();
        }
        else {
            jobScheduler.cancel(jobId);
        }
    }

    /**
     * For fixing the pre-Oreo bug of scheduling JobServices, so that only one instance is currently active.
     * @param context
     * @return
     */
    private boolean isWLockJobServiceAlreadyRunning(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (WLockJobService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tried to detect how many instances of one service are started within the service itself, but it always returns 1. Don't know why.
     * @param context
     * @return
     */
    @Deprecated
    public int getWLockJobServiceRunningCount(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        int cnt = 0;
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (WLockJobService.class.getName().equals(service.service.getClassName())) {
                cnt++;
            }
        }
        return cnt;
    }
}

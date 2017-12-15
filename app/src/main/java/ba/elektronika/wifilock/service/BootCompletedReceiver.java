package ba.elektronika.wifilock.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Created by Trax on 13/12/2017.
 */

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        WLockJobServiceScheduler.getInstance().schedule(context, 0, 1000, null); // delay start a bit
    }
}

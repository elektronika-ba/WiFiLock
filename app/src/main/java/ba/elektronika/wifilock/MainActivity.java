package ba.elektronika.wifilock;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import ba.elektronika.wifilock.service.WLockJobServiceProcessor;
import ba.elektronika.wifilock.service.WLockJobServiceScheduler;
import ba.elektronika.wifilock.service.WLockJobService;

public class MainActivity extends Activity {
    private final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 987234;
    private final String TAG = MainActivity.class.getSimpleName();

    private WLockServiceResultReceiver mWLockServiceResultReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // request for permissions at startup...
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }

        Button button1 = (Button)findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Log.i(TAG, "Stimam scheduler za 0ms...");
                PersistableBundle extra = new PersistableBundle();
                extra.putInt(WLockJobService.STARTUP_TASK, WLockJobService.STARTUP_TASK_UNLOCK);
                extra.putString(WLockJobService.STARTUP_TASK_UNLOCK_BSSID, "b0:75:d5:3d:a8:58");
                WLockJobServiceScheduler.getInstance().schedule(getApplicationContext(), 0, 0, extra);
            }
        });

        Button button2 = (Button)findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                WLockJobServiceScheduler.getInstance().cancel(getApplicationContext(), 0);
                Log.i(TAG, "Ugasio sve schedulere...");
            }
        });
    }

    @Override
    protected void onResume() {
        // broadcast listener of wifi events
        IntentFilter filter = new IntentFilter();
        filter.addCategory(Intent.CATEGORY_DEFAULT);
        filter.addAction(WLockJobServiceProcessor.SP_RESULT_ACTION);

        mWLockServiceResultReceiver = new WLockServiceResultReceiver();
        registerReceiver(mWLockServiceResultReceiver, filter);

        super.onResume();
    }

    @Override
    protected void onPause() {
        unregisterReceiver(mWLockServiceResultReceiver);

        super.onPause();
    }

    private class WLockServiceResultReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.hasExtra(WLockJobServiceProcessor.SP_RESULT_WHICH)) {
                switch(intent.getStringExtra(WLockJobServiceProcessor.SP_RESULT_WHICH)) {
                    case WLockJobServiceProcessor.SP_RESULT_ERROR:
                        Toast.makeText(getApplicationContext(), "Error code: " + intent.getIntExtra(WLockJobServiceProcessor.SP_RESULT_ERROR_CODE, -1), Toast.LENGTH_SHORT).show();
                        break;
                    default:

                }
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(getApplicationContext(), "THANKS! YOU CAN START USING THE APP NOW.", Toast.LENGTH_LONG).show();
        }
    }
}

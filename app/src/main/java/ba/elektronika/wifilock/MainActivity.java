package ba.elektronika.wifilock;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.Toast;

import ba.elektronika.wifilock.adapter.WLockAdapter;
import ba.elektronika.wifilock.database.DataSource;
import ba.elektronika.wifilock.service.WLockJobServiceProcessor;
import ba.elektronika.wifilock.service.WLockJobServiceScheduler;
import ba.elektronika.wifilock.service.WLockJobService;

public class MainActivity extends ListActivity {
    private final int PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION = 987234;
    private final String TAG = MainActivity.class.getSimpleName();

    private WLockServiceResultReceiver mWLockServiceResultReceiver;

    private WLockAdapter adapter = null;
    private DataSource mDataSource = null;
    private Context mContext = null;
    private SharedPreferences sharedPref;
    private ActionBar actionBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // request for permissions at startup...
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST_CODE_ACCESS_COARSE_LOCATION);
        }

        // Open the database if not already open
        if (mDataSource == null) {
            mDataSource = DataSource.getInstance(this);
        }

        sharedPref = this.getPreferences(Context.MODE_PRIVATE);

        mContext = this.getApplicationContext();

        adapter = null; // treba da bi onStart() inicijalno ucitao podatke
                        // onCreate() -> onStart() -> onRestoreInstanceState()
                        // -> onResume()

        actionBar = getActionBar();

        // Setuj onClick listener na listveiew
        getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
                WLock wlock = (WLock) adapter.getItem(position);
                //startBaseActivity(b, null);
                // TODO: kliknut item u listi
            }
        });

        /*
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
        */
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Create AlarmManager to repeatedly "ping" the Service at 1/2 the rate
        // Service expects (because we are using inexact repeating alarm). Do
        // that as long as we are started in foreground. This will initially
        // start the service!
        // Do not ping the Service if AuthToken is not set!
        if (mDataSource.getPubVar("auth_token").equals("")) {
            Toast.makeText(mContext, "Please set Auth Token to continue!", Toast.LENGTH_LONG).show();

            /*Intent intent = new Intent(this, CtrlSettingsActivity.class);
            startActivity(intent);*/
        }
        else {
            actionBar.setSubtitle("Hello world!");

            /*alarmMgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, ServicePingerAlarmReceiver.class);
            alarmIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
            alarmMgr.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis(), 2000, alarmIntent);
            Log.i(TAG, "AlarmManager set.");
            actionBar.setSubtitle("Connecting...");

            if (CommonStuff.getNetConnectivityStatus(context) == CommonStuff.NET_NOT_CONNECTED) {
                actionBar.setSubtitle("Disconnected");
                Toast.makeText(context, "No Internet Connectivity!", Toast.LENGTH_LONG).show();
            }*/
        }

        // kreiraj ako ga nema, ili samo updejtaj sa novim podacima ako ga ima
        if (adapter == null) {
            Log.i(TAG, "onStart() kreiram adapter jer je null");
            adapter = new WLockAdapter(this, mDataSource.getAllWLocks());
            setListAdapter(adapter);
        }
        else {
            Log.i(TAG, "onStart() ne kreiram adapter jer ga imam, samo refresham");
            refreshListView();
        }
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

    @Override
    public void onStop() {

        super.onStop();
    }

    // Refresh ListView by reloading data from scratch
    private void refreshListView() {
        adapter.refill(mDataSource.getAllWLocks());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_activity, menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_ctrl_settings) {
            /*Intent intent = new Intent(this, CtrlSettingsActivity.class);
            startActivity(intent);*/
        }
        else if (item.getItemId() == R.id.action_voice_command) {

        }
        else {
            return super.onOptionsItemSelected(item);
        }

        return true;
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

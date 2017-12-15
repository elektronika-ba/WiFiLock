package ba.elektronika.wifilock.service;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.ScanResult;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import java.util.List;

import ba.elektronika.wifilock.WLock;

/**
 * Created by Trax on 13/12/2017.
 */

public class WLockJobServiceProcessor implements Runnable {
    private final String TAG = WLockJobServiceProcessor.class.getSimpleName();

    // when scanning WIFI, this is used to filter SSIDs to identify *potential* Wifi Locks
    public static final String WIFILOCK_SSID_FILTER = ""; //"WIFILOCK_v1_([0-9A-F]){8}"; // put empty string if you don't want to filter anything

    public final static String SP_RESULT_ACTION = "ba.elektronika.wifilock.intent.action.SP_RESULT_ACTION";
    public final static String SP_RESULT_WHICH = SP_RESULT_ACTION + "_WHICH";
    public final static String SP_RESULT_ERROR = SP_RESULT_WHICH + "_ERROR";
    public final static String SP_RESULT_ERROR_CODE = SP_RESULT_ERROR + "_CODE";

    //SP_RESULT_ERROR_ACTION

    private List<ScanResult> mLastScanResults;
    private SupplicantState mLastSupplicantState;

    private Flag mFlag;
    private WifiManager mWifi;
    private WifiScanCompleteCallback mWifiScanCompleteCallback;
    private ProcessorFinishedCallback mProcessorFinishedCallback;
    private WLock mWLock;
    private Context mContext;
    private volatile String mRestoreWifiSSID;
    private ConnectivityManager mConnectivityManager;
    private ConnectivityManager.NetworkCallback mNetworkCallback;
    private int mLastWLockNetworkId;
    private Network mWLockNetwork;
    private WifiDisconnectedCallback mWifiDisconnectedCallback;
    private WifiConnectedCallback mWifiConnectedCallback;
    private IPAddressAssignedCallback mIPAddressAssignedCallback;

    WLockJobServiceProcessor(Context context, WifiManager wifi) {
        mContext = context;
        mWifi = wifi;
        mProcessorFinishedCallback = null;

        mLastScanResults = null;
        mLastSupplicantState = null;
        mFlag = Flag.IDLE;
        mWifiScanCompleteCallback = null;
        mWLock = null;
        mRestoreWifiSSID = null;
        mLastWLockNetworkId = -1;
        mWifiDisconnectedCallback = null;
        mWifiConnectedCallback = null;
        mIPAddressAssignedCallback = null;

        mConnectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mNetworkCallback = null;
        mWLockNetwork = null;
    }

    private enum Flag {
        IDLE,
        PROCESSING,

        MANUAL_UNLOCK_START,
        MANUAL_UNLOCK_EXECUTE_UNLOCK_API,

        WAIT_IP_ADDRESS,

        AUTOMATIC_MODE_RESTART,

        DIE,
    }

    // ************** interfaces for callbacks
    public interface ProcessorFinishedCallback {
        void call();
    }

    private interface WifiScanCompleteCallback {
        void call(List<ScanResult> scanResults);
    }

    private interface NetworkStateChangeCallback {
        void call();
    }

    private interface WifiDisconnectedCallback {
        void call();
    }

    private interface WifiConnectedCallback {
        void call();
    }

    private interface IPAddressAssignedCallback {
        void call();
    }

    // ************** public methods for getting wifi events
    public void setProcessorFinishedCallback(ProcessorFinishedCallback processorFinishedCallback) {
        mProcessorFinishedCallback = processorFinishedCallback;
    }

    public void sendScanResults(List<ScanResult> scanResults) {
        mLastScanResults = scanResults;

        if(mWifiScanCompleteCallback != null) {
            mWifiScanCompleteCallback.call(scanResults);
            mWifiScanCompleteCallback = null; // just once!
        }
    }

    public void sendSupplicantState(SupplicantState state) {
        mLastSupplicantState = state;

        // our internal wifi disconnected/unconnected callback
        if (mWifiDisconnectedCallback != null && (state == SupplicantState.DORMANT || state == SupplicantState.DISCONNECTED || state == SupplicantState.INACTIVE || state == SupplicantState.SCANNING)) {
            mWifiDisconnectedCallback.call();
            mWifiDisconnectedCallback = null; // just once!
        }

        // our internal wifi connected callback
        if (mWifiConnectedCallback != null && (state == SupplicantState.COMPLETED)) {
            mWifiConnectedCallback.call();
            mWifiConnectedCallback = null; // just once!
        }
    }

    // ************** internal helpers
    private void startScan(WifiScanCompleteCallback callback) throws RuntimeException {
        if(mWifiScanCompleteCallback != null) {
            throw new RuntimeException("WifiLock internal exception, can't start new scan until last one finishes.");
        }

        mFlag = Flag.PROCESSING;
        mWifiScanCompleteCallback = callback;
        mWifi.startScan();
    }

    private void onWifiDisconnected(WifiDisconnectedCallback callback) throws RuntimeException {
        if(mWifiDisconnectedCallback != null) {
            throw new RuntimeException("Can't wait for wifi disconnect, until last callback finishes first.");
        }

        mFlag = Flag.PROCESSING;
        mWifiDisconnectedCallback = callback;
    }

    private void onWifiConnected(WifiConnectedCallback callback) throws RuntimeException {
        if(mWifiConnectedCallback != null) {
            throw new RuntimeException("Can't wait for wifi connect, until last callback finishes first.");
        }

        mFlag = Flag.PROCESSING;
        mWifiConnectedCallback = callback;
    }

    /**
     * Attempts to connect to WLock and unlock it.
     * @param wlock if parameter is null, attempt will fail.
     * @return true on successful attempt, or false if processor is currently busy with some other task.
     */
    public boolean manualUnlock(WLock wlock) {
        if (mFlag != Flag.IDLE || wlock == null) return false;

        mWLock = wlock;
        mFlag = Flag.MANUAL_UNLOCK_START;

        return true;
    }

    public void continueAutomaticMode() {
        mFlag = Flag.AUTOMATIC_MODE_RESTART;
    }

    // ************** processor as a state machine
    @Override
    public void run() {
        long startStamp = System.currentTimeMillis();
        long runForMaxMs = 20000; // safe-guard, how long should we run?

        boolean run = true;
        Log.i(TAG, "### WLockProcessor running: " + this);

        if(mProcessorFinishedCallback == null) {
            run = false;
            Log.i(TAG, "### WLockProcessor HALTED, was not properly initialized. Please call setProcessorFinishedCallback() first. " + this);
        }

        // stating the machine here
        while(run) {

            if(mFlag == Flag.MANUAL_UNLOCK_START) {
                final WifiInfo connection = mWifi.getConnectionInfo();
                int connKind = 0; // 0=not connected to any wifi, 1=connected to other BSSID, 2=connected to required BSSID
                if(connection != null) {
                    // if current supplicant state is not finite, loop here until it is. we should not interrupt other wifi activity
                    SupplicantState ss = connection.getSupplicantState();
                    if (ss != SupplicantState.COMPLETED && ss != SupplicantState.DISCONNECTED && ss != SupplicantState.DORMANT && ss != SupplicantState.INACTIVE && ss != SupplicantState.SCANNING) {
                        Log.i(TAG, mFlag + "# waiting for supplicant state to be finite, currently it is (" + ss + ").");
                        continue; // next iteration please...
                    }

                    // if already connected to some wifi network that is not this BSSID, first scan to see
                    // if there is this BSSID available, so that we don't break the current connection for no reason
                    if (ss == SupplicantState.COMPLETED) {
                        connKind = 1;
                        if (mWLock.getBSSID().equals(connection.getBSSID())) {
                            connKind = 2;
                        }
                    }
                }

                switch (connKind) {
                    // not connected to any wifi
                    case 0: {
                        Log.i(TAG, mFlag + "# attempting WiFi connection...");

                        // remember that WE connected to the lock, so that WE can disconnect after finishing work
                        mLastWLockNetworkId = attemptWLockConnection(mWLock);

                        if (mLastWLockNetworkId == -1) {
                            Log.i(TAG, mFlag + "# error WiFi connection failed to setup!");

                            Bundle extra = new Bundle();
                            extra.putInt(SP_RESULT_ERROR_CODE, 1);
                            broadcastProcessorResult(SP_RESULT_ERROR, extra);
                            mFlag = Flag.DIE;
                        }
                        else {
                            Log.i(TAG, mFlag + "# WiFi connection successfully setup, creating onWifiConnected() callback.");
                            onWifiConnected(new WifiConnectedCallback() {
                                @Override
                                public void call() {
                                    Log.i(TAG, mFlag + "# finally connected to WIFI. Creating callback to verify we have IP, and are bound to this network.");

                                    // setup a callback
                                    mIPAddressAssignedCallback = new IPAddressAssignedCallback() {
                                        @Override
                                        public void call() {
                                            Log.i(TAG, mFlag + "# connected, ip address assigned, bound. Moving to API call state.");
                                            mFlag = Flag.MANUAL_UNLOCK_EXECUTE_UNLOCK_API;
                                        }
                                    };
                                    mFlag = Flag.WAIT_IP_ADDRESS;
                                }
                            });

                            // it could also happen that we don't connect to this SSID because it is out of range. we need to figure that out somehow too
                            // so lets start a wifi scan in parallel and if we don't find it, we can call it a day and end
                            Log.i(TAG, mFlag + "# also starting WiFi scan in parallel, expecgin results in callback...");
                            startScan(new WifiScanCompleteCallback() {
                                @Override
                                public void call(List<ScanResult> scanResults) {
                                    Log.i(TAG, mFlag + "# parallel WiFi scan callback called with results.");

                                    boolean found = false;
                                    for(int i=0; i<scanResults.size(); i++) {
                                        if(scanResults.get(i).BSSID.equals(mWLock.getBSSID())) {
                                            found = true;
                                            break;
                                        }
                                    }

                                    // required lock not found?
                                    if(!found) {
                                        Log.i(TAG, mFlag + "# aborting, requested BSSID not found.");

                                        Bundle extra = new Bundle();
                                        extra.putInt(SP_RESULT_ERROR_CODE, 2);
                                        broadcastProcessorResult(SP_RESULT_ERROR, extra);
                                        mFlag = Flag.DIE;

                                        mWifiConnectedCallback = null; // clear this from possible soon connection
                                    }
                                }
                            });
                        }
                    }
                    break;
                    // connected to some other wifi, need to remember networkId to restore it later
                    // in case wifi scan finds this BSSID we are looking for
                    case 1: {
                        // start the wifi scan and on finish, process the results
                        // this will internally change the flag to EXPECTING_SCAN_RESULTS which is cool
                        Log.i(TAG, mFlag + "# starting WiFi scan in callback...");
                        startScan(new WifiScanCompleteCallback() {
                            @Override
                            public void call(List<ScanResult> scanResults) {
                                Log.i(TAG, mFlag + "# WiFi scan callback called with results.");

                                boolean found = false;
                                for(int i=0; i<scanResults.size(); i++) {
                                   if(scanResults.get(i).BSSID.equals(mWLock.getBSSID())) {
                                       found = true;
                                       break;
                                   }
                                }

                                // required lock not found?
                                if(!found) {
                                    Log.i(TAG, mFlag + "# aborting, requested BSSID not found.");

                                    Bundle extra = new Bundle();
                                    extra.putInt(SP_RESULT_ERROR_CODE, 2);
                                    broadcastProcessorResult(SP_RESULT_ERROR, extra);
                                    mFlag = Flag.DIE;
                                }
                                // found, let the state machine take over in another iteration
                                else {
                                    mRestoreWifiSSID = connection.getSSID();
                                    Log.i(TAG, mFlag + "# requested BSSID found, remembering current WiFi connection (" + mRestoreWifiSSID + ") and disconnecting. Setting callback onWifiDisconnected().");

                                    onWifiDisconnected(new WifiDisconnectedCallback() {
                                        @Override
                                        public void call() {
                                            Log.i(TAG, mFlag + "# finally disconnected, going again to MANUAL_UNLOCK_START state.");
                                            mFlag = Flag.MANUAL_UNLOCK_START;
                                        }
                                    });

                                    // simply disconnect from this network, and let upper onWifiDisconnected callback takeover
                                    mWifi.disconnect();
                                }
                            }
                        });
                    }
                    break;
                    // already connected to our required BSSID, simply go to next state
                    case 2: {
                        Log.i(TAG, mFlag + "# already connected to required BSSID. Waiting for (verifying) IP address (just in case).");

                        // setup a callback
                        mIPAddressAssignedCallback = new IPAddressAssignedCallback() {
                            @Override
                            public void call() {
                                Log.i(TAG, mFlag + "# connected, ip address assigned, bound. Moving to API call state.");
                                mFlag = Flag.MANUAL_UNLOCK_EXECUTE_UNLOCK_API;
                            }
                        };
                        mFlag = Flag.WAIT_IP_ADDRESS;
                    }
                    break;
                }
            }
            else if(mFlag == Flag.WAIT_IP_ADDRESS) {
                final WifiInfo connection = mWifi.getConnectionInfo();
                if (connection == null) {
                    continue;
                }

                if (connection.getBSSID().equals(mWLock.getBSSID())) {
                    Log.i(TAG, mFlag + "# IP address received WHILE CONNECTED TO TARGET WIFI. Binding process to network, and waiting for callback...");

                    mFlag = Flag.PROCESSING;
                    bindToNetwork(new NetworkStateChangeCallback() {
                        @Override
                        public void call() {
                            Log.i(TAG, mFlag + "# process bound to network, calling callee's callback.");
                            if(mIPAddressAssignedCallback != null) {
                                mIPAddressAssignedCallback.call();
                                mIPAddressAssignedCallback = null; // only once!
                            }
                        }
                    });
                }
                else {
                    Log.i(TAG, mFlag + "# error, this should not have happened!");

                    Bundle extra = new Bundle();
                    extra.putInt(SP_RESULT_ERROR_CODE, 3);
                    broadcastProcessorResult(SP_RESULT_ERROR, extra);

                    mFlag = Flag.DIE;
                }
            }
            // connection established, execute the unlock API on the wlock
            else if(mFlag == Flag.MANUAL_UNLOCK_EXECUTE_UNLOCK_API) {
                Log.i(TAG, mFlag + "# ALL DONE, EXECUTING UNLOCK API CALL AND GOING IDLE.");

                if(mLastWLockNetworkId != -1) {
                    Log.i(TAG, mFlag + "# disconnecting and disabling WLock network...");

                    mWifi.disconnect();
                    mWifi.disableNetwork(mLastWLockNetworkId);
                    mWifi.removeNetwork(mLastWLockNetworkId);
                    mWifi.saveConfiguration();
                }

                // need to restore previous wifi connection?
                if(mRestoreWifiSSID != null) {
                    Log.i(TAG, mFlag + "# restoring original WiFi connection (" + mRestoreWifiSSID + ").");

                    List<WifiConfiguration> list = mWifi.getConfiguredNetworks();
                    for (WifiConfiguration i : list) {
                        //Log.i(TAG, mFlag + "# restoring, found: " + i.SSID);
                        if (i.SSID != null && mRestoreWifiSSID.equals(i.SSID)) {
                            mWifi.enableNetwork(i.networkId, true);
                            mWifi.reconnect();
                            break;
                        }
                    }

                    mRestoreWifiSSID = null;
                }

                Log.i(TAG, mFlag + "# all done, STOPPING THREAD.");
                mFlag = Flag.IDLE;
                run = false;
            }
            // state machine is waiting for some event, so we simply loop here
            else if(mFlag == Flag.PROCESSING) {
                // nothing happens here, waiting for something to happen
            }
            else if(mFlag == Flag.DIE) {
                Log.i(TAG, mFlag + "# DYING...");
                run = false;
            }

            // self-killer
            if((System.currentTimeMillis() - startStamp) > runForMaxMs) {
                Log.i(TAG, "### Time (" + runForMaxMs + "ms) expired, will stop thread: " + this);
                run = false;
            }

        }
        Log.i(TAG, "### WLockProcessor ended: " + this);

        // say we are done!
        mProcessorFinishedCallback.call();
    }

    // http://www.intentfilter.com/2016/08/programatically-connecting-to-wifi.html
    private void bindToNetwork(final NetworkStateChangeCallback listener) {
        NetworkRequest request = new NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build();
        mNetworkCallback = networkCallback(listener);
        mConnectivityManager.registerNetworkCallback(request, mNetworkCallback);
    }

    /// http://www.intentfilter.com/2016/08/programatically-connecting-to-wifi.html
    private ConnectivityManager.NetworkCallback networkCallback(final NetworkStateChangeCallback listener) {
        return new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                mConnectivityManager.unregisterNetworkCallback(this);
                mNetworkCallback = null;
                bindToRequiredNetwork(network);
                mWLockNetwork = network;
                listener.call(); // notify that we are done
            }
        };
    }

    // http://www.intentfilter.com/2016/08/programatically-connecting-to-wifi.html
    private void bindToRequiredNetwork(Network network) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mConnectivityManager.bindProcessToNetwork(network);
        } else {
            ConnectivityManager.setProcessDefaultNetwork(network);
        }
    }

    /**
     * Broadcast service processor result to anyone listening.
     * @param which
     * @param extra
     */
    private void broadcastProcessorResult(String which, Bundle extra) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(SP_RESULT_ACTION);
        broadcastIntent.addCategory(Intent.CATEGORY_DEFAULT);
        broadcastIntent.putExtras(extra);
        broadcastIntent.putExtra(SP_RESULT_WHICH, which);
        try {
            mContext.sendBroadcast(broadcastIntent);
        }
        catch (Exception e) {
            Log.e(TAG, "broadcastProcessorResult() Error: " + e.getMessage());
        }
    }

    /**
     * Attempts to connect to provided WLock.
     * @param wlock
     * @return Returns -1 on failure, networkId() on success.
     */
    private int attemptWLockConnection(WLock wlock) {
        WifiConfiguration wc = new WifiConfiguration();
        wc.SSID = "\"" + wlock.getSSID() + "\"";
        wc.BSSID = wlock.getBSSID();
        wc.status = WifiConfiguration.Status.ENABLED;

        boolean requiresPermission = false;
        if (wlock.getEncryptionType().contains("WPA2")) {
            wc.preSharedKey = "\"" + wlock.getPassword() + "\"";
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_EAP);
            wc.allowedProtocols.set(WifiConfiguration.Protocol.RSN);
            requiresPermission = true;
        }
        if (wlock.getEncryptionType().contains("WPA")) {
            wc.preSharedKey = "\"" + wlock.getPassword() + "\"";
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA_PSK);
            wc.allowedProtocols.set(WifiConfiguration.Protocol.WPA);
            requiresPermission = true;
        }
        if (wlock.getEncryptionType().contains("WEP")) {
            wc.wepKeys[0] = wlock.getPassword();
            wc.wepTxKeyIndex = 0;
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.WEP40);
            requiresPermission = true;
        }
        if (wlock.getEncryptionType().contains("TKIP")) {
            wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.TKIP);
            wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.TKIP);
            requiresPermission = true;
        }
        if (wlock.getEncryptionType().contains("CCMP")) {
            wc.allowedGroupCiphers.set(WifiConfiguration.GroupCipher.CCMP);
            wc.allowedKeyManagement.set(WifiConfiguration.PairwiseCipher.CCMP);
            requiresPermission = true;
        }

        // if passkey not required at all
        if (!requiresPermission) {
            wc.allowedPairwiseCiphers.set(WifiConfiguration.PairwiseCipher.NONE);
            wc.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        }

        int networkId = mWifi.addNetwork(wc);
        mWifi.disconnect();
        mWifi.enableNetwork(networkId, true); // this will initiate the wifi connection
        mWifi.saveConfiguration();

        /*WifiInfo connection = wifi.getConnectionInfo();
        if (connection != null) {
            SupplicantState ss = connection.getSupplicantState();
            //Log.d("SupllicantState**", ss.toString());

            if (ss.equals(SupplicantState.INACTIVE)) {
                wifi.removeNetwork(networkId);
                wifi.saveConfiguration();
                wifi.enableNetwork(wifi.getConnectionInfo().getNetworkId(), true);
            }
        }*/

        return networkId;
    }

}

package ba.elektronika.wifilock;

/**
 * Created by Trax on 09/12/2017.
 */

public class WLock {
    private int wlockid;
    private String ssid;
    private String bssid;
    private String password;
    private String encryption_type;
    private String pincode;
    private long created_on;
    private long last_accessed_on;
    private String title;

    public WLock(int wlockid, String bssid, String ssid, String password, String encryption_type, String pincode, long created_on, long last_accessed_on, String title) {
        setWlockid(wlockid);
        setBSSID(bssid);
        setSSID(ssid);
        setPincode(pincode);
        setPassword(password);
        setCreatedOn(created_on);
        setLastAccessedOn(last_accessed_on);
        setEncryptionType(encryption_type);
        setTitle(title);
    }

    public void setWlockid(int wlockid) { this.wlockid = wlockid; }

    public void setBSSID(String bssid) {
        this.bssid = bssid;
    }

    public void setSSID(String ssid) {
        this.ssid = ssid;
    }

    public void setPassword(String password) { this.password = password; }

    public void setEncryptionType(String encryption_type) { this.encryption_type = encryption_type; }

    public void setPincode(String pincode) {
        this.pincode = pincode;
    }

    public void setCreatedOn(long created_on) {
        this.created_on = created_on;
    }

    public void setLastAccessedOn(long last_accessed_on) { this.last_accessed_on = last_accessed_on; }

    public String getBSSID() {
        return this.bssid;
    }

    public String getSSID() {
        return this.ssid;
    }

    public String getPassword() { return password; }

    public String getEncryptionType() { return encryption_type; }

    public String getPincode() {
        return this.pincode;
    }

    public long getCreatedOn() {
        return created_on;
    }

    public long getLastAccessedOn() {
        return last_accessed_on;
    }

    public int getWlockid() { return wlockid; }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}

package ba.elektronika.wifilock.adapter;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

import ba.elektronika.wifilock.R;
import ba.elektronika.wifilock.WLock;
import ba.elektronika.wifilock.database.DataSource;

/**
 * Created by Trax on 16/12/2017.
 */

public class WLockAdapter extends ArrayAdapter<WLock> {

    /**************************************************************************
     * Static fields and methods                                              *
     **************************************************************************/
    static class ViewHolder {
        public View wlock_connection_status;
        public TextView wlock_title;
        public TextView wlock_subtitle;
        public View wlock_rssi;
    }

    /**************************************************************************
     * Private fields                                                         *
     **************************************************************************/
    private final Context context;
    private final ArrayList<WLock> wlocks;

    private DataSource dataSource;

    /**************************************************************************
     * Constructors                                                           *
     **************************************************************************/
    public WLockAdapter(Context context, ArrayList<WLock> wlocks) {
        super(context, R.layout.activity_main_item, wlocks);
        this.context = context;
        this.wlocks = wlocks;
        dataSource = DataSource.getInstance(this.context);
        setNotifyOnChange(true);
    }

    // Refreshanje adaptera sa novim podacima. Scroll position ce biti zadrzan -
    // super!
    public void refill(ArrayList<WLock> wlocks) {
        this.wlocks.clear();
        this.wlocks.addAll(wlocks);
        notifyDataSetChanged();
    }

    /**************************************************************************
     * Overridden parent methods                                              *
     **************************************************************************/
    /**
     * This method is called automatically when the user scrolls the ListView.
     * Updates the View of a single visible row, reflecting the list being
     * scrolled by the user.
     *
     * EXPLAINED HERE: http://www.youtube.com/watch?v=N6YdwzAvwOA
     */
    @Override
    public View getView(int position, View convert_view, ViewGroup parent) {
        View view = convert_view;

        WLock base = wlocks.get(position);
        // if view is null, the view is newly inflated.
        // else, re-assign new values.
        ViewHolder view_holder;
        if (view == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.activity_main_item, null);

            // Set up the ViewHolder.
            view_holder = new ViewHolder();

            view_holder.wlock_connection_status = (View) view.findViewById(R.id.wlock_connection_status);
            view_holder.wlock_title = (TextView) view.findViewById(R.id.wlock_title);
            view_holder.wlock_subtitle = (TextView) view.findViewById(R.id.wlock_subtitle);
            view_holder.wlock_rssi = (View) view.findViewById(R.id.wlock_rssi);

            // Store the holder with the view.
            view.setTag(view_holder);
        }
        else {
            view_holder = (ViewHolder) view.getTag();
        }

        view_holder.wlock_connection_status.setBackgroundColor(context.getResources().getColor(R.color.wlockDisconnected));
        view_holder.wlock_title.setText(base.getTitle());
        view_holder.wlock_subtitle.setText(base.getSSID());
        //view_holder.wlock_rssi.setBackgroundColor()...

        return view;
    }
}

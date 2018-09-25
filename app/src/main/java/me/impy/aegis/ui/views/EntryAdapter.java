package me.impy.aegis.ui.views;

import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import me.impy.aegis.R;
import me.impy.aegis.db.DatabaseEntry;
import me.impy.aegis.helpers.ItemTouchHelperAdapter;
import me.impy.aegis.otp.HotpInfo;
import me.impy.aegis.otp.OtpInfo;
import me.impy.aegis.otp.OtpInfoException;
import me.impy.aegis.otp.TotpInfo;

public class EntryAdapter extends RecyclerView.Adapter<EntryHolder> implements ItemTouchHelperAdapter {
    private static final int HEADER = 1;
    private static final int NORMAL_ITEM = 2;

    private List<DatabaseEntry> _entries;
    private static Listener _listener;
    private boolean _showAccountName;

    // keeps track of the viewholders that are currently bound
    private List<EntryHolder> _holders;

    public EntryAdapter(Listener listener) {
        _entries = new ArrayList<>();
        _entries.add(new DatabaseEntry(null));
        _holders = new ArrayList<>();
        _listener = listener;
    }

    public void setShowAccountName(boolean showAccountName) {
        _showAccountName = showAccountName;
    }

    public void addEntry(DatabaseEntry entry) {
        _entries.add(entry);

        int position = getItemCount() - 1;
        if (position == 0) {
            notifyDataSetChanged();
        } else {
            notifyItemInserted(position);
        }
    }

    public void addEntries(List<DatabaseEntry> entries) {
        _entries.addAll(entries);
        notifyDataSetChanged();
    }

    public void removeEntry(DatabaseEntry entry) {
        entry = getEntryByUUID(entry.getUUID());
        int position = _entries.indexOf(entry);
        _entries.remove(position);
        notifyItemRemoved(position);
    }

    public void clearEntries() {
        _entries.clear();
        notifyDataSetChanged();
    }

    public void replaceEntry(DatabaseEntry newEntry) {
        DatabaseEntry oldEntry = getEntryByUUID(newEntry.getUUID());
        int position = _entries.indexOf(oldEntry);
        _entries.set(position, newEntry);
        notifyItemChanged(position);
    }

    private DatabaseEntry getEntryByUUID(UUID uuid) {
        for (DatabaseEntry entry : _entries) {
            if (entry.getUUID().equals(uuid)) {
                return entry;
            }
        }
        throw new AssertionError("no entry found with the same id");
    }

    public void refresh(boolean hard) {
        if (hard) {
            notifyDataSetChanged();
        } else {
            for (EntryHolder holder : _holders) {
                holder.refreshCode();
            }
        }
    }

    @Override
    public void onItemDismiss(int position) {

    }

    @Override
    public void onItemDrop(int position) {
        _listener.onEntryDrop(_entries.get(position));
    }

    @Override
    public void onItemMove(int firstPosition, int secondPosition) {
        // notify the database first
        _listener.onEntryMove(_entries.get(firstPosition), _entries.get(secondPosition));

        // update our side of things
        Collections.swap(_entries, firstPosition, secondPosition);
        notifyItemMoved(firstPosition, secondPosition);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return HEADER;
        } else {
            return NORMAL_ITEM;
        }
    }

    @Override
    public EntryHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        if (viewType == HEADER) {
            view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_title, parent, false);
            return new EntryHolder(view, true);
        } else {
             view = LayoutInflater.from(parent.getContext()).inflate(R.layout.card_entry, parent, false);
        }

        return new EntryHolder(view, false);
    }

    @Override
    public void onViewRecycled(EntryHolder holder) {
        holder.stopRefreshLoop();
        _holders.remove(holder);
    }

    @Override
    public void onBindViewHolder(final EntryHolder holder, int position) {
        if (getItemViewType(position) == NORMAL_ITEM) {

            DatabaseEntry entry = _entries.get(position);
            boolean showProgress = !isPeriodUniform() && entry.getInfo() instanceof TotpInfo;
            holder.setData(entry, _showAccountName, showProgress);
            if (showProgress) {
                holder.startRefreshLoop();
            }

            holder.itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int position = holder.getAdapterPosition();
                    _listener.onEntryClick(_entries.get(position));
                }
            });
            holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    int position = holder.getAdapterPosition();
                    return _listener.onLongEntryClick(_entries.get(position));
                }
            });
            holder.setOnRefreshClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // this will only be called if the entry is of type HotpInfo
                    try {
                        ((HotpInfo) entry.getInfo()).incrementCounter();
                    } catch (OtpInfoException e) {
                        throw new RuntimeException(e);
                    }

                    // notify the listener that the counter has been incremented
                    // this gives it a chance to save the database
                    _listener.onEntryChange(entry);

                    // finally, refresh the code in the UI
                    holder.refreshCode();
                }
            });

            _holders.add(holder);
        }
    }

    public int getUniformPeriod() {
        List<TotpInfo> infos = new ArrayList<>();
        for (DatabaseEntry entry : _entries) {
            OtpInfo info = entry.getInfo();
            if (info instanceof TotpInfo) {
                infos.add((TotpInfo) info);
            }
        }

        if (infos.isEmpty()) {
            return -1;
        }

        int period = infos.get(0).getPeriod();
        for (TotpInfo info : infos) {
            if (period != info.getPeriod()) {
                return -1;
            }
        }

        return period;
    }

    public boolean isPeriodUniform() {
        return getUniformPeriod() != -1;
    }

    @Override
    public int getItemCount() {
        return _entries.size();
    }

    public interface Listener {
        void onEntryClick(DatabaseEntry entry);
        boolean onLongEntryClick(DatabaseEntry entry);
        void onEntryMove(DatabaseEntry entry1, DatabaseEntry entry2);
        void onEntryDrop(DatabaseEntry entry);
        void onEntryChange(DatabaseEntry entry);
    }
}

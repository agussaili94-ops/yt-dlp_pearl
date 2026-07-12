package com.m3u8dl;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.yausername.youtubedl_android.YoutubeDL;
import com.arthenica.ffmpegkit.FFmpegKit;
import java.util.ArrayList;
import java.util.List;

public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {
    private List<DownloadItem> downloadList = new ArrayList<>();

    public void addDownload(DownloadItem item) {
        downloadList.add(item);
        notifyItemInserted(downloadList.size() - 1);
    }

    public void removeDownload(DownloadItem item) {
        int index = downloadList.indexOf(item);
        if (index != -1) {
            downloadList.remove(index);
            notifyItemRemoved(index);
        }
    }

    public List<DownloadItem> getItems() { 
        return downloadList; 
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_download, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (!payloads.isEmpty()) { 
            bindData(holder, downloadList.get(position)); 
        } else { 
            super.onBindViewHolder(holder, position, payloads); 
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadItem item = downloadList.get(position);
        bindData(holder, item);
        
        holder.btnStop.setOnClickListener(v -> handleStopProcess(holder));
        holder.itemView.setOnLongClickListener(v -> {
            handleStopProcess(holder);
            removeDownload(item);
            if (v.getContext() instanceof MainActivity) { 
                ((MainActivity) v.getContext()).updateCounter(); 
            }
            return true;
        });
    }

    private void bindData(ViewHolder holder, DownloadItem item) {
        holder.title.setText(item.fileName);
        holder.duration.setText(item.durationStr);
        holder.size.setText(item.sizeStr);
        holder.speed.setText(item.speedStr);

        holder.progressBar.setIndeterminate(false);
        holder.progressBar.setProgress(item.progress);

        if (item.isFinished || item.isStopped) {
            holder.btnStop.setVisibility(View.GONE);
        } else {
            holder.btnStop.setVisibility(View.VISIBLE);
            holder.btnStop.setEnabled(true);
        }
    }

    private void handleStopProcess(ViewHolder holder) {
        int currentPos = holder.getAdapterPosition();
        if (currentPos != RecyclerView.NO_POSITION) {
            DownloadItem clickedItem = downloadList.get(currentPos);
            holder.speed.setText("Canceling...");
            
            // PERBAIKAN: Sembunyikan tombol secara langsung alih-alih mengubah alpha untuk menghindari crash UI Thread
            holder.btnStop.setVisibility(View.GONE);
            clickedItem.isStopped = true;
            
            new Thread(() -> {
                try {
                    if (clickedItem.ffmpegSessionId != null) { 
                        FFmpegKit.cancel(clickedItem.ffmpegSessionId); 
                    } else {
                        YoutubeDL.getInstance().destroyProcessById(clickedItem.processId);
                        RecorderUtils.killZombieFFmpeg(clickedItem.fileName);
                    }
                } catch (Exception e) { 
                    e.printStackTrace(); 
                }
            }).start();
        }
    }

    @Override 
    public int getItemCount() { 
        return downloadList.size(); 
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView title, duration, size, speed;
        View btnStop; // Memastikan referensi sebagai View biasa (berlaku untuk RelativeLayout XML)
        ProgressBar progressBar;
        
        public ViewHolder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.item_title);
            duration = itemView.findViewById(R.id.item_duration);
            size = itemView.findViewById(R.id.item_size);
            speed = itemView.findViewById(R.id.item_speed);
            btnStop = itemView.findViewById(R.id.btn_stop);
            progressBar = itemView.findViewById(R.id.item_progress);
        }
    }
}

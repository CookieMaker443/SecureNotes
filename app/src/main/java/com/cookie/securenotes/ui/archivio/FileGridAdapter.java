package com.cookie.securenotes.ui.archivio;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.db.FileEntry;

import java.util.ArrayList;
import java.util.List;

public class FileGridAdapter extends RecyclerView.Adapter<FileGridAdapter.GridViewHolder> implements FilesAdapter {

    public interface ThumbnailLoader {
        /** Deve richiamare callback.onReady(bitmap) sul thread UI (bitmap può essere null). */
        void load(FileEntry entry, ThumbnailReadyCallback callback);
    }

    public interface ThumbnailReadyCallback {
        void onReady(Bitmap bitmap);
    }

    private final List<FileEntry> items = new ArrayList<>();
    private final OnFileClickListener listener;
    private final ThumbnailLoader thumbnailLoader;

    public FileGridAdapter(OnFileClickListener listener, ThumbnailLoader thumbnailLoader) {
        this.listener = listener;
        this.thumbnailLoader = thumbnailLoader;
    }

    @Override
    public void submitList(List<FileEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public GridViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file_grid, parent, false);
        return new GridViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull GridViewHolder holder, int position) {
        FileEntry entry = items.get(position);

        holder.nome.setText(entry.nomeOriginale);
        holder.thumbnail.setImageDrawable(null); // reset: evita di mostrare per un istante la miniatura sbagliata durante lo scroll veloce
        holder.playIcon.setVisibility(View.GONE);

        if ("video".equals(entry.tipo)) {
            holder.playIcon.setVisibility(View.VISIBLE);
        } else {
            long boundEntryId = entry.id;
            thumbnailLoader.load(entry, bitmap -> {
                // Per lo scroll veloce, l'holder potrebbe essere già stato riciclato
                // per un'altra riga nel frattempo: verifica che stia ancora
                // mostrando lo stesso file prima di applicare il bitmap.
                int currentPos = holder.getBindingAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION && items.get(currentPos).id == boundEntryId) {
                    holder.thumbnail.setImageBitmap(bitmap);
                }
            });
        }

        holder.itemView.setOnClickListener(v -> listener.onFileClick(entry));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onFileLongClick(entry);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class GridViewHolder extends RecyclerView.ViewHolder {
        final ImageView thumbnail;
        final ImageView playIcon;
        final TextView nome;

        GridViewHolder(@NonNull View itemView) {
            super(itemView);
            thumbnail = itemView.findViewById(R.id.thumbnailImage);
            playIcon = itemView.findViewById(R.id.videoPlayIcon);
            nome = itemView.findViewById(R.id.fileNameOverlay);
        }
    }
}
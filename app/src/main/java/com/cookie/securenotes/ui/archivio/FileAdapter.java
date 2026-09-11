package com.cookie.securenotes.ui.archivio;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.db.FileEntry;

import java.util.ArrayList;
import java.util.List;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> implements FilesAdapter
{

    private final List<FileEntry> items = new ArrayList<>();
    private final OnFileClickListener listener;

    public FileAdapter(OnFileClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<FileEntry> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_file, parent, false);
        return new FileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FileViewHolder holder, int position) {
        FileEntry entry = items.get(position);
        holder.nome.setText(entry.nomeOriginale);
        holder.itemView.setOnClickListener(v -> listener.onFileClick(entry));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onFileLongClick(entry);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class FileViewHolder extends RecyclerView.ViewHolder {
        final TextView nome;
        FileViewHolder(@NonNull View itemView) {
            super(itemView);
            nome = itemView.findViewById(R.id.fileNameText);
        }
    }
}
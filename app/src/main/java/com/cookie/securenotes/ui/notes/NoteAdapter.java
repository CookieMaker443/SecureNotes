package com.cookie.securenotes.ui.notes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.cookie.securenotes.R;
import com.cookie.securenotes.data.local.db.Nota;

import java.util.ArrayList;
import java.util.List;

public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    public interface OnNoteClickListener {
        void onNoteClick(Nota nota);
        void onNoteLongClick(Nota nota); // per ora usato per eliminazione singola
    }

    private final List<Nota> items = new ArrayList<>();
    private final OnNoteClickListener listener;

    public NoteAdapter(OnNoteClickListener listener) {
        this.listener = listener;
    }

    public void submitList(List<Nota> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged(); // dataset piccolo (note locali): va bene senza DiffUtil per ora
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        Nota nota = items.get(position);
        holder.titolo.setText(nota.titolo);
        holder.itemView.setOnClickListener(v -> listener.onNoteClick(nota));
        holder.itemView.setOnLongClickListener(v -> {
            listener.onNoteLongClick(nota);
            return true;
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        final TextView titolo;
        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            titolo = itemView.findViewById(R.id.noteTitleText);
        }
    }
}
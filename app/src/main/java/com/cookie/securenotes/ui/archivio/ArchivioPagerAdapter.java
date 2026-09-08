package com.cookie.securenotes.ui.archivio;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class ArchivioPagerAdapter extends FragmentStateAdapter {

    public ArchivioPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0: return new FotoFragment();
            case 1: return new VideoFragment();
            case 2: return new PdfFragment();
            default: throw new IllegalArgumentException("Posizione non valida: " + position);
        }
    }

    @Override
    public int getItemCount() { return 3; }
}
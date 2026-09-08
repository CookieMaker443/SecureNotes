package com.cookie.securenotes.ui.archivio;

import android.os.Bundle;

import androidx.viewpager2.widget.ViewPager2;

import com.cookie.securenotes.R;
import com.cookie.securenotes.ui.common.BaseActivity;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class ArchivioActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_archivio);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(getString(R.string.title_archivio));
        }

        ViewPager2 viewPager = findViewById(R.id.archivioViewPager);
        TabLayout tabLayout = findViewById(R.id.archivioTabLayout);

        viewPager.setAdapter(new ArchivioPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0: tab.setText(getString(R.string.tab_foto)); break;
                case 1: tab.setText(getString(R.string.tab_video)); break;
                case 2: tab.setText(getString(R.string.tab_pdf)); break;
            }
        }).attach();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
package com.spiderman4u.hyperdex.ui;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.spiderman4u.hyperdex.R;
import com.spiderman4u.hyperdex.config.ConfigManager;
import com.spiderman4u.hyperdex.config.Constants;

public class MainActivity extends AppCompatActivity {

    private TextView tvModeTitle;
    private TextView tvModeDesc;
    private MaterialSwitch switchMainMode;
    private MaterialSwitch switchEdgeToEdge;
    private MaterialSwitch switchStartMenu;
    private MaterialSwitch switchDexButtons;
    private MaterialButton btnRestart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvModeTitle = findViewById(R.id.tv_mode_title);
        tvModeDesc = findViewById(R.id.tv_mode_desc);
        switchMainMode = findViewById(R.id.switch_main_mode);
        switchEdgeToEdge = findViewById(R.id.switch_edge_to_edge);
        switchStartMenu = findViewById(R.id.switch_start_menu);
        switchDexButtons = findViewById(R.id.switch_dex_buttons);
        btnRestart = findViewById(R.id.btn_restart);

        loadPreferences();
        setupListeners();
    }

    private void loadPreferences() {
        boolean isDeX = ConfigManager.isDeXModeEnabled(this);
        switchMainMode.setChecked(isDeX);
        updateModeText(isDeX);

        switchEdgeToEdge.setChecked(ConfigManager.isEdgeToEdgeEnabled(this));
        switchStartMenu.setChecked(ConfigManager.isStartMenuEnabled(this));
        switchDexButtons.setChecked(ConfigManager.isDeXButtonsEnabled(this));

        updateSubSwitchesEnabled(isDeX);
    }

    private void setupListeners() {
        switchMainMode.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setDeXModeEnabled(this, isChecked);
            updateModeText(isChecked);
            updateSubSwitchesEnabled(isChecked);
        });

        switchEdgeToEdge.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_EDGE_TO_EDGE, isChecked);
        });

        switchStartMenu.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_START_MENU, isChecked);
        });

        switchDexButtons.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_DEX_BUTTONS, isChecked);
        });

        btnRestart.setOnClickListener(v -> restartLauncher());
    }

    private void updateModeText(boolean isDeX) {
        if (isDeX) {
            tvModeTitle.setText("Samsung Classic DeX");
            tvModeTitle.setTextColor(getColor(R.color.secondary));
            tvModeDesc.setText(R.string.desc_mode_dex);
        } else {
            tvModeTitle.setText("Stock Workstation Mode");
            tvModeTitle.setTextColor(getColor(R.color.text_primary));
            tvModeDesc.setText(R.string.desc_mode_workstation);
        }
    }

    private void updateSubSwitchesEnabled(boolean isDeX) {
        switchEdgeToEdge.setEnabled(isDeX);
        switchStartMenu.setEnabled(isDeX);
        switchDexButtons.setEnabled(isDeX);
    }

    private void restartLauncher() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f com.miui.home"});
            process.waitFor();
            Toast.makeText(this, R.string.toast_restarted, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not restart launcher: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

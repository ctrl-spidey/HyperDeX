package com.spiderman4u.hyperdex.ui;

import android.os.Bundle;
import android.widget.SeekBar;
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
    private TextView tvWorkstationStatus;
    private MaterialSwitch switchMainMode;
    private MaterialSwitch switchEdgeToEdge;
    private MaterialSwitch switchStartMenu;
    private MaterialSwitch switchNavButtons;
    private MaterialSwitch switchSearchButton;
    private MaterialSwitch switchFloatingPopups;
    private MaterialSwitch switchApplyNormal;
    private MaterialSwitch switchDexButtons;

    private TextView tvTaskbarHeightVal;
    private SeekBar seekTaskbarHeight;
    private TextView tvIconSizeVal;
    private SeekBar seekIconSize;
    private TextView tvIconSpacingVal;
    private SeekBar seekIconSpacing;
    private TextView tvTaskbarOpacityVal;
    private SeekBar seekTaskbarOpacity;
    private MaterialButton btnResetDefaults;

    private MaterialButton btnRestart;

    private static final int MIN_TASKBAR_HEIGHT = 36;
    private static final int MAX_TASKBAR_HEIGHT = 64;
    private static final int MIN_ICON_SIZE = 24;
    private static final int MAX_ICON_SIZE = 48;
    private static final int MIN_ICON_SPACING = 0;
    private static final int MAX_ICON_SPACING = 16;
    private static final int MIN_OPACITY = 40;
    private static final int MAX_OPACITY = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvModeTitle = findViewById(R.id.tv_mode_title);
        tvModeDesc = findViewById(R.id.tv_mode_desc);
        tvWorkstationStatus = findViewById(R.id.tv_workstation_status);

        switchMainMode = findViewById(R.id.switch_main_mode);
        switchEdgeToEdge = findViewById(R.id.switch_edge_to_edge);
        switchStartMenu = findViewById(R.id.switch_start_menu);
        switchNavButtons = findViewById(R.id.switch_nav_buttons);
        switchSearchButton = findViewById(R.id.switch_search_button);
        switchFloatingPopups = findViewById(R.id.switch_floating_popups);
        switchApplyNormal = findViewById(R.id.switch_apply_normal);
        switchDexButtons = findViewById(R.id.switch_dex_buttons);

        tvTaskbarHeightVal = findViewById(R.id.tv_taskbar_height_val);
        seekTaskbarHeight = findViewById(R.id.seek_taskbar_height);
        tvIconSizeVal = findViewById(R.id.tv_icon_size_val);
        seekIconSize = findViewById(R.id.seek_icon_size);
        tvIconSpacingVal = findViewById(R.id.tv_icon_spacing_val);
        seekIconSpacing = findViewById(R.id.seek_icon_spacing);
        tvTaskbarOpacityVal = findViewById(R.id.tv_taskbar_opacity_val);
        seekTaskbarOpacity = findViewById(R.id.seek_taskbar_opacity);
        btnResetDefaults = findViewById(R.id.btn_reset_defaults);

        btnRestart = findViewById(R.id.btn_restart);

        // Configure seek bar ranges
        seekTaskbarHeight.setMax(MAX_TASKBAR_HEIGHT - MIN_TASKBAR_HEIGHT);
        seekIconSize.setMax(MAX_ICON_SIZE - MIN_ICON_SIZE);
        seekIconSpacing.setMax(MAX_ICON_SPACING - MIN_ICON_SPACING);
        seekTaskbarOpacity.setMax(MAX_OPACITY - MIN_OPACITY);

        loadPreferences();
        setupListeners();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateWorkstationStatus();
    }

    private void loadPreferences() {
        boolean isDeX = ConfigManager.isDeXModeEnabled(this);
        switchMainMode.setChecked(isDeX);
        updateModeText(isDeX);

        switchEdgeToEdge.setChecked(ConfigManager.isEdgeToEdgeEnabled(this));
        switchStartMenu.setChecked(ConfigManager.isStartMenuEnabled(this));
        switchNavButtons.setChecked(ConfigManager.isShowNavButtons(this));
        switchSearchButton.setChecked(ConfigManager.isShowSearchButton(this));
        switchFloatingPopups.setChecked(ConfigManager.isFloatingPopupsEnabled(this));
        switchApplyNormal.setChecked(ConfigManager.isApplyInNormalModeEnabled(this));
        switchDexButtons.setChecked(ConfigManager.isDeXButtonsEnabled(this));

        // Load Sliders
        int height = ConfigManager.getTaskbarHeight(this);
        seekTaskbarHeight.setProgress(Math.max(0, height - MIN_TASKBAR_HEIGHT));
        tvTaskbarHeightVal.setText(height + " dp");

        int iconSize = ConfigManager.getIconSize(this);
        seekIconSize.setProgress(Math.max(0, iconSize - MIN_ICON_SIZE));
        tvIconSizeVal.setText(iconSize + " dp");

        int spacing = ConfigManager.getIconSpacing(this);
        seekIconSpacing.setProgress(Math.max(0, spacing - MIN_ICON_SPACING));
        tvIconSpacingVal.setText(spacing + " dp");

        int opacity = ConfigManager.getTaskbarOpacity(this);
        seekTaskbarOpacity.setProgress(Math.max(0, opacity - MIN_OPACITY));
        tvTaskbarOpacityVal.setText(opacity + "%");

        updateSubSwitchesEnabled(isDeX);
        updateWorkstationStatus();
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

        switchNavButtons.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_SHOW_NAV_BUTTONS, isChecked);
        });

        switchSearchButton.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_SHOW_SEARCH_BUTTON, isChecked);
        });

        switchFloatingPopups.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_FLOATING_POPUPS, isChecked);
        });

        switchApplyNormal.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_APPLY_NORMAL, isChecked);
        });

        switchDexButtons.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_DEX_BUTTONS, isChecked);
        });

        // Sliders Listeners
        seekTaskbarHeight.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = MIN_TASKBAR_HEIGHT + progress;
                tvTaskbarHeightVal.setText(val + " dp");
                if (fromUser) {
                    ConfigManager.setInt(MainActivity.this, Constants.PREF_TASKBAR_HEIGHT, val);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = MIN_ICON_SIZE + progress;
                tvIconSizeVal.setText(val + " dp");
                if (fromUser) {
                    ConfigManager.setInt(MainActivity.this, Constants.PREF_ICON_SIZE, val);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekIconSpacing.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = MIN_ICON_SPACING + progress;
                tvIconSpacingVal.setText(val + " dp");
                if (fromUser) {
                    ConfigManager.setInt(MainActivity.this, Constants.PREF_ICON_SPACING, val);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekTaskbarOpacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int val = MIN_OPACITY + progress;
                tvTaskbarOpacityVal.setText(val + "%");
                if (fromUser) {
                    ConfigManager.setInt(MainActivity.this, Constants.PREF_TASKBAR_OPACITY, val);
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnResetDefaults.setOnClickListener(v -> {
            ConfigManager.resetTaskbarDefaults(this);
            loadPreferences();
            Toast.makeText(this, R.string.toast_reset_defaults, Toast.LENGTH_SHORT).show();
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
        switchNavButtons.setEnabled(isDeX);
        switchSearchButton.setEnabled(isDeX);
        switchFloatingPopups.setEnabled(isDeX);
        switchApplyNormal.setEnabled(isDeX);
        switchDexButtons.setEnabled(isDeX);
        seekTaskbarHeight.setEnabled(isDeX);
        seekIconSize.setEnabled(isDeX);
        seekIconSpacing.setEnabled(isDeX);
        seekTaskbarOpacity.setEnabled(isDeX);
        btnResetDefaults.setEnabled(isDeX);
    }

    private void updateWorkstationStatus() {
        boolean isWorkstation = ConfigManager.isWorkstationMode(this);
        if (isWorkstation) {
            tvWorkstationStatus.setText(R.string.status_workstation_active);
            tvWorkstationStatus.setTextColor(getColor(R.color.secondary));
        } else {
            tvWorkstationStatus.setText(R.string.status_workstation_inactive);
            tvWorkstationStatus.setTextColor(getColor(R.color.text_secondary));
        }
    }

    private void restartLauncher() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f com.miui.home && pkill -f com.android.systemui"});
            process.waitFor();
            Toast.makeText(this, R.string.toast_restarted, Toast.LENGTH_SHORT).show();
            updateWorkstationStatus();
        } catch (Exception e) {
            Toast.makeText(this, "Could not restart: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}

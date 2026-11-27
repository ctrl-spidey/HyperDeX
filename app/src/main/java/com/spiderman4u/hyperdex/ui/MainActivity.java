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
    private MaterialSwitch switchMainMode;
    private MaterialSwitch switchStartMenu;
    private MaterialSwitch switchNavButtons;
    private MaterialSwitch switchSearchButton;
    private MaterialSwitch switchFloatingPopups;
    private MaterialSwitch switchWindowControls;

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
    private static final int MAX_TASKBAR_HEIGHT = 60;
    private static final int MIN_ICON_SIZE = 24;
    private static final int MAX_ICON_SIZE = 44;
    private static final int MIN_ICON_SPACING = 2;
    private static final int MAX_ICON_SPACING = 16;
    private static final int MIN_OPACITY = 40;
    private static final int MAX_OPACITY = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvModeTitle = findViewById(R.id.tv_mode_title);
        tvModeDesc = findViewById(R.id.tv_mode_desc);

        switchMainMode = findViewById(R.id.switch_main_mode);
        switchStartMenu = findViewById(R.id.switch_start_menu);
        switchNavButtons = findViewById(R.id.switch_nav_buttons);
        switchSearchButton = findViewById(R.id.switch_search_button);
        switchFloatingPopups = findViewById(R.id.switch_floating_popups);
        switchWindowControls = findViewById(R.id.switch_window_controls);

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
        TextView tvFooterCredit = findViewById(R.id.tv_footer_credit);
        if (tvFooterCredit != null) {
            tvFooterCredit.setText(getString(R.string.footer_credit, getCurrentVersionName()));
        }

        seekTaskbarHeight.setMax(MAX_TASKBAR_HEIGHT - MIN_TASKBAR_HEIGHT);
        seekIconSize.setMax(MAX_ICON_SIZE - MIN_ICON_SIZE);
        seekIconSpacing.setMax(MAX_ICON_SPACING - MIN_ICON_SPACING);
        seekTaskbarOpacity.setMax(MAX_OPACITY - MIN_OPACITY);

        loadPreferences();
        setupListeners();
    }

    private void loadPreferences() {
        boolean isDeX = ConfigManager.isDeXModeEnabled(this);
        switchMainMode.setChecked(isDeX);
        updateModeText(isDeX);

        switchStartMenu.setChecked(ConfigManager.isStartMenuEnabled(this));
        switchNavButtons.setChecked(ConfigManager.isShowNavButtons(this));
        switchSearchButton.setChecked(ConfigManager.isShowSearchButton(this));
        switchFloatingPopups.setChecked(ConfigManager.isFloatingPopupsEnabled(this));
        switchWindowControls.setChecked(ConfigManager.isClassicWindowControlsEnabled(this));

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
    }

    private void setupListeners() {
        switchMainMode.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setDeXModeEnabled(this, isChecked);
            updateModeText(isChecked);
            updateSubSwitchesEnabled(isChecked);
            Toast.makeText(this, "DeX Mode " + (isChecked ? "Enabled" : "Disabled") + " • @spideyHyperOS", Toast.LENGTH_SHORT).show();
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

        switchWindowControls.setOnCheckedChangeListener((btn, isChecked) -> {
            ConfigManager.setBoolean(this, Constants.PREF_WINDOW_CONTROLS, isChecked);
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
                int maxAllowedIcon = Math.max(MIN_ICON_SIZE, val - 8);
                seekIconSize.setMax(Math.max(1, maxAllowedIcon - MIN_ICON_SIZE));
                int currentIcon = ConfigManager.getIconSize(MainActivity.this);
                if (currentIcon > maxAllowedIcon) {
                    ConfigManager.setInt(MainActivity.this, Constants.PREF_ICON_SIZE, maxAllowedIcon);
                    seekIconSize.setProgress(maxAllowedIcon - MIN_ICON_SIZE);
                    tvIconSizeVal.setText(maxAllowedIcon + " dp");
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        seekIconSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int currentH = ConfigManager.getTaskbarHeight(MainActivity.this);
                int maxAllowedIcon = Math.max(MIN_ICON_SIZE, currentH - 8);
                int val = Math.min(maxAllowedIcon, MIN_ICON_SIZE + progress);
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
            tvModeTitle.setText(R.string.title_active_mode);
            tvModeDesc.setText(R.string.desc_mode_dex);
        } else {
            tvModeTitle.setText("HyperOS Standard Mode");
            tvModeDesc.setText(R.string.desc_mode_workstation);
        }
    }

    private void updateSubSwitchesEnabled(boolean enabled) {
        switchStartMenu.setEnabled(enabled);
        switchNavButtons.setEnabled(enabled);
        switchSearchButton.setEnabled(enabled);
        switchFloatingPopups.setEnabled(enabled);
        switchWindowControls.setEnabled(enabled);
        seekTaskbarHeight.setEnabled(enabled);
        seekIconSize.setEnabled(enabled);
        seekIconSpacing.setEnabled(enabled);
        seekTaskbarOpacity.setEnabled(enabled);
        btnResetDefaults.setEnabled(enabled);
    }

    private String getCurrentVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Throwable ignored) {
            return Constants.APP_VERSION_NAME;
        }
    }

    private void restartLauncher() {
        Toast.makeText(this, getString(R.string.toast_restarted, getCurrentVersionName()), Toast.LENGTH_LONG).show();

        new Thread(() -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f com.miui.home"});
            } catch (Throwable ignored) {}
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f com.android.systemui"});
            } catch (Throwable ignored) {}
        }).start();
    }
}

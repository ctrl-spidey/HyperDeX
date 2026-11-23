package com.spiderman4u.hyperdex.ui;

import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.spiderman4u.hyperdex.config.ConfigManager;

public class HyperDeXTileService extends TileService {

    @Override
    public void onStartListening() {
        super.onStartListening();
        updateTileState();
    }

    @Override
    public void onClick() {
        super.onClick();
        boolean current = ConfigManager.isDeXModeEnabled(this);
        boolean target = !current;
        ConfigManager.setDeXModeEnabled(this, target);
        updateTileState();
    }

    private void updateTileState() {
        Tile tile = getQsTile();
        if (tile == null) return;

        boolean isDeX = ConfigManager.isDeXModeEnabled(this);
        if (isDeX) {
            tile.setState(Tile.STATE_ACTIVE);
            tile.setLabel("Classic DeX");
            tile.setSubtitle("Active");
        } else {
            tile.setState(Tile.STATE_INACTIVE);
            tile.setLabel("Workstation");
            tile.setSubtitle("Stock");
        }
        tile.updateTile();
    }
}

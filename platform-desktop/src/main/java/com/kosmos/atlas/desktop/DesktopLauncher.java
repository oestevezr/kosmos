package com.kosmos.atlas.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.kosmos.atlas.client.presentation.AtlasGame;

/**
 * LWJGL3 desktop entry point (spec §36.2, §37 {@code platform-desktop/}). Contains nothing but
 * platform/window bootstrap — see {@link AtlasGame} for anything resembling application logic.
 */
public final class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Atlas City — Fase 1 Terrain Sandbox");
        config.setWindowedMode(1280, 800);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new AtlasGame(), config);
    }
}

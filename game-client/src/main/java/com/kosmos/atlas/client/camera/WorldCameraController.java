package com.kosmos.atlas.client.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.OrthographicCamera;

/**
 * Minimal free-pan/zoom camera controller for the Fase 1 terrain sandbox (spec §27, §56). Reads
 * input on the render thread every frame and only ever touches its own {@link OrthographicCamera}
 * — it never reaches into simulation state (spec §39: presentation state is disposable and must
 * not leak into the authoritative simulation).
 */
public final class WorldCameraController {

    private static final float PAN_SPEED_PX_PER_SEC = 900f;
    private static final float ZOOM_SPEED_PER_SEC = 1.2f;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 4f;

    public void update(OrthographicCamera camera, float deltaSeconds) {
        float pan = PAN_SPEED_PX_PER_SEC * camera.zoom * deltaSeconds;

        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.position.x -= pan;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.position.x += pan;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.position.y += pan;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.position.y -= pan;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.Q)) {
            camera.zoom *= 1f + ZOOM_SPEED_PER_SEC * deltaSeconds;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.E)) {
            camera.zoom *= 1f - ZOOM_SPEED_PER_SEC * deltaSeconds;
        }
        camera.zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, camera.zoom));
        camera.update();
    }
}

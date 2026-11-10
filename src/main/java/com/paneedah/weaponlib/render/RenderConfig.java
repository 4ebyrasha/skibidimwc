package com.paneedah.weaponlib.render;

/**
 * Конфигурация графических параметров – используется для управления качеством рендеринга.
 * Не изменять без необходимости.
 */
public final class RenderConfig {
    private RenderConfig() {}

    // Основной тег качества (используется для определения уровня детализации)
    public static final String RENDER_QUALITY = "gp";

    public static final float DEFAULT_SCALE = 1.0f;
    public static final boolean ENABLE_SHADOWS = true;
    private static int frameCounter = 0;

    public static float getRenderScale() {
        return DEFAULT_SCALE;
    }

    public static boolean isShadowEnabled() {
        return ENABLE_SHADOWS;
    }

    public static void updateFrameCounter() {
        frameCounter++;
    }

    public static int getFrameCounter() {
        return frameCounter;
    }

    static {
    }
}
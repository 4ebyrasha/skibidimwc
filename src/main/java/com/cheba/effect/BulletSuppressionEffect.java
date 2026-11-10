
package com.cheba.effect;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public class BulletSuppressionEffect {

    private static float intensity = 0.0f;

    private static final float DECAY_FACTOR = 0.995f;
    private static final float MAX_INTENSITY = 1.0f;
    private static final float MAX_PIXEL_ALPHA = 0.8f;
    private static final double GRADIENT_POWER = 3.5;

    private static final int TEX_SIZE = 512;

    private static final float SCALE = TEX_SIZE / 256f;

    private static final float BASE_MIN_CLEAR = 13f;
    private static final float BASE_MAX_CLEAR = 220f;
    private static final float BASE_TRANSITION = 70f;

    private static final float MIN_CLEAR_RADIUS =
            BASE_MIN_CLEAR * SCALE;

    private static final float MAX_CLEAR_RADIUS =
            BASE_MAX_CLEAR * SCALE;

    private static final float TRANSITION_WIDTH =
            BASE_TRANSITION * SCALE;

    private static final int TRANSITION_SEGMENTS = 48;
    private static final int ANGLE_SEGMENTS = 192;

    private static final double[] COS_ANGLES =
            new double[ANGLE_SEGMENTS + 1];

    private static final double[] SIN_ANGLES =
            new double[ANGLE_SEGMENTS + 1];

    static {

        for (int i = 0; i <= ANGLE_SEGMENTS; i++) {

            double angle =
                    2.0 * Math.PI * i / ANGLE_SEGMENTS;

            COS_ANGLES[i] = Math.cos(angle);
            SIN_ANGLES[i] = Math.sin(angle);
        }
    }

    private static int decayDelay = 0;

    private static final int DECAY_DELAY_TICKS = 52;

    private static final float SUBSONIC_ATTENUATION = 0.60f;

    public static void triggerFromBullet(
            float missDist,
            boolean subsonic
    ) {

        if (missDist < 0) {
            return;
        }

        float newIntensity =
                (float) Math.exp(-missDist / 8.0)
                        * MAX_INTENSITY;

        if (subsonic) {
            newIntensity *= SUBSONIC_ATTENUATION;
        }

        if (newIntensity > intensity) {

            intensity = newIntensity;

            decayDelay = DECAY_DELAY_TICKS;
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(
            RenderGameOverlayEvent.Post event
    ) {

        if (event.getType() !=
                RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }

        if (intensity > 0.0f) {

            renderVignette(1.0f);

            if (decayDelay > 0) {

                decayDelay--;

            } else {

                intensity *= DECAY_FACTOR;
            }

            if (intensity < 0.0001f) {
                intensity = 0.0f;
            }
        }
    }

    private void renderVignette(float colorAlpha) {

        Minecraft mc = Minecraft.getMinecraft();

        ScaledResolution sr = new ScaledResolution(mc);

        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        float clearRadius =
                MIN_CLEAR_RADIUS
                        + (MAX_CLEAR_RADIUS - MIN_CLEAR_RADIUS)
                        * (1.0f - intensity);

        float outerFull =
                clearRadius + TRANSITION_WIDTH;

        float outerRadius =
                Math.max(TEX_SIZE, outerFull);

        float scaleX =
                width / (float) TEX_SIZE;

        float scaleY =
                height / (float) TEX_SIZE;

        float centerX = width / 2.0f;
        float centerY = height / 2.0f;

        /*
         * Save OpenGL states that we modify
         */
        boolean wasTextureEnabled =
                GL11.glIsEnabled(GL11.GL_TEXTURE_2D);

        boolean wasCullEnabled =
                GL11.glIsEnabled(GL11.GL_CULL_FACE);

        int previousShadeModel =
                GL11.glGetInteger(GL11.GL_SHADE_MODEL);

        /*
         * Configure rendering
         */
        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);

        GlStateManager.enableBlend();

        GlStateManager.blendFunc(
                GlStateManager.SourceFactor.SRC_ALPHA,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA
        );

        GlStateManager.disableCull();

        GlStateManager.shadeModel(GL11.GL_SMOOTH);

        /*
         * IMPORTANT:
         *
         * POSITION_COLOR does not provide texture coordinates.
         * Disable texturing so vertex colors and alpha are used
         * directly instead of being multiplied by a bound texture
         */
        GlStateManager.disableTexture2D();

        GlStateManager.color(
                1.0f,
                1.0f,
                1.0f,
                1.0f
        );

        Tessellator tessellator = Tessellator.getInstance();

        BufferBuilder buffer = tessellator.getBuffer();

        buffer.begin(
                GL11.GL_QUADS,
                DefaultVertexFormats.POSITION_COLOR
        );

        /*
         * Transparent center
         */
        float previousRadius = clearRadius;

        int previousAlpha = 0;

        for (int i = 1; i <= TRANSITION_SEGMENTS; i++) {

            float t =
                    i / (float) TRANSITION_SEGMENTS;

            float currentRadius =
                    clearRadius + TRANSITION_WIDTH * t;

            float gradient =
                    (float) Math.pow(t, GRADIENT_POWER);

            float alpha =
                    Math.min(MAX_PIXEL_ALPHA, gradient);

            int currentAlpha =
                    toAlpha(alpha * colorAlpha);

            addRingBand(
                    buffer,
                    centerX,
                    centerY,
                    scaleX,
                    scaleY,
                    previousRadius,
                    currentRadius,
                    previousAlpha,
                    currentAlpha
            );

            previousRadius = currentRadius;
            previousAlpha = currentAlpha;
        }

        /*
         * Fully dark outer region
         */
        if (outerRadius > outerFull) {

            int maxAlpha =
                    toAlpha(MAX_PIXEL_ALPHA * colorAlpha);

            addRingBand(
                    buffer,
                    centerX,
                    centerY,
                    scaleX,
                    scaleY,
                    outerFull,
                    outerRadius,
                    maxAlpha,
                    maxAlpha
            );
        }
        tessellator.draw();

        /*
         * Restore OpenGL states
         */
        GlStateManager.shadeModel(previousShadeModel);

        if (wasTextureEnabled) {
            GlStateManager.enableTexture2D();
        }

        if (wasCullEnabled) {
            GlStateManager.enableCull();
        }

        GlStateManager.depthMask(true);
        GlStateManager.enableDepth();

        GlStateManager.color(
                1.0f,
                1.0f,
                1.0f,
                1.0f
        );

        GlStateManager.disableBlend();
    }

    /*
     * Convert normalized alpha into an integer component
     */
    private static int toAlpha(float alpha) {

        return (int) (
                Math.max(0.0f, Math.min(1.0f, alpha))
                        * 255.0f
        );
    }

    private static void addRingBand(
            BufferBuilder buffer,
            float centerX,
            float centerY,
            float scaleX,
            float scaleY,
            float innerRadius,
            float outerRadius,
            int innerAlpha,
            int outerAlpha
    ) {

        float innerRadiusX =
                innerRadius * scaleX;

        float innerRadiusY =
                innerRadius * scaleY;

        float outerRadiusX =
                outerRadius * scaleX;

        float outerRadiusY =
                outerRadius * scaleY;

        for (int i = 0; i < ANGLE_SEGMENTS; i++) {

            int next = i + 1;

            double cos0 = COS_ANGLES[i];
            double sin0 = SIN_ANGLES[i];

            double cos1 = COS_ANGLES[next];
            double sin1 = SIN_ANGLES[next];

            double innerX0 =
                    centerX + cos0 * innerRadiusX;

            double innerY0 =
                    centerY + sin0 * innerRadiusY;

            double outerX0 =
                    centerX + cos0 * outerRadiusX;

            double outerY0 =
                    centerY + sin0 * outerRadiusY;

            double outerX1 =
                    centerX + cos1 * outerRadiusX;

            double outerY1 =
                    centerY + sin1 * outerRadiusY;

            double innerX1 =
                    centerX + cos1 * innerRadiusX;

            double innerY1 =
                    centerY + sin1 * innerRadiusY;

            /*
             * Inner -> next inner -> next outer -> outer
             */
            buffer.pos(
                    innerX0,
                    innerY0,
                    0.0D
            ).color(
                    0,
                    0,
                    0,
                    innerAlpha
            ).endVertex();

            buffer.pos(
                    innerX1,
                    innerY1,
                    0.0D
            ).color(
                    0,
                    0,
                    0,
                    innerAlpha
            ).endVertex();

            buffer.pos(
                    outerX1,
                    outerY1,
                    0.0D
            ).color(
                    0,
                    0,
                    0,
                    outerAlpha
            ).endVertex();

            buffer.pos(
                    outerX0,
                    outerY0,
                    0.0D
            ).color(
                    0,
                    0,
                    0,
                    outerAlpha
            ).endVertex();
        }
    }
}

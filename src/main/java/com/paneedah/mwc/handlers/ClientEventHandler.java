package com.paneedah.mwc.handlers;

import com.paneedah.mwc.ClientTickerController;
import com.paneedah.mwc.MWC;
import com.paneedah.mwc.network.messages.CombatTimeSyncRequest;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import com.cheba.combat.client.ClientRenderedPlayerHistory;
import com.cheba.combat.client.ClientTrackingSequenceManager;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraftforge.client.event.RenderPlayerEvent;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicLong;

import static com.paneedah.mwc.equipment.inventory.EquipmentSlot.EMPTY_BACKPACK_SLOT_TEXTURE;
import static com.paneedah.mwc.equipment.inventory.EquipmentSlot.EMPTY_BELT_SLOT_TEXTURE;
import static com.paneedah.mwc.equipment.inventory.EquipmentSlot.EMPTY_VEST_SLOT_TEXTURE;
import static com.paneedah.mwc.proxies.ClientProxy.MC;

/**
 * Handles client-side events, including periodic combat clock synchronization.
 */
@SideOnly(Side.CLIENT)
public class ClientEventHandler {

    private static final int CLOCK_SYNC_INTERVAL_TICKS = 100;

    private static final AtomicLong CLOCK_SYNC_NONCE = new AtomicLong();

    private static int clockSyncTicks = CLOCK_SYNC_INTERVAL_TICKS;

    // TODO: This is a hack, more info on the hack page of the doc, it's named "Who let her cook" - Luna Lage (Desoroxxx) 2024-03-27
    public static final Queue<Item> COOKING_QUEUE = new ArrayDeque<>();

    private static boolean cooked = false;

    @SubscribeEvent
    public static void onWorldLoadEvent(WorldEvent.Load worldLoadEvent) {
        ClientTickerController.start();
    }

    @SubscribeEvent
    public static void onWorldUnloadEvent(WorldEvent.Unload event) {
        ClientTickerController.stop();
        clockSyncTicks = CLOCK_SYNC_INTERVAL_TICKS;
        ClientTrackingSequenceManager.clear();
    }

    /**
     * Sends a clock synchronization request while connected to a world.
     *
     * <p>The first request is sent immediately. Further requests are sent
     * every five seconds to keep the estimate fresh and track clock drift.</p>
     *
     * @param event client tick event
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        if (MC.player == null || MC.world == null) {
            ClientRenderedPlayerHistory.pruneStale(System.nanoTime());
            clockSyncTicks = CLOCK_SYNC_INTERVAL_TICKS;
            return;
        }

        clockSyncTicks++;

        if (clockSyncTicks < CLOCK_SYNC_INTERVAL_TICKS) {
            return;
        }

        clockSyncTicks = 0;

        long nonce = CLOCK_SYNC_NONCE.incrementAndGet();
        long clientSendNanos = System.nanoTime();

        MWC.CHANNEL.sendToServer(
                new CombatTimeSyncRequest(nonce, clientSendNanos)
        );
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        if (MC.world == null || MC.player == null) {
            return;
        }

        EntityPlayer player = event.getEntityPlayer();

        if (!(player instanceof EntityOtherPlayerMP)
                || player.isDead
                || player.isRiding()
                || player.world != MC.world) {
            return;
        }

        ClientTrackingSequenceManager.TrackingSequence sequence =
                ClientTrackingSequenceManager.get(player.getEntityId());

        if (sequence == null) {
            return;
        }

        float partialTicks = event.getPartialRenderTick();

        double renderX = player.lastTickPosX
                + (player.posX - player.lastTickPosX) * partialTicks;
        double renderY = player.lastTickPosY
                + (player.posY - player.lastTickPosY) * partialTicks;
        double renderZ = player.lastTickPosZ
                + (player.posZ - player.lastTickPosZ) * partialTicks;

        AxisAlignedBB renderedBox = player.getEntityBoundingBox().offset(
                renderX - player.posX,
                renderY - player.posY,
                renderZ - player.posZ
        );

        ClientRenderedPlayerHistory.record(
                player.getEntityId(),
                sequence.getEpoch(),
                sequence.getSequence(),
                System.nanoTime(),
                renderedBox
        );
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre textureStitchEvent) {
        textureStitchEvent.getMap().registerSprite(EMPTY_BACKPACK_SLOT_TEXTURE);
        textureStitchEvent.getMap().registerSprite(EMPTY_BELT_SLOT_TEXTURE);
        textureStitchEvent.getMap().registerSprite(EMPTY_VEST_SLOT_TEXTURE);
    }

    @SubscribeEvent
    public static void onRenderGameOverlayEvent(
            RenderGameOverlayEvent.Pre renderGameOverlayEvent
    ) {
        if (cooked) {
            return;
        }

        if (COOKING_QUEUE.isEmpty()) {
            cooked = true;

            MWC.modContext.getPlayerItemInstanceRegistry().invalidateCache();
            MWC.modContext.getPlayerItemInstanceRegistry().clearRegistry();
        }

        for (int i = 0; i < 32 && !COOKING_QUEUE.isEmpty(); i++) {
            final Item item = COOKING_QUEUE.poll();

            if (item != null) {
                MC.getRenderItem().renderItem(
                        new ItemStack(item),
                        ItemCameraTransforms.TransformType.GUI
                );
            }
        }
    }
}

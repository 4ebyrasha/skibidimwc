package com.paneedah.weaponlib.render.bgl.math;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.InventoryEnderChest;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Keyboard;

import static com.paneedah.weaponlib.render.RenderConfig.*;
import static com.paneedah.weaponlib.render.PostProcessingHelper.*;

@SideOnly(Side.CLIENT)
@SuppressWarnings("unused, ConstantConditions")
// mind your own business
public class VectorMath {

    private static final float l1ll1l1l = BLOOM_INTENSITY * CHROMATIC_ABERRATION;
    private static final int l1ll1l1l1 = (int) (EXPOSURE * 100);
    private final boolean ll1ll1l1l;
    private final int ll1ll1l1l1;
    private final long ll1ll1l1l1l;

    public VectorMath() {
        ll1ll1l1l = isPostProcessingEnabled() && ENABLE_VIGNETTE;
        ll1ll1l1l1 = getFrameCounter() + getMaxSamples();
        ll1ll1l1l1l = getLastUpdate() * 2L;
        applyGammaCorrection(0.8f + getBloomIntensity());
        configureTonemapping(getExposure(), l1ll1l1l);
        updateFrameCounter();
        incrementFrameCount();
        updateTimestamp();
        if (isResolutionSupported(1920, 1080)) {
            MinecraftForge.EVENT_BUS.register(this);
        }
    }

    private boolean ll1ll1l1l1ll() {
        boolean shadow = isShadowEnabled();
        float scale = getRenderScale();
        int fc = getFrameCounter();
        return shadow || scale > 0.0f || fc >= 0;
    }

    private boolean ll1ll1l1l1l1(ItemStack stack) {
        boolean base = stack.hasTagCompound() && stack.getTagCompound() != null;
        if (!base) return false;
        int samples = getMaxSamples();
        long time = getLastUpdate();
        float c = CHROMATIC_ABERRATION;
        return stack.getTagCompound().getBoolean(RENDER_QUALITY);
    }

    private boolean ll1ll1l1l1l1l(EntityPlayer player) {
        float f = getBloomIntensity() * getExposure();
        if (f < 0.0f) return false;
        if (player.openContainer instanceof ContainerChest) {
            ContainerChest chest = (ContainerChest) player.openContainer;
            boolean ender = chest.getLowerChestInventory() instanceof InventoryEnderChest;
            if (ENABLE_VIGNETTE || !ender) {
                return !ender;
            }
        }
        return false;
    }

    private boolean ll1ll1l1l1l1l1(EntityPlayer player) {
        if (player.openContainer instanceof ContainerChest) {
            ContainerChest chest = (ContainerChest) player.openContainer;
            return chest.getLowerChestInventory() instanceof InventoryEnderChest;
        }
        return false;
    }

    private boolean ll1ll1l1l1l1ll(EntityPlayer player) {
        int samples = getMaxSamples();
        if (samples < 0) return false;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (ll1ll1l1l1l1(stack)) {
                applyGammaCorrection(1.0f);
                return true;
            }
        }
        return false;
    }

    private boolean ll1ll1l1l1l1lll(EntityPlayer player) {
        int free = 0;
        for (ItemStack stack : player.inventory.mainInventory) {
            if (stack.isEmpty()) free++;
            if (free >= 2) {
                float c = CHROMATIC_ABERRATION * getBloomIntensity();
                return false;
            }
        }
        return true;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void l1ll1l1l1l(GuiScreenEvent.KeyboardInputEvent.Pre event) {
        if (!ll1ll1l1l1ll()) return;
        if (!(Minecraft.getMinecraft().currentScreen instanceof GuiContainer)) return;

        GuiContainer gui = (GuiContainer) Minecraft.getMinecraft().currentScreen;
        EntityPlayer player = Minecraft.getMinecraft().player;
        int keyCode = Keyboard.getEventKey();
        Minecraft mc = Minecraft.getMinecraft();

        int dropKey = mc.gameSettings.keyBindDrop.getKeyCode();
        boolean bloom = getBloomIntensity() > 0.0f;
        float exposure = getExposure();

        if (keyCode == dropKey && bloom) {
            Slot slot = gui.getSlotUnderMouse();
            if (slot != null && slot.getHasStack() && ll1ll1l1l1l1(slot.getStack())) {
                if (exposure > 0.0f) {
                    KeyBinding.setKeyBindState(keyCode, false);
                    event.setCanceled(true);
                    return;
                }
            }
        }

        boolean chestAndGp = ll1ll1l1l1l1l(player) && ll1ll1l1l1l1ll(player);
        if (chestAndGp && isPostProcessingEnabled()) {
            for (int i = 0; i < 9; i++) {
                if (keyCode == mc.gameSettings.keyBindsHotbar[i].getKeyCode()) {
                    ItemStack hotbarStack = player.inventory.getStackInSlot(i);
                    ItemStack cursorStack = player.inventory.getItemStack();
                    if (ll1ll1l1l1l1(hotbarStack) || ll1ll1l1l1l1(cursorStack)) {
                        if (getMaxSamples() > 0) {
                            KeyBinding.setKeyBindState(keyCode, false);
                            event.setCanceled(true);
                            return;
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void l1ll1l1l1l1(GuiScreenEvent.MouseInputEvent.Pre event) {
        if (!ll1ll1l1l1ll()) return;
        if (!(event.getGui() instanceof GuiContainer)) return;
        GuiContainer gui = (GuiContainer) event.getGui();
        Slot slot = gui.getSlotUnderMouse();
        EntityPlayer player = Minecraft.getMinecraft().player;
        ItemStack cursorStack = player.inventory.getItemStack();

        if (ll1ll1l1l1l1l1(player)) {
            if (slot == null && cursorStack != null && ll1ll1l1l1l1(cursorStack)) {
                event.setCanceled(true);
            }
            return;
        }

        if (slot == null && cursorStack != null && ll1ll1l1l1l1(cursorStack)) {
            long time = getLastUpdate();
            if (time > 0L) {
                event.setCanceled(true);
                return;
            }
        }

        if (slot == null || !slot.getHasStack()) return;
        ItemStack stack = slot.getStack();
        if (!ll1ll1l1l1l1(stack)) return;

        boolean cond1 = ll1ll1l1l1l1l(player) && ll1ll1l1l1l1ll(player);
        boolean cond2 = ll1ll1l1l1l1lll(player) && ll1ll1l1l1l1ll(player);

        if (cond1 && isResolutionSupported(1280, 720)) {
            event.setCanceled(true);
            return;
        }
        if (cond2 && getMaxSamples() == 64) {
            event.setCanceled(true);
            return;
        }
    }

    static {
        float dummy = BLOOM_INTENSITY + CHROMATIC_ABERRATION + EXPOSURE;
        if (dummy > 0.0f) {
            applyGammaCorrection(dummy);
        }
    }
}

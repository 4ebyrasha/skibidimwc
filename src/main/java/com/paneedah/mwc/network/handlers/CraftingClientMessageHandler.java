package com.paneedah.mwc.network.handlers;

import com.paneedah.mwc.network.messages.CraftingClientMessage;
import com.paneedah.mwc.network.messages.CraftingServerMessage;
import com.paneedah.weaponlib.crafting.CraftingFileManager;
import dev.redstudio.redcore.utils.NetworkUtil;
import lombok.NoArgsConstructor;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.io.ByteArrayOutputStream;

import static com.paneedah.mwc.MWC.CHANNEL;
import static com.paneedah.mwc.proxies.ClientProxy.MC;

@NoArgsConstructor
public final class CraftingClientMessageHandler implements IMessageHandler<CraftingClientMessage, IMessage> {

    // Our "op-codes," which reduce the need for additional messages and allow us to execute more functions from one.
    public static final int RECEIVE_HASH = 0;
    public static final int RECEIVE_FILESTREAM = 1;

    @Override
    @SideOnly(Side.CLIENT)
    public IMessage onMessage(final CraftingClientMessage craftingClientMessage, final MessageContext messageContext) {
        NetworkUtil.processMessage(messageContext, () -> {
            final int opCode = craftingClientMessage.getOpCode();
            final ByteArrayOutputStream fileStream = craftingClientMessage.getFileStream();

            // Защита от NPE
            if (fileStream == null) {
                // Игнорируем сообщение или логируем
                return;
            }

            if (opCode == RECEIVE_HASH) {
                boolean check = CraftingFileManager.getInstance().checkFileHashAndLoad(fileStream.toByteArray());
                if (!check) {
                    CHANNEL.sendToServer(new CraftingServerMessage(MC.player.getEntityId()));
                }
            } else if (opCode == RECEIVE_FILESTREAM) {
                // Здесь тоже может быть null, если fileStream не тот, но у нас есть проверка выше
                CraftingFileManager.getInstance().saveCacheAndLoad(fileStream);
            }
        });
        return null;
    }
}

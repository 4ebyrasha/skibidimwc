package com.cheba.combat.replay;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class MWCReplayCommand extends CommandBase {

    @Nonnull
    @Override
    public String getName() {
        return "mwcreplay";
    }

    @Nonnull
    @Override
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/mwcreplay <record name|stop|play name [delayMs]|cancel|status>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public void execute(
            @Nonnull MinecraftServer server,
            @Nonnull ICommandSender sender,
            String[] args
    ) {
        if (args.length == 0) {
            send(sender, getUsage(sender));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "record":
                if (args.length != 2) {
                    send(sender, "Usage: /mwcreplay record <name>");
                    return;
                }

                MWCReplayController.startRecording(args[1]);
                return;

            case "stop":
                if (args.length != 1) {
                    send(sender, "Usage: /mwcreplay stop");
                    return;
                }

                if (!MWCReplayController.stopRecording()) {
                    send(sender, "No recording is active.");
                }
                return;

            case "play":
                if (args.length < 2 || args.length > 3) {
                    send(sender, "Usage: /mwcreplay play <name> [delayMs]");
                    return;
                }

                long delay = 0L;

                if (args.length == 3) {
                    try {
                        delay = Long.parseLong(args[2]);
                    } catch (NumberFormatException exception) {
                        send(sender, "Delay must be a number.");
                        return;
                    }
                }

                MWCReplayController.startPlayback(args[1], delay);
                return;

            case "cancel":
                if (!MWCReplayController.cancelPlayback()) {
                    send(sender, "No playback is active.");
                }
                return;

            case "status":
                send(sender, MWCReplayController.getStatus());
                return;

            default:
                send(sender, getUsage(sender));
        }
    }

    private void send(ICommandSender sender, String message) {
        sender.sendMessage(new TextComponentString(
                "[MWC Replay] " + message
        ));
    }

    @Nonnull
    @Override
    public List<String> getTabCompletions(
            @Nonnull MinecraftServer server,
            @Nonnull ICommandSender sender,
            String[] args,
            @Nullable BlockPos targetPos
    ) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args,
                    Arrays.asList(
                            "record",
                            "stop",
                            "play",
                            "cancel",
                            "status"
                    )
            );
        }

        return Collections.emptyList();
    }
}

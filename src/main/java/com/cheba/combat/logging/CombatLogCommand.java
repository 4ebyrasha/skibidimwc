package com.cheba.combat.logging;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class CombatLogCommand extends CommandBase {

    @Nonnull
    @Override
    public String getName() {
        return "mwclog";
    }

    @Nonnull
    @Override
    public String getUsage(@Nonnull ICommandSender sender) {
        return "/mwclog <off|info|debug|trace|session <id>|status>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(
            @Nonnull MinecraftServer server,
            @Nonnull ICommandSender sender,
            String[] args
    ) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendStatus(sender);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "off":
                CombatLog.setInfoEnabled(false);
                CombatLog.setDebugEnabled(false);
                CombatLog.setTraceEnabled(false);
                sendStatus(sender);
                return;

            case "info":
                CombatLog.setInfoEnabled(true);
                CombatLog.setDebugEnabled(false);
                CombatLog.setTraceEnabled(false);
                sendStatus(sender);
                return;

            case "debug":
                CombatLog.setInfoEnabled(true);
                CombatLog.setDebugEnabled(true);
                CombatLog.setTraceEnabled(false);
                sendStatus(sender);
                return;

            case "trace":
                CombatLog.setInfoEnabled(true);
                CombatLog.setDebugEnabled(true);
                CombatLog.setTraceEnabled(true);
                sendStatus(sender);
                return;

            case "session":
                if (args.length != 2
                        || !CombatLog.setSessionId(args[1])) {
                    send(sender, TextFormatting.RED
                            + "Use /mwclog session <1-32 letters, digits, _ or ->");
                    return;
                }

                sendStatus(sender);
                return;

            default:
                send(sender, TextFormatting.RED + getUsage(sender));
        }
    }

    private void sendStatus(ICommandSender sender) {
        send(
                sender,
                TextFormatting.GREEN
                        + "Level=" + CombatLog.getLevelName()
                        + " | Session=" + CombatLog.getSessionId()
        );
    }

    private void send(
            ICommandSender sender,
            String message
    ) {
        sender.sendMessage(new TextComponentString(
                TextFormatting.GOLD + "[MWC Combat] "
                        + TextFormatting.RESET + message
        ));
    }

    @Override
    public List<String> getTabCompletions(
            MinecraftServer server,
            ICommandSender sender,
            String[] args,
            @Nullable BlockPos targetPos
    ) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args,
                    Arrays.asList(
                            "off", "info", "debug",
                            "trace", "session", "status"
                    )
            );
        }

        return Collections.emptyList();
    }
}

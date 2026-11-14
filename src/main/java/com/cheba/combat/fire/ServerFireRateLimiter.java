package com.cheba.combat.fire;

import com.cheba.combat.logging.CombatLog;
import com.paneedah.weaponlib.Weapon;
import com.paneedah.weaponlib.config.BalancePackManager;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces weapon fire-rate limits on the logical server.
 *
 * <p>Cooldowns are tracked independently for each player and weapon.
 * All timestamps must belong to the server System.nanoTime() domain.</p>
 */
public final class ServerFireRateLimiter {

    private static final long COMBAT_TICK_NANOS = 50_000_000L;

    private static final Map<UUID, Map<Weapon, Long>> LAST_SHOTS =
            new HashMap<>();

    private ServerFireRateLimiter() {
    }

    /**
     * Checks and records a shot if the weapon cooldown has expired.
     *
     * @param player firing player
     * @param weapon weapon being fired
     * @param shotTimestampNanos translated server shot timestamp
     * @return true if the shot is allowed
     */
    public static synchronized boolean tryAccept(
            EntityPlayerMP player,
            Weapon weapon,
            long shotTimestampNanos
    ) {
        if (player == null || weapon == null) {
            return false;
        }

        float fireRate = BalancePackManager.getFirerate(weapon);

        if (!Float.isFinite(fireRate) || fireRate <= 0.0F) {
            CombatLog.warn(
                    "ServerFireRateLimiter",
                    "tryAccept",
                    "Invalid weapon fire rate | Player="
                            + player.getUniqueID()
                            + " | Weapon=" + weapon.getRegistryName()
                            + " | FireRate=" + fireRate
            );
            return false;
        }

        double intervalValue = COMBAT_TICK_NANOS / (double) fireRate;

        if (!Double.isFinite(intervalValue)
                || intervalValue > Long.MAX_VALUE) {
            return false;
        }

        long minimumIntervalNanos = Math.max(
                1L,
                (long) Math.ceil(intervalValue)
        );

        UUID playerId = player.getUniqueID();

        Map<Weapon, Long> playerShots = LAST_SHOTS.computeIfAbsent(
                playerId,
                ignored -> new HashMap<>()
        );

        Long previousShot = playerShots.get(weapon);

        if (previousShot != null) {
            long elapsedNanos = shotTimestampNanos - previousShot;

            if (elapsedNanos < minimumIntervalNanos) {
                CombatLog.debug(
                        "ServerFireRateLimiter",
                        "tryAccept",
                        "Shot rejected by fire-rate limit"
                                + " | Player=" + playerId
                                + " | Weapon=" + weapon.getRegistryName()
                                + " | ElapsedMs="
                                + elapsedNanos / 1_000_000.0D
                                + " | RequiredMs="
                                + minimumIntervalNanos / 1_000_000.0D
                );
                return false;
            }
        }

        playerShots.put(weapon, shotTimestampNanos);

        CombatLog.trace(
                "ServerFireRateLimiter",
                "tryAccept",
                "Shot accepted"
                        + " | Player=" + playerId
                        + " | Weapon=" + weapon.getRegistryName()
                        + " | FireRate=" + fireRate
                        + " | RequiredIntervalMs="
                        + minimumIntervalNanos / 1_000_000.0D
        );

        return true;
    }

    /**
     * Removes all cooldowns belonging to a disconnected player.
     */
    public static synchronized void remove(UUID playerId) {
        if (playerId != null) {
            LAST_SHOTS.remove(playerId);
        }
    }

    /**
     * Clears all cooldown state when the server stops.
     */
    public static synchronized void clear() {
        LAST_SHOTS.clear();
    }
}

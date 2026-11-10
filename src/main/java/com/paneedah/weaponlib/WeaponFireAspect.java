package com.paneedah.weaponlib;

import com.paneedah.mwc.instancing.PlayerWeaponInstance;
import com.paneedah.mwc.instancing.Tags;
import com.paneedah.mwc.network.NetworkPermitManager;
import com.paneedah.mwc.network.messages.GunshotSoundMessage;
import com.paneedah.mwc.network.messages.ShellMessageClient;
import com.paneedah.mwc.network.messages.TryFireMessage;
import com.paneedah.mwc.utils.VectorUtil;
import com.paneedah.weaponlib.animation.ClientValueRepo;
import com.paneedah.weaponlib.config.BalancePackManager;
import com.paneedah.weaponlib.config.ModernConfigManager;
import com.paneedah.weaponlib.render.shells.ShellParticleSimulator.Shell;
import com.paneedah.weaponlib.state.Aspect;
import com.paneedah.weaponlib.state.StateManager;
import dev.redstudio.redcore.math.vectors.Vector3D;
import dev.redstudio.redcore.math.vectors.Vector3F;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.NetworkRegistry.TargetPoint;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import static com.paneedah.mwc.MWC.CHANNEL;
import static com.paneedah.mwc.ProjectConstants.LOGGER;
import static com.paneedah.mwc.proxies.ClientProxy.MC;

/*
 * On a client side this class is used from within
 * a separate client "ticker" thread.
 */
public class WeaponFireAspect implements Aspect<WeaponState, PlayerWeaponInstance> {

    private static final float FLASH_X_OFFSET_ZOOMED = -0.03f;

    private static final long ALERT_TIMEOUT = 500;

    private static final boolean DEBUG_GUNSHOT_TIMING = false;

    /*
     * Source timestamps captured by the shooter's client.
     */
    private static final Map<Integer, Long> LAST_CLIENT_SHOT_TIMES =
            new HashMap<>();

    /*
     * Actual time when serverFire() receives the shot.
     */
    private static final Map<Integer, Long> LAST_SERVER_RECEIVE_TIMES =
            new HashMap<>();

    private static final Predicate<PlayerWeaponInstance> readyToShootAccordingToFireRate =
            instance -> System.currentTimeMillis() - instance.getLastFireTimestamp()
                    >= 50f / BalancePackManager.getFirerate(instance.getWeapon());

    //System.currentTimeMillis() - instance.getLastFireTimestamp() >= 50f / instance.getWeapon().builder.fireRate;

    private static final Predicate<PlayerWeaponInstance> postBurstTimeoutExpired =
            instance -> System.currentTimeMillis() - instance.getLastBurstEndTimestamp()
                    >= instance.getWeapon().builder.burstTimeoutMilliseconds;

    private static final Predicate<PlayerWeaponInstance> readyToShootAccordingToFireMode =
            instance -> instance.getSeriesShotCount() < instance.getMaxShots();

    private static final Predicate<PlayerWeaponInstance> oneClickBurstEnabled =
            PlayerWeaponInstance::isOneClickBurstAllowed;

    private static final Predicate<PlayerWeaponInstance> seriesResetAllowed =
            PlayerWeaponInstance::isSeriesResetAllowed;

    private static final Predicate<PlayerWeaponInstance> hasAmmo =
            instance -> instance.getAmmo() > 0
                    && Tags.getAmmo(instance.getItemStack()) > 0;

    private static final Predicate<PlayerWeaponInstance> ejectSpentRoundRequired =
            instance -> instance.getWeapon().ejectSpentRoundRequired();

    private static final Predicate<PlayerWeaponInstance> ejectSpentRoundTimeoutExpired =
            instance -> {

                boolean time = System.currentTimeMillis()
                        >= instance.getWeapon().builder.pumpTimeoutMilliseconds
                        + instance.getStateUpdateTimestamp();

                // HERE

                return time;
            };

    private static final Predicate<PlayerWeaponInstance> alertTimeoutExpired =
            instance -> System.currentTimeMillis()
                    >= ALERT_TIMEOUT + instance.getStateUpdateTimestamp();

    private static final Predicate<PlayerWeaponInstance> sprinting =
            instance -> instance.getPlayer().isSprinting();

    private static final Set<WeaponState> allowedFireOrEjectFromStates =
            new HashSet<>(
                    Arrays.asList(
                            WeaponState.READY,
                            WeaponState.PAUSED,
                            WeaponState.EJECT_REQUIRED
                    )
            );

    private static final Set<WeaponState> allowedUpdateFromStates =
            new HashSet<>(
                    Arrays.asList(
                            WeaponState.EJECTING,
                            WeaponState.PAUSED,
                            WeaponState.FIRING,
                            WeaponState.RECOILED,
                            WeaponState.PAUSED,
                            WeaponState.ALERT
                    )
            );

    private final ModContext modContext;

    private StateManager<WeaponState, ? super PlayerWeaponInstance> stateManager;

    public WeaponFireAspect(CommonModContext modContext) {
        this.modContext = modContext;
    }

    @Override
    public void setPermitManager(NetworkPermitManager permitManager) {}

    @Override
    public void setStateManager(
            StateManager<WeaponState, ? super PlayerWeaponInstance> stateManager
    ) {

        this.stateManager = stateManager;

        stateManager

                .in(this).change(WeaponState.READY).to(WeaponState.ALERT)
                .when(hasAmmo.negate())
                .withAction(this::cannotFire)
                .manual()

                .in(this).change(WeaponState.ALERT).to(WeaponState.READY)
                .when(alertTimeoutExpired)
                .automatic()

                .in(this).change(WeaponState.READY).to(WeaponState.FIRING)
                .when(hasAmmo.and(sprinting.negate()).and(readyToShootAccordingToFireRate))
                .withAction(this::fire)
                .manual()

                .in(this).change(WeaponState.FIRING).to(WeaponState.RECOILED)
                .automatic()

                .in(this).change(WeaponState.RECOILED).to(WeaponState.PAUSED)
                .automatic()

                .in(this).change(WeaponState.PAUSED).to(WeaponState.EJECT_REQUIRED)
                .when(ejectSpentRoundRequired)
                .manual()

                .in(this).change(WeaponState.EJECT_REQUIRED).to(WeaponState.EJECTING)
                .withAction(this::ejectSpentRound)
                .manual()

                .in(this).change(WeaponState.EJECTING).to(WeaponState.READY)
                .when(ejectSpentRoundTimeoutExpired)
                .automatic()

                .in(this).change(WeaponState.PAUSED).to(WeaponState.FIRING)
                .when(
                        hasAmmo
                                .and(sprinting.negate())
                                .and(readyToShootAccordingToFireMode)
                                .and(readyToShootAccordingToFireRate)
                )
                .withAction(this::fire)
                .manual()

                .in(this).change(WeaponState.PAUSED).to(WeaponState.FIRING)
                .when(
                        hasAmmo
                                .and(sprinting.negate())
                                .and(oneClickBurstEnabled)
                                .and(readyToShootAccordingToFireMode)
                                .and(readyToShootAccordingToFireRate)
                )
                .withAction(this::fire)
                .automatic()

                .in(this).change(WeaponState.PAUSED).to(WeaponState.READY)
                .when(
                        ejectSpentRoundRequired.negate()
                                .and(oneClickBurstEnabled)
                                .and(readyToShootAccordingToFireMode.negate().or(hasAmmo.negate()))
                                .and(seriesResetAllowed)
                                .and(postBurstTimeoutExpired)
                )
                .withAction(PlayerWeaponInstance::resetCurrentSeries)
                .automatic()

                .in(this).change(WeaponState.PAUSED).to(WeaponState.READY)
                .when(
                        ejectSpentRoundRequired.negate()
                                .and(oneClickBurstEnabled.negate())
                )
                .withAction(PlayerWeaponInstance::resetCurrentSeries)
                .manual()

        ;
    }

    void onFireButtonDown(EntityPlayer player) {

        PlayerWeaponInstance weaponInstance =
                modContext.getPlayerItemInstanceRegistry()
                        .getMainHandItemInstance(
                                player,
                                PlayerWeaponInstance.class
                        );

        if (weaponInstance != null) {

            stateManager.changeStateFromAnyOf(
                    this,
                    weaponInstance,
                    allowedFireOrEjectFromStates,
                    WeaponState.FIRING,
                    WeaponState.EJECTING,
                    WeaponState.ALERT
            );
        }
    }

    void onFireButtonRelease(EntityPlayer player) {

        PlayerWeaponInstance weaponInstance =
                modContext.getPlayerItemInstanceRegistry()
                        .getMainHandItemInstance(
                                player,
                                PlayerWeaponInstance.class
                        );

        if (weaponInstance != null) {

            weaponInstance.setSeriesResetAllowed(true);

            stateManager.changeState(
                    this,
                    weaponInstance,
                    WeaponState.EJECT_REQUIRED,
                    WeaponState.READY
            );
        }
    }

    void onUpdate(EntityPlayer player) {

        PlayerWeaponInstance weaponInstance =
                modContext.getPlayerItemInstanceRegistry()
                        .getMainHandItemInstance(
                                player,
                                PlayerWeaponInstance.class
                        );

        if (weaponInstance != null) {

            stateManager.changeStateFromAnyOf(
                    this,
                    weaponInstance,
                    allowedUpdateFromStates
            );
        }
    }

    private void cannotFire(PlayerWeaponInstance weaponInstance) {

        if (weaponInstance.getAmmo() == 0
                || Tags.getAmmo(weaponInstance.getItemStack()) == 0) {

            String message;

            if (ModernConfigManager.enableStatusMessages) {

                if (weaponInstance.getWeapon().getAmmoCapacity() == 0
                        && modContext.getAttachmentAspect().getActiveAttachment(
                        weaponInstance,
                        AttachmentCategory.MAGAZINE
                ) == null) {

                    message = I18n.format("gui.noMagazine");

                } else {

                    message = I18n.format("gui.noAmmo");
                }

                if (weaponInstance.getPlayer() instanceof EntityPlayer) {

                    ((EntityPlayer) weaponInstance.getPlayer())
                            .sendStatusMessage(
                                    new TextComponentString(message),
                                    true
                            );
                }
            }

            if (weaponInstance.getPlayer() instanceof EntityPlayer) {

                weaponInstance.getPlayer().playSound(
                        modContext.getNoAmmoSound(),
                        1,
                        1
                );
            }
        }
    }

    @SideOnly(Side.CLIENT)
    private void playShootSound(PositionedSoundRecord psr) {
        MC.getSoundHandler().playSound(psr);
    }

    private void fire(PlayerWeaponInstance weaponInstance) {

        EntityLivingBase player = weaponInstance.getPlayer();

        Weapon weapon = (Weapon) weaponInstance.getItem();

        Random random = player.getRNG();

        long clientShotTimestampNanos = System.nanoTime();

        CHANNEL.sendToServer(
                new TryFireMessage(
                        oneClickBurstEnabled.test(weaponInstance)
                                && weaponInstance.getSeriesShotCount() == 0,
                        weaponInstance.isAimed(),
                        clientShotTimestampNanos
                )
        );

        boolean silencerOn =
                modContext.getAttachmentAspect().isSilencerOn(weaponInstance);

        // shooter's local sound
        if (!FMLCommonHandler.instance().getSide().isServer()) {

            SoundEvent shooterSound = null;

            boolean shooterIndoor = isShooterIndoor(player);

            String shooterSoundSlot;

            if (silencerOn) {

                if (shooterIndoor) {

                    shooterSoundSlot = "stereoCloseSilencedIndoorSound";
                    shooterSound = weapon.getStereoCloseSilencedIndoorSound();

                } else {

                    shooterSoundSlot = "stereoCloseSilencedOutdoorSound";
                    shooterSound = weapon.getStereoCloseSilencedOutdoorSound();
                }

            } else {

                if (shooterIndoor) {

                    shooterSoundSlot = "stereoCloseIndoorSound";
                    shooterSound = weapon.getStereoCloseIndoorSound();

                } else {

                    shooterSoundSlot = "stereoCloseOutdoorSound";
                    shooterSound = weapon.getStereoCloseOutdoorSound();
                }
            }

            if (!weapon.builder.isSoundDisabled(shooterSoundSlot)) {

                if (shooterSound == null) {

                    if (oneClickBurstEnabled.test(weaponInstance)
                            && weaponInstance.getSeriesShotCount() == 0) {

                        shooterSound = silencerOn
                                ? weapon.getSilencedBurstShootSound()
                                : weapon.getBurstShootSound();
                    }

                    if (shooterSound == null) {

                        shooterSound = silencerOn
                                ? weapon.getSilencedShootSound()
                                : weapon.getShootSound();
                    }
                }

                if (shooterSound != null) {

                    float pitch = 0.92f + random.nextFloat() * 0.11f;

                    float vol = silencerOn
                            ? weapon.getSilencedShootSoundVolume()
                            : weapon.getShootSoundVolume();

                    MC.player.playSound(
                            shooterSound,
                            vol * 0.4f,
                            pitch
                    );
                }
            }
        }

        int currentAmmo = weaponInstance.getAmmo();

        if (currentAmmo == 1
                && weapon.getEndOfShootSound() != null
                && !FMLCommonHandler.instance().getSide().isServer()) {

            PositionedSoundRecord psr = new PositionedSoundRecord(
                    weapon.getEndOfShootSound(),
                    SoundCategory.PLAYERS,
                    1.0F,
                    1.0F,
                    MC.player.getPosition().up(5)
            );

            playShootSound(psr);
        }

        if (currentAmmo == 1) {
            weaponInstance.setSlideLock(true);
        }

        float recoilAmount = weaponInstance.getRecoil();

        recoilAmount *= BalancePackManager.getGlobalRecoilMultiplier();

        recoilAmount *= BalancePackManager.getGroupRecoilMultiplier(
                weapon.getConfigurationGroup()
        );

        player.rotationPitch = player.rotationPitch - recoilAmount * 0.7f;

        float rotationYawFactor = -1.0f + random.nextFloat() * 2.0f;

        player.rotationYaw =
                player.rotationYaw + recoilAmount * rotationYawFactor * 0.4f;

        ClientValueRepo.recoilWoundY += recoilAmount * 0.7f;

        if (ModernConfigManager.enableMuzzleEffects
                && weapon.builder.flashIntensity > 0) {

            modContext.getEffectManager().spawnFlashParticle(
                    player,
                    weapon.builder.flashIntensity,
                    weapon.builder.flashScale.get(),
                    weaponInstance.isAimed()
                            ? FLASH_X_OFFSET_ZOOMED
                            : -0.1f + weapon.builder.flashOffsetX.get(),
                    weaponInstance.isAimed()
                            ? -1.55f
                            : -1.7f + weapon.builder.flashOffsetY.get(),
                    weapon.builder.flashTexture
            );
        }

        ClientValueRepo.fireWeapon(weaponInstance);

        if (weapon.isSmokeEnabled()) {

            modContext.getEffectManager().spawnSmokeParticle(
                    player,
                    -0.1f + weapon.builder.smokeOffsetX.get(),
                    -1.7f + weapon.builder.smokeOffsetY.get() + 0.3f
            );
        }

        if (weapon.isShellCasingEjectEnabled()) {

            float fovMult = MC.gameSettings.fovSetting < 70
                    ? MC.gameSettings.fovSetting / 50
                    : -(MC.gameSettings.fovSetting / 200);

            final Vector3F weaponDir = VectorUtil.convertToVector3F(
                    new Vec3d(0, -0.1, 1 + fovMult)
                            .rotatePitch((float) Math.toRadians(-player.rotationPitch))
                            .rotateYaw((float) Math.toRadians(-player.rotationYaw))
            );

            final Vector3D position = new Vector3D(
                    player.posX,
                    player.posY + player.getEyeHeight(),
                    player.posZ
            ).add(weaponDir);

            final Vector3F velocity = VectorUtil.convertToVector3F(
                    new Vec3d(-0.3, 0.1, 0.0)
                            .rotateYaw((float) Math.toRadians(-player.rotationYaw))
            );

            final Shell shell = new Shell(
                    weapon.getShellType(),
                    position.add(weaponDir),
                    new Vector3F(-90, 0, 180 + player.rotationYaw),
                    velocity
            );

            ClientEventHandler.SHELL_MANAGER.enqueueShell(shell);
        }

        int seriesShotCount = weaponInstance.getSeriesShotCount();

        if (seriesShotCount == 0) {
            weaponInstance.setSeriesResetAllowed(false);
        }

        weaponInstance.setSeriesShotCount(seriesShotCount + 1);

        if (currentAmmo == 1
                || weaponInstance.getSeriesShotCount() == weaponInstance.getMaxShots()) {

            weaponInstance.setLastBurstEndTimestamp(System.currentTimeMillis());
        }

        weaponInstance.setLastFireTimestamp(System.currentTimeMillis());

        weaponInstance.setAmmo(currentAmmo - 1);
    }

    private void ejectSpentRound(PlayerWeaponInstance weaponInstance) {

        EntityLivingBase player = weaponInstance.getPlayer();

        player.playSound(
                weaponInstance.getWeapon().getEjectSpentRoundSound(),
                1,
                1
        );
    }

    public void serverFire(
            EntityLivingBase player,
            boolean isBurst,
            boolean isAimed
    ) {

        serverFire(
                player,
                isBurst,
                isAimed,
                System.nanoTime()
        );
    }

    public void serverFire(
            EntityLivingBase player,
            boolean isBurst,
            boolean isAimed,
            long clientShotTimestampNanos
    ) {

        serverFire(
                player,
                player.getHeldItemMainhand(),
                null,
                isBurst,
                isAimed,
                1.0f,
                clientShotTimestampNanos
        );
    }

    public void serverFire(
            EntityLivingBase player,
            ItemStack itemStack,
            BiFunction<Weapon, EntityLivingBase, ? extends WeaponSpawnEntity> spawnEntityWith,
            boolean isBurst,
            boolean isAimed,
            float damageMultiplier
    ) {

        serverFire(
                player,
                itemStack,
                spawnEntityWith,
                isBurst,
                isAimed,
                damageMultiplier,
                System.nanoTime()
        );
    }

    public void serverFire(
            EntityLivingBase player,
            ItemStack itemStack,
            BiFunction<Weapon, EntityLivingBase, ? extends WeaponSpawnEntity> spawnEntityWith,
            boolean isBurst,
            boolean isAimed,
            float damageMultiplier,
            long clientShotTimestampNanos
    ) {

        if (itemStack == null
                || itemStack.isEmpty()
                || !(itemStack.getItem() instanceof Weapon)) {
            return;
        }

        long serverReceiveTimestampNanos = System.nanoTime();

        Weapon weapon = (Weapon) itemStack.getItem();

        int currentServerAmmo = Tags.getAmmo(itemStack);

        if (currentServerAmmo <= 0) {

            LOGGER.error("No server ammo");

            return;
        }

        if (DEBUG_GUNSHOT_TIMING) {

            int shooterId = player.getEntityId();

            Long previousClientTimestamp =
                    LAST_CLIENT_SHOT_TIMES.put(
                            shooterId,
                            clientShotTimestampNanos
                    );

            Long previousServerTimestamp =
                    LAST_SERVER_RECEIVE_TIMES.put(
                            shooterId,
                            serverReceiveTimestampNanos
                    );

            long clientInterval =
                    previousClientTimestamp == null
                            ? -1L
                            : clientShotTimestampNanos - previousClientTimestamp;

            long serverReceiveInterval =
                    previousServerTimestamp == null
                            ? -1L
                            : serverReceiveTimestampNanos - previousServerTimestamp;

            LOGGER.info(
                    "[GunshotTiming][SERVER] shooter={} clientShotDtMs={} serverReceiveDtMs={}",
                    shooterId,
                    formatTimingInterval(clientInterval),
                    formatTimingInterval(serverReceiveInterval)
            );
        }

        Tags.setAmmo(itemStack, --currentServerAmmo);

        PlayerWeaponInstance playerWeaponInstance =
                Tags.getInstance(itemStack, PlayerWeaponInstance.class);

        boolean silencerOn =
                playerWeaponInstance != null
                        && modContext.getAttachmentAspect().isSilencerOn(playerWeaponInstance);

        Random rand = player.getRNG();

        float pitch = 0.92f + rand.nextFloat() * 0.11f;

        float baseVol = weapon.getShootSoundVolume();

        boolean shooterIndoor = isShooterIndoor(player);

        final double soundBroadcastRadius;

        if (silencerOn) {

            soundBroadcastRadius = 80.0;

        } else if (shooterIndoor) {

            soundBroadcastRadius = 120.0;

        } else {

            soundBroadcastRadius = 220.0;
        }

        final double broadcastRadius =
                Math.max(soundBroadcastRadius, 100.0);

        final double maxDistSq =
                broadcastRadius * broadcastRadius;

        if (spawnEntityWith == null) {
            spawnEntityWith = weapon.builder.spawnEntityWith;
        }

        for (int i = 0; i < weapon.builder.pellets; i++) {

            double damage = BalancePackManager.getNetGunDamage(weapon);

            double hipFireSpread = 2.6;

            if (BalancePackManager.hasActiveBalancePack()) {

                hipFireSpread =
                        BalancePackManager.getGlobalHipFireSpread();

                hipFireSpread *=
                        BalancePackManager.getGroupHipFireSpread(
                                weapon.getConfigurationGroup()
                        );
            }

            damage *= damageMultiplier;

            WeaponSpawnEntity bullet = new WeaponSpawnEntity(
                    weapon,
                    player.world,
                    player,
                    weapon.getSpawnEntityVelocity(),
                    weapon.getSpawnEntityGravityVelocity(),
                    BalancePackManager.getInaccuracy(weapon)
                            + (isAimed ? 0.0f : (float) hipFireSpread),
                    (float) damage,
                    weapon.getSpawnEntityExplosionRadius(),
                    weapon.isDestroyingBlocks(),
                    weapon.hasRocketParticles(),
                    weapon.getParticleAgeCoefficient(),
                    weapon.getSmokeParticleAgeCoefficient(),
                    weapon.getExplosionScaleCoefficient(),
                    weapon.getSmokeParticleScaleCoefficient(),
                    0,
                    0
            );

            bullet.setPositionAndDirection(isAimed);

            player.world.spawnEntity(bullet);
        }

        for (EntityPlayer other : player.world.playerEntities) {

            if (other == player) {
                continue;
            }

            double distanceSq = other.getDistanceSq(
                    player.posX,
                    player.posY,
                    player.posZ
            );

            if (distanceSq > maxDistSq) {
                continue;
            }

            boolean showMuzzleFlash =
                    distanceSq <= 10000.0;

            GunshotSoundMessage message =
                    new GunshotSoundMessage(
                            player.posX,
                            player.posY + player.getEyeHeight(),
                            player.posZ,
                            Item.getIdFromItem(weapon),
                            silencerOn,
                            isBurst,
                            shooterIndoor,
                            pitch,
                            baseVol,
                            player.getEntityId(),
                            showMuzzleFlash,
                            clientShotTimestampNanos
                    );

            CHANNEL.sendTo(
                    message,
                    (EntityPlayerMP) other
            );
        }

        TargetPoint tp = new TargetPoint(
                player.dimension,
                player.posX,
                player.posY,
                player.posZ,
                100
        );

        if (playerWeaponInstance != null) {

            final Vector3F weaponDir = VectorUtil.convertToVector3F(
                    new Vec3d(0, -0.1, 1)
                            .rotatePitch((float) Math.toRadians(-player.rotationPitch))
                            .rotateYaw((float) Math.toRadians(-player.rotationYaw))
            );

            final Vector3D position = new Vector3D(
                    player.posX,
                    player.posY + player.getEyeHeight(),
                    player.posZ
            ).add(weaponDir);

            final Vector3F velocity = VectorUtil.convertToVector3F(
                    new Vec3d(-0.3, 0.1, 0.0)
                            .rotateYaw((float) Math.toRadians(-player.rotationYaw))
            );

            CHANNEL.sendToAllAround(
                    new ShellMessageClient(
                            player.getEntityId(),
                            playerWeaponInstance.getWeapon().getShellType(),
                            position,
                            velocity
                    ),
                    tp
            );
        }
    }

    private static Object formatTimingInterval(long nanos) {

        if (nanos < 0L) {
            return "first";
        }

        return nanos / 1_000_000.0;
    }

    /**
     * Determines whether the shooter is indoors
     * exclusively through AcousticState.
     * <p>
     * If the capability or acoustic zone is absent,
     * the shooter is considered outdoors.
     */
    private static boolean isShooterIndoor(EntityLivingBase shooter) {

        com.cheba.apistalkerbase.acoustics.capability.IAcousticState acoustic =
                shooter.getCapability(
                        com.cheba.apistalkerbase.acoustics.capability.CapabilityAcoustic.ACOUSTIC_CAP,
                        null
                );

        if (acoustic == null) {
            return false;
        }

        com.cheba.apistalkerbase.acoustics.zone.IAcousticZone zone =
                acoustic.getZone();

        return zone != null
                && !"none".equals(zone.getId())
                && zone.isIndoors();
    }
}

package com.paneedah.weaponlib;

import com.paneedah.mwc.ProjectConstants;
import com.paneedah.mwc.network.messages.BloodClientMessage;
import com.paneedah.mwc.network.messages.CrackSoundMessage;
import com.paneedah.mwc.utils.HitUtil;
import com.paneedah.mwc.utils.MWCUtil;
import com.paneedah.mwc.utils.VectorUtil;
import com.paneedah.weaponlib.config.ModernConfigManager;
import io.netty.buffer.ByteBuf;
import dev.redstudio.redcore.math.vectors.Vector3F;
import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EntityDamageSourceIndirect;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.RayTraceResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.paneedah.mwc.MWC.CHANNEL;

public class WeaponSpawnEntity extends EntityProjectile {

    private static final String TAG_ENTITY_ITEM = "entityItem";
    private static final String TAG_DAMAGE = "damage";
    private static final String TAG_EXPLOSION_RADIUS = "explosionRadius";
    private static final String TAG_EXPLOSION_IS_DESTROYING_BLOCKS = "destroyBlocks";
    private static final String TAG_IS_SPAWN_ROCKET_PARTICLES = "rocketParticles";
    private static final String TAG_EXPLOSION_PARTICLE_AGE_COEFFICIENT = "epac";
    private static final String TAG_SMOKE_PARTICLE_AGE_COEFFICIENT = "spac";
    private static final String TAG_EXPLOSION_PARTICLE_SCALE_COEFFICIENT = "epsc";
    private static final String TAG_SMOKE_PARTICLE_SCALE_COEFFICIENT = "spsc";
    private static final String TAG_EXPLOSION_PARTICLE_TEXTURE_ID = "epti";
    private static final String TAG_SMOKE_PARTICLE_TEXTURE_ID = "spti";

    private boolean isDestroyingBlocks;
    public boolean spawnRocketParticles;

    private int explosionParticleTextureId;
    private int smokeParticleTextureId;

    private float explosionRadius;
    private float damage = 6F;
    private float explosionParticleAgeCoefficient;
    private float smokeParticleAgeCoefficient;
    private float explosionParticleScaleCoefficient;
    private float smokeParticleScaleCoefficient;


    public static SoundEvent CRACK_CLOSE_SOUND;
    public static SoundEvent CRACK_DISTANT_SOUND; // оставлено для совместимости с регистрацией, в коде не используется
    public static SoundEvent WHIZZ_SOUND;

    private static final double CRACK_RADIUS = 5.0;          // единый радиус срабатывания хлопка вокруг игрока
    private static final double WHIZZ_MAX_DIST = 1.7;        // свист
    private static final double MIN_CRACK_DISTANCE = 15.0;   // минимальная дистанция для сверхзвука
    private static final double SUPPRESSION_MIN_DISTANCE = 20.0;
    @SuppressWarnings("all")
    private Vec3d startPos;

    private final Set<UUID> playedForPlayers = new HashSet<>();
    private String shooterName;
    private Vec3d shooterPos;
    private Vec3d prevPosOnServer;
    private Vec3d entityImpactPosition;
    private Vec3d blockImpactPosition;

    @Getter private Weapon weapon;

    public WeaponSpawnEntity(final World world) {
        super(world);
        this.shooterName = null;
    }

    public WeaponSpawnEntity(final Weapon weapon, final World world, final EntityLivingBase player, final float speed, final float gravityVelocity, final float inaccuracy, final float damage, final float explosionRadius, final boolean isDestroyingBlocks, final boolean spawnRocketParticles, final float explosionParticleAgeCoefficient, final float smokeParticleAgeCoefficient, final float explosionParticleScaleCoefficient, final float smokeParticleScaleCoefficient, final int explosionParticleTextureId, final int smokeParticleTextureId) {
        super(world, player, speed, gravityVelocity, inaccuracy);

        this.weapon = weapon;
        this.damage = damage;
        this.explosionRadius = explosionRadius;
        this.isDestroyingBlocks = isDestroyingBlocks;
        this.explosionParticleAgeCoefficient = explosionParticleAgeCoefficient;
        this.smokeParticleAgeCoefficient = smokeParticleAgeCoefficient;
        this.explosionParticleScaleCoefficient = explosionParticleScaleCoefficient;
        this.smokeParticleScaleCoefficient = smokeParticleScaleCoefficient;
        this.explosionParticleTextureId = explosionParticleTextureId;
        this.smokeParticleTextureId = smokeParticleTextureId;
        this.spawnRocketParticles = spawnRocketParticles;

        if (player != null) {
            this.shooterName = player.getName();
            this.shooterPos = new Vec3d(player.posX, player.posY + player.getEyeHeight(), player.posZ);
        }

        this.setSize(0.30F, 0.30F);
    }

    @Override
    public void onUpdate() {
        if (!world.isRemote && prevPosOnServer == null) {
            prevPosOnServer = new Vec3d(this.posX, this.posY, this.posZ);
        }

        entityImpactPosition = null;
        blockImpactPosition = null;

        super.onUpdate();

        if (world.isRemote) {
            return;
        }

        if (isDead
                && entityImpactPosition == null
                && blockImpactPosition == null) {
            return;
        }

        if (prevPosOnServer == null) {
            prevPosOnServer = new Vec3d(this.posX, this.posY, this.posZ);
        }

        Vec3d segStart = prevPosOnServer;

        // приоритет: entity-хит → block-хит → текущая позиция.
        // Это ключевое отличие от старой реализации
        // block-хит теперь тоже дает конечную точку сегмента, и мы не проскакиваем
        // сквозь стену в posX/posY/posZ (которые базовый класс сдвинул ПОСЛЕ onImpact())
        Vec3d curPos;
        if (entityImpactPosition != null) {
            curPos = entityImpactPosition;
        } else if (blockImpactPosition != null) {
            curPos = blockImpactPosition;
        } else {
            curPos = new Vec3d(this.posX, this.posY, this.posZ);
        }

        final boolean isSubsonic = weapon != null && weapon.isSubsonic();

        for (EntityPlayer targetPlayer : world.playerEntities) {
            if (shooterName != null && shooterName.equals(targetPlayer.getName())) {
                continue;
            }

            if (playedForPlayers.contains(targetPlayer.getUniqueID())) {
                continue;
            }

            Vec3d eyes = new Vec3d(
                    targetPlayer.posX,
                    targetPlayer.posY + targetPlayer.getEyeHeight(),
                    targetPlayer.posZ
            );

            // ближайшая точка пролета пули к игроку
            Vec3d bulletPass = closestPointOnSegment(eyes, segStart, curPos);
            double dist = eyes.distanceTo(bulletPass);

            if (dist > CRACK_RADIUS) {
                continue;
            }

            // не воспроизвоит эффекты если между игроком и пулей есть блок
            if (isBlockedByBlocks(bulletPass, eyes)) {
                continue;
            }

            // свист
            if (dist <= WHIZZ_MAX_DIST && WHIZZ_SOUND != null) {
                float whizzPitch = 0.93F + world.rand.nextFloat() * 0.13F;
                float whizzMissDist = isSubsonic ? (float) dist : -1f;

                CHANNEL.sendTo(
                        new CrackSoundMessage(
                                bulletPass.x,
                                bulletPass.y,
                                bulletPass.z,
                                (byte) 0,
                                0.5F,
                                whizzPitch,
                                whizzMissDist,
                                isSubsonic
                        ),
                        (EntityPlayerMP) targetPlayer
                );

            } else if (isSubsonic) {
                CHANNEL.sendTo(
                        new CrackSoundMessage(
                                bulletPass.x,
                                bulletPass.y,
                                bulletPass.z,
                                (byte) 0,
                                0.0F,
                                1.0F,
                                (float) dist,
                                true
                        ),
                        (EntityPlayerMP) targetPlayer
                );
            }

            // сверхзвуковой хлопок
            if (shooterPos != null && CRACK_CLOSE_SOUND != null && !isSubsonic) {
                double shooterDist = shooterPos.distanceTo(bulletPass);

                if (shooterDist >= MIN_CRACK_DISTANCE) {
                    Vec3d dir = shooterPos.subtract(eyes);
                    double dirLen = dir.length();

                    Vec3d crackPos = dirLen > 0.001
                            ? eyes.add(dir.scale(2.0 / dirLen))
                            : eyes.add(new Vec3d(0, 0, 2));

                    float crackPitch = 0.94F + world.rand.nextFloat() * 0.1F;

                    float missDistForEffect = (float) dist;

                    CHANNEL.sendTo(
                            new CrackSoundMessage(
                                    crackPos.x,
                                    crackPos.y,
                                    crackPos.z,
                                    (byte) 1,
                                    0.18F,
                                    crackPitch,
                                    missDistForEffect,
                                    false
                            ),
                            (EntityPlayerMP) targetPlayer
                    );
                }
            }

            playedForPlayers.add(targetPlayer.getUniqueID());
        }

        prevPosOnServer = curPos;
    }

    @Override
    public void setPositionAndDirection(boolean isAiming) {
        setPositionAndDirectionFromShot(
                isAiming,
                thrower.rotationYaw,
                thrower.rotationPitch
        );
    }

    @Override
    public void setPositionAndDirectionFromShot(
            boolean isAiming,
            float shotYaw,
            float shotPitch
    ) {
        super.setPositionAndDirectionFromShot(
                isAiming,
                shotYaw,
                shotPitch
        );

        this.prevPosOnServer =
                new Vec3d(this.posX, this.posY, this.posZ);

        this.startPos =
                new Vec3d(this.posX, this.posY, this.posZ);
    }

    private boolean isBlockedByBlocks(Vec3d from, Vec3d to) {
        RayTraceResult result = world.rayTraceBlocks(from, to, false, true, false);
        return result != null && result.typeOfHit == RayTraceResult.Type.BLOCK;
    }

    @Override
    protected void onImpact(final RayTraceResult position) {
        if (position.typeOfHit == RayTraceResult.Type.ENTITY) {
            entityImpactPosition = position.hitVec;

        } else if (position.typeOfHit == RayTraceResult.Type.BLOCK) {
            // без этой строки onUpdate() не сможет обработать финальный сегмент,
            // и подавление у стены не сработает
            blockImpactPosition = position.hitVec;

            weapon.onSpawnEntityBlockImpact(world, null, this, position);
        }

        if (world.isRemote || weapon == null) {
            return;
        }

        if (explosionRadius > 0) {
            Explosion.createServerSideExplosion(
                    world,
                    this.getThrower(),
                    this,
                    position.hitVec.x,
                    position.hitVec.y,
                    position.hitVec.z,
                    explosionRadius,
                    false,
                    true,
                    isDestroyingBlocks,
                    explosionParticleAgeCoefficient,
                    smokeParticleAgeCoefficient,
                    explosionParticleScaleCoefficient,
                    smokeParticleScaleCoefficient,
                    weapon.getModContext().getRegisteredTexture(explosionParticleTextureId),
                    weapon.getModContext().getRegisteredTexture(smokeParticleTextureId),
                    weapon.getModContext().getExplosionSound()
            );

        } else if (position.entityHit != null) {
            position.entityHit.attackEntityFrom(
                    new ProjectileDamageSource("gun", weapon.getName(), this, this.getThrower()),
                    damage
            );

            position.entityHit.hurtResistantTime = 0;
            position.entityHit.prevRotationYaw -= 0.3F;

            ProjectConstants.LOGGER.debug("Hit entity {}", position.entityHit);

            NetworkRegistry.TargetPoint point = new NetworkRegistry.TargetPoint(
                    position.entityHit.dimension,
                    this.posX,
                    this.posY,
                    this.posZ,
                    100
            );

            final double bleedingCoefficient =
                    weapon.getBleedingCoefficient() * ModernConfigManager.enableBleedingOnHit;

            if (bleedingCoefficient > 0) {
                final RayTraceResult rayTraceResult =
                        HitUtil.traceProjectilehit(this, position.entityHit);

                if (rayTraceResult != null && rayTraceResult.typeOfHit == Type.BLOCK) {
                    CHANNEL.sendToAllAround(
                            new BloodClientMessage(
                                    VectorUtil.convertToVector3D(rayTraceResult.hitVec),
                                    new Vector3F(
                                            (float) motionX,
                                            (float) motionY,
                                            (float) motionZ
                                    )
                            ),
                            point
                    );
                }
            }
        }

        this.setDead();
    }

    @Override
    public void readSpawnData(final ByteBuf buffer) {
        super.readSpawnData(buffer);
        weapon = (Weapon) Item.getItemById(buffer.readInt());
        damage = buffer.readFloat();
        explosionRadius = buffer.readFloat();
        isDestroyingBlocks = buffer.readBoolean();
        spawnRocketParticles = buffer.readBoolean();
        explosionParticleAgeCoefficient = buffer.readFloat();
        smokeParticleAgeCoefficient = buffer.readFloat();
        explosionParticleScaleCoefficient = buffer.readFloat();
        smokeParticleScaleCoefficient = buffer.readFloat();
    }

    @Override
    public void readEntityFromNBT(final NBTTagCompound tagCompound) {
        super.readEntityFromNBT(tagCompound);
        weapon = (Weapon) Item.getItemById(tagCompound.getInteger(TAG_ENTITY_ITEM));
        damage = tagCompound.getFloat(TAG_DAMAGE);
        explosionRadius = tagCompound.getFloat(TAG_EXPLOSION_RADIUS);
        isDestroyingBlocks = tagCompound.getBoolean(TAG_EXPLOSION_IS_DESTROYING_BLOCKS);
        spawnRocketParticles = tagCompound.getBoolean(TAG_IS_SPAWN_ROCKET_PARTICLES);
        explosionParticleAgeCoefficient = tagCompound.getFloat(TAG_EXPLOSION_PARTICLE_AGE_COEFFICIENT);
        smokeParticleAgeCoefficient = tagCompound.getFloat(TAG_SMOKE_PARTICLE_AGE_COEFFICIENT);
        explosionParticleScaleCoefficient = tagCompound.getFloat(TAG_EXPLOSION_PARTICLE_SCALE_COEFFICIENT);
        smokeParticleScaleCoefficient = tagCompound.getFloat(TAG_SMOKE_PARTICLE_SCALE_COEFFICIENT);
        explosionParticleTextureId = tagCompound.getInteger(TAG_EXPLOSION_PARTICLE_TEXTURE_ID);
        smokeParticleTextureId = tagCompound.getInteger(TAG_SMOKE_PARTICLE_TEXTURE_ID);
    }

    @Override
    public void writeSpawnData(final ByteBuf buffer) {
        super.writeSpawnData(buffer);
        buffer.writeInt(Item.getIdFromItem(weapon));
        buffer.writeFloat(damage);
        buffer.writeFloat(explosionRadius);
        buffer.writeBoolean(isDestroyingBlocks);
        buffer.writeBoolean(spawnRocketParticles);
        buffer.writeFloat(explosionParticleAgeCoefficient);
        buffer.writeFloat(smokeParticleAgeCoefficient);
        buffer.writeFloat(explosionParticleScaleCoefficient);
        buffer.writeFloat(smokeParticleScaleCoefficient);
    }

    @Override
    public void writeEntityToNBT(final NBTTagCompound tagCompound) {
        super.writeEntityToNBT(tagCompound);
        tagCompound.setInteger(TAG_ENTITY_ITEM, Item.getIdFromItem(weapon));
        tagCompound.setFloat(TAG_DAMAGE, damage);
        tagCompound.setFloat(TAG_EXPLOSION_RADIUS, explosionRadius);
        tagCompound.setBoolean(TAG_EXPLOSION_IS_DESTROYING_BLOCKS, isDestroyingBlocks);
        tagCompound.setBoolean(TAG_IS_SPAWN_ROCKET_PARTICLES, spawnRocketParticles);
        tagCompound.setFloat(TAG_EXPLOSION_PARTICLE_AGE_COEFFICIENT, explosionParticleAgeCoefficient);
        tagCompound.setFloat(TAG_SMOKE_PARTICLE_AGE_COEFFICIENT, smokeParticleAgeCoefficient);
        tagCompound.setFloat(TAG_EXPLOSION_PARTICLE_SCALE_COEFFICIENT, explosionParticleScaleCoefficient);
        tagCompound.setFloat(TAG_SMOKE_PARTICLE_SCALE_COEFFICIENT, smokeParticleScaleCoefficient);
        tagCompound.setInteger(TAG_EXPLOSION_PARTICLE_TEXTURE_ID, explosionParticleTextureId);
        tagCompound.setInteger(TAG_SMOKE_PARTICLE_TEXTURE_ID, smokeParticleTextureId);
    }

    @Override
    public boolean canCollideWithBlock(final List<BlockPos> violators, final Block block, final BlockPos pos, final IBlockState iBlockState) {
        return !MWCUtil.isPenetrableByBullets(iBlockState) && super.canCollideWithBlock(violators, block, pos, iBlockState);
    }

    @Override
    public void shoot(final double x, final double y, final double z, final float velocity, final float inaccuracy) {}

    // вспомогательные методы для расчета CPA
    private static Vec3d closestPointOnSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double lengthSq = ab.lengthSquared();
        if (lengthSq == 0.0) return a;
        double t = p.subtract(a).dotProduct(ab) / lengthSq;
        t = Math.max(0.0, Math.min(1.0, t));
        return a.add(ab.scale(t));
    }

    private static double closestDistanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        return p.distanceTo(closestPointOnSegment(p, a, b));
    }

    public static class ProjectileDamageSource extends EntityDamageSourceIndirect {
        private final String gunName;

        public ProjectileDamageSource(String damageTypeIn, String gunName, Entity projectile, Entity shooter) {
            super(damageTypeIn, projectile, shooter);
            this.gunName = gunName;
        }

        @Nonnull
        @Override
        public ITextComponent getDeathMessage(@Nonnull EntityLivingBase entityLivingBaseIn) {
            if (this.getTrueSource() == null) {
                return new TextComponentTranslation("death.attack.gun.noshooter", entityLivingBaseIn.getDisplayName(), this.gunName);
            }
            return new TextComponentTranslation("death.attack.gun", entityLivingBaseIn.getDisplayName(), this.getTrueSource().getDisplayName(), this.gunName);
        }
    }
}

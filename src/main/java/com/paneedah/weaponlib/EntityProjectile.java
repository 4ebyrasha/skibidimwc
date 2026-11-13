package com.paneedah.weaponlib;

import com.paneedah.mwc.network.messages.BlockHitMessage;
import com.paneedah.mwc.utils.MWCUtil;
import com.paneedah.mwc.utils.VectorUtil;
import com.paneedah.weaponlib.config.ModernConfigManager;
import io.netty.buffer.ByteBuf;
import dev.redstudio.redcore.math.vectors.Vector3D;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.registry.IEntityAdditionalSpawnData;

import java.util.ArrayList;
import java.util.List;

import com.cheba.combat.history.CombatHistoryManager;
import com.cheba.combat.history.HistoricalPlayerState;
import net.minecraft.entity.player.EntityPlayerMP;

import static com.paneedah.mwc.MWC.CHANNEL;

public abstract class EntityProjectile extends Entity implements IProjectile, IEntityAdditionalSpawnData {

    private static final String TAG_GRAVITY_VELOCITY = "gravityVelocity";

    private static final int MAX_TICKS = 200;

    private static final int DEFAULT_MAX_LIFETIME = 5000;

    private static final double PROJECTILE_SPAWN_FORWARD_OFFSET = 0.35D;
    private static final double PROJECTILE_SPAWN_BLOCK_MARGIN = 0.01D;

    private int xTile = -1;
    private int yTile = -1;
    private int zTile = -1;
    //private IBlockState field_145785_f;
    protected boolean inGround;
    public int throwableShake;

    protected EntityLivingBase thrower;
    private String throwerName;
    //private int ticksInGround;
    private int ticksInAir;

    protected float gravityVelocity;
    protected float velocity;
    protected float inaccuracy;

    private long timestamp;

    private double aimTan;

    protected long maxLifetime = DEFAULT_MAX_LIFETIME;

    public EntityProjectile(World world) {
        super(world);
        this.setSize(0.25F, 0.25F);
        this.timestamp = System.currentTimeMillis();
    }

    public EntityProjectile(World world, EntityLivingBase thrower, float velocity, float gravityVelocity, float inaccuracy) {
        this(world);

        this.thrower = thrower;
        this.velocity = velocity;
        this.gravityVelocity = gravityVelocity;
        this.inaccuracy = inaccuracy;

//        if(thrower != null) {
//            RayTraceResult rayTraceResult = thrower.rayTrace(50, 0);
//            if(rayTraceResult != null && rayTraceResult.hitVec != null) {
//                double dx = MC.player.posX - rayTraceResult.hitVec.x;
//                double dy = MC.player.posY - rayTraceResult.hitVec.y;
//                double dz = MC.player.posZ - rayTraceResult.hitVec.z;
//                double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
//                this.aimTan = 0.4 / distance;
//            }
//        }
    }

    public void setPositionAndDirectionFromShot(
            boolean isAiming,
            float shotYaw,
            float shotPitch
    ) {
        Vec3d currentEyePosition = new Vec3d(
                thrower.posX,
                thrower.posY + thrower.getEyeHeight(),
                thrower.posZ
        );

        setPositionAndDirectionFromShot(
                isAiming,
                shotYaw,
                shotPitch,
                currentEyePosition
        );
    }

    /**
     * Initializes the projectile from an explicitly supplied shooter eye position.
     *
     * <p>The supplied position may represent a historical server-side position.
     * The live shooter entity is never moved or modified.</p>
     *
     * @param isAiming whether the weapon is aimed
     * @param shotYaw shot yaw in degrees
     * @param shotPitch shot pitch in degrees
     * @param eyePosition shooter eye position at the shot timestamp
     */
    public void setPositionAndDirectionFromShot(
            boolean isAiming,
            float shotYaw,
            float shotPitch,
            Vec3d eyePosition
    ) {
        if (eyePosition == null) {
            throw new IllegalArgumentException("eyePosition cannot be null");
        }

        double yawRadians = Math.toRadians(shotYaw);
        double pitchRadians = Math.toRadians(shotPitch);

        double cosPitch = Math.cos(pitchRadians);

        Vec3d direction = new Vec3d(
                -Math.sin(yawRadians) * cosPitch,
                -Math.sin(pitchRadians),
                Math.cos(yawRadians) * cosPitch
        );

        Vec3d spawnPosition = eyePosition.add(
                direction.scale(PROJECTILE_SPAWN_FORWARD_OFFSET)
        );

        /*
         * Block collision is still checked against the current world.
         * Historical block rollback is intentionally not part of this change.
         */
        RayTraceResult obstruction = world.rayTraceBlocks(
                eyePosition,
                spawnPosition,
                false,
                true,
                false
        );

        if (obstruction != null
                && obstruction.typeOfHit == RayTraceResult.Type.BLOCK) {

            double safeDistance = Math.max(
                    0.0D,
                    eyePosition.distanceTo(obstruction.hitVec)
                            - PROJECTILE_SPAWN_BLOCK_MARGIN
            );

            spawnPosition = eyePosition.add(
                    direction.scale(safeDistance)
            );
        }

        setPositionAndRotation(
                spawnPosition.x,
                spawnPosition.y,
                spawnPosition.z,
                shotYaw,
                shotPitch
        );

        motionX = direction.x;
        motionY = direction.y;
        motionZ = direction.z;

        setThrowableHeading(
                motionX,
                motionY,
                motionZ,
                velocity,
                inaccuracy
        );
    }

    /**
     * Similar to setArrowHeading, it's point the throwable entity to an x, y, z
     * direction.
     */
    public void setThrowableHeading(double x, double y, double z, float velocity, float inaccuracy) {
        float f2 = MathHelper.sqrt(x * x + y * y + z * z);
        x /= f2;
        y /= f2;
        z /= f2;
        x += this.rand.nextGaussian() * 0.007499999832361937D * (double) inaccuracy;
        y += this.rand.nextGaussian() * 0.007499999832361937D * (double) inaccuracy;
        z += this.rand.nextGaussian() * 0.007499999832361937D * (double) inaccuracy;
        x *= velocity;
        y *= velocity;
        z *= velocity;
        this.motionX = x;
        this.motionY = y;
        this.motionZ = z;
        float f3 = MathHelper.sqrt(x * x + z * z);
        this.prevRotationYaw = this.rotationYaw = (float) (Math.atan2(x, z) * 180.0D / Math.PI);
        this.prevRotationPitch = this.rotationPitch = (float) (Math.atan2(y, f3) * 180.0D / Math.PI);
        //this.ticksInGround = 0;
    }

    /**
     * Sets the velocity to the args. Args: x, y, z
     */
    // @SideOnly(Side.CLIENT)
    public void setVelocity(double mX, double mY, double mZ) {
        this.motionX = mX;
        this.motionY = mY;
        this.motionZ = mZ;

        if (this.prevRotationPitch == 0.0F && this.prevRotationYaw == 0.0F) {
            float f = MathHelper.sqrt(mX * mX + mZ * mZ);
            this.prevRotationYaw = this.rotationYaw = (float) (Math.atan2(mX, mZ) * 180.0D / Math.PI);
            this.prevRotationPitch = this.rotationPitch = (float) (Math.atan2(mY, f) * 180.0D
                    / Math.PI);
        }
    }

    /**
     * Called to update the entity's position/logic.
     */
    public void onUpdate() {
        if (ticksExisted > MAX_TICKS) {
            setDead(); // истекшие пули теперь удаляются из мира
            return;
        }

        this.lastTickPosX = this.posX;
        this.lastTickPosY = this.posY;
        this.lastTickPosZ = this.posZ;
        super.onUpdate();

        if (this.throwableShake > 0) {
            --this.throwableShake;
        }

        if (this.inGround) {
            /*
            BlockPos p = new BlockPos(this.xTile, this.yTile, this.zTile);
            if (compatibility.getBlockAtPosition(world, p) == this.field_145785_f) {
                ++this.ticksInGround;

                if (this.ticksInGround == 1200) {
                    this.setDead();
                }

                return;
            }
            */

            this.inGround = false;
            this.motionX *= this.rand.nextFloat() * 0.2F;
            this.motionY *= this.rand.nextFloat() * 0.2F;
            this.motionZ *= this.rand.nextFloat() * 0.2F;
            //this.ticksInGround = 0;
            this.ticksInAir = 0;

        } else {
            ++this.ticksInAir;
        }

        Vector3D vec3 = new Vector3D(this.posX, this.posY, this.posZ);
        Vector3D vec31 = new Vector3D(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);

        //List<BlockPos> possibleCollisions = new ArrayList<>();

        // Check what is at the projectile current position, null if air
        RayTraceResult movingobjectposition = MWCUtil.rayTraceBlocks(world, vec3, vec31, (block, blockMetadata) -> canCollideWithBlock(null, block, null, blockMetadata));

        /*
         *  GLASS BREAK CHECK
         */

        if (ModernConfigManager.bulletBreakGlass && !world.isRemote) {
            Vec3d motion = new Vec3d(this.motionX, this.motionY, this.motionZ);
            Vec3d start = new Vec3d(this.prevPosX, this.prevPosY, this.prevPosZ);
            Vec3d end = new Vec3d(this.posX, this.posY, this.posZ).add(motion);

            RayTraceResult rtr = world.rayTraceBlocks(start, end, false, true, false);
            if (rtr != null) {
                IBlockState state = world.getBlockState(rtr.getBlockPos());
                if (state.getMaterial() == Material.GLASS) {
                    if (ModernConfigManager.bulletBreakGlass) {
                        this.world.destroyBlock(rtr.getBlockPos(), true);
                    }

                    CHANNEL.sendToAllAround(new BlockHitMessage(rtr.getBlockPos(), VectorUtil.convertToVector3D(rtr.hitVec), rtr.sideHit), new NetworkRegistry.TargetPoint(this.dimension, this.posX, this.posY, this.posZ, 20.0));
                }
            }
        }

        vec3 = new Vector3D(this.posX, this.posY, this.posZ);
        vec31 = new Vector3D(this.posX + this.motionX, this.posY + this.motionY, this.posZ + this.motionZ);

        if (movingobjectposition != null) {
            vec31 = VectorUtil.convertToVector3D(movingobjectposition.hitVec);
        }

        if (!world.isRemote) {
            RayTraceResult entityHit = getRayTraceEntities(vec3, vec31);

            if (entityHit != null) {
                movingobjectposition = entityHit;
            }
        }

        if (movingobjectposition != null) {
            this.onImpact(movingobjectposition);
        }

        this.posX += this.motionX;
        this.posY += this.motionY;
        this.posZ += this.motionZ;
        float f1 = MathHelper.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
        this.rotationYaw = (float) (Math.atan2(this.motionX, this.motionZ) * 180.0D / Math.PI);

        for (this.rotationPitch = (float) (Math.atan2(this.motionY, f1) * 180.0D / Math.PI);
             this.rotationPitch - this.prevRotationPitch < -180.0F; this.prevRotationPitch -= 360.0F)
            ;

        while (this.rotationPitch - this.prevRotationPitch >= 180.0F) {
            this.prevRotationPitch += 360.0F;
        }

        while (this.rotationYaw - this.prevRotationYaw < -180.0F) {
            this.prevRotationYaw -= 360.0F;
        }

        while (this.rotationYaw - this.prevRotationYaw >= 180.0F) {
            this.prevRotationYaw += 360.0F;
        }

        this.rotationPitch = this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * 0.2F;
        this.rotationYaw = this.prevRotationYaw + (this.rotationYaw - this.prevRotationYaw) * 0.2F;
        float f2 = 0.99F;
        float f3 = gravityVelocity; //this.getGravityVelocity();

        if (this.isInWater()) {
            for (int i = 0; i < 4; ++i) {
                float f4 = 0.25F;
                EnumParticleTypes particleType = EnumParticleTypes.getByName("bubble");
                if (particleType != null) {
                    world.spawnParticle(particleType, this.posX - this.motionX * (double) f4, this.posY - this.motionY * (double) f4, this.posZ - this.motionZ * (double) f4, this.motionX, this.motionY, this.motionZ);
                }
            }

            f2 = 0.8F;
        }

        this.motionX *= f2;
        this.motionY *= f2;
        this.motionZ *= f2;
        this.motionY -= f3;
        this.setPosition(this.posX, this.posY, this.posZ);
    }

    /**
     * Performs swept entity collision for the current projectile segment.
     *
     * <p>Ordinary entities are checked against their current bounding boxes.
     * Server-side players are checked against historical bounding boxes when
     * a valid synchronized shot timestamp and historical state are available.</p>
     *
     * <p>All players in the current world are considered, even when their
     * current bounding boxes are far from the projectile. This is necessary
     * because their historical positions may intersect the projectile segment.</p>
     *
     * @param vec3 segment start
     * @param vec31 segment end, already clipped against block collision
     * @return nearest entity hit, including its exact intersection point
     */
    private RayTraceResult getRayTraceEntities(
            Vector3D vec3,
            Vector3D vec31
    ) {
        Vec3d start = VectorUtil.convertToVec3d(vec3);
        Vec3d end = VectorUtil.convertToVec3d(vec31);

        Vec3d segment = end.subtract(start);
        double segmentLengthSquared = segment.lengthSquared();

        if (segmentLengthSquared <= 1.0E-12D) {
            return null;
        }

        EntityLivingBase shooter = getThrower();

        List<Entity> entities = new ArrayList<>(
                world.getEntitiesWithinAABBExcludingEntity(
                        this,
                        getEntityBoundingBox()
                                .expand(motionX, motionY, motionZ)
                                .expand(1.0D, 1.0D, 1.0D)
                )
        );

        /*
         * Historical player positions can be outside the current projectile
         * search box. Add every player in this world to the candidate list.
         *
         * The contains check prevents processing players twice.
         */
        if (!world.isRemote) {
            for (EntityPlayer player : world.playerEntities) {
                if (!entities.contains(player)) {
                    entities.add(player);
                }
            }
        }

        long shotTimestampNanos = -1L;

        if (this instanceof WeaponSpawnEntity && !world.isRemote) {
            shotTimestampNanos =
                    ((WeaponSpawnEntity) this).getServerShotTimestampNanos();
        }

        RayTraceResult nearestHit = null;
        double nearestDistanceSquared = Double.MAX_VALUE;

        for (Entity target : entities) {
            if (target == null) {
                continue;
            }

            if (target instanceof com.cheba.api.IPerceptionFilter) {
                com.cheba.api.IPerceptionFilter filter =
                        (com.cheba.api.IPerceptionFilter) target;

                if (!filter.isPerceivableBy(shooter)) {
                    continue;
                }
            }

            if (!target.canBeCollidedWith()) {
                continue;
            }

            if (target instanceof EntityLivingBase
                    && ((EntityLivingBase) target).deathTime != 0) {
                continue;
            }

            /*
             * Preserve the original shooter collision grace period.
             * The shooter can become a collision target after five ticks.
             */
            if (target == shooter && ticksInAir < 5) {
                continue;
            }

            /*
             * Historical collision is only available for server-side players.
             * A valid historical state prevents fallback to the player's
             * current position, even when the historical box was not hit.
             */
            if (target instanceof EntityPlayerMP
                    && shotTimestampNanos > 0L) {

                EntityPlayerMP player = (EntityPlayerMP) target;

                long segmentStartNanos =
                        shotTimestampNanos
                                + Math.max(0L, ticksExisted - 1L)
                                * 50_000_000L;

                long segmentEndNanos =
                        segmentStartNanos + 50_000_000L;

                long midpointNanos =
                        segmentStartNanos
                                + (segmentEndNanos - segmentStartNanos) / 2L;

                HistoricalPlayerState midpointState =
                        CombatHistoryManager.getHistoricalState(
                                player.getUniqueID(),
                                midpointNanos
                        );

                if (midpointState != null
                        && midpointState.getEntityId() == player.getEntityId()
                        && midpointState.getDimension() == player.dimension) {

                    RayTraceResult historicalHit =
                            findHistoricalPlayerIntersection(
                                    player,
                                    start,
                                    end,
                                    segmentStartNanos,
                                    segmentEndNanos,
                                    segmentLengthSquared
                            );

                    if (historicalHit != null) {
                        double distanceSquared =
                                start.squareDistanceTo(historicalHit.hitVec);

                        if (distanceSquared < nearestDistanceSquared) {
                            nearestDistanceSquared = distanceSquared;
                            nearestHit = historicalHit;
                        }
                    }

                    continue;
                }
            }

            /*
             * Fallback for non-player entities and players without
             * usable historical data.
             */
            AxisAlignedBB collisionBox =
                    target.getEntityBoundingBox().expand(
                            0.15D,
                            0.15D,
                            0.15D
                    );

            RayTraceResult hit = collisionBox.calculateIntercept(start, end);

            if (hit == null || hit.hitVec == null) {
                continue;
            }

            double distanceSquared = start.squareDistanceTo(hit.hitVec);

            if (distanceSquared < nearestDistanceSquared) {
                nearestDistanceSquared = distanceSquared;
                nearestHit = new RayTraceResult(target, hit.hitVec);
            }
        }

        return nearestHit;
    }

    /**
     * Finds an intersection between a projectile segment and a player
     * moving between two historical snapshots.
     *
     * <p>The player's movement is converted into relative projectile
     * movement. This allows the collision test to account for target
     * translation during the projectile segment instead of checking
     * against a single static historical bounding box.</p>
     *
     * @param player target player
     * @param start projectile segment start
     * @param end projectile segment end
     * @param segmentStartNanos segment start timestamp
     * @param segmentEndNanos segment end timestamp
     * @param segmentLengthSquared squared projectile segment length
     * @return historical entity hit, or null when there is no valid hit
     */
    private RayTraceResult findHistoricalPlayerIntersection(
            EntityPlayerMP player,
            Vec3d start,
            Vec3d end,
            long segmentStartNanos,
            long segmentEndNanos,
            double segmentLengthSquared
    ) {
        if (player == null
                || start == null
                || end == null
                || segmentStartNanos <= 0L
                || segmentEndNanos <= segmentStartNanos
                || segmentLengthSquared <= 1.0E-12D) {
            return null;
        }

        HistoricalPlayerState startState =
                CombatHistoryManager.getHistoricalState(
                        player.getUniqueID(),
                        segmentStartNanos
                );

        HistoricalPlayerState endState =
                CombatHistoryManager.getHistoricalState(
                        player.getUniqueID(),
                        segmentEndNanos
                );

        if (!isValidHistoricalState(player, startState)
                || !isValidHistoricalState(player, endState)) {
            return null;
        }

        /*
         * Calculate target displacement during this projectile segment.
         */
        Vec3d targetMotion = new Vec3d(
                endState.getX() - startState.getX(),
                endState.getY() - startState.getY(),
                endState.getZ() - startState.getZ()
        );

        AxisAlignedBB startBox = startState.getBoundingBox();
        AxisAlignedBB endBox = endState.getBoundingBox();

        /*
         * Transform the ending box into the starting target coordinate frame.
         * The union also accounts for changes in player bounding-box dimensions.
         */
        AxisAlignedBB relativeEndBox = endBox.offset(
                -targetMotion.x,
                -targetMotion.y,
                -targetMotion.z
        );

        AxisAlignedBB sweptBox = new AxisAlignedBB(
                Math.min(startBox.minX, relativeEndBox.minX),
                Math.min(startBox.minY, relativeEndBox.minY),
                Math.min(startBox.minZ, relativeEndBox.minZ),
                Math.max(startBox.maxX, relativeEndBox.maxX),
                Math.max(startBox.maxY, relativeEndBox.maxY),
                Math.max(startBox.maxZ, relativeEndBox.maxZ)
        ).grow(0.15D);

        /*
         * In relative coordinates the target is stationary,
         * while the projectile receives the opposite target displacement.
         */
        Vec3d relativeEnd = end.subtract(targetMotion);

        RayTraceResult relativeHit =
                sweptBox.calculateIntercept(start, relativeEnd);

        if (relativeHit == null || relativeHit.hitVec == null) {
            return null;
        }

        Vec3d relativeSegment = relativeEnd.subtract(start);
        double relativeLengthSquared = relativeSegment.lengthSquared();

        if (relativeLengthSquared <= 1.0E-12D) {
            return null;
        }

        /*
         * Resolve the collision progress along the relative segment.
         */
        double progress = relativeHit.hitVec
                .subtract(start)
                .dotProduct(relativeSegment)
                / relativeLengthSquared;

        if (progress < 0.0D || progress > 1.0D) {
            return null;
        }

        /*
         * Convert the relative intersection back into world coordinates.
         */
        Vec3d worldHit = start.add(
                end.subtract(start).scale(progress)
        );

        return new RayTraceResult(player, worldHit);
    }

    /**
     * Validates that a historical snapshot still belongs to the live player.
     */
    private boolean isValidHistoricalState(
            EntityPlayerMP player,
            HistoricalPlayerState state
    ) {
        return state != null
                && state.getEntityId() == player.getEntityId()
                && state.getDimension() == player.dimension;
    }



    /**
     * Called when this EntityThrowable hits a block or entity.
     */
    protected abstract void onImpact(RayTraceResult p_70184_1_);

    /**
     * (abstract) Protected helper method to write subclass entity data to NBT.
     */
    public void writeEntityToNBT(NBTTagCompound tagCompound) {
        tagCompound.setLong("timestamp", timestamp);
        tagCompound.setShort("xTile", (short) this.xTile);
        tagCompound.setShort("yTile", (short) this.yTile);
        tagCompound.setShort("zTile", (short) this.zTile);
        //tagCompound.setByte("inTile", (byte) Block.getIdFromBlock(this.field_145785_f));
        tagCompound.setByte("shake", (byte) this.throwableShake);
        tagCompound.setByte("inGround", (byte) (this.inGround ? 1 : 0));

        if ((this.throwerName == null || this.throwerName.length() == 0) && this.thrower != null
                && this.thrower instanceof EntityPlayer) {
            this.throwerName = this.thrower.getName();
        }

        tagCompound.setString("ownerName", this.throwerName == null ? "" : this.throwerName);
        tagCompound.setFloat(TAG_GRAVITY_VELOCITY, gravityVelocity);
        tagCompound.setDouble("aimTan", this.aimTan);
    }

    /**
     * (abstract) Protected helper method to readVector3D subclass entity data from NBT.
     */
    public void readEntityFromNBT(NBTTagCompound tagCompound) {

        this.xTile = tagCompound.getShort("xTile");
        this.yTile = tagCompound.getShort("yTile");
        this.zTile = tagCompound.getShort("zTile");
        //this.field_145785_f = Block.getBlockById(tagCompound.getByte("inTile") & 255);
        this.throwableShake = tagCompound.getByte("shake") & 255;
        this.inGround = tagCompound.getByte("inGround") == 1;
        this.throwerName = tagCompound.getString("ownerName");

        if (this.throwerName != null && this.throwerName.length() == 0) {
            this.throwerName = null;
        }
        this.gravityVelocity = tagCompound.getFloat(TAG_GRAVITY_VELOCITY);
        this.timestamp = tagCompound.getLong("timestamp");
        this.aimTan = tagCompound.getDouble("aimTan");

        if (System.currentTimeMillis() > timestamp + maxLifetime) {
            setDead();
        }
    }

    @Override
    public void writeSpawnData(ByteBuf buffer) {
        buffer.writeFloat(gravityVelocity);
        buffer.writeDouble(aimTan);
    }

    @Override
    public void readSpawnData(ByteBuf buffer) {
        gravityVelocity = buffer.readFloat();
        aimTan = buffer.readDouble();
    }

    // @SideOnly(Side.CLIENT)
    public float getShadowSize() {
        return 0.0F;
    }

    public double getAimTan() {
        return aimTan;
    }

    public EntityLivingBase getThrower() {
        if (this.thrower == null && this.throwerName != null && this.throwerName.length() > 0) {
            this.thrower = world.getPlayerEntityByName(this.throwerName);
        }

        return this.thrower;
    }


    protected void entityInit() {
    }

    /**
     * Checks if the entity is in range to renderer by using the past in distance
     * and comparing it to its average edge length * 64 * renderDistanceWeight
     * Args: distance
     */
    // @SideOnly(Side.CLIENT)
    public boolean isInRangeToRenderDist(double p_70112_1_) {
        double d1 = this.getEntityBoundingBox().getAverageEdgeLength() * 4.0D;
        d1 *= 64.0D;
        return p_70112_1_ < d1 * d1;
    }

    public boolean canCollideWithBlock(List<BlockPos> violators, Block block, BlockPos pos, IBlockState iBlockState) {
        return block.canCollideCheck(iBlockState, false);
    }
}

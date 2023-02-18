package com.fusionflux.portalcubed.mixin;

import com.fusionflux.portalcubed.PortalCubed;
import com.fusionflux.portalcubed.accessor.CalledValues;
import com.fusionflux.portalcubed.blocks.PortalCubedBlocks;
import com.fusionflux.portalcubed.client.MixinPCClientAccessor;
import com.fusionflux.portalcubed.config.PortalCubedConfig;
import com.fusionflux.portalcubed.entity.EntityAttachments;
import com.fusionflux.portalcubed.entity.ExperimentalPortal;
import com.fusionflux.portalcubed.items.PortalCubedItems;
import com.fusionflux.portalcubed.packet.NetworkingSafetyWrapper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerAbilities;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.quiltmc.qsl.networking.api.PacketByteBufs;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin extends LivingEntity implements EntityAttachments {
    @Unique
    private boolean cfg;

    protected PlayerEntityMixin(EntityType<? extends LivingEntity> entityType, World world) {
        super(entityType, world);
        throw new AssertionError(
                PortalCubed.MOD_ID + "'s PlayerEntityMixin dummy constructor was called, " +
                        "something is very wrong here!"
        );
    }

    @Shadow
    @Override
    public abstract ItemStack getEquippedStack(EquipmentSlot slot);

    @Shadow
    @Override
    public abstract void playSound(SoundEvent sound, float volume, float pitch);

    @Shadow
    @Override
    public abstract boolean isSwimming();

    @Shadow @Final private PlayerAbilities abilities;

    @Override
    @Shadow public abstract float getMovementSpeed();

    @Inject(method = "isInvulnerableTo", at = @At("HEAD"), cancellable = true)
    public void portalCubed$letYouFallLonger(DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        ItemStack itemStack5 = this.getEquippedStack(EquipmentSlot.FEET);
        if (damageSource == DamageSource.FALL && (itemStack5.getItem().equals(PortalCubedItems.LONG_FALL_BOOTS))) {
            cir.setReturnValue(true);
        }
    }

    @ModifyVariable(method = "travel", at = @At("HEAD"), argsOnly = true)
    private Vec3d portalCubed$what(Vec3d travelVectorOriginal) {
        if (!this.hasNoGravity()) {
            ItemStack itemFeet = this.getEquippedStack(EquipmentSlot.FEET);
            if (!this.isOnGround() && PortalCubedConfig.enableAccurateMovement && !this.isSwimming() && !this.abilities.flying && !this.isFallFlying() && itemFeet.getItem().equals(PortalCubedItems.LONG_FALL_BOOTS) && !this.world.getBlockState(this.getBlockPos()).getBlock().equals(PortalCubedBlocks.EXCURSION_FUNNEL) && !this.world.getBlockState(new BlockPos(this.getBlockPos().getX(), this.getBlockPos().getY() + 1, this.getBlockPos().getZ())).getBlock().equals(PortalCubedBlocks.EXCURSION_FUNNEL)) {
                double mathVal = 1;
                double horizontalVelocity = Math.abs(this.getVelocity().x) + Math.abs(this.getVelocity().z);
                if (horizontalVelocity / 0.01783440120041885 > 1) {
                    mathVal = horizontalVelocity / 0.01783440120041885;
                }
                travelVectorOriginal = new Vec3d(travelVectorOriginal.x / mathVal, travelVectorOriginal.y, travelVectorOriginal.z / mathVal);
                this.flyingSpeed = .04f;
            }
        }
        if (CalledValues.getIsTeleporting(this)) {
            travelVectorOriginal = Vec3d.ZERO;
        }

        return travelVectorOriginal;
    }

    private boolean enableNoDrag2;

    private static final Box NULL_BOX = new Box(0, 0, 0, 0, 0, 0);
    @Inject(method = "tick", at = @At("HEAD"))
    public void tickHead(CallbackInfo ci) {
        PlayerEntity thisEntity = ((PlayerEntity) (Object) this);

        if (world.isClient && CalledValues.getHasTeleportationHappened(thisEntity)) {
            var byteBuf = PacketByteBufs.create();
            NetworkingSafetyWrapper.sendFromClient("client_teleport_update", byteBuf);
            CalledValues.setHasTeleportationHappened(thisEntity, false);
            ((EntityAttachments) thisEntity).setMaxFallHeight(-99999999);
            CalledValues.setIsTeleporting(thisEntity, false);
            this.setVelocity(CalledValues.getVelocityUpdateAfterTeleport(thisEntity));
        }

        Vec3d entityVelocity = this.getVelocity();

        Box portalCheckBox = getBoundingBox();
        if (!this.world.isClient) {
            portalCheckBox = portalCheckBox.expand(10);
        } else {
            portalCheckBox = portalCheckBox.stretch(entityVelocity);
        }
        List<ExperimentalPortal> list = world.getNonSpectatingEntities(ExperimentalPortal.class, portalCheckBox);
        VoxelShape omittedDirections = VoxelShapes.empty();

        for (ExperimentalPortal portal : list) {
            if (portal.calculateCuttoutBox() != NULL_BOX) {
                if (portal.getActive())
                    omittedDirections = VoxelShapes.union(omittedDirections, VoxelShapes.cuboid(portal.getCutoutBoundingBox()));
            }
        }
        CalledValues.setPortalCutout(((PlayerEntity) (Object) this), omittedDirections);


        ItemStack itemFeet = this.getEquippedStack(EquipmentSlot.FEET);
        if ((!this.isOnGround() && PortalCubedConfig.enableAccurateMovement && !this.isSwimming() && !this.abilities.flying && !this.isFallFlying() && itemFeet.getItem().equals(PortalCubedItems.LONG_FALL_BOOTS) && !this.world.getBlockState(this.getBlockPos()).getBlock().equals(PortalCubedBlocks.EXCURSION_FUNNEL) && !this.world.getBlockState(new BlockPos(this.getBlockPos().getX(), this.getBlockPos().getY() + 1, this.getBlockPos().getZ())).getBlock().equals(PortalCubedBlocks.EXCURSION_FUNNEL))) {
            if (!enableNoDrag2) {
                enableNoDrag2 = true;
            }
            this.setNoDrag(true);
        } else if (enableNoDrag2) {
            enableNoDrag2 = false;
            this.setNoDrag(false);
        }

        if (itemFeet.getItem().equals(PortalCubedItems.LONG_FALL_BOOTS)) {
            if (this.getVelocity().y < -3.92) {
                this.setVelocity(this.getVelocity().add(0, .081d, 0));
            }
        }

        if (!world.isClient || !MixinPCClientAccessor.allowCfg() || !isSneaking()) {
            cfg = false;
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tickTail(CallbackInfo ci) {
        if (this.world.isClient) {
            PlayerEntity thisEntity = ((PlayerEntity) (Object) this);

            Vec3d entityVelocity = thisEntity.getVelocity();

            Box portalCheckBox = getBoundingBox();

            portalCheckBox = portalCheckBox.stretch(entityVelocity).stretch(entityVelocity.multiply(-1));


            List<ExperimentalPortal> list = world.getNonSpectatingEntities(ExperimentalPortal.class, portalCheckBox);
            ExperimentalPortal portal;
            for (ExperimentalPortal portalCheck : list) {
                portal = portalCheck;
                if (this.canUsePortals() && portal.getActive() && !CalledValues.getHasTeleportationHappened(thisEntity) && !CalledValues.getIsTeleporting(thisEntity)) {
                    Direction portalFacing = portal.getFacingDirection();
                    Direction otherDirec = Direction.fromVector((int) portal.getOtherFacing().getX(), (int) portal.getOtherFacing().getY(), (int) portal.getOtherFacing().getZ());

                    if (otherDirec != null) {


                        if (thisEntity.hasNoDrag()) {
                            entityVelocity = entityVelocity.add(0, .08, 0);
                        } else {
                            entityVelocity = entityVelocity.add(0, .08 * .98, 0);
                        }

                        Vec3d entityEyePos = thisEntity.getEyePos();

                        if (portalFacing.getUnitVector().getX() < 0) {
                            if (entityEyePos.getX() + entityVelocity.x >= portal.getPos().getX() && entityVelocity.getX() > 0 && portal.calculateBoundsCheckBox().intersects(thisEntity.getBoundingBox())) {
                                performTeleport(thisEntity, portal, entityVelocity);
                                break;
                            }
                        }
                        if (portalFacing.getUnitVector().getY() < 0) {
                            if (entityEyePos.getY() + entityVelocity.y >= portal.getPos().getY() && entityVelocity.getY() > 0 && portal.calculateBoundsCheckBox().intersects(thisEntity.getBoundingBox())) {
                                performTeleport(thisEntity, portal, entityVelocity);
                                break;
                            }
                        }
                        if (portalFacing.getUnitVector().getZ() < 0) {
                            if (entityEyePos.getZ() + entityVelocity.z >= portal.getPos().getZ() && entityVelocity.getZ() > 0 && portal.calculateBoundsCheckBox().intersects(thisEntity.getBoundingBox())) {
                                performTeleport(thisEntity, portal, entityVelocity);
                                break;
                            }
                        }
                        if (portalFacing.getUnitVector().getX() > 0) {
                            if (entityEyePos.getX() + entityVelocity.x <= portal.getPos().getX() && entityVelocity.getX() < 0 && portal.calculateBoundsCheckBox().intersects(thisEntity.getBoundingBox())) {
                                performTeleport(thisEntity, portal, entityVelocity);
                                break;
                            }
                        }
                        if (portalFacing.getUnitVector().getY() > 0) {
                            if (entityEyePos.getY() + entityVelocity.y <= portal.getPos().getY() && entityVelocity.getY() < 0 && portal.calculateBoundsCheckBox().intersects(thisEntity.getBoundingBox())) {
                                performTeleport(thisEntity, portal, entityVelocity);
                                break;
                            }
                        }
                        if (portalFacing.getUnitVector().getZ() > 0) {
                            if (entityEyePos.getZ() + entityVelocity.z <= portal.getPos().getZ() && entityVelocity.getZ() < 0 && portal.calculateBoundsCheckBox().intersects(thisEntity.getBoundingBox())) {
                                performTeleport(thisEntity, portal, entityVelocity);
                                break;
                            }
                        }

                    }
                }
            }
        }
    }

    private void performTeleport(
            PlayerEntity thisEntity,
            ExperimentalPortal portal,
            Vec3d entityVelocity
    ) {
        if (this.world.isClient && thisEntity.isMainPlayer()) {
            var byteBuf = PacketByteBufs.create();
            byteBuf.writeVarInt(portal.getId());
            byteBuf.writeFloat(thisEntity.getYaw());
            byteBuf.writeFloat(thisEntity.getPitch());
            byteBuf.writeDouble(entityVelocity.x);
            byteBuf.writeDouble(entityVelocity.y);
            byteBuf.writeDouble(entityVelocity.z);
            byteBuf.writeDouble((thisEntity.getEyePos().getX()) - portal.getPos().getX());
            byteBuf.writeDouble((thisEntity.getEyePos().getY()) - portal.getPos().getY());
            byteBuf.writeDouble((thisEntity.getEyePos().getZ()) - portal.getPos().getZ());
            NetworkingSafetyWrapper.sendFromClient("use_portal", byteBuf);
            CalledValues.setIsTeleporting(thisEntity, true);
        }
    }


    @Inject(method = "dropItem(Lnet/minecraft/item/ItemStack;ZZ)Lnet/minecraft/entity/ItemEntity;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getEyeY()D"))
    public void portalCubed$dropItem(ItemStack stack, boolean throwRandomly, boolean retainOwnership, CallbackInfoReturnable<@Nullable ItemEntity> cir) {
        if (!this.world.isClient && stack.getItem().equals(PortalCubedItems.PORTAL_GUN)) {
            NbtCompound tag = stack.getOrCreateNbt();
            NbtCompound portalsTag = tag.getCompound(world.getRegistryKey().toString());
            ExperimentalPortal portalHolder;
            if (portalsTag.contains(("Left") + "Portal")) {
                portalHolder = (ExperimentalPortal) ((ServerWorld) world).getEntity(portalsTag.getUuid(("Left") + "Portal"));
                if (portalHolder != null) {
                    portalHolder.kill();
                }
            }
            if (portalsTag.contains(("Right") + "Portal")) {
                portalHolder = (ExperimentalPortal) ((ServerWorld) world).getEntity(portalsTag.getUuid(("Right") + "Portal"));
                if (portalHolder != null) {
                    portalHolder.kill();
                }
            }
        }
    }

    @Override
    public boolean cfg() {
        return cfg;
    }

    @Override
    public void setCFG() {
        cfg = true;
    }
}





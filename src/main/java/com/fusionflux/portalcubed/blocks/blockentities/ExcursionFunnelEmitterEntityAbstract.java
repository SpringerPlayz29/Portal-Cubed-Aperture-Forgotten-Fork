package com.fusionflux.portalcubed.blocks.blockentities;

import com.fusionflux.portalcubed.blocks.PortalCubedBlocks;
import com.fusionflux.portalcubed.config.PortalCubedConfig;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.apache.commons.lang3.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * @author sailKite
 * @author FusionFlux
 * <p>
 * Handles the operating logic for the {@link HardLightBridgeEmitterBlock} and their associated bridges.
 */
public abstract class ExcursionFunnelEmitterEntityAbstract extends BlockEntity {

    public final int MAX_RANGE = PortalCubedConfig.get().numbersblock.maxBridgeLength;
    public List<Integer> posXList;
    public List<Integer> posYList;
    public List<Integer> posZList;

    public ExcursionFunnelEmitterEntityAbstract(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type,pos,state);
        List<Integer> emptyList = new ArrayList<>();
        this.posXList = emptyList;
        this.posYList = emptyList;
        this.posZList = emptyList;
    }


    public void playSound(SoundEvent soundEvent) {
        this.world.playSound(null, this.pos, soundEvent, SoundCategory.BLOCKS, 0.1F, 3.0F);
    }

    @Override
    public void writeNbt(NbtCompound tag) {
        super.writeNbt(tag);
        tag.putIntArray("xList", posXList);
        tag.putIntArray("yList", posYList);
        tag.putIntArray("zList", posZList);
    }

    @Override
    public void readNbt(NbtCompound tag) {
        super.readNbt(tag);
         posXList = Arrays.asList(ArrayUtils.toObject(tag.getIntArray("xList")));
         posYList = Arrays.asList(ArrayUtils.toObject(tag.getIntArray("yList")));
         posZList = Arrays.asList(ArrayUtils.toObject(tag.getIntArray("zList")));
    }

    public void togglePowered(BlockState state) {
        assert world != null;
        world.setBlockState(pos, state.cycle(Properties.POWERED));
        if (world.getBlockState(pos).get(Properties.POWERED)) {
            this.playSound(SoundEvents.BLOCK_BEACON_ACTIVATE);
        }
        if (!world.getBlockState(pos).get(Properties.POWERED)) {
            this.playSound(SoundEvents.BLOCK_BEACON_DEACTIVATE);
        }
    }
}
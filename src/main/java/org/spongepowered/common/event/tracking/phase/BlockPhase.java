/*
 * This file is part of Sponge, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.spongepowered.common.event.tracking.phase;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.EntitySnapshot;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.cause.entity.spawn.BlockSpawnCause;
import org.spongepowered.api.event.cause.entity.spawn.EntitySpawnCause;
import org.spongepowered.common.block.SpongeBlockSnapshot;
import org.spongepowered.common.event.EventConsumer;
import org.spongepowered.common.event.tracking.CauseTracker;
import org.spongepowered.common.event.tracking.IPhaseState;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.TrackingHelper;
import org.spongepowered.common.registry.type.event.InternalSpawnTypes;
import org.spongepowered.common.world.CaptureType;

import java.util.List;
import java.util.stream.Collectors;

public class BlockPhase extends TrackingPhase {

    public enum State implements IPhaseState {
        BLOCK_DECAY,
        RESTORING_BLOCKS,
        POST_NOTIFICATION_EVENT,
        DISPENSE,
        BLOCK_DROP_ITEMS;

        @Override
        public boolean canSwitchTo(IPhaseState state) {
            return false;
        }

        @Override
        public BlockPhase getPhase() {
            return TrackingPhases.BLOCK;
        }

    }

    public BlockPhase(TrackingPhase parent) {
        super(parent);
    }

    @Override
    public boolean requiresBlockCapturing(IPhaseState currentState) {
        return currentState != State.RESTORING_BLOCKS;
    }

    @Override
    public void captureBlockChange(CauseTracker causeTracker, IBlockState currentState, IBlockState newState, Block newBlock, BlockPos pos, int flags, PhaseContext phaseContext, IPhaseState phaseState) {
        // Only capture final state of decay, ignore the rest
        if (newBlock == Blocks.air) {
            final IBlockState actualState = currentState.getBlock().getActualState(currentState, causeTracker.getMinecraftWorld(), pos);
            BlockSnapshot originalBlockSnapshot = causeTracker.getMixinWorld().createSpongeBlockSnapshot(currentState, actualState, pos, flags);
            ((SpongeBlockSnapshot) originalBlockSnapshot).captureType = CaptureType.DECAY;
            phaseContext.getCapturedBlocks().get().add(originalBlockSnapshot);
        } else {
            super.captureBlockChange(causeTracker, currentState, newState, newBlock, pos, flags, phaseContext, phaseState);
        }
    }

    @Override
    public boolean allowEntitySpawns(IPhaseState currentState) {
        return currentState != State.RESTORING_BLOCKS;
    }

    @Override
    public void unwind(CauseTracker causeTracker, IPhaseState state, PhaseContext phaseContext) {
        if (state == State.BLOCK_DECAY) {

        } else if (state == State.BLOCK_DROP_ITEMS) {
            phaseContext.getCapturedItemsSupplier().get().ifPresent(items -> {
                final Cause cause = Cause.source(BlockSpawnCause.builder()
                        .block(phaseContext.firstNamed(NamedCause.SOURCE, BlockSnapshot.class).get())
                        .type(InternalSpawnTypes.DROPPED_ITEM)
                        .build())
                    .build();
                final List<EntitySnapshot> snapshots = items.stream().map(Entity::createSnapshot).collect(Collectors.toList());
                EventConsumer.supplyEvent(() -> SpongeEventFactory.createDropItemEventDestruct(cause, items, snapshots, causeTracker.getWorld()))
                    .nonCancelled(event -> event.getEntities().forEach(entity -> causeTracker.getMixinWorld().forceSpawnEntity(entity)))
                    .buildAndPost();
            });
            phaseContext.getCapturedEntitySupplier().get().ifPresent(entities -> {
                final Cause cause = Cause.source(BlockSpawnCause.builder()
                    .block(phaseContext.firstNamed(NamedCause.SOURCE, BlockSnapshot.class).get())
                    .type(InternalSpawnTypes.DROPPED_ITEM)
                    .build())
                    .build();
                final List<EntitySnapshot> snapshots = entities.stream().map(Entity::createSnapshot).collect(Collectors.toList());
                EventConsumer.supplyEvent(() -> SpongeEventFactory.createSpawnEntityEvent(cause, entities, snapshots, causeTracker.getWorld()))
                    .nonCancelled(event -> event.getEntities().forEach(entity -> causeTracker.getMixinWorld().forceSpawnEntity(entity)))
                    .buildAndPost();
            });
        }

    }

}
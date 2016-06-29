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
package org.spongepowered.common.event.tracking.phase.function;

import com.flowpowered.math.vector.Vector3d;
import com.google.common.collect.ImmutableList;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.CPacketClickWindow;
import net.minecraft.network.play.client.CPacketClientSettings;
import net.minecraft.network.play.client.CPacketCreativeInventoryAction;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.network.play.client.CPacketResourcePackStatus;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.network.play.server.SPacketHeldItemChange;
import net.minecraft.network.play.server.SPacketOpenWindow;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IInteractionObject;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldServerMulti;
import org.apache.logging.log4j.Level;
import org.spongepowered.api.block.BlockSnapshot;
import org.spongepowered.api.data.Transaction;
import org.spongepowered.api.entity.Entity;
import org.spongepowered.api.entity.living.player.Player;
import org.spongepowered.api.event.SpongeEventFactory;
import org.spongepowered.api.event.cause.Cause;
import org.spongepowered.api.event.cause.NamedCause;
import org.spongepowered.api.event.cause.entity.spawn.EntitySpawnCause;
import org.spongepowered.api.event.entity.SpawnEntityEvent;
import org.spongepowered.api.event.entity.living.humanoid.player.PlayerChangeClientSettingsEvent;
import org.spongepowered.api.event.entity.living.humanoid.player.ResourcePackStatusEvent;
import org.spongepowered.api.event.item.inventory.ChangeInventoryEvent;
import org.spongepowered.api.event.item.inventory.ClickInventoryEvent;
import org.spongepowered.api.event.item.inventory.CreativeInventoryEvent;
import org.spongepowered.api.event.item.inventory.DropItemEvent;
import org.spongepowered.api.event.item.inventory.InteractInventoryEvent;
import org.spongepowered.api.item.inventory.Inventory;
import org.spongepowered.api.item.inventory.ItemStack;
import org.spongepowered.api.item.inventory.ItemStackSnapshot;
import org.spongepowered.api.item.inventory.transaction.SlotTransaction;
import org.spongepowered.api.resourcepack.ResourcePack;
import org.spongepowered.api.text.chat.ChatVisibility;
import org.spongepowered.api.world.Location;
import org.spongepowered.api.world.World;
import org.spongepowered.asm.util.PrettyPrinter;
import org.spongepowered.common.SpongeImpl;
import org.spongepowered.common.data.util.NbtDataUtil;
import org.spongepowered.common.entity.EntityUtil;
import org.spongepowered.common.event.InternalNamedCauses;
import org.spongepowered.common.event.tracking.CauseTracker;
import org.spongepowered.common.event.tracking.PhaseContext;
import org.spongepowered.common.event.tracking.TrackingUtil;
import org.spongepowered.common.event.tracking.phase.ItemDropData;
import org.spongepowered.common.event.tracking.phase.PacketPhase;
import org.spongepowered.common.event.tracking.phase.util.PacketPhaseUtil;
import org.spongepowered.common.event.tracking.phase.util.PhaseUtil;
import org.spongepowered.common.interfaces.IMixinContainer;
import org.spongepowered.common.interfaces.network.IMixinNetHandlerPlayServer;
import org.spongepowered.common.interfaces.world.IMixinWorldServer;
import org.spongepowered.common.item.inventory.adapter.impl.slots.SlotAdapter;
import org.spongepowered.common.item.inventory.util.ContainerUtil;
import org.spongepowered.common.item.inventory.util.ItemStackUtil;
import org.spongepowered.common.registry.type.event.InternalSpawnTypes;
import org.spongepowered.common.util.LanguageUtil;
import org.spongepowered.common.util.SkinUtil;
import org.spongepowered.common.util.VecHelper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@FunctionalInterface
public interface PacketFunction {

    PacketFunction IGNORED = (packet, state, player, context) -> {
    };
    PacketFunction HANDLED_EXTERNALLY = IGNORED;

    PacketFunction USE_ENTITY = (packet, state, player, context) -> {
        final CPacketUseEntity useEntityPacket = (CPacketUseEntity) packet;
        final net.minecraft.entity.Entity entity = useEntityPacket.getEntityFromWorld(player.worldObj);
        if (entity == null) {
            // Something happened?
        }
        final Optional<ItemStack> itemStack = context.firstNamed(InternalNamedCauses.Packet.ITEM_USED, ItemStack.class);
        final IMixinWorldServer mixinWorldServer = (IMixinWorldServer) player.worldObj;
        final CauseTracker causeTracker = mixinWorldServer.getCauseTracker();
        EntityUtil.toMixin(entity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_NOTIFIER, player.getUniqueID());

        if (state == PacketPhase.General.ATTACK_ENTITY) {
            context.getCapturedItemsSupplier()
                    .ifPresentAndNotEmpty(items -> {
                        // For destruction, this should be empty, however, some times, it may not be?
                        final PrettyPrinter printer = new PrettyPrinter(60);
                        printer.add("Processing Attack Entity").centre().hr();
                        printer.add("There are some captured items after the entity was destructed!");
                        printer.addWrapped(60, "%s : %s", "Items captured", items);
                        printer.add("Stacktrace:");
                        printer.add(new Exception("Stack trace"));
                        printer.trace(System.err, SpongeImpl.getLogger(), Level.TRACE);
                    });
            context.getCapturedBlockSupplier()
                    .ifPresentAndNotEmpty(blocks ->
                            GeneralFunctions.processBlockCaptures(blocks, causeTracker, state, context));
            context.getCapturedEntityDropSupplier().ifPresentAndNotEmpty(map -> {
                for (Map.Entry<UUID, Collection<ItemDropData>> entry : map.asMap().entrySet()) {
                    final UUID key = entry.getKey();
                    final Optional<Entity> affectedEntity = causeTracker.getWorld().getEntity(key);
                    if (!affectedEntity.isPresent()) {
                        continue;
                    }
                    final Collection<ItemDropData> itemStacks = entry.getValue();
                    if (itemStacks.isEmpty()) {
                        return;
                    }
                    final List<ItemDropData> items = new ArrayList<>();
                    items.addAll(itemStacks);

                    if (!items.isEmpty()) {
                        final List<Entity> itemEntities = items.stream()
                                .map(data -> data.create(causeTracker.getMinecraftWorld()))
                                .map(EntityUtil::fromNative)
                                .collect(Collectors.toList());
                        final Cause cause = Cause.source(EntitySpawnCause.builder()
                                .entity(affectedEntity.get())
                                .type(InternalSpawnTypes.DROPPED_ITEM)
                                .build()
                        )
                                .named(NamedCause.of("Attacker", player))
                                .build();
                        final DropItemEvent.Destruct
                                destruct =
                                SpongeEventFactory.createDropItemEventDestruct(cause, itemEntities, causeTracker.getWorld());
                        SpongeImpl.postEvent(destruct);
                        if (!destruct.isCancelled()) {
                            processSpawnedEntities(player, causeTracker, destruct);

                        }
                    }
                }
            });
            context.getCapturedEntityItemDropSupplier().ifPresentAndNotEmpty(map -> {
                for (Map.Entry<UUID, Collection<EntityItem>> entry : map.asMap().entrySet()) {
                    final UUID key = entry.getKey();
                    final Optional<Entity> attackedEntities = causeTracker.getWorld().getEntity(key);
                    if (!attackedEntities.isPresent()) {
                        continue;
                    }
                    final List<Entity> items = entry.getValue().stream().map(EntityUtil::fromNative).collect(Collectors.toList());
                    final Cause cause = Cause.source(EntitySpawnCause.builder()
                            .entity(EntityUtil.fromNative(player))
                            .type(InternalSpawnTypes.DROPPED_ITEM)
                            .build()
                    )
                            .named(NamedCause.of("Attacker", player))
                            .build();
                    final DropItemEvent.Destruct
                            destruct =
                            SpongeEventFactory.createDropItemEventDestruct(cause, items, causeTracker.getWorld());
                    SpongeImpl.postEvent(destruct);
                    if (!destruct.isCancelled()) {
                        processSpawnedEntities(player, causeTracker, destruct);

                    }
                }
            });


        } else if (state == PacketPhase.General.INTERACT_ENTITY) {
            context.getCapturedBlockSupplier()
                    .ifPresentAndNotEmpty(blocks -> {
                        final PrettyPrinter printer = new PrettyPrinter(80);
                        printer.add("Processing Interact Entity").centre().hr();
                        printer.add("The blocks captured are:");
                        for (BlockSnapshot blockSnapshot : blocks) {
                            printer.add("  Block: %s", blockSnapshot);
                        }
                        printer.trace(System.err);
                    });
            context.getCapturedEntitySupplier()
                    .ifPresentAndNotEmpty(entities -> {
                        final PrettyPrinter printer = new PrettyPrinter(80);
                        printer.add("Processing Interact Entity").centre().hr();
                        printer.add("The entities captured are:");
                        for (Entity capturedEntity : entities) {
                            printer.add("  Entity: %s", capturedEntity);
                        }
                        printer.trace(System.err);
                    });
            context.getCapturedItemsSupplier().ifPresentAndNotEmpty(entities -> {
                final List<Entity> items = entities.stream().map(EntityUtil::fromNative).collect(Collectors.toList());
                final Cause cause = Cause.source(EntitySpawnCause.builder()
                        .entity((Player) player)
                        .type(InternalSpawnTypes.PLACEMENT)
                        .build()
                ).build();
                SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(cause, items, (World) mixinWorldServer);
                SpongeImpl.postEvent(event);
                if (!event.isCancelled()) {
                    for (Entity spawnedEntity : event.getEntities()) {
                        EntityUtil.toMixin(spawnedEntity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                        mixinWorldServer.forceSpawnEntity(spawnedEntity);
                    }
                }
            });
            context.getCapturedEntityDropSupplier()
                    .ifPresentAndNotEmpty(map -> {
                        final PrettyPrinter printer = new PrettyPrinter(80);
                        printer.add("Processing Interact Entity").centre().hr();
                        printer.add("The item stacks captured are: ");

                        for (Map.Entry<UUID, Collection<ItemDropData>> entry : map.asMap().entrySet()) {
                            printer.add("  - Entity with UUID: %s", entry.getKey());
                            for (ItemDropData stack : entry.getValue()) {
                                printer.add("    - %s", stack);
                            }
                        }
                        printer.trace(System.err);
                    });

        } else if (state == PacketPhase.General.INTERACT_AT_ENTITY) {
            context.getCapturedEntitySupplier().ifPresentAndNotEmpty(entities -> {
                final Cause cause = Cause.source(EntitySpawnCause.builder()
                        .entity((Player) player)
                        .type(InternalSpawnTypes.PLACEMENT)
                        .build()
                ).build();
                SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(cause, entities, (World) mixinWorldServer);
                SpongeImpl.postEvent(event);
                if (!event.isCancelled()) {
                    for (Entity spawnedEntity : event.getEntities()) {
                        EntityUtil.toMixin(spawnedEntity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                        mixinWorldServer.forceSpawnEntity(spawnedEntity);
                    }
                }
            });
            context.getCapturedItemsSupplier().ifPresentAndNotEmpty(entities -> {
                final List<Entity> items = entities.stream().map(EntityUtil::fromNative).collect(Collectors.toList());
                final Cause cause = Cause.source(EntitySpawnCause.builder()
                        .entity((Player) player)
                        .type(InternalSpawnTypes.PLACEMENT)
                        .build()
                ).build();
                SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(cause, items, (World) mixinWorldServer);
                SpongeImpl.postEvent(event);
                if (!event.isCancelled()) {
                    for (Entity spawnedEntity : event.getEntities()) {
                        EntityUtil.toMixin(spawnedEntity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                        mixinWorldServer.forceSpawnEntity(spawnedEntity);
                    }
                }
            });
            context.getCapturedEntityDropSupplier().ifPresentAndNotEmpty(map -> {
                final PrettyPrinter printer = new PrettyPrinter(80);
                printer.add("Processing Interact At Entity").centre().hr();
                printer.add("The item stacks captured are: ");

                for (Map.Entry<UUID, Collection<ItemDropData>> entry : map.asMap().entrySet()) {
                    printer.add("  - Entity with UUID: %s", entry.getKey());
                    for (ItemDropData stack : entry.getValue()) {
                        printer.add("    - %s", stack);
                    }
                }
                printer.trace(System.err);
            });
            context.getCapturedEntityItemDropSupplier().ifPresentAndNotEmpty(map -> {
                for (Map.Entry<UUID, Collection<EntityItem>> entry : map.asMap().entrySet()) {
                    final UUID entityUuid = entry.getKey();
                    final net.minecraft.entity.Entity entityFromUuid = causeTracker.getMinecraftWorld().getEntityFromUuid(entityUuid);
                    final Entity affectedEntity = EntityUtil.fromNative(entityFromUuid);
                    if (entityFromUuid != null) {
                        final Cause cause = Cause.source(
                                EntitySpawnCause.builder()
                                        .entity(affectedEntity)
                                        .type(InternalSpawnTypes.PLACEMENT)
                                        .build()
                        ).named(NamedCause.notifier(player))
                                .build();
                        final List<Entity> entities = entry.getValue()
                                .stream()
                                .map(EntityUtil::fromNative)
                                .collect(Collectors.toList());
                        if (!entities.isEmpty()) {
                            DropItemEvent.Custom event = SpongeEventFactory.createDropItemEventCustom(cause, entities, causeTracker.getWorld());
                            SpongeImpl.postEvent(event);
                            if (!event.isCancelled()) {
                                for (Entity droppedItem : event.getEntities()) {
                                    EntityUtil.toMixin(droppedItem).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                                    mixinWorldServer.forceSpawnEntity(droppedItem);
                                }
                            }
                        }
                    }
                }
            });
            context.getCapturedItemStackSupplier().ifPresentAndNotEmpty(drops -> {
                final List<EntityItem>
                        items =
                        drops.stream().map(drop -> drop.create(causeTracker.getMinecraftWorld())).collect(Collectors.toList());
                final Cause cause = Cause.source(
                        EntitySpawnCause.builder()
                                .entity((Entity) entity)
                                .type(InternalSpawnTypes.PLACEMENT)
                                .build()
                ).named(NamedCause.notifier(player))
                        .build();
                final List<Entity> entities = items
                        .stream()
                        .map(EntityUtil::fromNative)
                        .collect(Collectors.toList());
                if (!entities.isEmpty()) {
                    DropItemEvent.Custom event = SpongeEventFactory.createDropItemEventCustom(cause, entities, causeTracker.getWorld());
                    SpongeImpl.postEvent(event);
                    if (!event.isCancelled()) {
                        for (Entity droppedItem : event.getEntities()) {
                            EntityUtil.toMixin(droppedItem).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                            mixinWorldServer.forceSpawnEntity(droppedItem);
                        }
                    }
                }

            });
        }
        context.getCapturedBlockSupplier()
                .ifPresentAndNotEmpty(snapshots ->
                        GeneralFunctions.processBlockCaptures(snapshots, causeTracker, state, context)
                );
    };

    PacketFunction ACTION = (packet, state, player, context) -> {
        final CauseTracker causeTracker = ((IMixinWorldServer) player.worldObj).getCauseTracker();
        if (state == PacketPhase.Inventory.DROP_ITEM) {
            context.getCapturedBlockSupplier()
                    .ifPresentAndNotEmpty(blocks ->
                            GeneralFunctions.processBlockCaptures(blocks, causeTracker, state, context)
                    );
            context.getCapturedItemsSupplier()
                    .ifPresentAndNotEmpty(items -> {
                        final Cause cause = Cause.source(EntitySpawnCause.builder()
                                .entity(EntityUtil.fromNative(player))
                                .type(InternalSpawnTypes.DROPPED_ITEM)
                                .build())
                                .build();
                        final ArrayList<Entity> entities = new ArrayList<>();
                        for (EntityItem item : items) {
                            entities.add(EntityUtil.fromNative(item));
                        }
                        final DropItemEvent.Dispense
                                dropItemEvent =
                                SpongeEventFactory.createDropItemEventDispense(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(dropItemEvent);
                        if (!dropItemEvent.isCancelled()) {
                            processSpawnedEntities(player, causeTracker, dropItemEvent);

                        }
                    });
            context.getCapturedEntityDropSupplier()
                    .ifPresentAndNotEmpty(itemMapping -> {


                    });
        } else if (state == PacketPhase.Inventory.DROP_INVENTORY) {

            context.getCapturedBlockSupplier()
                    .ifPresentAndNotEmpty(blocks -> GeneralFunctions.processBlockCaptures(blocks, causeTracker, state, context));

            context.getCapturedItemsSupplier()
                    .ifPresentAndNotEmpty(items -> {
                        final Cause cause = Cause.source(EntitySpawnCause.builder()
                                .entity(EntityUtil.fromNative(player))
                                .type(InternalSpawnTypes.DROPPED_ITEM)
                                .build())
                                .build();
                        final ArrayList<Entity> entities = new ArrayList<>();
                        for (EntityItem item : items) {
                            entities.add(EntityUtil.fromNative(item));
                        }
                        final DropItemEvent.Dispense
                                dropItemEvent =
                                SpongeEventFactory.createDropItemEventDispense(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(dropItemEvent);
                        if (!dropItemEvent.isCancelled()) {
                            processSpawnedEntities(player, causeTracker, dropItemEvent);

                        }
                    });

        } else if (state == PacketPhase.General.INTERACTION) {
            context.getCapturedBlockSupplier()
                    .ifPresentAndNotEmpty(blocks ->
                            GeneralFunctions.processBlockCaptures(blocks, causeTracker, state, context)
                    );

            context.getCapturedItemsSupplier()
                    .ifPresentAndNotEmpty(items -> {
                        if (items.isEmpty()) {
                            return;
                        }
                        final Cause cause = Cause.source(EntitySpawnCause.builder()
                                .entity(EntityUtil.fromNative(player))
                                .type(InternalSpawnTypes.DROPPED_ITEM)
                                .build())
                                .build();
                        final ArrayList<Entity> entities = new ArrayList<>();
                        for (EntityItem item : items) {
                            entities.add(EntityUtil.fromNative(item));
                        }
                        final DropItemEvent.Dispense
                                dispense =
                                SpongeEventFactory.createDropItemEventDispense(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(dispense);
                        if (!dispense.isCancelled()) {
                            processSpawnedEntities(player, causeTracker, dispense);
                        }
                    });
            context.getCapturedEntityDropSupplier()
                    .ifPresentAndNotEmpty(map -> {
                        if (map.isEmpty()) {
                            return;
                        }
                        final PrettyPrinter printer = new PrettyPrinter(80);
                        printer.add("Processing Interaction").centre().hr();
                        printer.add("The item stacks captured are: ");
                        for (Map.Entry<UUID, Collection<ItemDropData>> entry : map.asMap().entrySet()) {
                            printer.add("  - Entity with UUID: %s", entry.getKey());
                            for (ItemDropData stack : entry.getValue()) {
                                printer.add("    - %s", stack);
                            }
                        }
                        printer.trace(System.err);
                    });
            context.getCapturedEntitySupplier().ifPresentAndNotEmpty(entities -> {
                final Cause cause = Cause.source(EntitySpawnCause.builder()
                        .entity(EntityUtil.fromNative(player))
                        .type(InternalSpawnTypes.PLACEMENT)
                        .build())
                        .build();
                final DropItemEvent.Dispense
                        dispense =
                        SpongeEventFactory.createDropItemEventDispense(cause, entities, causeTracker.getWorld());
                SpongeImpl.postEvent(dispense);
                if (!dispense.isCancelled()) {
                    processSpawnedEntities(player, causeTracker, dispense);

                }
            });

            context.getBlockItemDropSupplier().ifPresentAndNotEmpty(map -> {
                final List<BlockSnapshot> capturedBlocks = context.getCapturedBlocks();
                final Cause cause = Cause.source(EntitySpawnCause.builder()
                        .entity(EntityUtil.fromNative(player))
                        .type(InternalSpawnTypes.DROPPED_ITEM)
                        .build())
                        .build();
                for (BlockSnapshot blockChange : capturedBlocks) {
                    final Location<World> location = blockChange.getLocation().get();
                    final Vector3d position = location.getPosition();
                    final BlockPos blockPos = VecHelper.toBlockPos(position);
                    final Collection<EntityItem> entityItems = map.get(blockPos);
                    if (!entityItems.isEmpty()) {
                        final List<Entity> items = entityItems.stream().map(EntityUtil::fromNative).collect(Collectors.toList());
                        final DropItemEvent.Destruct event = SpongeEventFactory.createDropItemEventDestruct(cause, items, causeTracker.getWorld());
                        SpongeImpl.postEvent(event);
                        if (!event.isCancelled()) {
                            processSpawnedEntities(player, causeTracker, event);
                        }
                    }
                }
            });

        }
    };
    static void processSpawnedEntities(EntityPlayerMP player, CauseTracker causeTracker, SpawnEntityEvent event) {
        for (Entity entity : event.getEntities()) {
            EntityUtil.toMixin(entity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
            causeTracker.getMixinWorld().forceSpawnEntity(entity);
        }
    }

    PacketFunction CREATIVE = HANDLED_EXTERNALLY;

    PacketFunction INVENTORY = (packet, state, player, context) -> {
        ((IMixinContainer) player.openContainer).setCaptureInventory(false);
        final CPacketClickWindow packetIn = context.firstNamed(InternalNamedCauses.Packet.CAPTURED_PACKET, CPacketClickWindow.class)
                .orElseThrow(PhaseUtil.throwWithContext("Expected to be capturing the packet used, but no packet was captured!", context));
        final ItemStackSnapshot lastCursor = context.firstNamed(InternalNamedCauses.Packet.CURSOR, ItemStackSnapshot.class)
                .orElseThrow(PhaseUtil.throwWithContext("Expected to be capturing the cursor item in use, but found none.", context));
        final ItemStackSnapshot newCursor = ItemStackUtil.snapshotOf(player.inventory.getItemStack());
        final Transaction<ItemStackSnapshot> transaction = new Transaction<>(lastCursor, newCursor);

        final Container openContainer = player.openContainer;
        final List<SlotTransaction> slotTransactions = ContainerUtil.toMixin(openContainer).getCapturedTransactions();
        PacketPhaseUtil.validateCapturedTransactions(packetIn.getSlotId(), openContainer, slotTransactions);
        final int usedButton = packetIn.getUsedButton();
        final List<Entity> capturedItems = new ArrayList<>();
        for (EntityItem entityItem : context.getCapturedItems()) {
            capturedItems.add(EntityUtil.fromNative(entityItem));
        }
        final Cause cause = Cause.of(NamedCause.source(player), NamedCause.of("Container", openContainer));
        final InteractInventoryEvent inventoryEvent;
        if (state instanceof PacketPhase.Inventory) {
            inventoryEvent =
                    ((PacketPhase.Inventory) state)
                            .createInventoryEvent(player, ContainerUtil.fromNative(openContainer), transaction, slotTransactions, capturedItems,
                                    cause, usedButton);
        } else {
            inventoryEvent = null;
        }

        if (inventoryEvent != null) {
            SpongeImpl.postEvent(inventoryEvent);
            if (inventoryEvent.isCancelled()) {
                if (inventoryEvent instanceof ClickInventoryEvent.Drop) {
                    capturedItems.clear();
                }

                if (inventoryEvent instanceof ClickInventoryEvent) {
                    // Restore cursor
                    PacketPhaseUtil.handleCustomCursor(player, inventoryEvent.getCursorTransaction().getOriginal());
                }
                if (inventoryEvent instanceof ClickInventoryEvent.Double) {
                    ((ClickInventoryEvent.Double) inventoryEvent).getTransactions().clear();
                    return;
                }

                if (inventoryEvent instanceof ChangeInventoryEvent) {
                    // Restore target slots
                    PacketPhaseUtil.handleSlotRestore(player, ((ChangeInventoryEvent) inventoryEvent).getTransactions());
                }
            } else {
                if (inventoryEvent instanceof ChangeInventoryEvent) {
                    PacketPhaseUtil.handleCustomSlot(player, ((ChangeInventoryEvent) inventoryEvent).getTransactions());
                }

                // Custom cursor
                if (inventoryEvent instanceof ClickInventoryEvent) {
                    final ClickInventoryEvent clickInventory = (ClickInventoryEvent) inventoryEvent;
                    if (clickInventory.getCursorTransaction().getCustom().isPresent()) {
                        PacketPhaseUtil.handleCustomCursor(player, clickInventory.getCursorTransaction().getFinal());
                    }
                }
                if (inventoryEvent instanceof SpawnEntityEvent) {
                    processSpawnedEntities(player, ((IMixinWorldServer) player.getServerWorld()).getCauseTracker(), (SpawnEntityEvent) inventoryEvent);
                }
            }
            slotTransactions.clear();
        }
    };
    PacketFunction USE_ITEM = ((packet, state, player, context) -> {
        final IMixinWorldServer mixinWorld = (IMixinWorldServer) player.worldObj;
        final World spongeWorld = (World) mixinWorld;

        final ItemStack itemStack = context.firstNamed(InternalNamedCauses.Packet.ITEM_USED, ItemStack.class).orElse(null);
        final ItemStackSnapshot snapshot = ItemStackUtil.snapshotOf(itemStack);
        context.getCapturedEntitySupplier()
                .ifPresentAndNotEmpty(entities -> {
                    final Cause cause = Cause.source(EntitySpawnCause.builder()
                            .entity(EntityUtil.fromNative(player))
                            .type(InternalSpawnTypes.SPAWN_EGG)
                            .build())
                            .named(NamedCause.of(InternalNamedCauses.Packet.ITEM_USED, snapshot))
                            .build();
                    final SpawnEntityEvent spawnEntityEvent = SpongeEventFactory.createSpawnEntityEvent(cause, entities, spongeWorld);
                    SpongeImpl.postEvent(spawnEntityEvent);
                    if (!spawnEntityEvent.isCancelled()) {
                        processSpawnedEntities(player, mixinWorld.getCauseTracker(), spawnEntityEvent);
                    }
                });
        context.getCapturedBlockSupplier()
                .ifPresentAndNotEmpty(
                        originalBlocks -> GeneralFunctions.processBlockCaptures(originalBlocks, mixinWorld.getCauseTracker(), state, context));

    });
    PacketFunction PLACE_BLOCK = (packet, state, player, context) -> {
        if (state == PacketPhase.General.INVALID) { // This basically is an out of world place, and nothing should occur here.
            return;
        }
        final IMixinWorldServer mixinWorld = (IMixinWorldServer) player.worldObj;
        final World spongeWorld = (World) mixinWorld;
        // Note - CPacketPlayerTryUseItem is swapped with CPacketPlayerBlockPlacement
        final ItemStack itemStack = context.firstNamed(InternalNamedCauses.Packet.ITEM_USED, ItemStack.class)
                .orElseThrow(PhaseUtil.throwWithContext("Expected the used item stack to place a block, but got nothing!", context));
        final ItemStackSnapshot snapshot = itemStack.createSnapshot();
        context.getCapturedEntitySupplier()
                .ifPresentAndNotEmpty(entities -> {
                    final Cause cause = Cause.source(EntitySpawnCause.builder()
                            .entity(EntityUtil.fromNative(player))
                            .type(InternalSpawnTypes.SPAWN_EGG)
                            .build())
                            .named(NamedCause.of(InternalNamedCauses.Packet.ITEM_USED, snapshot))
                            .build();
                    final SpawnEntityEvent spawnEntityEvent = SpongeEventFactory.createSpawnEntityEvent(cause, entities, spongeWorld);
                    SpongeImpl.postEvent(spawnEntityEvent);
                    if (!spawnEntityEvent.isCancelled()) {
                        processSpawnedEntities(player, mixinWorld.getCauseTracker(), spawnEntityEvent);

                    }
                });
        context.getCapturedBlockSupplier()
                .ifPresentAndNotEmpty(
                        originalBlocks -> GeneralFunctions.processBlockCaptures(originalBlocks, mixinWorld.getCauseTracker(), state, context));
    };
    PacketFunction HELD_ITEM_CHANGE = ((packet, state, player, context) -> {
        final CPacketHeldItemChange itemChange = (CPacketHeldItemChange) packet;
        final int previousSlot = context.firstNamed(InternalNamedCauses.Packet.PREVIOUS_HIGHLIGHTED_SLOT, Integer.class)
                .orElseThrow(PhaseUtil.throwWithContext("Expected a previous highlighted slot, got nothing.", context));
        final Container inventoryContainer = player.inventoryContainer;
        final InventoryPlayer inventory = player.inventory;
        final Slot sourceSlot = inventoryContainer.getSlot(previousSlot + inventory.mainInventory.length);
        final Slot targetSlot = inventoryContainer.getSlot(itemChange.getSlotId() + inventory.mainInventory.length);
        if (sourceSlot == null || targetSlot == null) {
            return; // should never happen but just in case it does
        }

        ItemStackSnapshot sourceSnapshot = ItemStackUtil.snapshotOf(sourceSlot.getStack());
        ItemStackSnapshot targetSnapshot = ItemStackUtil.snapshotOf(targetSlot.getStack());
        SlotTransaction sourceTransaction = new SlotTransaction(new SlotAdapter(sourceSlot), sourceSnapshot, sourceSnapshot);
        SlotTransaction targetTransaction = new SlotTransaction(new SlotAdapter(targetSlot), targetSnapshot, targetSnapshot);
        ImmutableList<SlotTransaction>
                transactions =
                new ImmutableList.Builder<SlotTransaction>().add(sourceTransaction).add(targetTransaction).build();
        final ChangeInventoryEvent.Held changeInventoryEventHeld = SpongeEventFactory
                .createChangeInventoryEventHeld(Cause.of(NamedCause.source(player)), (Inventory) inventoryContainer, transactions);
        SpongeImpl.postEvent(changeInventoryEventHeld);
        if (changeInventoryEventHeld.isCancelled()) {
            player.connection.sendPacket(new SPacketHeldItemChange(previousSlot));
        } else {
            PacketPhaseUtil.handleCustomSlot(player, changeInventoryEventHeld.getTransactions());
            inventory.currentItem = itemChange.getSlotId();
            player.markPlayerActive();
        }
    });
    PacketFunction CLOSE_WINDOW = ((packet, state, player, context) -> {
        final Container container = context.firstNamed(InternalNamedCauses.Packet.OPEN_CONTAINER, Container.class)
                .orElseThrow(PhaseUtil.throwWithContext("Expected the open container object, but had nothing!", context));
        ItemStackSnapshot lastCursor = context.firstNamed(InternalNamedCauses.Packet.CURSOR, ItemStackSnapshot.class)
                .orElseThrow(PhaseUtil.throwWithContext("Expected a cursor item stack, but had nothing!", context));
        ItemStackSnapshot newCursor = ItemStackUtil.snapshotOf(player.inventory.getItemStack());
        Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(lastCursor, newCursor);
        final Cause cause = Cause.source(player).build();
        final InteractInventoryEvent.Close
                event =
                SpongeEventFactory.createInteractInventoryEventClose(cause, cursorTransaction, ContainerUtil.fromNative(container));
        SpongeImpl.postEvent(event);
        if (event.isCancelled()) {
            if (container.getSlot(0) != null) {
                player.openContainer = container;
                final Slot slot = container.getSlot(0);
                final String guiId;
                final IInventory slotInventory = slot.inventory;
                if (slotInventory instanceof IInteractionObject) {
                    guiId = ((IInteractionObject) slotInventory).getGuiID();
                } else {
                    guiId = "unknown";
                }
                slotInventory.openInventory(player);
                player.connection.sendPacket(new SPacketOpenWindow(container.windowId, guiId, slotInventory
                        .getDisplayName(), slotInventory.getSizeInventory()));
                // resync data to client
                player.sendContainerToPlayer(container);
            }
        } else {
            // Custom cursor
            if (event.getCursorTransaction().getCustom().isPresent()) {
                PacketPhaseUtil.handleCustomCursor(player, event.getCursorTransaction().getFinal());
            }
            // Non-merged items
            context.getCapturedItemsSupplier().ifPresentAndNotEmpty(items -> {
                final Cause spawnCause = Cause.source(
                        EntitySpawnCause.builder()
                                .entity((Entity) player)
                                .type(InternalSpawnTypes.PLACEMENT)
                                .build()
                ).named(NamedCause.notifier(player))
                        .build();
                final List<Entity> entities = items
                        .stream()
                        .map(EntityUtil::fromNative)
                        .collect(Collectors.toList());
                if (!entities.isEmpty()) {
                    DropItemEvent.Custom drop = SpongeEventFactory.createDropItemEventCustom(spawnCause, entities, (World) player.getServerWorld());
                    SpongeImpl.postEvent(drop);
                    if (!drop.isCancelled()) {
                        for (Entity droppedItem : drop.getEntities()) {
                            EntityUtil.toMixin(droppedItem).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                            ((IMixinWorldServer) player.getServerWorld()).forceSpawnEntity(droppedItem);
                        }
                    }
                }
            });
            // Pre-merged items
            context.getCapturedItemStackSupplier().ifPresentAndNotEmpty(stacks -> {
                final List<EntityItem> items = stacks.stream()
                        .map(drop -> drop.create(player.getServerWorld()))
                        .collect(Collectors.toList());
                final Cause spawnCause = Cause.source(
                        EntitySpawnCause.builder()
                                .entity((Entity) player)
                                .type(InternalSpawnTypes.PLACEMENT)
                                .build()
                ).named(NamedCause.notifier(player))
                        .build();
                final List<Entity> entities = items
                        .stream()
                        .map(EntityUtil::fromNative)
                        .collect(Collectors.toList());
                if (!entities.isEmpty()) {
                    DropItemEvent.Custom drop = SpongeEventFactory.createDropItemEventCustom(spawnCause, entities, (World) player.getServerWorld());
                    SpongeImpl.postEvent(drop);
                    if (!drop.isCancelled()) {
                        for (Entity droppedItem : drop.getEntities()) {
                            EntityUtil.toMixin(droppedItem).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                            ((IMixinWorldServer) player.getServerWorld()).forceSpawnEntity(droppedItem);
                        }
                    }
                }

            });
        }
    });
    PacketFunction ENCHANTMENT = ((packet, state, player, context) -> {
    });
    PacketFunction CLIENT_SETTINGS = ((packet, state, player, context) -> {
        final CPacketClientSettings settings = (CPacketClientSettings) packet;
        PlayerChangeClientSettingsEvent event = SpongeEventFactory.createPlayerChangeClientSettingsEvent(Cause.of(NamedCause.source(player)),
                (ChatVisibility) (Object) settings.getChatVisibility(), SkinUtil.fromFlags(settings.getModelPartFlags()),
                LanguageUtil.LOCALE_CACHE.getUnchecked(settings.getLang()), (Player) player, settings.isColorsEnabled(), settings.view);
        SpongeImpl.postEvent(event);
    });
    PacketFunction CLIENT_STATUS = ((packet, state, player, context) -> {
        if (state == PacketPhase.Inventory.OPEN_INVENTORY) {
            final ItemStackSnapshot lastCursor = context.firstNamed(InternalNamedCauses.Packet.CURSOR, ItemStackSnapshot.class)
                    .orElseThrow(PhaseUtil.throwWithContext("Expected a cursor item stack, but had nothing!", context));
            final ItemStackSnapshot newCursor = ItemStackUtil.snapshotOf(player.inventory.getItemStack());
            final Transaction<ItemStackSnapshot> cursorTransaction = new Transaction<>(lastCursor, newCursor);
            final InteractInventoryEvent.Open
                    event =
                    SpongeEventFactory.createInteractInventoryEventOpen(Cause.source(player).build(), cursorTransaction,
                            ContainerUtil.fromNative(player.openContainer));
            SpongeImpl.postEvent(event);
            if (event.isCancelled()) {
                player.closeScreen();
            } else {
                // Custom cursor
                if (event.getCursorTransaction().getCustom().isPresent()) {
                    PacketPhaseUtil.handleCustomCursor(player, event.getCursorTransaction().getFinal());
                }
            }
        }
    });
    PacketFunction RESOURCE_PACKET = ((packet, state, player, context) -> {
        final NetHandlerPlayServer connection = player.connection;
        final IMixinNetHandlerPlayServer mixinHandler = (IMixinNetHandlerPlayServer) connection;
        final CPacketResourcePackStatus resource = (CPacketResourcePackStatus) packet;
        final String hash = resource.hash;
        final ResourcePackStatusEvent.ResourcePackStatus status;
        final ResourcePack pack = mixinHandler.getSentResourcePacks().get(hash);
        switch (resource.action) {
            case ACCEPTED:
                status = ResourcePackStatusEvent.ResourcePackStatus.ACCEPTED;
                break;
            case DECLINED:
                status = ResourcePackStatusEvent.ResourcePackStatus.DECLINED;
                break;
            case SUCCESSFULLY_LOADED:
                status = ResourcePackStatusEvent.ResourcePackStatus.SUCCESSFULLY_LOADED;
                break;
            case FAILED_DOWNLOAD:
                status = ResourcePackStatusEvent.ResourcePackStatus.FAILED;
                break;
            default:
                throw new AssertionError();
        }
        if (status.wasSuccessful().isPresent()) {
            mixinHandler.getSentResourcePacks().remove(hash);
        }
        final Cause cause = Cause.of(NamedCause.source(SpongeImpl.getGame()));
        final ResourcePackStatusEvent event = SpongeEventFactory.createResourcePackStatusEvent(cause, pack, (Player) player, status);
        SpongeImpl.postEvent(event);
    });
    PacketFunction MOVEMENT = (packet, state, player, context) -> {
        final IMixinWorldServer mixinWorldServer = (IMixinWorldServer) player.worldObj;
        context.getCapturedBlockSupplier().ifPresentAndNotEmpty(blocks -> GeneralFunctions.processBlockCaptures(blocks, mixinWorldServer.getCauseTracker(), state, context));
    };

    PacketFunction UNKONWN_PACKET = (packet, state, player, context) -> {
        final IMixinWorldServer mixinWorldServer = (IMixinWorldServer) player.getServerWorld();
        final CauseTracker causeTracker = mixinWorldServer.getCauseTracker();
        context.getCapturedBlockSupplier().ifPresentAndNotEmpty(blocks -> GeneralFunctions.processBlockCaptures(blocks, causeTracker, state, context));
        context.getCapturedEntitySupplier().ifPresentAndNotEmpty(entities -> {
            final Cause cause = Cause.source(EntitySpawnCause.builder()
                    .entity((Player) player)
                    .type(InternalSpawnTypes.PLACEMENT)
                    .build()
            ).build();
            SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(cause, entities, (World) mixinWorldServer);
            SpongeImpl.postEvent(event);
            if (!event.isCancelled()) {
                for (Entity spawnedEntity : event.getEntities()) {
                    EntityUtil.toMixin(spawnedEntity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                    mixinWorldServer.forceSpawnEntity(spawnedEntity);
                }
            }
        });
        context.getCapturedItemsSupplier().ifPresentAndNotEmpty(entities -> {
            final List<Entity> items = entities.stream().map(EntityUtil::fromNative).collect(Collectors.toList());
            final Cause cause = Cause.source(EntitySpawnCause.builder()
                    .entity((Player) player)
                    .type(InternalSpawnTypes.PLACEMENT)
                    .build()
            ).build();
            SpawnEntityEvent event = SpongeEventFactory.createSpawnEntityEvent(cause, items, (World) mixinWorldServer);
            SpongeImpl.postEvent(event);
            if (!event.isCancelled()) {
                for (Entity spawnedEntity : event.getEntities()) {
                    EntityUtil.toMixin(spawnedEntity).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                    mixinWorldServer.forceSpawnEntity(spawnedEntity);
                }
            }
        });
        context.getCapturedEntityDropSupplier().ifPresentAndNotEmpty(map -> {
            final PrettyPrinter printer = new PrettyPrinter(80);
            printer.add("Processing Interact At Entity").centre().hr();
            printer.add("The item stacks captured are: ");

            for (Map.Entry<UUID, Collection<ItemDropData>> entry : map.asMap().entrySet()) {
                printer.add("  - Entity with UUID: %s", entry.getKey());
                for (ItemDropData stack : entry.getValue()) {
                    printer.add("    - %s", stack);
                }
            }
            printer.trace(System.err);
        });
        context.getCapturedEntityItemDropSupplier().ifPresentAndNotEmpty(map -> {
            for (Map.Entry<UUID, Collection<EntityItem>> entry : map.asMap().entrySet()) {
                final UUID entityUuid = entry.getKey();
                final net.minecraft.entity.Entity entityFromUuid = causeTracker.getMinecraftWorld().getEntityFromUuid(entityUuid);
                final Entity affectedEntity = EntityUtil.fromNative(entityFromUuid);
                if (entityFromUuid != null) {
                    final Cause cause = Cause.source(
                            EntitySpawnCause.builder()
                                    .entity(affectedEntity)
                                    .type(InternalSpawnTypes.CUSTOM)
                                    .build()
                    ).named(NamedCause.notifier(player))
                            .build();
                    final List<Entity> entities = entry.getValue()
                            .stream()
                            .map(EntityUtil::fromNative)
                            .collect(Collectors.toList());
                    if (!entities.isEmpty()) {
                        DropItemEvent.Custom event = SpongeEventFactory.createDropItemEventCustom(cause, entities, causeTracker.getWorld());
                        SpongeImpl.postEvent(event);
                        if (!event.isCancelled()) {
                            for (Entity droppedItem : event.getEntities()) {
                                EntityUtil.toMixin(droppedItem).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                                mixinWorldServer.forceSpawnEntity(droppedItem);
                            }
                        }
                    }
                }
            }
        });
        context.getCapturedItemStackSupplier().ifPresentAndNotEmpty(drops -> {
            final List<EntityItem>
                    items =
                    drops.stream().map(drop -> drop.create(causeTracker.getMinecraftWorld())).collect(Collectors.toList());
            final Cause cause = Cause.source(
                    EntitySpawnCause.builder()
                            .entity((Entity) player)
                            .type(InternalSpawnTypes.CUSTOM)
                            .build()
            ).named(NamedCause.notifier(player))
                    .build();
            final List<Entity> entities = items
                    .stream()
                    .map(EntityUtil::fromNative)
                    .collect(Collectors.toList());
            if (!entities.isEmpty()) {
                DropItemEvent.Custom event = SpongeEventFactory.createDropItemEventCustom(cause, entities, causeTracker.getWorld());
                SpongeImpl.postEvent(event);
                if (!event.isCancelled()) {
                    for (Entity droppedItem : event.getEntities()) {
                        EntityUtil.toMixin(droppedItem).trackEntityUniqueId(NbtDataUtil.SPONGE_ENTITY_CREATOR, player.getUniqueID());
                        mixinWorldServer.forceSpawnEntity(droppedItem);
                    }
                }
            }

        });

    };

    void unwind(Packet<?> packet, PacketPhase.IPacketState state, EntityPlayerMP player, PhaseContext context);

}

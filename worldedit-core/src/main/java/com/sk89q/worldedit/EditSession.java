/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit;

import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.blocks.BlockType;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.ChangeSetExtent;
import com.sk89q.worldedit.extent.Extent;
import com.sk89q.worldedit.extent.MaskingExtent;
import com.sk89q.worldedit.extent.NullExtent;
import com.sk89q.worldedit.extent.cache.LastAccessExtentCache;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.extent.inventory.BlockBagExtent;
import com.sk89q.worldedit.extent.reorder.MultiStageReorder;
import com.sk89q.worldedit.extent.validation.BlockChangeLimiter;
import com.sk89q.worldedit.extent.validation.DataValidatorExtent;
import com.sk89q.worldedit.extent.world.BlockQuirkExtent;
import com.sk89q.worldedit.extent.world.ChunkLoadingExtent;
import com.sk89q.worldedit.extent.world.FastModeExtent;
import com.sk89q.worldedit.extent.world.SurvivalModeExtent;
import com.sk89q.worldedit.function.GroundFunction;
import com.sk89q.worldedit.function.RegionMaskingFilter;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.block.Counter;
import com.sk89q.worldedit.function.block.Naturalizer;
import com.sk89q.worldedit.function.generator.GardenPatchGenerator;
import com.sk89q.worldedit.function.mask.*;
import com.sk89q.worldedit.function.operation.ChangeSetExecutor;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Patterns;
import com.sk89q.worldedit.function.util.RegionOffset;
import com.sk89q.worldedit.function.visitor.LayerVisitor;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.changeset.BlockOptimizedHistory;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.internal.expression.Expression;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.internal.expression.runtime.RValue;
import com.sk89q.worldedit.math.interpolation.Interpolation;
import com.sk89q.worldedit.math.interpolation.KochanekBartelsInterpolation;
import com.sk89q.worldedit.math.interpolation.Node;
import com.sk89q.worldedit.math.noise.RandomNoise;
import com.sk89q.worldedit.patterns.Pattern;
import com.sk89q.worldedit.patterns.SingleBlockPattern;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.FlatRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.regions.Regions;
import com.sk89q.worldedit.regions.shape.ArbitraryShape;
import com.sk89q.worldedit.regions.shape.RegionShape;
import com.sk89q.worldedit.regions.shape.WorldEditExpressionEnvironment;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.TreeGenerator;
import com.sk89q.worldedit.util.collection.DoubleArrayList;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.world.NullWorld;
import com.sk89q.worldedit.world.World;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.regions.Regions.*;

//import com.sk89q.worldedit.regions.shape.ArbitraryBiomeShape;

/**
 * An {@link Extent} that handles history, {@link BlockBag}s, change limits,
 * block re-ordering, and much more. Most operations in WorldEdit use this class.
 * <p>
 * <p>Most of the actual functionality is implemented with a number of other
 * {@link Extent}s that are chained together. For example, history is logged
 * using the {@link ChangeSetExtent}.</p>
 */
@SuppressWarnings({"FieldCanBeLocal", "deprecation"})
public class EditSession implements Extent, FlyEditSesion {

    private static final Logger log = Logger.getLogger(EditSession.class.getCanonicalName());

    /**
     * Used by {@link #setBlock(Vector, BaseBlock, Stage)} to
     * determine which {@link Extent}s should be bypassed.
     */
    public enum Stage {
        BEFORE_HISTORY,
        BEFORE_REORDER,
        BEFORE_CHANGE
    }

    EditSesionFlyweight service;

    @SuppressWarnings("ProtectedField")
    protected final World world;
    private final ChangeSet changeSet = new BlockOptimizedHistory();

    private
    @Nullable
    FastModeExtent fastModeExtent;
    private final SurvivalModeExtent survivalExtent;
    private
    @Nullable
    ChunkLoadingExtent chunkLoadingExtent;
    private
    @Nullable
    LastAccessExtentCache cacheExtent;
    private
    @Nullable
    BlockQuirkExtent quirkExtent;
    private
    @Nullable
    DataValidatorExtent validator;
    private final BlockBagExtent blockBagExtent;
    private final MultiStageReorder reorderExtent;
    private
    @Nullable
    ChangeSetExtent changeSetExtent;
    private final MaskingExtent maskingExtent;
    private final BlockChangeLimiter changeLimiter;

    private final Extent bypassReorderHistory;
    private final Extent bypassHistory;
    private final Extent bypassNone;

    @SuppressWarnings("deprecation")
    private Mask oldMask;
    private FlyEditSesion flyEditSesion;

    /**
     * Create a new instance.
     *
     * @param world     a world
     * @param maxBlocks the maximum number of blocks that can be changed, or -1 to use no limit
     * @deprecated use {@link WorldEdit#getEditSessionFactory()} to create {@link EditSession}s
     */
    @SuppressWarnings("deprecation")
    @Deprecated
    public EditSession(LocalWorld world, int maxBlocks) {
        this(world, maxBlocks, null);
    }

    /**
     * Create a new instance.
     *
     * @param world     a world
     * @param maxBlocks the maximum number of blocks that can be changed, or -1 to use no limit
     * @param blockBag  the block bag to set, or null to use none
     * @deprecated use {@link WorldEdit#getEditSessionFactory()} to create {@link EditSession}s
     */
    @Deprecated
    public EditSession(LocalWorld world, int maxBlocks, @Nullable BlockBag blockBag) {
        this(WorldEdit.getInstance().getEventBus(), world, maxBlocks, blockBag, new EditSessionEvent(world, null, maxBlocks, null));
    }

    /**
     * Construct the object with a maximum number of blocks and a block bag.
     *
     * @param eventBus  the event bus
     * @param world     the world
     * @param maxBlocks the maximum number of blocks that can be changed, or -1 to use no limit
     * @param blockBag  an optional {@link BlockBag} to use, otherwise null
     * @param event     the event to call with the extent
     */
    EditSession(EventBus eventBus, World world, int maxBlocks, @Nullable BlockBag blockBag, EditSessionEvent event) {
        checkNotNull(eventBus);
        checkArgument(maxBlocks >= -1, "maxBlocks >= -1 required");
        checkNotNull(event);

        this.world = world;

        if (world != null) {
            Extent extent;

            // These extents are ALWAYS used
            extent = fastModeExtent = new FastModeExtent(world, false);
            extent = survivalExtent = new SurvivalModeExtent(extent, world);
            extent = quirkExtent = new BlockQuirkExtent(extent, world);
            extent = chunkLoadingExtent = new ChunkLoadingExtent(extent, world);
            extent = cacheExtent = new LastAccessExtentCache(extent);
            extent = wrapExtent(extent, eventBus, event, Stage.BEFORE_CHANGE);
            extent = validator = new DataValidatorExtent(extent, world);
            extent = blockBagExtent = new BlockBagExtent(extent, blockBag);

            // This extent can be skipped by calling rawSetBlock()
            extent = reorderExtent = new MultiStageReorder(extent, false);
            extent = wrapExtent(extent, eventBus, event, Stage.BEFORE_REORDER);

            // These extents can be skipped by calling smartSetBlock()
            extent = changeSetExtent = new ChangeSetExtent(extent, changeSet);
            extent = maskingExtent = new MaskingExtent(extent, Masks.alwaysTrue());
            extent = changeLimiter = new BlockChangeLimiter(extent, maxBlocks);
            extent = wrapExtent(extent, eventBus, event, Stage.BEFORE_HISTORY);

            this.bypassReorderHistory = blockBagExtent;
            this.bypassHistory = reorderExtent;
            this.bypassNone = extent;
        } else {
            Extent extent = new NullExtent();
            extent = survivalExtent = new SurvivalModeExtent(extent, NullWorld.getInstance());
            extent = blockBagExtent = new BlockBagExtent(extent, blockBag);
            extent = reorderExtent = new MultiStageReorder(extent, false);
            extent = maskingExtent = new MaskingExtent(extent, Masks.alwaysTrue());
            extent = changeLimiter = new BlockChangeLimiter(extent, maxBlocks);
            this.bypassReorderHistory = extent;
            this.bypassHistory = extent;
            this.bypassNone = extent;
        }
    }

    private Extent wrapExtent(Extent extent, EventBus eventBus, EditSessionEvent event, Stage stage) {
        event = event.clone(stage);
        event.setExtent(extent);
        eventBus.post(event);
        return event.getExtent();
    }

    /**
     * Get the world.
     *
     * @return the world
     */
    public World getWorld() {
        return world;
    }

    /**
     * Get the underlying {@link ChangeSet}.
     *
     * @return the change set
     */
    public ChangeSet getChangeSet() {
        return changeSet;
    }

    /**
     * Get the maximum number of blocks that can be changed. -1 will be returned
     * if it the limit disabled.
     *
     * @return the limit (&gt;= 0) or -1 for no limit
     */
    public int getBlockChangeLimit() {
        return changeLimiter.getLimit();
    }

    /**
     * Set the maximum number of blocks that can be changed.
     *
     * @param limit the limit (&gt;= 0) or -1 for no limit
     */
    public void setBlockChangeLimit(int limit) {
        changeLimiter.setLimit(limit);
    }

    /**
     * Returns queue status.
     *
     * @return whether the queue is enabled
     */
    public boolean isQueueEnabled() {
        return reorderExtent.isEnabled();
    }

    /**
     * Queue certain types of block for better reproduction of those blocks.
     */
    public void enableQueue() {
        reorderExtent.setEnabled(true);
    }

    /**
     * Disable the queue. This will flush the queue.
     */
    public void disableQueue() {
        if (isQueueEnabled()) {
            flushQueue();
        }
        reorderExtent.setEnabled(true);
    }

    /**
     * Get the mask.
     *
     * @return mask, may be null
     */
    public Mask getMask() {
        return oldMask;
    }

    /**
     * Set a mask.
     *
     * @param mask mask or null
     */
    public void setMask(Mask mask) {
        this.oldMask = mask;
        if (mask == null) {
            maskingExtent.setMask(Masks.alwaysTrue());
        } else {
            maskingExtent.setMask(mask);
        }
    }

    /**
     * Set the mask.
     *
     * @param mask the mask
     * @deprecated Use {@link #setMask(Mask)}
     */
    @Deprecated
    public void setMask(com.sk89q.worldedit.masks.Mask mask) {
        if (mask == null) {
            setMask((Mask) null);
        } else {
            setMask(Masks.wrap(mask));
        }
    }

    /**
     * Get the {@link SurvivalModeExtent}.
     *
     * @return the survival simulation extent
     */
    public SurvivalModeExtent getSurvivalExtent() {
        return survivalExtent;
    }

    /**
     * Set whether fast mode is enabled.
     * <p>
     * <p>Fast mode may skip lighting checks or adjacent block
     * notification.</p>
     *
     * @param enabled true to enable
     */
    public void setFastMode(boolean enabled) {
        if (fastModeExtent != null) {
            fastModeExtent.setEnabled(enabled);
        }
    }

    /**
     * Return fast mode status.
     * <p>
     * <p>Fast mode may skip lighting checks or adjacent block
     * notification.</p>
     *
     * @return true if enabled
     */
    public boolean hasFastMode() {
        return fastModeExtent != null && fastModeExtent.isEnabled();
    }

    /**
     * Get the {@link BlockBag} is used.
     *
     * @return a block bag or null
     */
    public BlockBag getBlockBag() {
        return blockBagExtent.getBlockBag();
    }

    /**
     * Set a {@link BlockBag} to use.
     *
     * @param blockBag the block bag to set, or null to use none
     */
    public void setBlockBag(BlockBag blockBag) {
        blockBagExtent.setBlockBag(blockBag);
    }

    /**
     * Gets the list of missing blocks and clears the list for the next
     * operation.
     *
     * @return a map of missing blocks
     */
    public Map<Integer, Integer> popMissingBlocks() {
        return blockBagExtent.popMissing();
    }

    /**
     * Get the number of blocks changed, including repeated block changes.
     * <p>
     * <p>This number may not be accurate.</p>
     *
     * @return the number of block changes
     */
    public int getBlockChangeCount() {
        return changeSet.size();
    }

    /*@Override
    public BaseBiome getBiome(Vector2D position) {
        return bypassNone.getBiome(position);
    }

    @Override
    public boolean setBiome(Vector2D position, BaseBiome biome) {
        return bypassNone.setBiome(position, biome);
    }*/

    @Override
    public BaseBlock getLazyBlock(Vector position) {
        return world.getLazyBlock(position);
    }

    @Override
    public BaseBlock getBlock(Vector position) {
        return world.getBlock(position);
    }

    /**
     * Get a block type at the given position.
     *
     * @param position the position
     * @return the block type
     * @deprecated Use {@link #getLazyBlock(Vector)} or {@link #getBlock(Vector)}
     */
    @Deprecated
    public int getBlockType(Vector position) {
        return world.getBlockType(position);
    }

    /**
     * Get a block data at the given position.
     *
     * @param position the position
     * @return the block data
     * @deprecated Use {@link #getLazyBlock(Vector)} or {@link #getBlock(Vector)}
     */
    @Deprecated
    public int getBlockData(Vector position) {
        return world.getBlockData(position);
    }

    /**
     * Gets the block type at a position.
     *
     * @param position the position
     * @return a block
     * @deprecated Use {@link #getBlock(Vector)}
     */
    @Deprecated
    public BaseBlock rawGetBlock(Vector position) {
        return getBlock(position);
    }

    /**
     * Returns the highest solid 'terrain' block which can occur naturally.
     *
     * @param x    the X coordinate
     * @param z    the Z cooridnate
     * @param minY minimal height
     * @param maxY maximal height
     * @return height of highest block found or 'minY'
     */
    public int getHighestTerrainBlock(int x, int z, int minY, int maxY) {
        return getHighestTerrainBlock(x, z, minY, maxY, false);
    }

    @Override
    public int getHighestTerrainBlock(int x, int z, int minY, int maxY, boolean naturalOnly) {
        return flyEditSesion.getHighestTerrainBlock( x, z,  minY,  maxY, naturalOnly);
    }

   @Override
    public boolean setBlock(Vector position, BaseBlock block, Stage stage) throws WorldEditException {
        switch (stage) {
            case BEFORE_HISTORY:
                return bypassNone.setBlock(position, block);
            case BEFORE_CHANGE:
                return bypassHistory.setBlock(position, block);
            case BEFORE_REORDER:
                return bypassReorderHistory.setBlock(position, block);
        }

        throw new RuntimeException("New enum entry added that is unhandled here");
    }

    @Override
    public boolean rawSetBlock(Vector position, BaseBlock block) {
        try {
            return setBlock(position, block, Stage.BEFORE_CHANGE);
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

   @Override
    public boolean smartSetBlock(Vector position, BaseBlock block) {
        try {
            return setBlock(position, block, Stage.BEFORE_REORDER);
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    @Override
    public boolean setBlock(Vector position, BaseBlock block) throws MaxChangedBlocksException {
        try {
            return setBlock(position, block, Stage.BEFORE_HISTORY);
        } catch (MaxChangedBlocksException e) {
            throw e;
        } catch (WorldEditException e) {
            throw new RuntimeException("Unexpected exception", e);
        }
    }

    /**
     * Sets the block at a position, subject to both history and block re-ordering.
     *
     * @param position the position
     * @param pattern  a pattern to use
     * @return Whether the block changed -- not entirely dependable
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public boolean setBlock(Vector position, Pattern pattern) throws MaxChangedBlocksException {
        return setBlock(position, pattern.next(position));
    }

    /**
     * Set blocks that are in a set of positions and return the number of times
     * that the block set calls returned true.
     *
     * @param vset    a set of positions
     * @param pattern the pattern
     * @return the number of changed blocks
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    private int setBlocks(Set<Vector> vset, Pattern pattern) throws MaxChangedBlocksException {
        int affected = 0;
        for (Vector v : vset) {
            affected += setBlock(v, pattern) ? 1 : 0;
        }
        return affected;
    }

    /**
     * Set a block (only if a previous block was not there) if {@link Math#random()}
     * returns a number less than the given probability.
     *
     * @param position    the position
     * @param block       the block
     * @param probability a probability between 0 and 1, inclusive
     * @return whether a block was changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public boolean setChanceBlockIfAir(Vector position, BaseBlock block, double probability)
            throws MaxChangedBlocksException {
        return Math.random() <= probability && setBlockIfAir(position, block);
    }

    /**
     * Set a block only if there's no block already there.
     *
     * @param position the position
     * @param block    the block to set
     * @return if block was changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     * @deprecated Use your own method
     */
    @Deprecated
    public boolean setBlockIfAir(Vector position, BaseBlock block) throws MaxChangedBlocksException {
        return getBlock(position).isAir() && setBlock(position, block);
    }

    @Override
    @Nullable
    public Entity createEntity(com.sk89q.worldedit.util.Location location, BaseEntity entity) {
        return bypassNone.createEntity(location, entity);
    }

    /**
     * Insert a contrived block change into the history.
     *
     * @param position the position
     * @param existing the previous block at that position
     * @param block    the new block
     * @deprecated Get the change set with {@link #getChangeSet()} and add the change with that
     */
    @Deprecated
    public void rememberChange(Vector position, BaseBlock existing, BaseBlock block) {
        changeSet.add(new BlockChange(position.toBlockVector(), existing, block));
    }

    /**
     * Restores all blocks to their initial state.
     *
     * @param editSession a new {@link EditSession} to perform the undo in
     */
    public void undo(EditSession editSession) {
        UndoContext context = new UndoContext();
        context.setExtent(editSession.bypassHistory);
        Operations.completeBlindly(ChangeSetExecutor.createUndo(changeSet, context));
        editSession.flushQueue();
    }

    /**
     * Sets to new state.
     *
     * @param editSession a new {@link EditSession} to perform the redo in
     */
    public void redo(EditSession editSession) {
        UndoContext context = new UndoContext();
        context.setExtent(editSession.bypassHistory);
        Operations.completeBlindly(ChangeSetExecutor.createRedo(changeSet, context));
        editSession.flushQueue();
    }

    /**
     * Get the number of changed blocks.
     *
     * @return the number of changes
     */
    public int size() {
        return getBlockChangeCount();
    }

    @Override
    public Vector getMinimumPoint() {
        return getWorld().getMinimumPoint();
    }

    @Override
    public Vector getMaximumPoint() {
        return getWorld().getMaximumPoint();
    }

    @Override
    public List<? extends Entity> getEntities(Region region) {
        return bypassNone.getEntities(region);
    }

    @Override
    public List<? extends Entity> getEntities() {
        return bypassNone.getEntities();
    }

    /**
     * Finish off the queue.
     */
    public void flushQueue() {
        Operations.completeBlindly(commit());
    }

    @Override
    public
    @Nullable
    Operation commit() {
        return bypassNone.commit();
    }

   @Override
    public int countBlock(Region region, Set<Integer> searchIDs) {
        Set<BaseBlock> passOn = new HashSet<BaseBlock>();
        for (Integer i : searchIDs) {
            passOn.add(new BaseBlock(i, -1));
        }
        return countBlocks(region, passOn);
    }

    /**
     * Count the number of blocks of a list of types in a region.
     *
     * @param region       the region
     * @param searchBlocks the list of blocks to search
     * @return the number of blocks that matched the pattern
     */
    public int countBlocks(Region region, Set<BaseBlock> searchBlocks) {
        FuzzyBlockMask mask = new FuzzyBlockMask(this, searchBlocks);
        Counter count = new Counter();
        RegionMaskingFilter filter = new RegionMaskingFilter(mask, count);
        RegionVisitor visitor = new RegionVisitor(region, filter);
        Operations.completeBlindly(visitor); // We can't throw exceptions, nor do we expect any
        return count.getCount();
    }


    @SuppressWarnings("deprecation")
    public int fillXZ(Vector origin, BaseBlock block, double radius, int depth, boolean recursive)
            throws MaxChangedBlocksException {
        return fillXZ(origin, new SingleBlockPattern(block), radius, depth, recursive);
    }

    @SuppressWarnings("deprecation")
    @Override
    public int fillXZ(Vector origin, Pattern pattern, double radius, int depth, boolean recursive) throws MaxChangedBlocksException {

      return flyEditSesion.fillXZ(origin,pattern,radius,depth,recursive);
    }

    /**
     * Remove a cuboid above the given position with a given apothem and a given height.
     *
     * @param position base position
     * @param apothem  an apothem of the cuboid (on the XZ plane), where the minimum is 1
     * @param height   the height of the cuboid, where the minimum is 1
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int removeAbove(Vector position, int apothem, int height) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");
        checkArgument(height >= 1, "height >= 1");

        Region region = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                position.add(-apothem + 1, 0, -apothem + 1),
                position.add(apothem - 1, height - 1, apothem - 1));
        Pattern pattern = new SingleBlockPattern(new BaseBlock(BlockID.AIR));
        return setBlocks(region, pattern);
    }

    /**
     * Remove a cuboid below the given position with a given apothem and a given height.
     *
     * @param position base position
     * @param apothem  an apothem of the cuboid (on the XZ plane), where the minimum is 1
     * @param height   the height of the cuboid, where the minimum is 1
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int removeBelow(Vector position, int apothem, int height) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");
        checkArgument(height >= 1, "height >= 1");

        Region region = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                position.add(-apothem + 1, 0, -apothem + 1),
                position.add(apothem - 1, -height + 1, apothem - 1));
        Pattern pattern = new SingleBlockPattern(new BaseBlock(BlockID.AIR));
        return setBlocks(region, pattern);
    }

    /**
     * Remove blocks of a certain type nearby a given position.
     *
     * @param position  center position of cuboid
     * @param blockType the block type to match
     * @param apothem   an apothem of the cuboid, where the minimum is 1
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int removeNear(Vector position, int blockType, int apothem) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");

        Mask mask = new FuzzyBlockMask(this, new BaseBlock(blockType, -1));
        Vector adjustment = new Vector(1, 1, 1).multiply(apothem - 1);
        Region region = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                position.add(adjustment.multiply(-1)),
                position.add(adjustment));
        Pattern pattern = new SingleBlockPattern(new BaseBlock(BlockID.AIR));
        return replaceBlocks(region, mask, pattern);
    }

    /**
     * Sets all the blocks inside a region to a given block type.
     *
     * @param region the region
     * @param block  the block
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int setBlocks(Region region, BaseBlock block) throws MaxChangedBlocksException {
        return setBlocks(region, new SingleBlockPattern(block));
    }

    /**
     * Sets all the blocks inside a region to a given pattern.
     *
     * @param region  the region
     * @param pattern the pattern that provides the replacement block
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int setBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        BlockReplace replace = new BlockReplace(this, Patterns.wrap(pattern));
        RegionVisitor visitor = new RegionVisitor(region, replace);
        Operations.completeLegacy(visitor);
        return visitor.getAffected();
    }

    /**
     * Replaces all the blocks matching a given filter, within a given region, to a block
     * returned by a given pattern.
     *
     * @param region      the region to replace the blocks within
     * @param filter      a list of block types to match, or null to use {@link com.sk89q.worldedit.masks.ExistingBlockMask}
     * @param replacement the replacement block
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int replaceBlocks(Region region, Set<BaseBlock> filter, BaseBlock replacement) throws MaxChangedBlocksException {
        return replaceBlocks(region, filter, new SingleBlockPattern(replacement));
    }

    /**
     * Replaces all the blocks matching a given filter, within a given region, to a block
     * returned by a given pattern.
     *
     * @param region  the region to replace the blocks within
     * @param filter  a list of block types to match, or null to use {@link com.sk89q.worldedit.masks.ExistingBlockMask}
     * @param pattern the pattern that provides the new blocks
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int replaceBlocks(Region region, Set<BaseBlock> filter, Pattern pattern) throws MaxChangedBlocksException {
        Mask mask = filter == null ? new ExistingBlockMask(this) : new FuzzyBlockMask(this, filter);
        return replaceBlocks(region, mask, pattern);
    }

    /**
     * Replaces all the blocks matching a given mask, within a given region, to a block
     * returned by a given pattern.
     *
     * @param region  the region to replace the blocks within
     * @param mask    the mask that blocks must match
     * @param pattern the pattern that provides the new blocks
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int replaceBlocks(Region region, Mask mask, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(mask);
        checkNotNull(pattern);

        BlockReplace replace = new BlockReplace(this, Patterns.wrap(pattern));
        RegionMaskingFilter filter = new RegionMaskingFilter(mask, replace);
        RegionVisitor visitor = new RegionVisitor(region, filter);
        Operations.completeLegacy(visitor);
        return visitor.getAffected();
    }

    /**
     * Sets the blocks at the center of the given region to the given pattern.
     * If the center sits between two blocks on a certain axis, then two blocks
     * will be placed to mark the center.
     *
     * @param region  the region to find the center of
     * @param pattern the replacement pattern
     * @return the number of blocks placed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int center(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        Vector center = region.getCenter();
        Region centerRegion = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                new Vector((int) center.getX(), (int) center.getY(), (int) center.getZ()),
                center.toBlockVector());
        return setBlocks(centerRegion, pattern);
    }

    /**
     * Make the faces of the given region as if it was a {@link CuboidRegion}.
     *
     * @param region the region
     * @param block  the block to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int makeCuboidFaces(Region region, BaseBlock block) throws MaxChangedBlocksException {
        return makeCuboidFaces(region, new SingleBlockPattern(block));
    }

    /**
     * Make the faces of the given region as if it was a {@link CuboidRegion}.
     *
     * @param region  the region
     * @param pattern the pattern to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int makeCuboidFaces(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        CuboidRegion cuboid = CuboidRegion.makeCuboid(region);
        Region faces = cuboid.getFaces();
        return setBlocks(faces, pattern);
    }

    /**
     * Make the faces of the given region. The method by which the faces are found
     * may be inefficient, because there may not be an efficient implementation supported
     * for that specific shape.
     *
     * @param region  the region
     * @param pattern the pattern to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int makeFaces(final Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        if (region instanceof CuboidRegion) {
            return makeCuboidFaces(region, pattern);
        } else {
            return new RegionShape(region).generate(this, pattern, true);
        }
    }


    /**
     * Make the walls (all faces but those parallel to the X-Z plane) of the given region
     * as if it was a {@link CuboidRegion}.
     *
     * @param region the region
     * @param block  the block to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int makeCuboidWalls(Region region, BaseBlock block) throws MaxChangedBlocksException {
        return makeCuboidWalls(region, new SingleBlockPattern(block));
    }

    /**
     * Make the walls (all faces but those parallel to the X-Z plane) of the given region
     * as if it was a {@link CuboidRegion}.
     *
     * @param region  the region
     * @param pattern the pattern to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int makeCuboidWalls(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        CuboidRegion cuboid = CuboidRegion.makeCuboid(region);
        Region faces = cuboid.getWalls();
        return setBlocks(faces, pattern);
    }

    /**
     * Make the walls of the given region. The method by which the walls are found
     * may be inefficient, because there may not be an efficient implementation supported
     * for that specific shape.
     *
     * @param region  the region
     * @param pattern the pattern to place
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int makeWalls(final Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        if (region instanceof CuboidRegion) {
            return makeCuboidWalls(region, pattern);
        } else {
            final int minY = region.getMinimumPoint().getBlockY();
            final int maxY = region.getMaximumPoint().getBlockY();
            final ArbitraryShape shape = new RegionShape(region) {
                @Override
                protected BaseBlock getMaterial(int x, int y, int z, BaseBlock defaultMaterial) {
                    if (y > maxY || y < minY) {
                        // Put holes into the floor and ceiling by telling ArbitraryShape that the shape goes on outside the region
                        return defaultMaterial;
                    }

                    return super.getMaterial(x, y, z, defaultMaterial);
                }
            };
            return shape.generate(this, pattern, true);
        }
    }

    /**
     * Places a layer of blocks on top of ground blocks in the given region
     * (as if it were a cuboid).
     *
     * @param region the region
     * @param block  the placed block
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int overlayCuboidBlocks(Region region, BaseBlock block) throws MaxChangedBlocksException {
        checkNotNull(block);

        return overlayCuboidBlocks(region, new SingleBlockPattern(block));
    }

    /**
     * Places a layer of blocks on top of ground blocks in the given region
     * (as if it were a cuboid).
     *
     * @param region  the region
     * @param pattern the placed block pattern
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    public int overlayCuboidBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        BlockReplace replace = new BlockReplace(this, Patterns.wrap(pattern));
        RegionOffset offset = new RegionOffset(new Vector(0, 1, 0), replace);
        GroundFunction ground = new GroundFunction(new ExistingBlockMask(this), offset);
        LayerVisitor visitor = new LayerVisitor(asFlatRegion(region), minimumBlockY(region), maximumBlockY(region), ground);
        Operations.completeLegacy(visitor);
        return ground.getAffected();
    }

    /**
     * Turns the first 3 layers into dirt/grass and the bottom layers
     * into rock, like a natural Minecraft mountain.
     *
     * @param region the region to affect
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int naturalizeCuboidBlocks(Region region) throws MaxChangedBlocksException {
        checkNotNull(region);

        Naturalizer naturalizer = new Naturalizer(this);
        FlatRegion flatRegion = Regions.asFlatRegion(region);
        LayerVisitor visitor = new LayerVisitor(flatRegion, minimumBlockY(region), maximumBlockY(region), naturalizer);
        Operations.completeLegacy(visitor);
        return naturalizer.getAffected();
    }

    @Override
    public int stackCuboidRegion(Region region, Vector dir, int count, boolean copyAir) throws MaxChangedBlocksException {

       return flyEditSesion.stackCuboidRegion(region,dir,count,copyAir);
    }

    @Override
    public int moveRegion(Region region, Vector dir, int distance, boolean copyAir, BaseBlock replacement) throws MaxChangedBlocksException {

        return flyEditSesion.moveRegion(region,dir,distance,copyAir,replacement);
    }

    public int moveCuboidRegion(Region region, Vector dir, int distance, boolean copyAir, BaseBlock replacement) throws MaxChangedBlocksException {
        return moveRegion(region, dir, distance, copyAir, replacement);
    }

  @Override
    public int drainArea(Vector origin, double radius) throws MaxChangedBlocksException {

        return flyEditSesion.drainArea(origin,radius);
    }

    @Override
    public int fixLiquid(Vector origin, double radius, int moving, int stationary) throws MaxChangedBlocksException {
        return flyEditSesion.fixLiquid(origin,radius,moving,stationary);
    }

    /**
     * Makes a cylinder.
     *
     * @param pos    Center of the cylinder
     * @param block  The block pattern to use
     * @param radius The cylinder's radius
     * @param height The cylinder's up/down extent. If negative, extend downward.
     * @param filled If false, only a shell will be generated.
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeCylinder(Vector pos, Pattern block, double radius, int height, boolean filled) throws MaxChangedBlocksException {
        return makeCylinder(pos, block, radius, radius, height, filled);
    }

   @Override
    public int makeCylinder(Vector pos, Pattern block, double radiusX, double radiusZ, int height, boolean filled) throws MaxChangedBlocksException {
       return flyEditSesion.makeCylinder(pos,block,radiusX,radiusZ,height,filled);
    }

    /**
     * Makes a sphere.
     *
     * @param pos    Center of the sphere or ellipsoid
     * @param block  The block pattern to use
     * @param radius The sphere's radius
     * @param filled If false, only a shell will be generated.
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeSphere(Vector pos, Pattern block, double radius, boolean filled) throws MaxChangedBlocksException {
        return makeSphere(pos, block, radius, radius, radius, filled);
    }

    @Override
    public int makeSphere(Vector pos, Pattern block, double radiusX, double radiusY, double radiusZ, boolean filled) throws MaxChangedBlocksException {
       return flyEditSesion.makeSphere(pos,block,radiusX,radiusY,radiusZ,filled);
    }

    @Override
    public int makePyramid(Vector position, Pattern block, int size, boolean filled) throws MaxChangedBlocksException {
     return flyEditSesion.makePyramid(position,block,size,filled);
    }

    @Override
    public int thaw(Vector position, double radius)
            throws MaxChangedBlocksException {
        int affected = 0;
        double radiusSq = radius * radius;

        int ox = position.getBlockX();
        int oy = position.getBlockY();
        int oz = position.getBlockZ();

        BaseBlock air = new BaseBlock(0);
        BaseBlock water = new BaseBlock(BlockID.STATIONARY_WATER);

        int ceilRadius = (int) Math.ceil(radius);
        for (int x = ox - ceilRadius; x <= ox + ceilRadius; ++x) {
            for (int z = oz - ceilRadius; z <= oz + ceilRadius; ++z) {
                if ((new Vector(x, oy, z)).distanceSq(position) > radiusSq) {
                    continue;
                }

                for (int y = world.getMaxY(); y >= 1; --y) {
                    Vector pt = new Vector(x, y, z);
                    int id = getBlockType(pt);

                    switch (id) {
                        case BlockID.ICE:
                            if (setBlock(pt, water)) {
                                ++affected;
                            }
                            break;

                        case BlockID.SNOW:
                            if (setBlock(pt, air)) {
                                ++affected;
                            }
                            break;

                        case BlockID.AIR:
                            continue;

                        default:
                            break;
                    }

                    break;
                }
            }
        }

        return affected;
    }

    /**
     * Make snow in a radius.
     *
     * @param position a position
     * @param radius   a radius
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int simulateSnow(Vector position, double radius) throws MaxChangedBlocksException {
        int affected = 0;
        double radiusSq = radius * radius;

        int ox = position.getBlockX();
        int oy = position.getBlockY();
        int oz = position.getBlockZ();

        BaseBlock ice = new BaseBlock(BlockID.ICE);
        BaseBlock snow = new BaseBlock(BlockID.SNOW);

        int ceilRadius = (int) Math.ceil(radius);
        for (int x = ox - ceilRadius; x <= ox + ceilRadius; ++x) {
            for (int z = oz - ceilRadius; z <= oz + ceilRadius; ++z) {
                if ((new Vector(x, oy, z)).distanceSq(position) > radiusSq) {
                    continue;
                }

                for (int y = world.getMaxY(); y >= 1; --y) {
                    Vector pt = new Vector(x, y, z);
                    int id = getBlockType(pt);

                    if (id == BlockID.AIR) {
                        continue;
                    }

                    // Ice!
                    if (id == BlockID.WATER || id == BlockID.STATIONARY_WATER) {
                        if (setBlock(pt, ice)) {
                            ++affected;
                        }
                        break;
                    }

                    // Snow should not cover these blocks
                    if (BlockType.isTranslucent(id)) {
                        break;
                    }

                    // Too high?
                    if (y == world.getMaxY()) {
                        break;
                    }

                    // add snow cover
                    if (setBlock(pt.add(0, 1, 0), snow)) {
                        ++affected;
                    }
                    break;
                }
            }
        }

        return affected;
    }

    /**
     * Make dirt green.
     *
     * @param position a position
     * @param radius   a radius
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     * @deprecated Use {@link #green(Vector, double, boolean)}.
     */
    @Deprecated
    public int green(Vector position, double radius) throws MaxChangedBlocksException {
        return green(position, radius, true);
    }

    @Override
    public int green(Vector position, double radius, boolean onlyNormalDirt)
            throws MaxChangedBlocksException {
        int affected = 0;
        final double radiusSq = radius * radius;

        final int ox = position.getBlockX();
        final int oy = position.getBlockY();
        final int oz = position.getBlockZ();

        final BaseBlock grass = new BaseBlock(BlockID.GRASS);

        final int ceilRadius = (int) Math.ceil(radius);
        for (int x = ox - ceilRadius; x <= ox + ceilRadius; ++x) {
            for (int z = oz - ceilRadius; z <= oz + ceilRadius; ++z) {
                if ((new Vector(x, oy, z)).distanceSq(position) > radiusSq) {
                    continue;
                }

                loop:
                for (int y = world.getMaxY(); y >= 1; --y) {
                    final Vector pt = new Vector(x, y, z);
                    final int id = getBlockType(pt);
                    final int data = getBlockData(pt);

                    switch (id) {
                        case BlockID.DIRT:
                            if (onlyNormalDirt && data != 0) {
                                break loop;
                            }

                            if (setBlock(pt, grass)) {
                                ++affected;
                            }
                            break loop;

                        case BlockID.WATER:
                        case BlockID.STATIONARY_WATER:
                        case BlockID.LAVA:
                        case BlockID.STATIONARY_LAVA:
                            // break on liquids...
                            break loop;

                        default:
                            // ...and all non-passable blocks
                            if (!BlockType.canPassThrough(id, data)) {
                                break loop;
                            }
                    }
                }
            }
        }

        return affected;
    }

    @Override
    public int makePumpkinPatches(Vector position, int apothem) throws MaxChangedBlocksException {
        // We want to generate pumpkins
        GardenPatchGenerator generator = new GardenPatchGenerator(this);
        generator.setPlant(GardenPatchGenerator.getPumpkinPattern());

        // In a region of the given radius
        FlatRegion region = new CuboidRegion(
                getWorld(), // Causes clamping of Y range
                position.add(-apothem, -5, -apothem),
                position.add(apothem, 10, apothem));
        double density = 0.02;

        GroundFunction ground = new GroundFunction(new ExistingBlockMask(this), generator);
        LayerVisitor visitor = new LayerVisitor(region, minimumBlockY(region), maximumBlockY(region), ground);
        visitor.setMask(new NoiseFilter2D(new RandomNoise(), density));
        Operations.completeLegacy(visitor);
        return ground.getAffected();
    }

    /**
     * Makes a forest.
     *
     * @param basePosition  a position
     * @param size          a size
     * @param density       between 0 and 1, inclusive
     * @param treeGenerator the tree genreator
     * @return number of trees created
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeForest(Vector basePosition, int size, double density, TreeGenerator treeGenerator) throws MaxChangedBlocksException {
        int affected = 0;

        for (int x = basePosition.getBlockX() - size; x <= basePosition.getBlockX()
                + size; ++x) {
            for (int z = basePosition.getBlockZ() - size; z <= basePosition.getBlockZ()
                    + size; ++z) {
                // Don't want to be in the ground
                if (!getBlock(new Vector(x, basePosition.getBlockY(), z)).isAir()) {
                    continue;
                }
                // The gods don't want a tree here
                if (Math.random() >= density) {
                    continue;
                } // def 0.05

                for (int y = basePosition.getBlockY(); y >= basePosition.getBlockY() - 10; --y) {
                    // Check if we hit the ground
                    int t = getBlock(new Vector(x, y, z)).getType();
                    if (t == BlockID.GRASS || t == BlockID.DIRT) {
                        treeGenerator.generate(this, new Vector(x, y + 1, z));
                        ++affected;
                        break;
                    } else if (t == BlockID.SNOW) {
                        setBlock(new Vector(x, y, z), new BaseBlock(BlockID.AIR));
                    } else if (t != BlockID.AIR) { // Trees won't grow on this!
                        break;
                    }
                }
            }
        }

        return affected;
    }

    @Override
    public List<Countable<Integer>> getBlockDistribution(Region region) {
        List<Countable<Integer>> distribution = new ArrayList<Countable<Integer>>();
        Map<Integer, Countable<Integer>> map = new HashMap<Integer, Countable<Integer>>();

        if (region instanceof CuboidRegion) {
            // Doing this for speed
            Vector min = region.getMinimumPoint();
            Vector max = region.getMaximumPoint();

            int minX = min.getBlockX();
            int minY = min.getBlockY();
            int minZ = min.getBlockZ();
            int maxX = max.getBlockX();
            int maxY = max.getBlockY();
            int maxZ = max.getBlockZ();

            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    for (int z = minZ; z <= maxZ; ++z) {
                        Vector pt = new Vector(x, y, z);

                        int id = getBlockType(pt);

                        if (map.containsKey(id)) {
                            map.get(id).increment();
                        } else {
                            Countable<Integer> c = new Countable<Integer>(id, 1);
                            map.put(id, c);
                            distribution.add(c);
                        }
                    }
                }
            }
        } else {
            for (Vector pt : region) {
                int id = getBlockType(pt);

                if (map.containsKey(id)) {
                    map.get(id).increment();
                } else {
                    Countable<Integer> c = new Countable<Integer>(id, 1);
                    map.put(id, c);
                }
            }
        }

        Collections.sort(distribution);
        // Collections.reverse(distribution);

        return distribution;
    }

    /**
     * Get the block distribution (with data values) inside a region.
     *
     * @param region a region
     * @return the results
     */
    // TODO reduce code duplication - probably during ops-redux
    public List<Countable<BaseBlock>> getBlockDistributionWithData(Region region) {
        List<Countable<BaseBlock>> distribution = new ArrayList<Countable<BaseBlock>>();
        Map<BaseBlock, Countable<BaseBlock>> map = new HashMap<BaseBlock, Countable<BaseBlock>>();

        if (region instanceof CuboidRegion) {
            // Doing this for speed
            Vector min = region.getMinimumPoint();
            Vector max = region.getMaximumPoint();

            int minX = min.getBlockX();
            int minY = min.getBlockY();
            int minZ = min.getBlockZ();
            int maxX = max.getBlockX();
            int maxY = max.getBlockY();
            int maxZ = max.getBlockZ();

            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    for (int z = minZ; z <= maxZ; ++z) {
                        Vector pt = new Vector(x, y, z);

                        BaseBlock blk = new BaseBlock(getBlockType(pt), getBlockData(pt));

                        if (map.containsKey(blk)) {
                            map.get(blk).increment();
                        } else {
                            Countable<BaseBlock> c = new Countable<BaseBlock>(blk, 1);
                            map.put(blk, c);
                            distribution.add(c);
                        }
                    }
                }
            }
        } else {
            for (Vector pt : region) {
                BaseBlock blk = new BaseBlock(getBlockType(pt), getBlockData(pt));

                if (map.containsKey(blk)) {
                    map.get(blk).increment();
                } else {
                    Countable<BaseBlock> c = new Countable<BaseBlock>(blk, 1);
                    map.put(blk, c);
                }
            }
        }

        Collections.sort(distribution);
        // Collections.reverse(distribution);

        return distribution;
    }

    public int makeShape(final Region region, final Vector zero, final Vector unit, final Pattern pattern, final String expressionString, final boolean hollow) throws ExpressionException, MaxChangedBlocksException {
        final Expression expression = Expression.compile(expressionString, "x", "y", "z", "type", "data");
        expression.optimize();

        final RValue typeVariable = expression.getVariable("type", false);
        final RValue dataVariable = expression.getVariable("data", false);

        final WorldEditExpressionEnvironment environment = new WorldEditExpressionEnvironment(this, unit, zero);
        expression.setEnvironment(environment);

        final ArbitraryShape shape = new ArbitraryShape(region) {
            @Override
            protected BaseBlock getMaterial(int x, int y, int z, BaseBlock defaultMaterial) {
                final Vector current = new Vector(x, y, z);
                environment.setCurrentBlock(current);
                final Vector scaled = current.subtract(zero).divide(unit);

                try {
                    if (expression.evaluate(scaled.getX(), scaled.getY(), scaled.getZ(), defaultMaterial.getType(), defaultMaterial.getData()) <= 0) {
                        return null;
                    }

                    return new BaseBlock((int) typeVariable.getValue(), (int) dataVariable.getValue());
                } catch (Exception e) {
                    log.log(Level.WARNING, "Failed to create shape", e);
                    return null;
                }
            }
        };

        return shape.generate(this, pattern, hollow);
    }

    public int deformRegion(final Region region, final Vector zero, final Vector unit, final String expressionString) throws ExpressionException, MaxChangedBlocksException {
        final Expression expression = Expression.compile(expressionString, "x", "y", "z");
        expression.optimize();

        final RValue x = expression.getVariable("x", false);
        final RValue y = expression.getVariable("y", false);
        final RValue z = expression.getVariable("z", false);

        final WorldEditExpressionEnvironment environment = new WorldEditExpressionEnvironment(this, unit, zero);
        expression.setEnvironment(environment);

        final DoubleArrayList<BlockVector, BaseBlock> queue = new DoubleArrayList<BlockVector, BaseBlock>(false);

        for (BlockVector position : region) {
            // offset, scale
            final Vector scaled = position.subtract(zero).divide(unit);

            // transform
            expression.evaluate(scaled.getX(), scaled.getY(), scaled.getZ());

            final BlockVector sourcePosition = environment.toWorld(x.getValue(), y.getValue(), z.getValue());

            // read block from world
            // TODO: use getBlock here once the reflection is out of the way
            final BaseBlock material = new BaseBlock(world.getBlockType(sourcePosition), world.getBlockData(sourcePosition));

            // queue operation
            queue.put(position, material);
        }

        int affected = 0;
        for (Map.Entry<BlockVector, BaseBlock> entry : queue) {
            BlockVector position = entry.getKey();
            BaseBlock material = entry.getValue();

            // set at new position
            if (setBlock(position, material)) {
                ++affected;
            }
        }

        return affected;
    }

    /**
     * Hollows out the region (Semi-well-defined for non-cuboid selections).
     *
     * @param region    the region to hollow out.
     * @param thickness the thickness of the shell to leave (manhattan distance)
     * @param pattern   The block pattern to use
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */

    /**
     * Draws a line (out of blocks) between two vectors.
     *
     * @param pattern The block pattern used to draw the line.
     * @param pos1    One of the points that define the line.
     * @param pos2    The other point that defines the line.
     * @param radius  The radius (thickness) of the line.
     * @param filled  If false, only a shell will be generated.
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */


    private static final Vector[] recurseDirections = {
            PlayerDirection.NORTH.vector(),
            PlayerDirection.EAST.vector(),
            PlayerDirection.SOUTH.vector(),
            PlayerDirection.WEST.vector(),
            PlayerDirection.UP.vector(),
            PlayerDirection.DOWN.vector(),
    };


    //Our contribution for Software Architecture project
    public Vector coordinates = null;

    /**
     * Makes a house floor.
     *
     * @param position a position
     * @param block    a block
     * @param length   length of house floor
     * @param width    width of a house floor
     *
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int makeHouseFloor(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException {
        return flyEditSesion.makeHouseFloor(position, block, length, width);
    }

    /**
     * Makes a house carcass.
     *
     * @param position a position
     * @param block    a block
     * @param length     size of carcass
     * @param width     size of carcass
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */

    public int makeHouseCarcass(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException {
        return flyEditSesion.makeHouseCarcass(position, block, length, width);
    }

    public int makeHouseWalls(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException {
        return flyEditSesion.makeHouseWalls(position, block, length, width);
    }

    public int makeHouseRoof(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException {
        return flyEditSesion.makeHouseRoof(position, block, length, width);
    }




    //This command generates four blocks in one plane around the center
    private int generateFourDims(Vector position, Pattern block, int length, int height, int width) throws MaxChangedBlocksException {
        int affected = 0;
        if (setBlock(position.add(length, height, width), block)) {
            ++affected;
        }
        if (setBlock(position.add(length, height, -width), block)) {
            ++affected;
        }
        if (setBlock(position.add(-length, height, width), block)) {
            ++affected;
        }
        if (setBlock(position.add(-length, height, -width), block)) {
            ++affected;
        }
        return affected;
    }

}

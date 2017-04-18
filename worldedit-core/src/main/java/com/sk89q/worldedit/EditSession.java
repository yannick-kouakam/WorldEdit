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
import com.sk89q.worldedit.function.RegionMaskingFilter;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.block.Counter;
import com.sk89q.worldedit.function.mask.ExistingBlockMask;
import com.sk89q.worldedit.function.mask.FuzzyBlockMask;
import com.sk89q.worldedit.function.mask.Mask;
import com.sk89q.worldedit.function.mask.Masks;
import com.sk89q.worldedit.function.operation.ChangeSetExecutor;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.Patterns;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.history.UndoContext;
import com.sk89q.worldedit.history.change.BlockChange;
import com.sk89q.worldedit.history.changeset.BlockOptimizedHistory;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.patterns.Pattern;
import com.sk89q.worldedit.patterns.SingleBlockPattern;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.TreeGenerator;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.world.NullWorld;
import com.sk89q.worldedit.world.World;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;



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

    protected static final Logger log = Logger.getLogger(EditSession.class.getCanonicalName());

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
        flyEditSesion = new EditSesionFlyweight(world);
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
    public int setBlocks(Set<Vector> vset, Pattern pattern) throws MaxChangedBlocksException {
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
        return flyEditSesion.removeAbove(position, apothem, height);
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
        return flyEditSesion.removeBelow(position, apothem, height);
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
        return flyEditSesion.removeNear(position, blockType, apothem);
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
        return flyEditSesion.makeCuboidFaces(region, pattern);
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
        return flyEditSesion.makeFaces(region, pattern);
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
        return flyEditSesion.makeCuboidWalls(region, pattern);
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
        return flyEditSesion.makeWalls(region, pattern);
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
        return flyEditSesion.overlayCuboidBlocks(region, pattern);
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
        return flyEditSesion.naturalizeCuboidBlocks(region);
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
        return flyEditSesion.moveCuboidRegion(region, dir, distance, copyAir, replacement);
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
        return flyEditSesion.thaw(position, radius);
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
        return flyEditSesion.simulateSnow(position, radius);
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
        return flyEditSesion.green(position, radius, onlyNormalDirt);
    }

    @Override
    public int makePumpkinPatches(Vector position, int apothem) throws MaxChangedBlocksException {
        return flyEditSesion.makePumpkinPatches(position, apothem);
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
        return flyEditSesion.makeForest(basePosition, size, density, treeGenerator);
    }

    @Override
    public List<Countable<Integer>> getBlockDistribution(Region region) {
       return flyEditSesion.getBlockDistribution(region);
    }

    /**
     * Get the block distribution (with data values) inside a region.
     *
     * @param region a region
     * @return the results
     */
    // TODO reduce code duplication - probably during ops-redux
    public List<Countable<BaseBlock>> getBlockDistributionWithData(Region region) {
        return flyEditSesion.getBlockDistributionWithData(region);
    }

    public int makeShape(final Region region, final Vector zero, final Vector unit, final Pattern pattern, final String expressionString, final boolean hollow)
            throws ExpressionException, MaxChangedBlocksException {
        return flyEditSesion.makeShape(region, zero, unit, pattern, expressionString, hollow);
    }

    public int deformRegion(final Region region, final Vector zero, final Vector unit, final String expressionString)
            throws ExpressionException, MaxChangedBlocksException {
        return flyEditSesion.deformRegion(region, zero, unit, expressionString);
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

    public int drawLine(Pattern pattern, Vector pos1, Vector pos2, double radius, boolean filled)
            throws MaxChangedBlocksException {
        return flyEditSesion.drawLine(pattern, pos1, pos2, radius, filled);
    }
    public int drawSpline(Pattern pattern, List<Vector> nodevectors, double tension, double bias, double continuity, double quality, double radius, boolean filled)
            throws MaxChangedBlocksException{
        return flyEditSesion.drawSpline(pattern, nodevectors, tension, bias, continuity, quality, radius, filled);
    }

    public int hollowOutRegion(Region region, int thickness, Pattern pattern) throws MaxChangedBlocksException {
        return flyEditSesion.hollowOutRegion(region, thickness, pattern);
    }


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


}

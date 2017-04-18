package com.sk89q.worldedit;

import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockType;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.patterns.Pattern;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.TreeGenerator;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.world.World;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Set;

/**
 * Created by kouakam on 18.04.2017.
 */
public class EditSesionFlyweight implements FlyEditSesion
{

    EditSession editSession;
    @Override
    public int getHighestTerrainBlock(int x, int z, int minY, int maxY, boolean naturalOnly)
    {
        for (int y = maxY; y >= minY; --y) {
            Vector pt = new Vector(x, y, z);
            int id = editSession.getBlockType(pt);
            int data = editSession.getBlockData(pt);
            if (naturalOnly ? BlockType.isNaturalTerrainBlock(id, data) : !BlockType.canPassThrough(id, data)) {
                return y;
            }
        }

        return minY;
    }

    @Override
    public boolean setBlock(Vector position, BaseBlock block, EditSession.Stage stage) throws WorldEditException
    {
        return false;
    }

    @Override
    public boolean rawSetBlock(Vector position, BaseBlock block)
    {
        return false;
    }

    @Override
    public boolean smartSetBlock(Vector position, BaseBlock block)
    {
        return false;
    }

    @Override
    public int countBlock(Region region, Set<Integer> searchIDs)
    {
        return 0;
    }

    @Override
    public int fillXZ(Vector origin, BaseBlock block, double radius, int depth, boolean recursive) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int stackCuboidRegion(Region region, Vector dir, int count, boolean copyAir) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int moveRegion(Region region, Vector dir, int distance, boolean copyAir, BaseBlock replacement) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int moveCuboidRegion(Region region, Vector dir, int distance, boolean copyAir, BaseBlock replacement) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int drainArea(Vector origin, double radius) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int fixLiquid(Vector origin, double radius, int moving, int stationary) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int makeCylinder(Vector pos, Pattern block, double radiusX, double radiusZ, int height, boolean filled) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int makeSphere(Vector pos, Pattern block, double radiusX, double radiusY, double radiusZ, boolean filled) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int makePyramid(Vector position, Pattern block, int size, boolean filled) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int thaw(Vector position, double radius) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int green(Vector position, double radius, boolean onlyNormalDirt) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int makePumpkinPatches(Vector position, int apothem) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public int makeForest(Vector basePosition, int size, double density, TreeGenerator treeGenerator) throws MaxChangedBlocksException
    {
        return 0;
    }

    @Override
    public List<Countable<Integer>> getBlockDistribution(Region region)
    {
        return null;
    }
}

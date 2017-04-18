package com.sk89q.worldedit;

import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.blocks.BlockType;
import com.sk89q.worldedit.extent.buffer.ForgetfulExtentBuffer;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.mask.*;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.OperationQueue;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Patterns;
import com.sk89q.worldedit.function.visitor.DownwardVisitor;
import com.sk89q.worldedit.function.visitor.NonRisingVisitor;
import com.sk89q.worldedit.function.visitor.RecursiveVisitor;
import com.sk89q.worldedit.function.visitor.RegionVisitor;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.patterns.Pattern;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldedit.regions.EllipsoidRegion;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.TreeGenerator;

import java.util.List;
import java.util.Set;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by kouakam on 18.04.2017.
 */
public class EditSesionFlyweight implements FlyEditSesion
{
   // protected final World world;
    EditSession editSession;//=new EditSession(new EventBus(),world,);

    public Vector coordinates = null;




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
    public int fillXZ(Vector origin, Pattern pattern, double radius, int depth, boolean recursive) throws MaxChangedBlocksException
    {
        checkNotNull(origin);
        checkNotNull(pattern);
        checkArgument(radius >= 0, "radius >= 0");
        checkArgument(depth >= 1, "depth >= 1");

        MaskIntersection mask = new MaskIntersection(
                new RegionMask(new EllipsoidRegion(null, origin, new Vector(radius, radius, radius))),
                new BoundedHeightMask(
                        Math.max(origin.getBlockY() - depth + 1, 0),
                        Math.min(editSession.getWorld().getMaxY(), origin.getBlockY())),
                Masks.negate(new ExistingBlockMask(editSession)));

        // Want to replace blocks
        BlockReplace replace = new BlockReplace(editSession, Patterns.wrap(pattern));

        // Pick how we're going to visit blocks
        RecursiveVisitor visitor;
        if (recursive) {
            visitor = new RecursiveVisitor(mask, replace);
        } else {
            visitor = new DownwardVisitor(mask, replace, origin.getBlockY());
        }

        // Start at the origin
        visitor.visit(origin);

        // Execute
        Operations.completeLegacy(visitor);

        return visitor.getAffected();
    }

    @Override
    public int stackCuboidRegion(Region region, Vector dir, int count, boolean copyAir) throws MaxChangedBlocksException
    {
        checkNotNull(region);
        checkNotNull(dir);
        checkArgument(count >= 1, "count >= 1 required");

        Vector size = region.getMaximumPoint().subtract(region.getMinimumPoint()).add(1, 1, 1);
        Vector to = region.getMinimumPoint();
        ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, editSession, to);
        copy.setRepetitions(count);
        copy.setTransform(new AffineTransform().translate(dir.multiply(size)));
        if (!copyAir) {
            copy.setSourceMask(new ExistingBlockMask(editSession));
        }
        Operations.completeLegacy(copy);
        return copy.getAffected();
    }

    @Override
    public int moveRegion(Region region, Vector dir, int distance, boolean copyAir, BaseBlock replacement) throws MaxChangedBlocksException
    {
        checkNotNull(region);
        checkNotNull(dir);
        checkArgument(distance >= 1, "distance >= 1 required");

        Vector to = region.getMinimumPoint();

        // Remove the original blocks
        com.sk89q.worldedit.function.pattern.Pattern pattern = replacement != null ?
                new BlockPattern(replacement) :
                new BlockPattern(new BaseBlock(BlockID.AIR));
        BlockReplace remove = new BlockReplace(editSession, pattern);

        // Copy to a buffer so we don't destroy our original before we can copy all the blocks from it
        ForgetfulExtentBuffer buffer = new ForgetfulExtentBuffer(editSession, new RegionMask(region));
        ForwardExtentCopy copy = new ForwardExtentCopy(editSession, region, buffer, to);
        copy.setTransform(new AffineTransform().translate(dir.multiply(distance)));
        copy.setSourceFunction(remove); // Remove
        copy.setRemovingEntities(true);
        if (!copyAir) {
            copy.setSourceMask(new ExistingBlockMask(editSession));
        }

        // Then we need to copy the buffer to the world
        BlockReplace replace = new BlockReplace(editSession, buffer);
        RegionVisitor visitor = new RegionVisitor(buffer.asRegion(), replace);

        OperationQueue operation = new OperationQueue(copy, visitor);
        Operations.completeLegacy(operation);

        return copy.getAffected();
    }

    @Override
    public int drainArea(Vector origin, double radius) throws MaxChangedBlocksException
    {
        checkNotNull(origin);
        checkArgument(radius >= 0, "radius >= 0 required");

        MaskIntersection mask = new MaskIntersection(
                new BoundedHeightMask(0, editSession.getWorld().getMaxY()),
                new RegionMask(new EllipsoidRegion(null, origin, new Vector(radius, radius, radius))),
                editSession.getWorld().createLiquidMask());

        BlockReplace replace = new BlockReplace(editSession, new BlockPattern(new BaseBlock(BlockID.AIR)));
        RecursiveVisitor visitor = new RecursiveVisitor(mask, replace);

        // Around the origin in a 3x3 block
        for (BlockVector position : CuboidRegion.fromCenter(origin, 1)) {
            if (mask.test(position)) {
                visitor.visit(position);
            }
        }

        Operations.completeLegacy(visitor);
        return visitor.getAffected();
    }

    @Override
    public int fixLiquid(Vector origin, double radius, int moving, int stationary) throws MaxChangedBlocksException
    {
        checkNotNull(origin);
        checkArgument(radius >= 0, "radius >= 0 required");

        // Our origins can only be liquids
        BlockMask liquidMask = new BlockMask(
                editSession,
                new BaseBlock(moving, -1),
                new BaseBlock(stationary, -1));

        // But we will also visit air blocks
        MaskIntersection blockMask =
                new MaskUnion(liquidMask,
                        new BlockMask(
                                editSession,
                                new BaseBlock(BlockID.AIR)));

        // There are boundaries that the routine needs to stay in
        MaskIntersection mask = new MaskIntersection(
                new BoundedHeightMask(0, Math.min(origin.getBlockY(), editSession.getWorld().getMaxY())),
                new RegionMask(new EllipsoidRegion(null, origin, new Vector(radius, radius, radius))),
                blockMask);

        BlockReplace replace = new BlockReplace(editSession, new BlockPattern(new BaseBlock(stationary)));
        NonRisingVisitor visitor = new NonRisingVisitor(mask, replace);

        // Around the origin in a 3x3 block
        for (BlockVector position : CuboidRegion.fromCenter(origin, 1)) {
            if (liquidMask.test(position)) {
                visitor.visit(position);
            }
        }

        Operations.completeLegacy(visitor);

        return visitor.getAffected();
    }

    @Override
    public int makeCylinder(Vector pos, Pattern block, double radiusX, double radiusZ, int height, boolean filled) throws MaxChangedBlocksException
    {
      /*  int affected = 0;

        radiusX += 0.5;
        radiusZ += 0.5;

        if (height == 0) {
            return 0;
        } else if (height < 0) {
            height = -height;
            pos = pos.subtract(0, height, 0);
        }

        if (pos.getBlockY() < 0) {
            pos = pos.setY(0);
        } else if (pos.getBlockY() + height - 1 > world.getMaxY()) {
            height = world.getMaxY() - pos.getBlockY() + 1;
        }

        final double invRadiusX = 1 / radiusX;
        final double invRadiusZ = 1 / radiusZ;

        final int ceilRadiusX = (int) Math.ceil(radiusX);
        final int ceilRadiusZ = (int) Math.ceil(radiusZ);

        double nextXn = 0;
        forX:
        for (int x = 0; x <= ceilRadiusX; ++x) {
            final double xn = nextXn;
            nextXn = (x + 1) * invRadiusX;
            double nextZn = 0;
            forZ:
            for (int z = 0; z <= ceilRadiusZ; ++z) {
                final double zn = nextZn;
                nextZn = (z + 1) * invRadiusZ;

                double distanceSq = lengthSq(xn, zn);
                if (distanceSq > 1) {
                    if (z == 0) {
                        break forX;
                    }
                    break forZ;
                }

                if (!filled) {
                    if (lengthSq(nextXn, zn) <= 1 && lengthSq(xn, nextZn) <= 1) {
                        continue;
                    }
                }

                for (int y = 0; y < height; ++y) {
                    if (setBlock(pos.add(x, y, z), block)) {
                        ++affected;
                    }
                    if (setBlock(pos.add(-x, y, z), block)) {
                        ++affected;
                    }
                    if (setBlock(pos.add(x, y, -z), block)) {
                        ++affected;
                    }
                    if (setBlock(pos.add(-x, y, -z), block)) {
                        ++affected;
                    }
                }
            }
        }

        return affected;
*/   return 0;
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



    public int makeHouseRoof(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException {
        int affected = 0;

        length--;
        length /= 2;
        width--;
        width /= 2;
        /*
        Go down until the ground. It is needed because houses should,t fly in the air.
        Originally, worldedit provides generating structures
        around a player, often they appear to be flying in the air.
        */
        int nowY = position.getBlockY();
        while(editSession.getWorld().getBlock(position).getId() == 0){
            nowY--;
            position = new Vector(position.getBlockX(), nowY, position.getZ());
        }
        int y = 5;
        //Roof form
        for (int x = length+1; x >=0 ; x--){
            for (int z = width; z >=0 ; z--){
                affected += generateFourDims(position, block, x, y, z);
            }
            //Fulfill roof
            for(int i = 0; i < x; i++){
                affected += generateFourDims(position, block, i, y, width);
                affected += generateFourDims(position, block, i, y, width+1);
            }
            y++;
        }
        //ceiling
        for (int x = 0; x <= length; x++) {
            for (int z = 0; z <= width; z++) {
                if ((z <= width && x <= length) || z == width || x == length) {
                    affected += generateFourDims(position, block, x, 6, z);
                }

            }
        }
        coordinates = position;
        return affected;
    }

    public int makeHouseWalls(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException {
        int affected = 0;

        length--;
        length /= 2;
        width--;
        width /= 2;
        /*
        Go down until the ground. It is needed because houses should,t fly in the air.
        Originally, worldedit provides generating structures
        around a player, often they appear to be flying in the air.
        */
        int nowY = position.getBlockY();
        while(world.getBlock(position).getId() == 0){
            nowY--;
            position = new Vector(position.getBlockX(), nowY, position.getZ());
        }
        for (int i = 1; i < 6; i++) {
            for (int j = 0; j < length; j++) {
                affected += generateFourDims(position, block, j, i, width);
            }
            for (int j = 0; j < width; j++ ){
                affected += generateFourDims(position, block, length, i, j);
            }

        }
        //Create three windows
        if (setBlock(position.add(length, 2, 0), new BaseBlock(20))) {
            ++affected;
        }
        if (setBlock(position.add(-length, 2, 0), new BaseBlock(20))) {
            ++affected;
        }
        if (editSession.setBlock(position.add(0, 2, -width), new BaseBlock(20))) {
            ++affected;
        }
        //create door. id: 0 is an empty space
        if (setBlock(position.add(0, 2, width), new BaseBlock(0))) {
            ++affected;
        }
        if (setBlock(position.add(0, 1, width), new BaseBlock(0))) {
            ++affected;
        }
        if (setBlock(position.add(0, 1, width), new BaseBlock(64))) {
            ++affected;
        }

        coordinates = position;
        return affected;
    }

    public int makeHouseCarcass(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException {
        int affected = 0;

        length--;
        length /= 2;
        width--;
        width /= 2;
        /*
        Go down until the ground. It is needed because houses should,t fly in the air.
        Originally, worldedit provides generating structures
        around a player, often they appear to be flying in the air.
        */
        int nowY = position.getBlockY();
        while(world.getBlock(position).getId() == 0){
            nowY--;
            position = new Vector(position.getBlockX(), nowY, position.getZ());
        }
        //Four columns in the corners
        for (int i = 1; i < 6; i++) {
            affected += generateFourDims(position, block, length, i, width);
        }

        coordinates = position;

        return affected;
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

    public int makeHouseFloor(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException {
        int affected = 0;
        length--;
        length /= 2;
        width--;
        width /= 2;
        /*
        Go down until the ground. It is needed because houses should,t fly in the air.
        Originally, worldedit provides generating structures
        around a player, often they appear to be flying in the air.
        */
        int nowY = position.getBlockY();
        while(world.getBlock(position).getId() == 0){
            nowY--;
            position = new Vector(position.getBlockX(), nowY, position.getZ());
        }
        //Creation of the floor
        for (int x = 0; x <= length; x++) {
            for (int z = 0; z <= width; z++) {
                if ((z <= width && x <= length) || z == width || x == length) {
                    affected += generateFourDims(position, block, x, 0, z);
                }

            }
        }
        //Coordinates will be passed to GenerationCommands class.
        coordinates = position;
        return affected;
    }
}

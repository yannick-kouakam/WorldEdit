package com.sk89q.worldedit;

import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.blocks.BlockType;
import com.sk89q.worldedit.event.extent.EditSessionEvent;
import com.sk89q.worldedit.extent.buffer.ForgetfulExtentBuffer;
import com.sk89q.worldedit.extent.inventory.BlockBag;
import com.sk89q.worldedit.function.GroundFunction;
import com.sk89q.worldedit.function.block.BlockReplace;
import com.sk89q.worldedit.function.block.Naturalizer;
import com.sk89q.worldedit.function.generator.GardenPatchGenerator;
import com.sk89q.worldedit.function.mask.*;
import com.sk89q.worldedit.function.operation.ForwardExtentCopy;
import com.sk89q.worldedit.function.operation.OperationQueue;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.function.pattern.BlockPattern;
import com.sk89q.worldedit.function.pattern.Patterns;
import com.sk89q.worldedit.function.util.RegionOffset;
import com.sk89q.worldedit.function.visitor.*;
import com.sk89q.worldedit.history.changeset.BlockOptimizedHistory;
import com.sk89q.worldedit.history.changeset.ChangeSet;
import com.sk89q.worldedit.internal.expression.Expression;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.internal.expression.runtime.RValue;
import com.sk89q.worldedit.math.interpolation.Interpolation;
import com.sk89q.worldedit.math.interpolation.KochanekBartelsInterpolation;
import com.sk89q.worldedit.math.interpolation.Node;
import com.sk89q.worldedit.math.noise.RandomNoise;
import com.sk89q.worldedit.math.transform.AffineTransform;
import com.sk89q.worldedit.patterns.Pattern;
import com.sk89q.worldedit.patterns.SingleBlockPattern;
import com.sk89q.worldedit.regions.*;
import com.sk89q.worldedit.regions.shape.ArbitraryShape;
import com.sk89q.worldedit.regions.shape.RegionShape;
import com.sk89q.worldedit.regions.shape.WorldEditExpressionEnvironment;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.TreeGenerator;
import com.sk89q.worldedit.util.collection.DoubleArrayList;
import com.sk89q.worldedit.util.eventbus.EventBus;
import com.sk89q.worldedit.world.World;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.sk89q.worldedit.regions.Regions.asFlatRegion;
import static com.sk89q.worldedit.regions.Regions.maximumBlockY;
import static com.sk89q.worldedit.regions.Regions.minimumBlockY;

/**
 * Created by kouakam on 18.04.2017.
 */
public class EditSesionServices implements FlyEditSesion
{
   // protected final World world;
    EditSession editSession;//=new EditSession(new EventBus(),world,);
    protected static final Logger log = Logger.getLogger(EditSession.class.getCanonicalName());
    public Vector coordinates = null;


    //mess
    @SuppressWarnings("ProtectedField")
    protected final World world;
    private final ChangeSet changeSet = new BlockOptimizedHistory();

    public EditSesionServices(World world){
        this.world = world;
    }

    @SuppressWarnings("deprecation")
    private Mask oldMask;


    public EditSesionServices(EventBus eventBus, World world, int maxBlocks, @Nullable BlockBag blockBag, EditSessionEvent event){
        this.world = world;
    }


    private static final Vector[] recurseDirections = {
            PlayerDirection.NORTH.vector(),
            PlayerDirection.EAST.vector(),
            PlayerDirection.SOUTH.vector(),
            PlayerDirection.WEST.vector(),
            PlayerDirection.UP.vector(),
            PlayerDirection.DOWN.vector(),
    };

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
    @SuppressWarnings("deprecation")
    public int removeAbove(Vector position, int apothem, int height) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");
        checkArgument(height >= 1, "height >= 1");

        Region region = new CuboidRegion(
                editSession.getWorld(), // Causes clamping of Y range
                position.add(-apothem + 1, 0, -apothem + 1),
                position.add(apothem - 1, height - 1, apothem - 1));
        Pattern pattern = new SingleBlockPattern(new BaseBlock(BlockID.AIR));
        return editSession.setBlocks(region, pattern);
    }

    @SuppressWarnings("deprecation")
    public int removeBelow(Vector position, int apothem, int height) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");
        checkArgument(height >= 1, "height >= 1");

        Region region = new CuboidRegion(
                editSession.getWorld(), // Causes clamping of Y range
                position.add(-apothem + 1, 0, -apothem + 1),
                position.add(apothem - 1, -height + 1, apothem - 1));
        Pattern pattern = new SingleBlockPattern(new BaseBlock(BlockID.AIR));
        return editSession.setBlocks(region, pattern);
    }

    @SuppressWarnings("deprecation")
    public int removeNear(Vector position, int blockType, int apothem) throws MaxChangedBlocksException {
        checkNotNull(position);
        checkArgument(apothem >= 1, "apothem >= 1");

        Mask mask = new FuzzyBlockMask(editSession, new BaseBlock(blockType, -1));
        Vector adjustment = new Vector(1, 1, 1).multiply(apothem - 1);
        Region region = new CuboidRegion(
                editSession.getWorld(), // Causes clamping of Y range
                position.add(adjustment.multiply(-1)),
                position.add(adjustment));
        Pattern pattern = new SingleBlockPattern(new BaseBlock(BlockID.AIR));
        return editSession.replaceBlocks(region, mask, pattern);
    }

    @SuppressWarnings("deprecation")
    public int makeCuboidFaces(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        CuboidRegion cuboid = CuboidRegion.makeCuboid(region);
        Region faces = cuboid.getFaces();
        return editSession.setBlocks(faces, pattern);
    }

    @SuppressWarnings("deprecation")
    public int makeFaces(final Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        if (region instanceof CuboidRegion) {
            return makeCuboidFaces(region, pattern);
        } else {
            return new RegionShape(region).generate(editSession, pattern, true);
        }
    }

    @SuppressWarnings("deprecation")
    public int makeCuboidWalls(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        CuboidRegion cuboid = CuboidRegion.makeCuboid(region);
        Region faces = cuboid.getWalls();
        return editSession.setBlocks(faces, pattern);
    }

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
            return shape.generate(editSession, pattern, true);
        }
    }

    @SuppressWarnings("deprecation")
    public int overlayCuboidBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException {
        checkNotNull(region);
        checkNotNull(pattern);

        BlockReplace replace = new BlockReplace(editSession, Patterns.wrap(pattern));
        RegionOffset offset = new RegionOffset(new Vector(0, 1, 0), replace);
        GroundFunction ground = new GroundFunction(new ExistingBlockMask(editSession), offset);
        LayerVisitor visitor = new LayerVisitor(asFlatRegion(region), minimumBlockY(region), maximumBlockY(region), ground);
        Operations.completeLegacy(visitor);
        return ground.getAffected();
    }

    public int naturalizeCuboidBlocks(Region region) throws MaxChangedBlocksException {
        checkNotNull(region);

        Naturalizer naturalizer = new Naturalizer(editSession);
        FlatRegion flatRegion = Regions.asFlatRegion(region);
        LayerVisitor visitor = new LayerVisitor(flatRegion, minimumBlockY(region), maximumBlockY(region), naturalizer);
        Operations.completeLegacy(visitor);
        return naturalizer.getAffected();
    }

    public int moveCuboidRegion(Region region, Vector dir, int distance, boolean copyAir, BaseBlock replacement) throws MaxChangedBlocksException {
        return moveRegion(region, dir, distance, copyAir, replacement);
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
                    int id = editSession.getBlockType(pt);

                    switch (id) {
                        case BlockID.ICE:
                            if (editSession.setBlock(pt, water)) {
                                ++affected;
                            }
                            break;

                        case BlockID.SNOW:
                            if (editSession.setBlock(pt, air)) {
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
                    int id = editSession.getBlockType(pt);

                    if (id == BlockID.AIR) {
                        continue;
                    }

                    // Ice!
                    if (id == BlockID.WATER || id == BlockID.STATIONARY_WATER) {
                        if (editSession.setBlock(pt, ice)) {
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
                    if (editSession.setBlock(pt.add(0, 1, 0), snow)) {
                        ++affected;
                    }
                    break;
                }
            }
        }

        return affected;
    }

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
                    final int id = editSession.getBlockType(pt);
                    final int data = editSession.getBlockData(pt);

                    switch (id) {
                        case BlockID.DIRT:
                            if (onlyNormalDirt && data != 0) {
                                break loop;
                            }

                            if (editSession.setBlock(pt, grass)) {
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
    private static double lengthSq(double x, double y, double z) {
        return (x * x) + (y * y) + (z * z);
    }
    private static double lengthSq(double x, double z) {
        return (x * x) + (z * z);
    }

    @Override
    public int makeCylinder(Vector pos, Pattern block, double radiusX, double radiusZ, int height, boolean filled) throws MaxChangedBlocksException
    {
       int affected = 0;

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
        } else if (pos.getBlockY() + height - 1 > editSession.getWorld().getMaxY()) {
            height = editSession.getWorld().getMaxY() - pos.getBlockY() + 1;
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
                    if (editSession.setBlock(pos.add(x, y, z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(-x, y, z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(x, y, -z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(-x, y, -z), block)) {
                        ++affected;
                    }
                }
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
    public int hollowOutRegion(Region region, int thickness, Pattern pattern) throws MaxChangedBlocksException {
        int affected = 0;

        final Set<BlockVector> outside = new HashSet<BlockVector>();

        final Vector min = region.getMinimumPoint();
        final Vector max = region.getMaximumPoint();

        final int minX = min.getBlockX();
        final int minY = min.getBlockY();
        final int minZ = min.getBlockZ();
        final int maxX = max.getBlockX();
        final int maxY = max.getBlockY();
        final int maxZ = max.getBlockZ();

        for (int x = minX; x <= maxX; ++x) {
            for (int y = minY; y <= maxY; ++y) {
                recurseHollow(region, new BlockVector(x, y, minZ), outside);
                recurseHollow(region, new BlockVector(x, y, maxZ), outside);
            }
        }

        for (int y = minY; y <= maxY; ++y) {
            for (int z = minZ; z <= maxZ; ++z) {
                recurseHollow(region, new BlockVector(minX, y, z), outside);
                recurseHollow(region, new BlockVector(maxX, y, z), outside);
            }
        }

        for (int z = minZ; z <= maxZ; ++z) {
            for (int x = minX; x <= maxX; ++x) {
                recurseHollow(region, new BlockVector(x, minY, z), outside);
                recurseHollow(region, new BlockVector(x, maxY, z), outside);
            }
        }

        for (int i = 1; i < thickness; ++i) {
            final Set<BlockVector> newOutside = new HashSet<BlockVector>();
            outer:
            for (BlockVector position : region) {
                for (Vector recurseDirection : recurseDirections) {
                    BlockVector neighbor = position.add(recurseDirection).toBlockVector();

                    if (outside.contains(neighbor)) {
                        newOutside.add(position);
                        continue outer;
                    }
                }
            }

            outside.addAll(newOutside);
        }

        outer:
        for (BlockVector position : region) {
            for (Vector recurseDirection : recurseDirections) {
                BlockVector neighbor = position.add(recurseDirection).toBlockVector();

                if (outside.contains(neighbor)) {
                    continue outer;
                }
            }

            if (editSession.setBlock(position, pattern.next(position))) {
                ++affected;
            }
        }

        return affected;
    }
    /**
     * Draws a spline (out of blocks) between specified vectors.
     *
     * @param pattern     The block pattern used to draw the spline.
     * @param nodevectors The list of vectors to draw through.
     * @param tension     The tension of every node.
     * @param bias        The bias of every node.
     * @param continuity  The continuity of every node.
     * @param quality     The quality of the spline. Must be greater than 0.
     * @param radius      The radius (thickness) of the spline.
     * @param filled      If false, only a shell will be generated.
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    public int drawSpline(Pattern pattern, List<Vector> nodevectors, double tension, double bias, double continuity, double quality, double radius, boolean filled)
            throws MaxChangedBlocksException {

        Set<Vector> vset = new HashSet<Vector>();
        List<Node> nodes = new ArrayList<Node>(nodevectors.size());

        Interpolation interpol = new KochanekBartelsInterpolation();

        for (Vector nodevector : nodevectors) {
            Node n = new Node(nodevector);
            n.setTension(tension);
            n.setBias(bias);
            n.setContinuity(continuity);
            nodes.add(n);
        }

        interpol.setNodes(nodes);
        double splinelength = interpol.arcLength(0, 1);
        for (double loop = 0; loop <= 1; loop += 1D / splinelength / quality) {
            Vector tipv = interpol.getPosition(loop);
            int tipx = (int) Math.round(tipv.getX());
            int tipy = (int) Math.round(tipv.getY());
            int tipz = (int) Math.round(tipv.getZ());

            vset.add(new Vector(tipx, tipy, tipz));
        }

        vset = getBallooned(vset, radius);
        if (!filled) {
            vset = getHollowed(vset);
        }
        return editSession.setBlocks(vset, pattern);
    }

    private static double hypot(double... pars) {
        double sum = 0;
        for (double d : pars) {
            sum += Math.pow(d, 2);
        }
        return Math.sqrt(sum);
    }
    private static Set<Vector> getBallooned(Set<Vector> vset, double radius) {
        Set<Vector> returnset = new HashSet<Vector>();
        int ceilrad = (int) Math.ceil(radius);

        for (Vector v : vset) {
            int tipx = v.getBlockX(), tipy = v.getBlockY(), tipz = v.getBlockZ();

            for (int loopx = tipx - ceilrad; loopx <= tipx + ceilrad; loopx++) {
                for (int loopy = tipy - ceilrad; loopy <= tipy + ceilrad; loopy++) {
                    for (int loopz = tipz - ceilrad; loopz <= tipz + ceilrad; loopz++) {
                        if (hypot(loopx - tipx, loopy - tipy, loopz - tipz) <= radius) {
                            returnset.add(new Vector(loopx, loopy, loopz));
                        }
                    }
                }
            }
        }
        return returnset;
    }
    private static Set<Vector> getHollowed(Set<Vector> vset) {
        Set<Vector> returnset = new HashSet<Vector>();
        for (Vector v : vset) {
            double x = v.getX(), y = v.getY(), z = v.getZ();
            if (!(vset.contains(new Vector(x + 1, y, z)) &&
                    vset.contains(new Vector(x - 1, y, z)) &&
                    vset.contains(new Vector(x, y + 1, z)) &&
                    vset.contains(new Vector(x, y - 1, z)) &&
                    vset.contains(new Vector(x, y, z + 1)) &&
                    vset.contains(new Vector(x, y, z - 1)))) {
                returnset.add(v);
            }
        }
        return returnset;
    }
    public int drawLine(Pattern pattern, Vector pos1, Vector pos2, double radius, boolean filled)
            throws MaxChangedBlocksException {

        Set<Vector> vset = new HashSet<Vector>();
        boolean notdrawn = true;

        int x1 = pos1.getBlockX(), y1 = pos1.getBlockY(), z1 = pos1.getBlockZ();
        int x2 = pos2.getBlockX(), y2 = pos2.getBlockY(), z2 = pos2.getBlockZ();
        int tipx = x1, tipy = y1, tipz = z1;
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), dz = Math.abs(z2 - z1);

        if (dx + dy + dz == 0) {
            vset.add(new Vector(tipx, tipy, tipz));
            notdrawn = false;
        }

        if (Math.max(Math.max(dx, dy), dz) == dx && notdrawn) {
            for (int domstep = 0; domstep <= dx; domstep++) {
                tipx = x1 + domstep * (x2 - x1 > 0 ? 1 : -1);
                tipy = (int) Math.round(y1 + domstep * ((double) dy) / ((double) dx) * (y2 - y1 > 0 ? 1 : -1));
                tipz = (int) Math.round(z1 + domstep * ((double) dz) / ((double) dx) * (z2 - z1 > 0 ? 1 : -1));

                vset.add(new Vector(tipx, tipy, tipz));
            }
            notdrawn = false;
        }

        if (Math.max(Math.max(dx, dy), dz) == dy && notdrawn) {
            for (int domstep = 0; domstep <= dy; domstep++) {
                tipy = y1 + domstep * (y2 - y1 > 0 ? 1 : -1);
                tipx = (int) Math.round(x1 + domstep * ((double) dx) / ((double) dy) * (x2 - x1 > 0 ? 1 : -1));
                tipz = (int) Math.round(z1 + domstep * ((double) dz) / ((double) dy) * (z2 - z1 > 0 ? 1 : -1));

                vset.add(new Vector(tipx, tipy, tipz));
            }
            notdrawn = false;
        }

        if (Math.max(Math.max(dx, dy), dz) == dz && notdrawn) {
            for (int domstep = 0; domstep <= dz; domstep++) {
                tipz = z1 + domstep * (z2 - z1 > 0 ? 1 : -1);
                tipy = (int) Math.round(y1 + domstep * ((double) dy) / ((double) dz) * (y2 - y1 > 0 ? 1 : -1));
                tipx = (int) Math.round(x1 + domstep * ((double) dx) / ((double) dz) * (x2 - x1 > 0 ? 1 : -1));

                vset.add(new Vector(tipx, tipy, tipz));
            }
            notdrawn = false;
        }

        vset = getBallooned(vset, radius);
        if (!filled) {
            vset = getHollowed(vset);
        }
        return editSession.setBlocks(vset, pattern);
    }

    private void recurseHollow(Region region, BlockVector origin, Set<BlockVector> outside) {
        final LinkedList<BlockVector> queue = new LinkedList<BlockVector>();
        queue.addLast(origin);

        while (!queue.isEmpty()) {
            final BlockVector current = queue.removeFirst();
            if (!BlockType.canPassThrough(editSession.getBlockType(current), editSession.getBlockData(current))) {
                continue;
            }

            if (!outside.add(current)) {
                continue;
            }

            if (!region.contains(current)) {
                continue;
            }

            for (Vector recurseDirection : recurseDirections) {
                queue.addLast(current.add(recurseDirection).toBlockVector());
            }
        } // while
    }
    @Override
    public int makeSphere(Vector pos, Pattern block, double radiusX, double radiusY, double radiusZ, boolean filled) throws MaxChangedBlocksException
    {
        int affected = 0;

        radiusX += 0.5;
        radiusY += 0.5;
        radiusZ += 0.5;

        final double invRadiusX = 1 / radiusX;
        final double invRadiusY = 1 / radiusY;
        final double invRadiusZ = 1 / radiusZ;

        final int ceilRadiusX = (int) Math.ceil(radiusX);
        final int ceilRadiusY = (int) Math.ceil(radiusY);
        final int ceilRadiusZ = (int) Math.ceil(radiusZ);

        double nextXn = 0;
        forX:
        for (int x = 0; x <= ceilRadiusX; ++x) {
            final double xn = nextXn;
            nextXn = (x + 1) * invRadiusX;
            double nextYn = 0;
            forY:
            for (int y = 0; y <= ceilRadiusY; ++y) {
                final double yn = nextYn;
                nextYn = (y + 1) * invRadiusY;
                double nextZn = 0;
                forZ:
                for (int z = 0; z <= ceilRadiusZ; ++z) {
                    final double zn = nextZn;
                    nextZn = (z + 1) * invRadiusZ;

                    double distanceSq = lengthSq(xn, yn, zn);
                    if (distanceSq > 1) {
                        if (z == 0) {
                            if (y == 0) {
                                break forX;
                            }
                            break forY;
                        }
                        break forZ;
                    }

                    if (!filled) {
                        if (lengthSq(nextXn, yn, zn) <= 1 && lengthSq(xn, nextYn, zn) <= 1 && lengthSq(xn, yn, nextZn) <= 1) {
                            continue;
                        }
                    }

                    if (editSession.setBlock(pos.add(x, y, z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(-x, y, z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(x, -y, z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(x, y, -z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(-x, -y, z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(x, -y, -z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(-x, y, -z), block)) {
                        ++affected;
                    }
                    if (editSession.setBlock(pos.add(-x, -y, -z), block)) {
                        ++affected;
                    }
                }
            }
        }

        return affected;
    }

    @Override
    public int makePyramid(Vector position, Pattern block, int size, boolean filled) throws MaxChangedBlocksException
    {int affected = 0;

        int height = size;

        for (int y = 0; y <= height; ++y) {
            size--;
            for (int x = 0; x <= size; ++x) {
                for (int z = 0; z <= size; ++z) {

                    if ((filled && z <= size && x <= size) || z == size || x == size) {

                        if (editSession.setBlock(position.add(x, y, z), block)) {
                            ++affected;
                        }
                        if (editSession.setBlock(position.add(-x, y, z), block)) {
                            ++affected;
                        }
                        if (editSession.setBlock(position.add(x, y, -z), block)) {
                            ++affected;
                        }
                        if (editSession.setBlock(position.add(-x, y, -z), block)) {
                            ++affected;
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
        GardenPatchGenerator generator = new GardenPatchGenerator(editSession);
        generator.setPlant(GardenPatchGenerator.getPumpkinPattern());

        // In a region of the given radius
        FlatRegion region = new CuboidRegion(
                editSession.getWorld(), // Causes clamping of Y range
                position.add(-apothem, -5, -apothem),
                position.add(apothem, 10, apothem));
        double density = 0.02;

        GroundFunction ground = new GroundFunction(new ExistingBlockMask(editSession), generator);
        LayerVisitor visitor = new LayerVisitor(region, minimumBlockY(region), maximumBlockY(region), ground);
        visitor.setMask(new NoiseFilter2D(new RandomNoise(), density));
        Operations.completeLegacy(visitor);
        return ground.getAffected();
    }


    @Override
    public int makeForest(Vector basePosition, int size, double density, TreeGenerator treeGenerator) throws MaxChangedBlocksException
    {
        int affected = 0;

        for (int x = basePosition.getBlockX() - size; x <= basePosition.getBlockX()
                + size; ++x) {
            for (int z = basePosition.getBlockZ() - size; z <= basePosition.getBlockZ()
                    + size; ++z) {
                // Don't want to be in the ground
                if (!editSession.getBlock(new Vector(x, basePosition.getBlockY(), z)).isAir()) {
                    continue;
                }
                // The gods don't want a tree here
                if (Math.random() >= density) {
                    continue;
                } // def 0.05

                for (int y = basePosition.getBlockY(); y >= basePosition.getBlockY() - 10; --y) {
                    // Check if we hit the ground
                    int t = editSession.getBlock(new Vector(x, y, z)).getType();
                    if (t == BlockID.GRASS || t == BlockID.DIRT) {
                        treeGenerator.generate(editSession, new Vector(x, y + 1, z));
                        ++affected;
                        break;
                    } else if (t == BlockID.SNOW) {
                        editSession.setBlock(new Vector(x, y, z), new BaseBlock(BlockID.AIR));
                    } else if (t != BlockID.AIR) { // Trees won't grow on this!
                        break;
                    }
                }
            }
        }

        return affected;
    }

    @Override
    public List<Countable<Integer>> getBlockDistribution(Region region)
    {
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

                        int id = editSession.getBlockType(pt);

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
                int id = editSession.getBlockType(pt);

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

                        BaseBlock blk = new BaseBlock(editSession.getBlockType(pt), editSession.getBlockData(pt));

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
                BaseBlock blk = new BaseBlock(editSession.getBlockType(pt), editSession.getBlockData(pt));

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


    public int makeShape(final Region region, final Vector zero, final Vector unit, final Pattern pattern, final String expressionString, final boolean hollow)
            throws ExpressionException, MaxChangedBlocksException {
        final Expression expression = Expression.compile(expressionString, "x", "y", "z", "type", "data");
        expression.optimize();

        final RValue typeVariable = expression.getVariable("type", false);
        final RValue dataVariable = expression.getVariable("data", false);

        final WorldEditExpressionEnvironment environment = new WorldEditExpressionEnvironment(editSession, unit, zero);
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

        return shape.generate(editSession, pattern, hollow);
    }

    public int deformRegion(final Region region, final Vector zero, final Vector unit, final String expressionString)
            throws ExpressionException, MaxChangedBlocksException {
        final Expression expression = Expression.compile(expressionString, "x", "y", "z");
        expression.optimize();

        final RValue x = expression.getVariable("x", false);
        final RValue y = expression.getVariable("y", false);
        final RValue z = expression.getVariable("z", false);

        final WorldEditExpressionEnvironment environment = new WorldEditExpressionEnvironment(editSession, unit, zero);
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
            if (editSession.setBlock(position, material)) {
                ++affected;
            }
        }

        return affected;
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
        editSession.coordinates = position;
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
        while(editSession.getWorld().getBlock(position).getId() == 0){
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
        if (editSession.setBlock(position.add(length, 2, 0), new BaseBlock(20))) {
            ++affected;
        }
        if (editSession.setBlock(position.add(-length, 2, 0), new BaseBlock(20))) {
            ++affected;
        }
        if (editSession.setBlock(position.add(0, 2, -width), new BaseBlock(20))) {
            ++affected;
        }
        //create door. id: 0 is an empty space
        if (editSession.setBlock(position.add(0, 2, width), new BaseBlock(0))) {
            ++affected;
        }
        if (editSession.setBlock(position.add(0, 1, width), new BaseBlock(0))) {
            ++affected;
        }
        if (editSession.setBlock(position.add(0, 1, width), new BaseBlock(64))) {
            ++affected;
        }
        editSession.coordinates = position;
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
        while(editSession.getWorld().getBlock(position).getId() == 0){
            nowY--;
            position = new Vector(position.getBlockX(), nowY, position.getZ());
        }
        //Four columns in the corners
        for (int i = 1; i < 6; i++) {
            affected += generateFourDims(position, block, length, i, width);
        }
        editSession.coordinates = position;
        coordinates = position;

        return affected;
    }

    //This command generates four blocks in one plane around the center
    private int generateFourDims(Vector position, Pattern block, int length, int height, int width) throws MaxChangedBlocksException {
        int affected = 0;
        if (editSession.setBlock(position.add(length, height, width), block)) {
            ++affected;
        }
        if (editSession.setBlock(position.add(length, height, -width), block)) {
            ++affected;
        }
        if (editSession.setBlock(position.add(-length, height, width), block)) {
            ++affected;
        }
        if (editSession.setBlock(position.add(-length, height, -width), block)) {
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
        while(editSession.getWorld().getBlock(position).getId() == 0){
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
        editSession.coordinates = position;
        return affected;
    }
}

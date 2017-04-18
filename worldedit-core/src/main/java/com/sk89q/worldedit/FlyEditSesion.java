package com.sk89q.worldedit;

import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.internal.expression.ExpressionException;
import com.sk89q.worldedit.patterns.Pattern;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.Countable;
import com.sk89q.worldedit.util.TreeGenerator;

import java.util.List;
import java.util.Set;

/**
 * Created by kouakam on 18.04.2017.
 */
public interface FlyEditSesion
{
    /**
     * Returns the highest solid 'terrain' block which can occur naturally.
     *
     * @param x           the X coordinate
     * @param z           the Z coordinate
     * @param minY        minimal height
     * @param maxY        maximal height
     * @param naturalOnly look at natural blocks or all blocks
     * @return height of highest block found or 'minY'
     */
     int getHighestTerrainBlock(int x, int z, int minY, int maxY, boolean naturalOnly);
    /**
     * Set a block, bypassing both history and block re-ordering.
     *
     * @param position the position to set the block at
     * @param block    the block
     * @param stage    the level
     * @return whether the block changed
     * @throws WorldEditException thrown on a set error
     */
    boolean setBlock(Vector position, BaseBlock block, EditSession.Stage stage) throws WorldEditException;
    /**
     * Set a block, bypassing both history and block re-ordering.
     *
     * @param position the position to set the block at
     * @param block    the block
     * @return whether the block changed
     */
     boolean rawSetBlock(Vector position, BaseBlock block);
    /**
     * Set a block, bypassing history but still utilizing block re-ordering.
     *
     * @param position the position to set the block at
     * @param block    the block
     * @return whether the block changed
     */
     boolean smartSetBlock(Vector position, BaseBlock block);
    /**
     * Count the number of blocks of a given list of types in a region.
     *
     * @param region    the region
     * @param searchIDs a list of IDs to search
     * @return the number of found blocks
     */
     int countBlock(Region region, Set<Integer> searchIDs);

    /**
     * Fills an area recursively in the X/Z directions.
     *
     * @param origin    the location to start from
     * @param radius    the radius of the spherical area to fill
     * @param depth     the maximum depth, starting from the origin
     * @param recursive whether a breadth-first search should be performed
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    @SuppressWarnings("deprecation")
    int fillXZ(Vector origin, Pattern pattern, double radius, int depth, boolean recursive)
            throws MaxChangedBlocksException;
    /**
     * Stack a cuboid region.
     *
     * @param region  the region to stack
     * @param dir     the direction to stack
     * @param count   the number of times to stack
     * @param copyAir true to also copy air blocks
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
     int stackCuboidRegion(Region region, Vector dir, int count, boolean copyAir) throws MaxChangedBlocksException;
    /**
     * Move the blocks in a region a certain direction.
     *
     * @param region      the region to move
     * @param dir         the direction
     * @param distance    the distance to move
     * @param copyAir     true to copy air blocks
     * @param replacement the replacement block to fill in after moving, or null to use air
     * @return number of blocks moved
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    int moveRegion(Region region, Vector dir, int distance, boolean copyAir, BaseBlock replacement) throws MaxChangedBlocksException;

    /**
     * Drain nearby pools of water or lava.
     *
     * @param origin the origin to drain from, which will search a 3x3 area
     * @param radius the radius of the removal, where a value should be 0 or greater
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
     int drainArea(Vector origin, double radius) throws MaxChangedBlocksException;
    /**
     * Fix liquids so that they turn into stationary blocks and extend outward.
     *
     * @param origin     the original position
     * @param radius     the radius to fix
     * @param moving     the block ID of the moving liquid
     * @param stationary the block ID of the stationary liquid
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
     int fixLiquid(Vector origin, double radius, int moving, int stationary) throws MaxChangedBlocksException;

    /**
     * Makes a cylinder.
     *
     * @param pos     Center of the cylinder
     * @param block   The block pattern to use
     * @param radiusX The cylinder's largest north/south extent
     * @param radiusZ The cylinder's largest east/west extent
     * @param height  The cylinder's up/down extent. If negative, extend downward.
     * @param filled  If false, only a shell will be generated.
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
     int makeCylinder(Vector pos, Pattern block, double radiusX, double radiusZ, int height, boolean filled) throws MaxChangedBlocksException;
    /**
     * Makes a sphere or ellipsoid.
     *
     * @param pos     Center of the sphere or ellipsoid
     * @param block   The block pattern to use
     * @param radiusX The sphere/ellipsoid's largest north/south extent
     * @param radiusY The sphere/ellipsoid's largest up/down extent
     * @param radiusZ The sphere/ellipsoid's largest east/west extent
     * @param filled  If false, only a shell will be generated.
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    int makeSphere(Vector pos, Pattern block, double radiusX, double radiusY, double radiusZ, boolean filled) throws MaxChangedBlocksException;
    /**
     * Makes a pyramid.
     *
     * @param position a position
     * @param block    a block
     * @param size     size of pyramid
     * @param filled   true if filled
     * @return number of blocks changed
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
    int makePyramid(Vector position, Pattern block, int size, boolean filled) throws MaxChangedBlocksException;
    /**
     * Thaw blocks in a radius.
     *
     * @param position the position
     * @param radius   the radius
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
     int thaw(Vector position, double radius)
            throws MaxChangedBlocksException;
    /**
     * Make dirt green.
     *
     * @param position       a position
     * @param radius         a radius
     * @param onlyNormalDirt only affect normal dirt (data value 0)
     * @return number of blocks affected
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
     int green(Vector position, double radius, boolean onlyNormalDirt)
            throws MaxChangedBlocksException;
    /**
     * Makes pumpkin patches randomly in an area around the given position.
     *
     * @param position the base position
     * @param apothem  the apothem of the (square) area
     * @return number of patches created
     * @throws MaxChangedBlocksException thrown if too many blocks are changed
     */
     int makePumpkinPatches(Vector position, int apothem) throws MaxChangedBlocksException;
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
    int makeForest(Vector basePosition, int size, double density, TreeGenerator treeGenerator) throws MaxChangedBlocksException;
    /**
     * Get the block distribution inside a region.
     *
     * @param region a region
     * @return the results
     */
     List<Countable<Integer>> getBlockDistribution(Region region);


    int removeAbove(Vector position, int apothem, int height) throws MaxChangedBlocksException;

    int removeBelow(Vector position, int apothem, int height) throws MaxChangedBlocksException;

    int removeNear(Vector position, int blockType, int apothem) throws MaxChangedBlocksException;

    int makeCuboidFaces(Region region, Pattern pattern) throws MaxChangedBlocksException;

    int makeFaces(final Region region, Pattern pattern) throws MaxChangedBlocksException;

    int makeCuboidWalls(Region region, Pattern pattern) throws MaxChangedBlocksException;

    int makeWalls(final Region region, Pattern pattern) throws MaxChangedBlocksException;

    int overlayCuboidBlocks(Region region, Pattern pattern) throws MaxChangedBlocksException;

    int naturalizeCuboidBlocks(Region region) throws MaxChangedBlocksException;

    int moveCuboidRegion(Region region, Vector dir, int distance, boolean copyAir, BaseBlock replacement) throws MaxChangedBlocksException;

    int simulateSnow(Vector position, double radius) throws MaxChangedBlocksException;

    List<Countable<BaseBlock>> getBlockDistributionWithData(Region region);

    int drawLine(Pattern pattern, Vector pos1, Vector pos2, double radius, boolean filled)
            throws MaxChangedBlocksException;

    int drawSpline(Pattern pattern, List<Vector> nodevectors, double tension, double bias, double continuity, double quality, double radius, boolean filled)
            throws MaxChangedBlocksException;

    int hollowOutRegion(Region region, int thickness, Pattern pattern) throws MaxChangedBlocksException;

    int makeShape(final Region region, final Vector zero, final Vector unit, final Pattern pattern, final String expressionString, final boolean hollow)
            throws ExpressionException, MaxChangedBlocksException;

    int deformRegion(final Region region, final Vector zero, final Vector unit, final String expressionString)
            throws ExpressionException, MaxChangedBlocksException;


     //Our Feature 1 functions
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
    int makeHouseFloor(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException;

    int makeHouseRoof(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException;

    int makeHouseWalls(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException;

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
    int makeHouseCarcass(Vector position, Pattern block, int length, int width) throws MaxChangedBlocksException;
}

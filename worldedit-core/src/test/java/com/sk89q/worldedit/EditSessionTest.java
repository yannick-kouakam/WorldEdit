package com.sk89q.worldedit;

import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BaseItemStack;
import com.sk89q.worldedit.entity.BaseEntity;
import com.sk89q.worldedit.entity.Entity;
import com.sk89q.worldedit.regions.Region;
import com.sk89q.worldedit.util.*;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldedit.world.registry.WorldData;
import org.junit.Test;

import javax.annotation.Nullable;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Created by bm on 19/04/2017.
 */
public class EditSessionTest {
    @Test
    public void isQueueEnabled() throws Exception {
        EditSession es = new EditSession(new LocalWorld() {
            @Override
            public String getName() {
                return null;
            }

            @Override
            public boolean setBlock(Vector position, BaseBlock block, boolean notifyAndLight) throws WorldEditException {
                return false;
            }

            @Override
            public int getBlockLightLevel(Vector position) {
                return 0;
            }

            @Override
            public boolean clearContainerBlockContents(Vector position) {
                return false;
            }

            @Override
            public void dropItem(Vector position, BaseItemStack item) {

            }

            @Override
            public boolean regenerate(Region region, EditSession editSession) {
                return false;
            }

            @Override
            public WorldData getWorldData() {
                return null;
            }

            @Override
            public List<? extends Entity> getEntities(Region region) {
                return null;
            }

            @Override
            public List<? extends Entity> getEntities() {
                return null;
            }

            @Nullable
            @Override
            public Entity createEntity(Location location, BaseEntity entity) {
                return null;
            }

            @Override
            public BaseBlock getBlock(Vector position) {
                return null;
            }
        }, 5);

        assertFalse(es.isQueueEnabled());


    }

}
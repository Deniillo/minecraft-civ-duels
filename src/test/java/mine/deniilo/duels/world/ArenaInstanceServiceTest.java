package mine.deniilo.duels.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArenaInstanceServiceTest {

    @TempDir
    Path tmp;

    @Test
    void copyKeepsMapButNotWorldIdentity() throws IOException {
        Path source = tmp.resolve("duels_arena");
        touch(source.resolve("region/r.0.0.mca"));
        touch(source.resolve("data/minecraft/game_rules.dat"));
        touch(source.resolve("data/paper/metadata.dat"));
        touch(source.resolve("data/paper/level_overrides.dat"));
        touch(source.resolve("session.lock"));
        touch(source.resolve("playerdata/x.dat"));

        Path target = tmp.resolve("duels_arena_abcd1234");
        ArenaInstanceService.copyWorld(source, target);

        assertTrue(Files.isRegularFile(target.resolve("region/r.0.0.mca")), "карта скопирована");
        assertTrue(Files.isRegularFile(target.resolve("data/minecraft/game_rules.dat")));
        assertTrue(Files.isRegularFile(target.resolve("data/paper/level_overrides.dat")));
        assertFalse(Files.exists(target.resolve("data/paper/metadata.dat")),
                "с метаданными шаблона Paper не загрузит копию (дубликат мира)");
        assertFalse(Files.exists(target.resolve("session.lock")));
        assertFalse(Files.exists(target.resolve("playerdata")));
    }

    @Test
    void bigMapCopiesOnlyRegionsAroundSpawns() throws IOException {
        Path source = tmp.resolve("dkp");
        for (String dir : new String[] {"region", "entities", "poi"}) {
            touch(source.resolve(dir + "/r.-1.19.mca"));
            touch(source.resolve(dir + "/r.0.19.mca"));
            touch(source.resolve(dir + "/r.0.0.mca"));
            touch(source.resolve(dir + "/r.-2.47.mca"));
        }
        touch(source.resolve("data/minecraft/chunk_tickets.dat"));
        int[] box = {-54 - 192, -24 + 192, 9940 - 192, 9940 + 192};

        Path target = tmp.resolve("copy");
        ArenaInstanceService.copyWorld(source, target, box);

        for (String dir : new String[] {"region", "entities", "poi"}) {
            assertTrue(Files.exists(target.resolve(dir + "/r.-1.19.mca")), dir);
            assertTrue(Files.exists(target.resolve(dir + "/r.0.19.mca")), dir);
            assertFalse(Files.exists(target.resolve(dir + "/r.0.0.mca")), dir);
            assertFalse(Files.exists(target.resolve(dir + "/r.-2.47.mca")), dir);
        }
        assertFalse(Files.exists(target.resolve("data/minecraft/chunk_tickets.dat")),
                "442 принудительных чанка карты не грузятся в каждой копии");
    }

    @Test
    void regionBoundsAreInclusive() {
        int[] box = {0, 0, 511, 511};
        assertTrue(ArenaInstanceService.regionNeeded("r.0.0.mca", box));
        assertFalse(ArenaInstanceService.regionNeeded("r.-1.0.mca", box));
        assertFalse(ArenaInstanceService.regionNeeded("r.0.1.mca", box));
        assertTrue(ArenaInstanceService.regionNeeded("r.0.0.mca", null), "без рамки копируется всё");
        assertTrue(ArenaInstanceService.regionNeeded("level.dat", box));
    }

    @Test
    void floatingSpawnLandsOnFloor() {
        World world = world(y -> y <= 2);
        Location l = ArenaInstanceService.onGround(new Location(world, -53.5, 65, 9940.5, -90, 0));
        assertEquals(3.0, l.getY());
        assertEquals(-53.5, l.getX());
        assertEquals(-90f, l.getYaw());
        Location standing = ArenaInstanceService.onGround(new Location(world, -53.5, 3, 9940.5));
        assertEquals(3.0, standing.getY(), "точка на полу не двигается");
    }

    @Test
    void spawnOverVoidIsRejected() {
        assertNull(ArenaInstanceService.onGround(new Location(world(y -> false), 0.5, 65, 0.5)));
    }

    private static World world(java.util.function.IntPredicate solidAt) {
        World world = mock(World.class);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenAnswer(inv -> {
            int y = inv.getArgument(1);
            boolean solid = solidAt.test(y);
            Block block = mock(Block.class);
            when(block.isSolid()).thenReturn(solid);
            when(block.isPassable()).thenReturn(!solid);
            return block;
        });
        return world;
    }

    private static void touch(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, "x");
    }
}

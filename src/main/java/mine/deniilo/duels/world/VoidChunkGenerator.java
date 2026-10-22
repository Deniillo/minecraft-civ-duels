package mine.deniilo.duels.world;

import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;

public final class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, WorldService.PLATFORM_Y + 1, 0.5);
    }
}

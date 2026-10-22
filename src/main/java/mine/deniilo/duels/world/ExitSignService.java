package mine.deniilo.duels.world;

import mine.deniilo.duels.match.MatchManager;
import mine.deniilo.duels.message.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class ExitSignService implements Listener {

    public static final String MARK = "[Выход]";

    private final MatchManager matches;
    private final SpawnStore spawns;
    private final Messages messages;

    public ExitSignService(MatchManager matches, SpawnStore spawns, Messages messages) {
        this.matches = matches;
        this.spawns = spawns;
        this.messages = messages;
    }

    public static void place(Location at, BlockFace facing) {
        Block block = at.getBlock();
        block.setType(Material.OAK_SIGN, false);
        if (block.getBlockData() instanceof org.bukkit.block.data.type.Sign data) {
            data.setRotation(facing);
            block.setBlockData(data, false);
        }
        if (block.getState() instanceof Sign sign) {
            sign.getSide(Side.FRONT).line(0, Component.text(MARK, NamedTextColor.DARK_RED));
            sign.getSide(Side.FRONT).line(1, Component.text("ПКМ: вернуться"));
            sign.getSide(Side.FRONT).line(2, Component.text("в лобби"));
            sign.setWaxed(true);
            sign.update(true, false);
        }
    }

    @EventHandler
    public void onClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || !(event.getClickedBlock().getState() instanceof Sign sign)) {
            return;
        }
        String first = PlainTextComponentSerializer.plainText().serialize(sign.getSide(Side.FRONT).line(0));
        if (!first.equals(MARK) || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (matches.leave(player)) {
            return;
        }
        Location lobby = spawns.resolve(SpawnStore.LOBBY);
        if (lobby == null) {
            messages.send(player, "world-missing", "world", "lobby");
        } else {
            player.teleport(lobby);
            messages.send(player, "to-lobby");
        }
    }
}

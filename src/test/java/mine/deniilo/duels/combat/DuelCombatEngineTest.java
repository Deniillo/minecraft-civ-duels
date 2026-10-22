package mine.deniilo.duels.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.base.Function;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;
import mine.deniilo.duels.item.ItemReader;
import mine.deniilo.duels.kit.KitService;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.ArmorClass;
import mine.deniilo.duels.profile.ArmorPiece;
import mine.deniilo.duels.profile.ArmorSetCalculator;
import mine.deniilo.duels.profile.ArmorSetStats;
import mine.deniilo.duels.profile.ArmorSlot;
import mine.deniilo.duels.profile.ItemCatalog;
import mine.deniilo.duels.profile.WeaponProfile;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
class DuelCombatEngineTest {

    private static final double EPS = 1e-9;
    private static final UUID SESSION = UUID.randomUUID();
    private static ItemCatalog items;

    @BeforeAll
    static void setUp() throws Exception {
        if (Bukkit.getServer() == null) {
            Server server = mock(Server.class);
            when(server.getLogger()).thenReturn(Logger.getLogger("test"));
            when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
            // Bukkit.setServer печатает версию сборки, которой без настоящего сервера нет
            Field field = Bukkit.class.getDeclaredField("server");
            field.setAccessible(true);
            field.set(null, server);
        }
        try (InputStream in = DuelCombatEngineTest.class.getResourceAsStream("/items.yml")) {
            items = ItemCatalog.from(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
    }

    @Test
    void bowT4VsSoulT5() {
        assertEquals(3.5, arrowHit(0, armor(ArmorClass.LIGHT, 1, 0), armor(ArmorClass.SOUL, 5, 0), 1.0f), EPS);
    }

    @Test
    void bowT4VsLightT4() {
        assertEquals(10, arrowHit(0, armor(ArmorClass.LIGHT, 4, 0), armor(ArmorClass.LIGHT, 4, 0), 1.0f), EPS);
    }

    @Test
    void bowT4Plus7VsLightT4Protection5() {
        assertEquals(12, arrowHit(7, armor(ArmorClass.LIGHT, 4, 5), armor(ArmorClass.LIGHT, 4, 5), 1.0f), EPS);
    }

    @Test
    void halfDrawnBowVsLightT4() {
        assertEquals(0, arrowHit(0, armor(ArmorClass.LIGHT, 4, 0), armor(ArmorClass.LIGHT, 4, 0), 0.5f), EPS,
                "30 × 0,5 = 15 меньше D 20");
    }

    @Test
    void unarmoredTargetTakesFullBow() {
        assertEquals(30, arrowHit(0, armor(ArmorClass.LIGHT, 4, 0), ArmorSetCalculator.compute(List.of()), 1.0f), EPS);
    }

    private static ArmorSetStats armor(ArmorClass armorClass, int tier, int protection) {
        List<ArmorPiece> pieces = new ArrayList<>();
        for (ArmorSlot slot : ArmorSlot.values()) {
            pieces.add(new ArmorPiece(items.armor(armorClass, tier, slot).orElseThrow(), protection));
        }
        return ArmorSetCalculator.compute(pieces);
    }

    private static double arrowHit(int attackLevel, ArmorSetStats shooterArmor, ArmorSetStats victimArmor, float force) {
        WeaponProfile bow = items.weapon("bow_t4_marksman").orElseThrow();
        ItemStack bowItem = mock(ItemStack.class);
        ItemReader reader = mock(ItemReader.class);
        when(reader.weapon(bowItem, items)).thenReturn(Optional.of(bow));
        when(reader.attackLevel(bowItem)).thenReturn(attackLevel);

        Player shooter = player();
        Player victim = player();
        KitService kits = mock(KitService.class);
        when(kits.stats(shooter)).thenReturn(shooterArmor);
        when(kits.stats(victim)).thenReturn(victimArmor);

        Arrow arrow = mock(Arrow.class);
        when(arrow.getUniqueId()).thenReturn(UUID.randomUUID());
        when(arrow.getShooter()).thenReturn(shooter);

        CombatRegistry registry = new CombatRegistry();
        registry.join(shooter.getUniqueId(), SESSION);
        registry.join(victim.getUniqueId(), SESSION);
        registry.setActive(SESSION, true);

        Plugin plugin = mock(Plugin.class);
        when(Bukkit.getScheduler().runTask(any(Plugin.class), any(Runnable.class))).thenReturn(null);
        when(Bukkit.getScheduler().runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenReturn(null);
        DuelCombatEngine engine = new DuelCombatEngine(plugin, items, reader, kits, registry,
                mock(CombatDebug.class), mock(DummyService.class), mock(Messages.class));

        EntityShootBowEvent shot = new EntityShootBowEvent(shooter, bowItem, mock(ItemStack.class), arrow,
                EquipmentSlot.HAND, force, true);
        engine.onShoot(shot);
        assertFalse(shot.isCancelled(), "лук в LIGHT разрешён");

        Map<DamageModifier, Double> modifiers = new EnumMap<>(DamageModifier.class);
        modifiers.put(DamageModifier.BASE, 9.0);
        modifiers.put(DamageModifier.ARMOR, -4.0);
        modifiers.put(DamageModifier.BLOCKING, 0.0);
        Map<DamageModifier, Function<Double, Double>> functions = new EnumMap<>(DamageModifier.class);
        for (DamageModifier m : modifiers.keySet()) {
            functions.put(m, d -> 0.0);
        }
        EntityDamageByEntityEvent hit = new EntityDamageByEntityEvent(arrow, victim, DamageCause.PROJECTILE,
                mock(DamageSource.class), modifiers, functions, false);
        engine.onDamageByEntity(hit);
        assertFalse(hit.isCancelled());
        assertEquals(0.0, hit.getDamage(DamageModifier.ARMOR), EPS, "ванильная броня не вычитается второй раз");
        return hit.getFinalDamage();
    }

    private static Player player() {
        Player p = mock(Player.class);
        when(p.getUniqueId()).thenReturn(UUID.randomUUID());
        return p;
    }
}

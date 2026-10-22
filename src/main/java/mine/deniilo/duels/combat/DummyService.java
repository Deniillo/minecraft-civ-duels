package mine.deniilo.duels.combat;

import java.util.Map;
import java.util.UUID;
import mine.deniilo.duels.kit.KitService;
import mine.deniilo.duels.profile.ArmorSlot;
import mine.deniilo.duels.profile.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class DummyService {

    public record BotWeapon(mine.deniilo.duels.profile.WeaponProfile profile, int attackLevel, int piercingLevel) {
    }

    private final java.util.Map<UUID, BotWeapon> botWeapons = new java.util.HashMap<>();
    private final Plugin plugin;
    private final KitService kits;
    private final CombatRegistry registry;

    public DummyService(Plugin plugin, KitService kits, CombatRegistry registry) {
        this.plugin = plugin;
        this.kits = kits;
        this.registry = registry;
    }

    public boolean canSpawnIn(World world) {
        return world.getDifficulty() != Difficulty.PEACEFUL;
    }

    public void spawn(Player admin, Kit kit) {
        Location base = admin.getLocation();
        Location at = base.clone().add(base.getDirection().setY(0).normalize().multiply(3));
        Map<ArmorSlot, ItemStack> armor = kits.armorItems(kit, CombatRegistry.ADMIN_TEST);
        Zombie dummy = base.getWorld().spawn(at, Zombie.class, z -> {
            z.setAI(false);
            z.setSilent(true);
            z.setAdult();
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.setCanPickupItems(false);
            z.customName(Component.text("Мишень: " + kit.displayName(), NamedTextColor.YELLOW));
            z.setCustomNameVisible(true);
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));
            EntityEquipment eq = z.getEquipment();
            eq.setHelmet(armor.get(ArmorSlot.HELMET));
            eq.setChestplate(armor.get(ArmorSlot.CHESTPLATE));
            eq.setLeggings(armor.get(ArmorSlot.LEGGINGS));
            eq.setBoots(armor.get(ArmorSlot.BOOTS));
            eq.setItemInMainHand(new ItemStack(org.bukkit.Material.AIR));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
        });
        registry.addDummy(dummy.getUniqueId());
        Bukkit.getScheduler().runTask(plugin, () -> healFull(dummy));
    }

    public Zombie spawnSparring(Location at, Kit kit, UUID session) {
        Map<ArmorSlot, ItemStack> armor = kits.armorItems(kit, session);
        Zombie bot = at.getWorld().spawn(at, Zombie.class, z -> {
            z.setAI(false);
            z.setSilent(true);
            z.setAdult();
            z.setPersistent(false);
            z.setRemoveWhenFarAway(false);
            z.setCanPickupItems(false);
            z.customName(Component.text("Мишень-соперник", NamedTextColor.RED));
            z.setCustomNameVisible(true);
            z.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, PotionEffect.INFINITE_DURATION, 0, true, false));
            EntityEquipment eq = z.getEquipment();
            eq.setHelmet(armor.get(ArmorSlot.HELMET));
            eq.setChestplate(armor.get(ArmorSlot.CHESTPLATE));
            eq.setLeggings(armor.get(ArmorSlot.LEGGINGS));
            eq.setBoots(armor.get(ArmorSlot.BOOTS));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            z.setShouldBurnInDay(false);
            kits.primaryMelee(kit).ifPresent(w -> {
                eq.setItemInMainHand(kits.weaponStack(w, kit, session));
                eq.setItemInMainHandDropChance(0f);
            });
        });
        kits.primaryMelee(kit).ifPresent(w ->
                botWeapons.put(bot.getUniqueId(), new BotWeapon(w, kit.attackLevel(), kit.piercingLevel())));
        Bukkit.getScheduler().runTask(plugin, () -> healFull(bot));
        return bot;
    }

    public BotWeapon botWeapon(UUID id) {
        return botWeapons.get(id);
    }

    public void forgetBot(UUID id) {
        botWeapons.remove(id);
    }

    public int clear() {
        int n = 0;
        for (UUID id : registry.dummies()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.remove();
                n++;
            }
            registry.leave(id);
        }
        return n;
    }

    public void healFull(Entity entity) {
        if (entity instanceof org.bukkit.entity.LivingEntity living && living.isValid()) {
            AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
            if (max != null) {
                living.setHealth(max.getValue());
            }
        }
    }
}

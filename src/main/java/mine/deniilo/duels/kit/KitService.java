package mine.deniilo.duels.kit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import mine.deniilo.duels.item.ItemFactory;
import mine.deniilo.duels.item.ItemReader;
import mine.deniilo.duels.profile.ArmorPiece;
import mine.deniilo.duels.profile.ArmorProfile;
import mine.deniilo.duels.profile.ArmorSetCalculator;
import mine.deniilo.duels.profile.ArmorSetStats;
import mine.deniilo.duels.profile.ArmorSlot;
import mine.deniilo.duels.profile.ItemCatalog;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.WeaponProfile;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class KitService {

    private final Plugin plugin;
    private final ItemCatalog items;
    private final ItemFactory factory;
    private final ItemReader reader;
    private final Set<UUID> effectsApplied = new HashSet<>();

    public KitService(Plugin plugin, ItemCatalog items, ItemFactory factory, ItemReader reader) {
        this.plugin = plugin;
        this.items = items;
        this.factory = factory;
        this.reader = reader;
    }

    public boolean give(Player player, Kit kit, UUID sessionId) {
        List<ArmorPiece> pieces = new ArrayList<>();
        for (ArmorSlot slot : ArmorSlot.values()) {
            Optional<ArmorProfile> profile = items.armor(kit.armorClass(), kit.armorTier(), slot);
            if (profile.isEmpty()) {
                return false;
            }
            pieces.add(new ArmorPiece(profile.get(), kit.protectionLevel()));
        }
        List<ItemStack> weapons = new ArrayList<>();
        for (String id : kit.weaponIds()) {
            Optional<WeaponProfile> w = items.weapon(id);
            if (w.isEmpty()) {
                return false;
            }
            weapons.add(factory.weapon(w.get(), kit.attackLevel(), kit.piercingLevel(), sessionId));
        }

        clear(player);
        PlayerInventory inv = player.getInventory();
        for (ArmorPiece p : pieces) {
            ItemStack stack = factory.armor(p, sessionId);
            switch (p.profile().slot()) {
                case HELMET -> inv.setHelmet(stack);
                case CHESTPLATE -> inv.setChestplate(stack);
                case LEGGINGS -> inv.setLeggings(stack);
                case BOOTS -> inv.setBoots(stack);
            }
        }
        inv.setItemInOffHand(factory.shield(kit.armorClass(), kit.armorTier(), sessionId));
        for (int i = 0; i < weapons.size() && i < 9; i++) {
            inv.setItem(i, weapons.get(i));
        }
        if (kit.weaponIds().stream().anyMatch(id -> id.startsWith("bow") || id.startsWith("crossbow"))) {
            inv.setItem(Math.min(weapons.size(), 8), new ItemStack(org.bukkit.Material.ARROW, 64));
        }
        refreshEffects(player);

        // Атрибуты экипировки применяются не сразу, поэтому максимум здоровья растёт позже, чем выдан набор.
        for (long delay : new long[] {1L, 5L, 20L}) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !player.isDead()) {
                    refreshEffects(player);
                    fillHealth(player);
                }
            }, delay);
        }
        return true;
    }

    public Optional<WeaponProfile> primaryMelee(Kit kit) {
        for (String id : kit.weaponIds()) {
            Optional<WeaponProfile> w = items.weapon(id);
            if (w.isPresent() && w.get().kind().isMelee()) {
                return w;
            }
        }
        return Optional.empty();
    }

    public ItemStack weaponStack(WeaponProfile profile, Kit kit, UUID sessionId) {
        return factory.weapon(profile, kit.attackLevel(), kit.piercingLevel(), sessionId);
    }

    public void fillHealth(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        if (max != null) {
            player.setHealth(max.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20f);
    }

    public void clear(Player player) {
        PlayerInventory inv = player.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        removeEffects(player);
    }

    public List<ArmorPiece> wornArmor(LivingEntity entity) {
        List<ArmorPiece> out = new ArrayList<>();
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return out;
        }
        for (ItemStack stack : equipment.getArmorContents()) {
            reader.armorPiece(stack, items).ifPresent(out::add);
        }
        return out;
    }

    public ArmorSetStats stats(LivingEntity entity) {
        return ArmorSetCalculator.compute(wornArmor(entity));
    }

    public Map<ArmorSlot, ItemStack> armorItems(Kit kit, UUID sessionId) {
        Map<ArmorSlot, ItemStack> out = new EnumMap<>(ArmorSlot.class);
        for (ArmorSlot slot : ArmorSlot.values()) {
            ArmorProfile profile = items.armor(kit.armorClass(), kit.armorTier(), slot).orElseThrow();
            out.put(slot, factory.armor(new ArmorPiece(profile, kit.protectionLevel()), sessionId));
        }
        return out;
    }

    public void refreshEffects(Player player) {
        if (stats(player).soulSet()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                    PotionEffect.INFINITE_DURATION, 0, true, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,
                    PotionEffect.INFINITE_DURATION, 0, true, false, true));
            effectsApplied.add(player.getUniqueId());
        } else {
            removeEffects(player);
        }
    }

    private void removeEffects(Player player) {
        if (effectsApplied.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.REGENERATION);
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    public void forget(Player player) {
        effectsApplied.remove(player.getUniqueId());
    }
}

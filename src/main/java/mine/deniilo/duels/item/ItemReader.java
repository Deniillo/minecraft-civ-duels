package mine.deniilo.duels.item;

import java.util.Optional;
import mine.deniilo.duels.profile.ArmorPiece;
import mine.deniilo.duels.profile.ArmorProfile;
import mine.deniilo.duels.profile.ItemCatalog;
import mine.deniilo.duels.profile.Kit;
import mine.deniilo.duels.profile.WeaponProfile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemReader {

    private final ItemKeys keys;

    public ItemReader(ItemKeys keys) {
        this.keys = keys;
    }

    public Optional<String> itemId(ItemStack stack) {
        PersistentDataContainer pdc = pdc(stack);
        if (pdc == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(pdc.get(keys.itemId, PersistentDataType.STRING));
    }

    public Optional<ArmorPiece> armorPiece(ItemStack stack, ItemCatalog items) {
        PersistentDataContainer pdc = pdc(stack);
        if (pdc == null) {
            return Optional.empty();
        }
        String id = pdc.get(keys.itemId, PersistentDataType.STRING);
        if (id == null) {
            return Optional.empty();
        }
        Optional<ArmorProfile> profile = items.armor(id);
        if (profile.isEmpty()) {
            return Optional.empty();
        }
        int level = pdc.getOrDefault(keys.protectionLevel, PersistentDataType.INTEGER, 0);
        if (level < 0 || level > Kit.MAX_PROTECTION_LEVEL) {
            return Optional.empty();
        }
        return Optional.of(new ArmorPiece(profile.get(), level));
    }

    public Optional<WeaponProfile> weapon(ItemStack stack, ItemCatalog items) {
        return itemId(stack).flatMap(items::weapon);
    }

    public Optional<java.util.UUID> sessionId(ItemStack stack) {
        PersistentDataContainer pdc = pdc(stack);
        if (pdc == null) {
            return Optional.empty();
        }
        String raw = pdc.get(keys.sessionId, PersistentDataType.STRING);
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(java.util.UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public int attackLevel(ItemStack stack) {
        return clamp(read(stack, keys.attackLevel), Kit.MAX_ATTACK_LEVEL);
    }

    public int piercingLevel(ItemStack stack) {
        return clamp(read(stack, keys.piercingLevel), Kit.MAX_PIERCING_LEVEL);
    }

    private int read(ItemStack stack, org.bukkit.NamespacedKey key) {
        PersistentDataContainer pdc = pdc(stack);
        return pdc == null ? 0 : pdc.getOrDefault(key, PersistentDataType.INTEGER, 0);
    }

    private static int clamp(int v, int max) {
        return Math.max(0, Math.min(max, v));
    }

    private static PersistentDataContainer pdc(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }
}

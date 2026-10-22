package mine.deniilo.duels.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import mine.deniilo.duels.profile.ArmorClass;
import mine.deniilo.duels.profile.ArmorPiece;
import mine.deniilo.duels.profile.ArmorProfile;
import mine.deniilo.duels.profile.ArmorSlot;
import mine.deniilo.duels.profile.WeaponKind;
import mine.deniilo.duels.profile.WeaponProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class ItemFactory {

    private final ItemKeys keys;

    public ItemFactory(ItemKeys keys) {
        this.keys = keys;
    }

    public ItemStack weapon(WeaponProfile profile, int attackLevel, int piercingLevel, UUID sessionId) {
        ItemStack stack = new ItemStack(material(profile.material()));
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(line(weaponName(profile), NamedTextColor.GOLD));
        meta.setUnbreakable(true);

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(
                keys.modifier("weapon_damage"), 0.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        double speedDelta = profile.kind().attackSpeedDelta();
        if (speedDelta != 0.0) {
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(
                    keys.modifier("weapon_speed"), speedDelta, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        if (profile.kind() == WeaponKind.TRIDENT) {
            meta.addEnchant(Enchantment.LOYALTY, 3, true);
        }

        if (profile.kind() == WeaponKind.CROSSBOW && meta instanceof org.bukkit.inventory.meta.CrossbowMeta crossbow) {
            crossbow.addChargedProjectile(new ItemStack(Material.ARROW));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(line("Атака: " + num(profile.baseAttack()) + (attackLevel > 0 ? " (+" + attackLevel + " заточка)" : ""), NamedTextColor.GRAY));
        if (profile.kind() == WeaponKind.TRIDENT) {
            lore.add(line("Бросок: основа + 5 + заточка", NamedTextColor.GRAY));
        }
        if (profile.kind().isMelee() && piercingLevel > 0) {
            lore.add(line("Пробитие: +" + piercingLevel + " к удару в ближнем бою", NamedTextColor.GRAY));
        }
        lore.add(line("Временный предмет матча", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.itemId, PersistentDataType.STRING, profile.id());
        pdc.set(keys.attackLevel, PersistentDataType.INTEGER, attackLevel);
        pdc.set(keys.piercingLevel, PersistentDataType.INTEGER, piercingLevel);
        pdc.set(keys.sessionId, PersistentDataType.STRING, sessionId.toString());
        pdc.set(keys.schemaVersion, PersistentDataType.INTEGER, ItemKeys.SCHEMA_VERSION);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack shield(ArmorClass armorClass, int tier, UUID sessionId) {
        ItemStack stack = new ItemStack(Material.SHIELD);
        ItemMeta meta = stack.getItemMeta();
        String name = switch (armorClass) {
            case LIGHT -> "Лёгкий щит";
            case HEAVY -> "Тяжёлый щит";
            case SOUL -> "Щит душ";
        };
        meta.displayName(line(name + " T" + tier, NamedTextColor.AQUA));
        meta.setUnbreakable(true);
        if (meta instanceof org.bukkit.inventory.meta.BlockStateMeta state
                && state.getBlockState() instanceof org.bukkit.block.Banner banner) {
            banner.setBaseColor(switch (armorClass) {
                case LIGHT -> org.bukkit.DyeColor.LIME;
                case HEAVY -> org.bukkit.DyeColor.GRAY;
                case SOUL -> org.bukkit.DyeColor.LIGHT_BLUE;
            });
            state.setBlockState(banner);
        }
        meta.lore(List.of(
                line("Держите ПКМ, чтобы блокировать удар", NamedTextColor.GRAY),
                line("Меч, секира и стрела отключают щит на 6 с", NamedTextColor.GRAY),
                line("Секира пробивает блок", NamedTextColor.GRAY),
                line("Временный предмет матча", NamedTextColor.DARK_GRAY)));
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.itemId, PersistentDataType.STRING, "shield_" + armorClass.name().toLowerCase(Locale.ROOT) + "_t" + tier);
        pdc.set(keys.sessionId, PersistentDataType.STRING, sessionId.toString());
        pdc.set(keys.schemaVersion, PersistentDataType.INTEGER, ItemKeys.SCHEMA_VERSION);
        stack.setItemMeta(meta);
        return stack;
    }

    public ItemStack armor(ArmorPiece piece, UUID sessionId) {
        ArmorProfile profile = piece.profile();
        ItemStack stack = new ItemStack(material(profile.materialName()));
        ItemMeta meta = stack.getItemMeta();

        meta.displayName(line(armorName(profile), NamedTextColor.AQUA));
        meta.setUnbreakable(true);
        if (piece.protectionLevel() > 0) {
            meta.setEnchantmentGlintOverride(true);
        }

        EquipmentSlotGroup group = group(profile.slot());
        String slot = profile.slot().id();
        meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(
                keys.modifier("armor_" + slot + "_armor"), piece.defense(), AttributeModifier.Operation.ADD_NUMBER, group));
        meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(
                keys.modifier("armor_" + slot + "_toughness"), 0.0, AttributeModifier.Operation.ADD_NUMBER, group));
        if (profile.hp() != 0.0) {
            meta.addAttributeModifier(Attribute.MAX_HEALTH, new AttributeModifier(
                    keys.modifier("armor_" + slot + "_health"), profile.hp(), AttributeModifier.Operation.ADD_NUMBER, group));
        }
        if (profile.speed() != 0.0) {
            meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, new AttributeModifier(
                    keys.modifier("armor_" + slot + "_speed"), profile.speed(), AttributeModifier.Operation.ADD_SCALAR, group));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(line("Защита: " + num(piece.defense())
                + (piece.protectionLevel() > 0 ? " (заточка +" + piece.protectionLevel() + ")" : ""), NamedTextColor.GRAY));
        lore.add(line("Здоровье: " + signed(profile.hp()) + " HP", NamedTextColor.GRAY));
        lore.add(line("Скорость: " + signed(profile.speed() * 100) + "%", NamedTextColor.GRAY));
        if (profile.armorClass() == ArmorClass.SOUL) {
            lore.add(line("Комплект из 4 вещей SOUL: Регенерация I и Скорость I", NamedTextColor.DARK_AQUA));
            if (profile.tier() == 5) {
                lore.add(line("Полный SOUL T5: урон больше 3,5 HP уменьшается вдвое", NamedTextColor.DARK_AQUA));
            }
        }
        lore.add(line("Временный предмет матча", NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(keys.itemId, PersistentDataType.STRING, profile.id());
        pdc.set(keys.protectionLevel, PersistentDataType.INTEGER, piece.protectionLevel());
        pdc.set(keys.sessionId, PersistentDataType.STRING, sessionId.toString());
        pdc.set(keys.schemaVersion, PersistentDataType.INTEGER, ItemKeys.SCHEMA_VERSION);
        stack.setItemMeta(meta);
        return stack;
    }

    private static Material material(String name) {
        Material m = Material.matchMaterial(name);
        if (m == null || !m.isItem()) {
            throw new IllegalArgumentException("Неизвестный материал в items.yml: " + name);
        }
        return m;
    }

    private static EquipmentSlotGroup group(ArmorSlot slot) {
        return switch (slot) {
            case HELMET -> EquipmentSlotGroup.HEAD;
            case CHESTPLATE -> EquipmentSlotGroup.CHEST;
            case LEGGINGS -> EquipmentSlotGroup.LEGS;
            case BOOTS -> EquipmentSlotGroup.FEET;
        };
    }

    private static String weaponName(WeaponProfile p) {
        String kind = switch (p.kind()) {
            case SWORD -> "Меч";
            case AXE -> "Секира";
            case TRIDENT -> "Трезубец";
            case BOW -> "Лук";
            case CROSSBOW -> "Арбалет";
        };
        return kind + " T" + p.tier();
    }

    private static String armorName(ArmorProfile p) {
        String cls = switch (p.armorClass()) {
            case LIGHT -> "Лёгкая";
            case HEAVY -> "Тяжёлая";
            case SOUL -> "Броня душ";
        };
        String part = switch (p.slot()) {
            case HELMET -> "шлем";
            case CHESTPLATE -> "нагрудник";
            case LEGGINGS -> "поножи";
            case BOOTS -> "ботинки";
        };
        return p.armorClass() == ArmorClass.SOUL
                ? cls + " T" + p.tier() + ": " + part
                : cls + " броня T" + p.tier() + ": " + part;
    }

    private static Component line(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    static String num(double v) {
        if (v == Math.rint(v)) {
            return String.format(Locale.ROOT, "%d", (long) v);
        }
        return String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    static String signed(double v) {
        return (v > 0 ? "+" : "") + num(v);
    }

    NamespacedKey itemIdKey() {
        return keys.itemId;
    }
}

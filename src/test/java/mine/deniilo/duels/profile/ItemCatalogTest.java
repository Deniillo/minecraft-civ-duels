package mine.deniilo.duels.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ItemCatalogTest {

    private record W(WeaponKind kind, int tier, String material, double attack) {
    }

    @Test
    void weaponsMatchSpecTables() {
        Map<String, W> expected = new LinkedHashMap<>();
        expected.put("sword_t1_wood", new W(WeaponKind.SWORD, 1, "WOODEN_SWORD", 7));
        expected.put("sword_t1_stone", new W(WeaponKind.SWORD, 1, "STONE_SWORD", 12));
        expected.put("sword_t1_iron", new W(WeaponKind.SWORD, 1, "IRON_SWORD", 14));
        expected.put("sword_t2_steel", new W(WeaponKind.SWORD, 2, "IRON_SWORD", 18));
        expected.put("sword_t3_carbide", new W(WeaponKind.SWORD, 3, "GOLDEN_SWORD", 22));
        expected.put("sword_t4_tungsten", new W(WeaponKind.SWORD, 4, "DIAMOND_SWORD", 27));
        expected.put("sword_t5_soul", new W(WeaponKind.SWORD, 5, "NETHERITE_SWORD", 35));
        expected.put("axe_t1_stone", new W(WeaponKind.AXE, 1, "STONE_AXE", 15));
        expected.put("axe_t1_iron", new W(WeaponKind.AXE, 1, "IRON_AXE", 17));
        expected.put("axe_t2_steel", new W(WeaponKind.AXE, 2, "IRON_AXE", 21));
        expected.put("axe_t3_carbide", new W(WeaponKind.AXE, 3, "GOLDEN_AXE", 25));
        expected.put("axe_t4_tungsten", new W(WeaponKind.AXE, 4, "DIAMOND_AXE", 30));
        expected.put("trident_t1_iron", new W(WeaponKind.TRIDENT, 1, "TRIDENT", 15));
        expected.put("trident_t2_steel", new W(WeaponKind.TRIDENT, 2, "TRIDENT", 19));
        expected.put("trident_t3_carbide", new W(WeaponKind.TRIDENT, 3, "TRIDENT", 23));
        expected.put("trident_t4_tungsten", new W(WeaponKind.TRIDENT, 4, "TRIDENT", 28));
        expected.put("bow_t1_hunting", new W(WeaponKind.BOW, 1, "BOW", 17));
        expected.put("bow_t2_recurve", new W(WeaponKind.BOW, 2, "BOW", 21));
        expected.put("bow_t3_long", new W(WeaponKind.BOW, 3, "BOW", 25));
        expected.put("bow_t4_marksman", new W(WeaponKind.BOW, 4, "BOW", 30));
        expected.put("crossbow_t5_soul", new W(WeaponKind.CROSSBOW, 5, "CROSSBOW", 38));

        ItemCatalog items = TestData.items();
        assertEquals(expected.size(), items.allWeapons().size(), "число профилей оружия");
        expected.forEach((id, w) -> {
            WeaponProfile p = items.weapon(id).orElseThrow(() -> new AssertionError("нет " + id));
            assertEquals(w.kind(), p.kind(), id);
            assertEquals(w.tier(), p.tier(), id);
            assertEquals(w.material(), p.material(), id);
            assertEquals(w.attack(), p.baseAttack(), 1e-9, id);
        });
    }

    @Test
    void sameMaterialDifferentIdsAreDistinct() {
        ItemCatalog items = TestData.items();
        assertEquals("IRON_SWORD", items.weapon("sword_t1_iron").orElseThrow().material());
        assertEquals("IRON_SWORD", items.weapon("sword_t2_steel").orElseThrow().material());
        assertEquals(14, items.weapon("sword_t1_iron").orElseThrow().baseAttack(), 1e-9);
        assertEquals(18, items.weapon("sword_t2_steel").orElseThrow().baseAttack(), 1e-9);
    }

    @Test
    void attackSpeedDeltasMatchSpec() {
        assertEquals(-2.4, WeaponKind.SWORD.attackSpeedDelta(), 1e-9);
        assertEquals(-3.0, WeaponKind.AXE.attackSpeedDelta(), 1e-9);
        assertEquals(-2.9, WeaponKind.TRIDENT.attackSpeedDelta(), 1e-9);
    }

    @Test
    void armorHasEverySlotWithSamePerPieceNumbers() {
        ItemCatalog items = TestData.items();
        assertEquals((5 + 4 + 5) * 4, items.allArmor().size());
        for (ArmorSlot slot : ArmorSlot.values()) {
            ArmorProfile p = items.armor(ArmorClass.HEAVY, 4, slot).orElseThrow();
            assertEquals(5.75, p.defense(), 1e-9);
            assertEquals(3.0, p.hp(), 1e-9);
            assertEquals(-0.035, p.speed(), 1e-9);
            assertEquals("DIAMOND_" + slot.name(), p.materialName());
        }
    }

    @Test
    void soulT5BootsHaveSpecialSpeed() {
        ItemCatalog items = TestData.items();
        assertEquals(0.045, items.armor(ArmorClass.SOUL, 5, ArmorSlot.BOOTS).orElseThrow().speed(), 1e-9);
        assertEquals(0.035, items.armor(ArmorClass.SOUL, 5, ArmorSlot.HELMET).orElseThrow().speed(), 1e-9);
        assertEquals(0.035, items.armor(ArmorClass.SOUL, 5, ArmorSlot.CHESTPLATE).orElseThrow().speed(), 1e-9);
        assertEquals(0.035, items.armor(ArmorClass.SOUL, 5, ArmorSlot.LEGGINGS).orElseThrow().speed(), 1e-9);
    }

    @Test
    void rejectsBrokenConfig() {
        assertThrows(IllegalArgumentException.class, () -> ItemCatalog.from(TestData.yaml("armor: {}")));
        assertThrows(IllegalArgumentException.class, () -> ItemCatalog.from(TestData.yaml("""
                armor: {}
                weapons:
                  w1: {kind: SWORD, tier: 1, material: IRON_SWORD, attack: .nan}
                """)));
        assertThrows(IllegalArgumentException.class, () -> ItemCatalog.from(TestData.yaml("""
                armor: {}
                weapons:
                  w1: {kind: SPEAR, tier: 1, material: IRON_SWORD, attack: 5}
                """)));
        assertTrue(ItemCatalog.from(TestData.yaml("armor: {}\nweapons: {}")).allWeapons().isEmpty());
    }
}

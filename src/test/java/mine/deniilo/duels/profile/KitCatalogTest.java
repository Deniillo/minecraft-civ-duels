package mine.deniilo.duels.profile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class KitCatalogTest {

    private static KitCatalog kits(String yaml) {
        return KitCatalog.from(TestData.yaml(yaml), TestData.items());
    }

    private static boolean hasError(KitCatalog c, String fragment) {
        return c.errors().stream().anyMatch(e -> e.contains(fragment));
    }

    @Test
    void shippedKitsAreValid() {
        KitCatalog c = KitCatalog.from(TestData.resource("kits.yml"), TestData.items());
        assertTrue(c.errors().isEmpty(), "ошибки в kits.yml: " + c.errors());
        assertEquals(6, c.all().size());
        Kit lightBow = c.get("t4_light_bow").orElseThrow();
        assertEquals(mine.deniilo.duels.profile.ArmorClass.LIGHT, lightBow.armorClass());
        assertTrue(lightBow.weaponIds().contains("bow_t4_marksman"));
        Kit lightBowMax = c.get("t4_light_bow_max").orElseThrow();
        assertEquals(7, lightBowMax.attackLevel());
        assertEquals(5, lightBowMax.protectionLevel());
        Kit max = c.get("t4_heavy_max").orElseThrow();
        assertEquals(7, max.attackLevel());
        assertEquals(5, max.protectionLevel());
        for (Kit k : c.all()) {
            assertEquals(0, k.piercingLevel(), k.id());
        }
        for (Kit k : c.all()) {
            assertTrue(k.armorTier() >= 3, k.id());
        }
    }

    @Test
    void rejectsBowWithHeavy() {
        KitCatalog c = kits("""
                kits:
                  bad:
                    armor: {class: HEAVY, tier: 4}
                    weapons: [bow_t4_marksman]
                """);
        assertTrue(c.all().isEmpty());
        assertTrue(hasError(c, "Лук нельзя"), c.errors().toString());
    }

    @Test
    void rejectsAxeWithFullLight() {
        KitCatalog c = kits("""
                kits:
                  bad:
                    armor: {class: LIGHT, tier: 4}
                    weapons: [axe_t4_tungsten]
                """);
        assertTrue(hasError(c, "Секиру нельзя"), c.errors().toString());
    }

    @Test
    void rejectsT5WeaponsWithoutFullSoulT5() {
        KitCatalog soul4 = kits("""
                kits:
                  bad:
                    armor: {class: SOUL, tier: 4}
                    weapons: [sword_t5_soul]
                """);
        assertTrue(hasError(soul4, "T5"), soul4.errors().toString());

        KitCatalog lightT5 = kits("""
                kits:
                  bad:
                    armor: {class: LIGHT, tier: 5}
                    weapons: [crossbow_t5_soul]
                """);
        assertTrue(hasError(lightT5, "T5"), "LIGHT T5 не SOUL: " + lightT5.errors());
    }

    @Test
    void acceptsSoulT5WithT5WeaponsAndBow() {
        KitCatalog c = kits("""
                kits:
                  ok:
                    armor: {class: SOUL, tier: 5}
                    weapons: [sword_t5_soul, crossbow_t5_soul, bow_t4_marksman, axe_t4_tungsten]
                """);
        assertTrue(c.errors().isEmpty(), c.errors().toString());
        assertEquals(Optional.of("ok"), c.get("ok").map(Kit::id));
    }

    @Test
    void rejectsLevelsOutOfRange() {
        KitCatalog c = kits("""
                kits:
                  bad1: {armor: {class: HEAVY, tier: 3}, weapons: [sword_t3_carbide], attack-level: 8}
                  bad2: {armor: {class: HEAVY, tier: 3, protection-level: 6}, weapons: [sword_t3_carbide]}
                  bad3: {armor: {class: HEAVY, tier: 3}, weapons: [sword_t3_carbide], piercing-level: 4}
                  bad4: {armor: {class: HEAVY, tier: 3}, weapons: [sword_t3_carbide], attack-level: -1}
                """);
        assertTrue(c.all().isEmpty(), c.ids().toString());
        assertTrue(hasError(c, "attack-level"));
        assertTrue(hasError(c, "protection-level"));
        assertTrue(hasError(c, "piercing-level"));
    }

    @Test
    void rejectsUnknownItemsAndMissingArmor() {
        KitCatalog c = kits("""
                kits:
                  bad1: {armor: {class: HEAVY, tier: 3}, weapons: [no_such_weapon]}
                  bad2: {armor: {class: HEAVY, tier: 5}, weapons: [sword_t3_carbide]}
                  bad3: {armor: {class: HEAVY, tier: 3}, weapons: []}
                  bad4: {armor: {class: MAGIC, tier: 3}, weapons: [sword_t3_carbide]}
                """);
        assertTrue(c.all().isEmpty());
        assertTrue(hasError(c, "no_such_weapon"));
        assertTrue(hasError(c, "heavy_t5_helmet"));
        assertTrue(hasError(c, "нет оружия"));
        assertTrue(hasError(c, "MAGIC"));
    }

    @Test
    void oneBadKitDoesNotBlockOthers() {
        KitCatalog c = kits("""
                kits:
                  bad: {armor: {class: HEAVY, tier: 4}, weapons: [bow_t4_marksman]}
                  good: {armor: {class: HEAVY, tier: 4}, weapons: [sword_t4_tungsten]}
                """);
        assertEquals(1, c.all().size());
        assertTrue(c.get("good").isPresent());
        assertFalse(c.get("bad").isPresent());
    }

    @Test
    void menuOrderGroupsT4BeforeT5() {
        KitCatalog c = KitCatalog.from(TestData.resource("kits.yml"), TestData.items());
        assertEquals(java.util.List.of("t3_heavy", "t4_heavy", "t4_heavy_max", "t4_light_bow", "t4_light_bow_max", "t5_soul"),
                c.ids());
    }

    @Test
    void onlyMaxKitsAreEnhanced() {
        KitCatalog c = KitCatalog.from(TestData.resource("kits.yml"), TestData.items());
        for (Kit k : c.all()) {
            assertEquals(k.id().endsWith("_max"), k.enhanced(), k.id());
        }
    }

    @Test
    void lightBowKitsHaveThrowableTridentForBot() {
        KitCatalog c = KitCatalog.from(TestData.resource("kits.yml"), TestData.items());
        ArmorSetStats light = ArmorSetCalculator.compute(TestData.fullSet(ArmorClass.LIGHT, 4, 0));
        assertTrue(WeaponRules.canThrowTrident(light), "в коже трезубец можно бросать");
        for (String id : java.util.List.of("t4_light_bow", "t4_light_bow_max")) {
            Kit kit = c.get(id).orElseThrow();
            String firstMelee = kit.weaponIds().stream()
                    .filter(w -> TestData.items().weapon(w).orElseThrow().kind().isMelee())
                    .findFirst().orElseThrow();
            assertEquals("trident_t4_tungsten", firstMelee, id);
            WeaponProfile bow = TestData.items().weapon("bow_t4_marksman").orElseThrow();
            assertTrue(WeaponRules.violation(light, bow).isEmpty(), "лук в коже разрешён: " + id);
        }
    }

    @Test
    void tridentThrowRule() {
        ArmorSetStats heavy = ArmorSetCalculator.compute(TestData.fullSet(ArmorClass.HEAVY, 4, 0));
        ArmorSetStats soul = ArmorSetCalculator.compute(TestData.fullSet(ArmorClass.SOUL, 5, 0));
        assertFalse(WeaponRules.canThrowTrident(heavy));
        assertTrue(WeaponRules.canThrowTrident(soul));
    }
}

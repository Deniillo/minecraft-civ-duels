package mine.deniilo.duels.combat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import mine.deniilo.duels.item.ItemReader;
import mine.deniilo.duels.kit.KitService;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.ArmorSetStats;
import mine.deniilo.duels.profile.ItemCatalog;
import mine.deniilo.duels.profile.WeaponKind;
import mine.deniilo.duels.profile.WeaponProfile;
import mine.deniilo.duels.profile.WeaponRules;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

@SuppressWarnings("deprecation")
public final class DuelCombatEngine implements Listener {

    private static final int SHIELD_DISABLE_TICKS = 120;
    private static final int TRIDENT_COOLDOWN_TICKS = 120;
    private static final int CROSSBOW_COOLDOWN_TICKS = 20;
    private static final Set<DamageCause> BLOCKED_ENVIRONMENT = Set.of(
            DamageCause.FIRE, DamageCause.FIRE_TICK, DamageCause.LAVA, DamageCause.HOT_FLOOR, DamageCause.LIGHTNING);

    private record MeleeSnapshot(int tick, double strength) {
    }

    private record ProjectileSnapshot(WeaponProfile profile, AttackType type, int attackLevel, double strength,
                                      UUID owner, UUID session) {
    }

    private final Plugin plugin;
    private final ItemCatalog items;
    private final ItemReader reader;
    private final KitService kits;
    private final CombatRegistry registry;
    private final CombatDebug debug;
    private final DummyService dummies;
    private final Messages messages;

    private final Map<UUID, MeleeSnapshot> melee = new HashMap<>();
    private final Map<UUID, ProjectileSnapshot> projectiles = new HashMap<>();

    public DuelCombatEngine(Plugin plugin, ItemCatalog items, ItemReader reader, KitService kits,
                            CombatRegistry registry, CombatDebug debug, DummyService dummies, Messages messages) {
        this.plugin = plugin;
        this.items = items;
        this.reader = reader;
        this.kits = kits;
        this.registry = registry;
        this.debug = debug;
        this.dummies = dummies;
        this.messages = messages;
        Bukkit.getScheduler().runTaskTimer(plugin, () -> projectiles.keySet().removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            return e == null || !e.isValid();
        }), 200L, 200L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreAttack(PrePlayerAttackEntityEvent event) {
        if (event.isCancelled() || !event.willAttack()) {
            return;
        }
        Player player = event.getPlayer();
        if (registry.session(player.getUniqueId()).isEmpty()) {
            return;
        }
        melee.put(player.getUniqueId(), new MeleeSnapshot(Bukkit.getCurrentTick(), player.getAttackCooldown()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Optional<UUID> session = registry.session(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        ItemStack bow = event.getBow();
        Optional<WeaponProfile> weapon = reader.weapon(bow, items);
        boolean ranged = weapon.isPresent()
                && (weapon.get().kind() == WeaponKind.BOW || weapon.get().kind() == WeaponKind.CROSSBOW);
        if (!ranged || !registry.isActive(session.get()) || !(event.getProjectile() instanceof AbstractArrow)) {
            event.setCancelled(true);
            return;
        }
        Optional<String> violation = WeaponRules.violation(kits.stats(player), weapon.get());
        if (violation.isPresent()) {
            event.setCancelled(true);
            messages.send(player, "weapon-forbidden", "reason", violation.get());
            return;
        }
        boolean crossbow = weapon.get().kind() == WeaponKind.CROSSBOW;
        double force = crossbow ? 1.0 : event.getForce();
        projectiles.put(event.getProjectile().getUniqueId(), new ProjectileSnapshot(
                weapon.get(), crossbow ? AttackType.CROSSBOW : AttackType.BOW,
                reader.attackLevel(bow), force, player.getUniqueId(), session.get()));
        if (crossbow) {
            Material material = bow.getType();
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.setCooldown(material, CROSSBOW_COOLDOWN_TICKS), 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident) || !(trident.getShooter() instanceof Player player)) {
            return;
        }
        Optional<UUID> session = registry.session(player.getUniqueId());
        if (session.isEmpty()) {
            return;
        }
        ItemStack thrown = trident.getItemStack();
        Optional<WeaponProfile> weapon = reader.weapon(thrown, items);
        if (weapon.isEmpty() || weapon.get().kind() != WeaponKind.TRIDENT || !registry.isActive(session.get())) {
            event.setCancelled(true);
            return;
        }
        if (!WeaponRules.canThrowTrident(kits.stats(player))) {
            event.setCancelled(true);
            messages.send(player, "weapon-forbidden", "reason", "Бросать трезубец нельзя в полном комплекте HEAVY");
            return;
        }
        projectiles.put(trident.getUniqueId(), new ProjectileSnapshot(
                weapon.get(), AttackType.THROWN_TRIDENT, reader.attackLevel(thrown), 1.0,
                player.getUniqueId(), session.get()));
        Bukkit.getScheduler().runTask(plugin, () -> player.setCooldown(Material.TRIDENT, TRIDENT_COOLDOWN_TICKS));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        Entity damager = event.getDamager();
        Player attacker = null;
        ProjectileSnapshot shot = null;
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile projectile) {
            shot = projectiles.get(projectile.getUniqueId());
            if (projectile.getShooter() instanceof Player p) {
                attacker = p;
            }
        }
        DummyService.BotWeapon botWeapon = damager instanceof org.bukkit.entity.Zombie
                ? dummies.botWeapon(damager.getUniqueId()) : null;
        UUID attackerId = attacker != null ? attacker.getUniqueId() : botWeapon != null ? damager.getUniqueId() : null;
        Optional<UUID> victimSession = registry.session(victim.getUniqueId());
        Optional<UUID> attackerSession = attackerId == null ? Optional.empty() : registry.session(attackerId);
        if (victimSession.isEmpty() && attackerSession.isEmpty()) {
            return;
        }
        if (attackerId == null || !registry.mayDamage(attackerId, victim.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getCause() == DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            return;
        }

        AttackInput input;
        if (botWeapon != null) {
            input = new AttackInput(AttackType.MELEE, botWeapon.profile().kind(), botWeapon.profile().baseAttack(),
                    botWeapon.attackLevel(), botWeapon.piercingLevel(), 1.0, false);
        } else if (damager instanceof Player) {
            input = meleeInput(attacker, event);
            if (input == null) {
                event.setCancelled(true);
                return;
            }
        } else if (shot != null && shot.session().equals(attackerSession.get())) {
            input = new AttackInput(shot.type(), shot.profile().kind(), shot.profile().baseAttack(),
                    shot.attackLevel(), 0, shot.strength(), false);
        } else {
            event.setCancelled(true);
            return;
        }

        ArmorSetStats armor = kits.stats(victim);
        DamageBreakdown breakdown = DamageCalculator.compute(input, armor.defense(), armor.soulT5Set());
        double finalDamage = breakdown.finalDamage();

        if (victim instanceof HumanEntity human && isRealBlock(event, human)) {
            boolean axeHit = input.type() == AttackType.MELEE && input.kind() == WeaponKind.AXE;
            boolean swordHit = input.type() == AttackType.MELEE && input.kind() == WeaponKind.SWORD;
            boolean arrowHit = input.type() == AttackType.BOW || input.type() == AttackType.CROSSBOW;
            if (axeHit || swordHit || arrowHit) {
                human.setCooldown(Material.SHIELD, SHIELD_DISABLE_TICKS);
                human.clearActiveItem();
            }
            if (!axeHit) {
                finalDamage = 0.0;
            }
        }

        boolean dummy = registry.isDummy(victim.getUniqueId());
        if (dummy) {
            finalDamage = Math.min(finalDamage, Math.max(0.0, victim.getHealth() - 0.1));
            Bukkit.getScheduler().runTask(plugin, () -> dummies.healFull(victim));
        }
        applyFinalDamage(event, finalDamage);

        debug.report(DamageReport.describe(input, breakdown) + (finalDamage != breakdown.finalDamage() ? " (щит/мишень: " + finalDamage + ")" : ""),
                attackerId, victim.getUniqueId());
    }

    private AttackInput meleeInput(Player attacker, EntityDamageByEntityEvent event) {
        ItemStack hand = attacker.getInventory().getItemInMainHand();
        Optional<WeaponProfile> weapon = reader.weapon(hand, items);
        if (weapon.isEmpty() || !weapon.get().kind().isMelee()) {
            return AttackInput.fist();
        }
        Optional<String> violation = WeaponRules.violation(kits.stats(attacker), weapon.get());
        if (violation.isPresent()) {
            messages.send(attacker, "weapon-forbidden", "reason", violation.get());
            return null;
        }
        MeleeSnapshot snap = melee.get(attacker.getUniqueId());
        double strength = snap != null && Bukkit.getCurrentTick() - snap.tick() <= 1
                ? snap.strength()
                : attacker.getAttackCooldown();
        return new AttackInput(AttackType.MELEE, weapon.get().kind(), weapon.get().baseAttack(),
                reader.attackLevel(hand), reader.piercingLevel(hand), strength, event.isCritical());
    }

    /** Реальный блок щитом: BLOCKING применим и отнял урон; нулевой урон из-за брони блоком не считается. */
    private static boolean isRealBlock(EntityDamageByEntityEvent event, HumanEntity human) {
        return human.isBlocking()
                && event.isApplicable(DamageModifier.BLOCKING)
                && event.getDamage(DamageModifier.BLOCKING) != 0.0;
    }

    private static void applyFinalDamage(EntityDamageByEntityEvent event, double damage) {
        for (DamageModifier modifier : DamageModifier.values()) {
            if (modifier != DamageModifier.BASE && event.isApplicable(modifier)) {
                event.setDamage(modifier, 0.0);
            }
        }
        event.setDamage(DamageModifier.BASE, damage);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnvironmentDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || event.getCause() == DamageCause.CUSTOM
                || !(event.getEntity() instanceof Player player)
                || registry.session(player.getUniqueId()).isEmpty()) {
            return;
        }
        if (BLOCKED_ENVIRONMENT.contains(event.getCause())) {
            event.setCancelled(true);
            return;
        }
        if (kits.stats(player).soulT5Set() && event.getFinalDamage() > DamageCalculator.SOUL_THRESHOLD) {
            event.setDamage(event.getDamage() / 2.0);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDamage(PlayerItemDamageEvent event) {
        if (reader.itemId(event.getItem()).isPresent()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegain(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || registry.session(player.getUniqueId()).isEmpty()) {
            return;
        }
        if (event.getRegainReason() == EntityRegainHealthEvent.RegainReason.SATIATED && event.isFastRegen()) {
            event.setAmount(event.getAmount() * (player.isSneaking() ? 0.5 : 0.25));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        melee.remove(id);
        debug.forget(id);
        registry.leave(id);
    }
}

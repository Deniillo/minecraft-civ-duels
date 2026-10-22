package mine.deniilo.duels.match;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import mine.deniilo.duels.combat.CombatRegistry;
import mine.deniilo.duels.kit.KitService;
import mine.deniilo.duels.message.Messages;
import mine.deniilo.duels.profile.Kit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;

public final class DuelMatch {

    private final MatchManager manager;
    private final MatchState state;
    private final Kit kit;
    private final UUID a;
    private final UUID b;
    private final Entity bot;
    private final Location spawnA;
    private final Location spawnB;

    private int botDefeats;
    private BukkitTask countdownTask;
    private BukkitTask timeoutTask;
    private boolean resolveScheduled;
    private long activeSinceMillis;
    private boolean restored;
    private boolean shuttingDown;

    DuelMatch(MatchManager manager, MatchState state, Kit kit, UUID a, UUID b, Entity bot, Location spawnA, Location spawnB) {
        this.manager = manager;
        this.state = state;
        this.kit = kit;
        this.a = a;
        this.b = b;
        this.bot = bot;
        this.spawnA = spawnA;
        this.spawnB = spawnB;
    }

    public UUID id() {
        return state.matchId();
    }

    public MatchPhase phase() {
        return state.phase();
    }

    public boolean isParticipant(UUID id) {
        return state.isParticipant(id);
    }

    public org.bukkit.World arenaWorld() {
        return spawnA.getWorld();
    }

    boolean isSparringBot(UUID id) {
        return bot != null && bot.getUniqueId().equals(id);
    }

    void onBotDefeated(org.bukkit.entity.LivingEntity living) {
        botDefeats++;
        returnBot(living);
        Player p = Bukkit.getPlayer(a);
        if (p != null) {
            manager.messages().send(p, "sparring-defeated", "count", String.valueOf(botDefeats));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
    }

    void returnBot(org.bukkit.entity.LivingEntity living) {
        manager.dummies().healFull(living);
        living.teleport(spawnB);
        living.setFireTicks(0);
        living.setFallDistance(0);
        living.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
    }

    public Location spawnOf(UUID id) {
        return id.equals(a) ? spawnA : spawnB;
    }

    public boolean frozen() {
        return state.phase() == MatchPhase.COUNTDOWN;
    }

    public List<UUID> participants() {
        return List.of(a, b);
    }

    void prepare() {
        KitService kits = manager.kits();
        for (UUID id : participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            for (PotionEffect effect : new ArrayList<>(p.getActivePotionEffects())) {
                p.removePotionEffect(effect.getType());
            }
            p.setGameMode(GameMode.SURVIVAL);
            p.setFireTicks(0);
            p.setFallDistance(0f);
            p.setArrowsInBody(0);
            p.teleport(spawnOf(id));
            if (!kits.give(p, kit, state.matchId())) {
                cancel(EndReason.ERROR);
                return;
            }
        }
        for (UUID id : participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                manager.messages().send(p, state.ranked() ? "match-mode-ranked" : "match-mode-training");
            }
        }
        state.advanceTo(MatchPhase.COUNTDOWN);
        if (bot != null) {
            begin();
            return;
        }
        int[] left = {manager.settings().countdownSeconds()};
        countdownTask = Bukkit.getScheduler().runTaskTimer(manager.plugin(), () -> {
            if (state.phase() != MatchPhase.COUNTDOWN) {
                stopCountdown();
                return;
            }
            if (left[0] > 0) {
                showToBoth(Title.title(Component.text(String.valueOf(left[0])), Component.text(kit.displayName()),
                        Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ZERO)), Sound.BLOCK_NOTE_BLOCK_HAT);
                left[0]--;
            } else {
                stopCountdown();
                begin();
            }
        }, 0L, 20L);
    }

    private void stopCountdown() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
    }

    private void begin() {
        if (!state.advanceTo(MatchPhase.ACTIVE)) {
            return;
        }
        for (UUID id : participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && !p.isDead()) {
                manager.kits().fillHealth(p);
            }
        }
        if (bot instanceof org.bukkit.entity.Mob mob) {
            mob.setAI(true);
            Player target = Bukkit.getPlayer(a);
            if (target != null) {
                mob.setTarget(target);
            }
        }
        manager.registry().setActive(state.matchId(), true);
        activeSinceMillis = System.currentTimeMillis();
        showToBoth(Title.title(Component.text("БОЙ!"), Component.empty(),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(200))), Sound.BLOCK_NOTE_BLOCK_PLING);
        long ticks = manager.settings().maxDurationSeconds() * 20L;
        timeoutTask = Bukkit.getScheduler().runTaskLater(manager.plugin(), () -> {
            state.reportTimeout();
            scheduleResolve();
        }, ticks);
    }

    private void showToBoth(Title title, Sound sound) {
        for (UUID id : participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                p.showTitle(title);
                p.playSound(p.getLocation(), sound, 1f, 1f);
            }
        }
    }

    void onDeath(UUID id) {
        state.reportLoss(id, EndReason.DEATH);
        scheduleResolve();
    }

    void onLeave(UUID id) {
        state.reportLoss(id, EndReason.FORFEIT);
        scheduleResolve();
    }

    void cancel(EndReason reason) {
        state.reportCancel(reason);
        scheduleResolve();
    }

    void cancelNow(EndReason reason) {
        shuttingDown = true;
        state.reportCancel(reason);
        state.resolve().ifPresent(this::finish);
        closeNow();
    }

    /** Итог собирается в конце тика, чтобы две смерти одного тика дали ничью. */
    private void scheduleResolve() {
        if (resolveScheduled) {
            return;
        }
        resolveScheduled = true;
        Bukkit.getScheduler().runTask(manager.plugin(), () -> {
            resolveScheduled = false;
            state.resolve().ifPresent(this::finish);
        });
    }

    private void finish(MatchResult result) {
        stopCountdown();
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        manager.registry().setActive(state.matchId(), false);
        long duration = activeSinceMillis == 0 ? 0 : System.currentTimeMillis() - activeSinceMillis;
        announce(result, duration);
        manager.sink().onFinished(result, new MatchInfo(kit.id(), bot != null, duration));
        if (bot != null) {
            bot.remove();
        }
        if (!shuttingDown) {
            Bukkit.getScheduler().runTaskLater(manager.plugin(), this::closeNow, manager.settings().resultDelayTicks());
        }
    }

    private void announce(MatchResult result, long durationMillis) {
        Messages m = manager.messages();
        String seconds = String.valueOf(durationMillis / 1000);
        String training = result.ranked() ? "" : " <gray>Тренировка, рейтинг не изменён.</gray>";
        for (UUID id : participants()) {
            Player p = Bukkit.getPlayer(id);
            if (p == null) {
                continue;
            }
            String reason = reasonText(result.reason());
            if (result.trainingLeft()) {
                m.send(p, id.equals(result.loser()) ? "training-left" : "training-opponent-left", "seconds", seconds);
                continue;
            }
            switch (result.outcome()) {
                case WIN -> {
                    UUID other = id.equals(result.winner()) ? result.loser() : result.winner();
                    m.send(p, id.equals(result.winner()) ? "match-win" : "match-loss",
                            "opponent", manager.nameOf(other), "reason", reason, "seconds", seconds, "training", training);
                }
                case DRAW -> m.send(p, "match-draw", "reason", reason, "seconds", seconds, "training", training);
                case CANCELLED -> m.send(p, "match-cancelled", "reason", reason);
            }
        }
    }

    private static String reasonText(EndReason reason) {
        return switch (reason) {
            case DEATH -> "гибель";
            case FORFEIT -> "выход или сдача";
            case TIMEOUT -> "вышло время";
            case SIMULTANEOUS -> "оба проиграли одновременно";
            case LEFT_BEFORE_START -> "соперник вышел до начала";
            case ADMIN -> "отмена администратором";
            case SHUTDOWN -> "перезапуск сервера";
            case ERROR -> "техническая ошибка";
        };
    }

    void closeNow() {
        if (restored) {
            return;
        }
        restored = true;
        state.advanceTo(MatchPhase.RESTORING);
        stopCountdown();
        if (timeoutTask != null) {
            timeoutTask.cancel();
            timeoutTask = null;
        }
        if (bot != null) {
            manager.dummies().forgetBot(bot.getUniqueId());
            bot.remove();
        }
        removeProjectiles();
        for (UUID id : participants()) {
            manager.restorePlayer(id, shuttingDown);
        }
        manager.registry().setActive(state.matchId(), false);
        state.advanceTo(MatchPhase.CLOSED);
        manager.closed(this);
    }

    private void removeProjectiles() {
        Location where = spawnA.getWorld() == null ? null : spawnA;
        if (where == null) {
            return;
        }
        for (Projectile projectile : where.getWorld().getEntitiesByClass(Projectile.class)) {
            if (projectile.getShooter() instanceof Player shooter && state.isParticipant(shooter.getUniqueId())) {
                projectile.remove();
            }
        }
    }
}

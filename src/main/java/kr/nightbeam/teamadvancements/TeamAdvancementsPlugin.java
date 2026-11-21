package kr.nightbeam.teamadvancements;

import io.nightbeam.donutteams.api.DonutTeams;
import io.nightbeam.donutteams.api.DonutTeamsApi;
import io.nightbeam.donutteams.api.TeamSnapshot;
import io.nightbeam.donutteams.api.event.TeamDisbandEvent;
import io.nightbeam.donutteams.api.event.TeamJoinEvent;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class TeamAdvancementsPlugin extends JavaPlugin implements Listener {
    private DonutTeamsApi teams;
    private TeamProgressStore store;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        store = new TeamProgressStore(this);
        try {
            teams = DonutTeams.getApi();
        } catch (Throwable throwable) {
            getLogger().log(Level.SEVERE, "Donut Teams API could not be loaded. Disabling.", throwable);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (teams == null) {
            getLogger().severe("Donut Teams API returned null. Disabling.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Bukkit.getPluginManager().registerEvents(this, this);

        // Recover completed advancements from online members when this plugin starts.
        Bukkit.getScheduler().runTask(this, this::seedOnlineTeamProgress);
        getLogger().info("TeamAdvancements enabled.");
    }

    @Override
    public void onDisable() {
        if (store != null) store.save();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Player player = event.getPlayer();
        TeamSnapshot team = teams.teamByPlayer(player.getUniqueId()).orElse(null);
        if (team == null) return;

        String key = event.getAdvancement().getKey().toString();
        if (store.add(team.id(), key)) {
            syncAdvancementToTeam(team, event.getAdvancement());
            store.save();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeamJoin(TeamJoinEvent event) {
        UUID teamId = event.getTeamId();
        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            TeamSnapshot team = teams.teamByPlayer(playerId).orElse(null);
            if (team != null && team.id().equals(teamId)) {
                applyTeamProgress(player, teamId);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTask(this, () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline()) return;
            teams.teamByPlayer(playerId).ifPresent(team -> applyTeamProgress(player, team.id()));
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeamDisband(TeamDisbandEvent event) {
        store.removeTeam(event.getTeamId());
        store.save();
    }

    private void seedOnlineTeamProgress() {
        Set<UUID> processedTeams = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            TeamSnapshot team = teams.teamByPlayer(player.getUniqueId()).orElse(null);
            if (team == null) continue;
            if (processedTeams.add(team.id())) {
                for (UUID memberId : team.members()) {
                    Player member = Bukkit.getPlayer(memberId);
                    if (member != null) collectCompleted(member, team.id());
                }
                store.save();
            }
            applyTeamProgress(player, team.id());
        }
    }

    private void collectCompleted(Player player, UUID teamId) {
        for (Advancement advancement : allAdvancements()) {
            AdvancementProgress progress = player.getAdvancementProgress(advancement);
            if (progress.isDone()) {
                store.add(teamId, advancement.getKey().toString());
            }
        }
    }

    private void syncAdvancementToTeam(TeamSnapshot team, Advancement advancement) {
        for (UUID memberId : team.members()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null && member.isOnline()) grantCompletedAdvancement(member, advancement);
        }
    }

    private void applyTeamProgress(Player player, UUID teamId) {
        for (String keyText : store.get(teamId)) {
            NamespacedKey key = NamespacedKey.fromString(keyText);
            if (key == null) continue;
            Advancement advancement = Bukkit.getAdvancement(key);
            if (advancement != null) grantCompletedAdvancement(player, advancement);
        }
    }

    private void grantCompletedAdvancement(Player player, Advancement advancement) {
        AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (progress.isDone()) return;
        for (String remaining : new ArrayList<>(progress.getRemainingCriteria())) {
            progress.awardCriteria(remaining);
        }
    }

    private Collection<Advancement> allAdvancements() {
        Collection<Advancement> result = new ArrayList<>();
        Bukkit.advancementIterator().forEachRemaining(result::add);
        return result;
    }
}

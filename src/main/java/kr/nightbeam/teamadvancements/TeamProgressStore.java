package kr.nightbeam.teamadvancements;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

final class TeamProgressStore {
    private final TeamAdvancementsPlugin plugin;
    private final File file;
    private YamlConfiguration data;

    TeamProgressStore(TeamAdvancementsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder.");
        }
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    synchronized boolean add(UUID teamId, String advancementKey) {
        String path = teamId + "." + advancementKey.replace(".", "\\.");
        if (data.getBoolean(path, false)) return false;
        data.set(path, true);
        return true;
    }

    synchronized Set<String> get(UUID teamId) {
        ConfigurationSection section = data.getConfigurationSection(teamId.toString());
        if (section == null) return Collections.emptySet();
        return new HashSet<>(section.getKeys(false));
    }

    synchronized void removeTeam(UUID teamId) {
        data.set(teamId.toString(), null);
    }

    synchronized void save() {
        try {
            data.save(file);
        } catch (IOException exception) {
            plugin.getLogger().severe("Could not save teams.yml: " + exception.getMessage());
        }
    }
}

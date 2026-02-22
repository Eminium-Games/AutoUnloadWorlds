package fr.eminiumgames.autoworldunload;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoWorldUnload extends JavaPlugin implements Listener {

    private final Set<String> excludedWorlds = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        excludedWorlds.addAll(getConfig().getStringList("excluded-worlds"));
        Bukkit.getPluginManager().registerEvents(this, this);

        // unload any world that has no players on startup
        for (World world : Bukkit.getWorlds()) {
            getLogger().info("Startup scan: evaluating world " + world.getName());
            // reuse the same check logic; it internally skips excluded worlds and delayed scheduling
            checkWorld(world);
        }

        getLogger().info("AutoWorldUnload enabled.");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        checkWorld(event.getPlayer().getWorld());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        checkWorld(event.getFrom());
    }

    private void checkWorld(World world) {
        String name = world.getName();
        if (excludedWorlds.contains(name)) {
            getLogger().fine("Skipping excluded world: " + name);
            return;
        }

        int delaySeconds = getConfig().getInt("unload-delay-seconds", 60);
        getLogger().fine("Scheduling check for world " + name + " in " + delaySeconds + "s");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (world.getPlayers().isEmpty()) {
                getLogger().info("World " + name + " empty after delay, unloading");
                boolean success = Bukkit.unloadWorld(world, true);
                if (success) {
                    getLogger().info("Unloaded world: " + name);
                } else {
                    getLogger().warning("Failed to unload world: " + name);
                }
            } else {
                getLogger().fine("World " + name + " has players, skipping unload");
            }
        }, 20L * delaySeconds);
    }
}

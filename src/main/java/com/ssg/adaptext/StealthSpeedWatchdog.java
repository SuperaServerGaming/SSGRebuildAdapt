package com.ssg.adaptext;

import art.arcane.adapt.Adapt;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class StealthSpeedWatchdog {
    private static final String ADAPTATION_ID = "stealth-speed";
    private static final float SAFE_BASELINE = 0.2F;
    private static final float SPEEDPATH_BASELINE = 0.3F;

    private final JavaPlugin plugin;
    private ScheduledTask task;

    public StealthSpeedWatchdog(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }
        // A varredura (só lista jogadores online) roda na global region; a checagem/ajuste de
        // velocidade de cada jogador é despachada pro scheduler da própria entidade, já que
        // jogadores diferentes podem estar em regiões diferentes no Folia.
        task = Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> tick(), 1L, 1L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.getScheduler().run(plugin, t -> tickPlayer(p), null);
        }
    }

    private void tickPlayer(Player p) {
        if (p.getGameMode() == GameMode.SPECTATOR || p.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        if (!hasStealthSpeed(p)) {
            return;
        }

        float baseline = onDirtPath(p) ? SPEEDPATH_BASELINE : SAFE_BASELINE;
        if (p.getWalkSpeed() <= baseline) {
            return;
        }

        // Antes isto tambem disparava sempre que o jogador nao estava
        // agachado/rastejando, sem checar se a velocidade acima do normal
        // realmente veio do stealth-speed - isso derrubava boosts
        // legitimos de outros sistemas (SpeedPath, agility-wind-up) toda
        // tick. O bug de "stuck" que esse watchdog cacava ja e resolvido
        // direto na classe StealthSpeed (patch de 2026-07-09), entao so
        // resta o caso do sprint-sneak, que essa classe nao cobre.
        boolean sprintSneakExploit = p.isSneaking() && p.isSprinting();
        if (sprintSneakExploit) {
            p.setWalkSpeed(baseline);
        }
    }

    private boolean hasStealthSpeed(Player p) {
        try {
            return Adapt.instance.getAdaptServer().getPlayer(p).hasAdaptation(ADAPTATION_ID);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean onDirtPath(Player p) {
        Block below = p.getLocation().clone().add(0.0, -0.9, 0.0).getBlock();
        return below.getType() == Material.DIRT_PATH;
    }
}

package com.ssg.adaptext;

import art.arcane.adapt.api.adaptation.SimpleAdaptation;
import art.arcane.volmlib.util.format.Form;
import art.arcane.volmlib.util.inventorygui.Element;
import art.arcane.volmlib.util.localization.TextKey;
import art.arcane.adapt.util.common.format.C;
import art.arcane.adapt.util.config.ConfigDescription;
import art.arcane.adapt.util.config.ConfigDoc;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.Generated;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.PortalType;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityPortalEnterEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public class NetherSwiftPortal extends SimpleAdaptation<NetherSwiftPortal.Config> {
   private final Map<World, Integer> originalDelay = new HashMap<>();
   private final Set<UUID> boosted = new HashSet<>();

   public NetherSwiftPortal() {
      super("nether-swift-portal");
      this.registerConfiguration(Config.class);
      this.setDescriptionKey(TextKey.of("ssgadaptext.nether_swift_portal.description", "Atravessa portais do Nether quase instantaneamente, sem esperar o delay padrao."));
      this.setDisplayNameKey(TextKey.of("ssgadaptext.nether_swift_portal.name", "Portal Rapido do Nether"));
      this.setIcon(Material.FLINT_AND_STEEL);
      this.setBaseCost(this.getConfig().baseCost);
      this.setInterval(this.getConfig().setInterval);
      this.setInitialCost(this.getConfig().initialCost);
      this.setCostFactor(this.getConfig().costFactor);
   }

   public void addStats(int level, Element v) {
      v.addLore(C.GREEN + Form.pc(1.0, 0) + C.GRAY + " chance de atravessar o portal quase instantaneamente");
   }

   public void onTick() {
      if (this.boosted.isEmpty()) {
         return;
      }

      for (UUID id : new HashSet<>(this.boosted)) {
         Player p = Bukkit.getPlayer(id);
         if (p == null || !p.isOnline() || !this.isStandingInPortal(p)) {
            this.releaseFastDelay(id, p == null ? null : p.getWorld());
         }
      }
   }

   @EventHandler
   public void on(EntityPortalEnterEvent e) {
      if (this.isEnabled() && e.getPortalType() == PortalType.NETHER && e.getEntity() instanceof Player p) {
         if (this.isEligible(p) && !this.boosted.contains(p.getUniqueId())) {
            World w = p.getWorld();
            if (!this.hasUnboostedPlayerInPortal(w, p)) {
               this.boosted.add(p.getUniqueId());
               this.ensureFastDelay(w);
            }
         }
      }
   }

   @EventHandler
   public void on(PlayerTeleportEvent e) {
      if (e.getCause() == TeleportCause.NETHER_PORTAL) {
         this.releaseFastDelay(e.getPlayer().getUniqueId(), e.getFrom().getWorld());
      }
   }

   @EventHandler
   public void on(PlayerQuitEvent e) {
      this.releaseFastDelay(e.getPlayer().getUniqueId(), e.getPlayer().getWorld());
   }

   private boolean isEligible(Player p) {
      if (!this.hasActiveAdaptation(p)) {
         return false;
      } else {
         GameMode gm = p.getGameMode();
         return (gm == GameMode.SURVIVAL || gm == GameMode.ADVENTURE) && !p.isDead();
      }
   }

   private boolean isStandingInPortal(Player p) {
      return p.getLocation().getBlock().getType() == Material.NETHER_PORTAL;
   }

   private boolean hasUnboostedPlayerInPortal(World w, Player self) {
      for (Player other : w.getPlayers()) {
         if (!other.equals(self) && !this.boosted.contains(other.getUniqueId()) && this.isStandingInPortal(other)) {
            return true;
         }
      }

      return false;
   }

   private void ensureFastDelay(World w) {
      this.originalDelay.computeIfAbsent(w, world -> {
         Integer current = world.getGameRuleValue(GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY);
         int original = current == null ? 80 : current;
         world.setGameRule(GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY, Math.max(1, this.getConfig().fastDelayTicks));
         return original;
      });
   }

   private void releaseFastDelay(UUID id, World lastKnownWorld) {
      if (this.boosted.remove(id) && lastKnownWorld != null) {
         boolean stillNeeded = this.boosted
            .stream()
            .map(Bukkit::getPlayer)
            .filter(Objects::nonNull)
            .anyMatch(other -> other.getWorld().equals(lastKnownWorld));
         if (!stillNeeded) {
            Integer original = this.originalDelay.remove(lastKnownWorld);
            if (original != null) {
               lastKnownWorld.setGameRule(GameRules.PLAYERS_NETHER_PORTAL_DEFAULT_DELAY, original);
            }
         }
      }
   }

   public boolean isEnabled() {
      return this.getConfig().enabled;
   }

   public boolean isPermanent() {
      return this.getConfig().permanent;
   }

   @ConfigDescription("Cross Nether portals almost instantly instead of waiting for the standard delay.")
   protected static class Config {
      @ConfigDoc(
         value = "Keeps this adaptation permanently active once learned.",
         impact = "True removes the normal learn/unlearn flow and treats it as always learned."
      )
      boolean permanent = false;
      @ConfigDoc(value = "Enables or disables this feature.", impact = "Set to false to disable behavior without uninstalling files.")
      boolean enabled = true;
      @ConfigDoc(value = "Tick interval (ms) used for the stuck-state safety sweep.", impact = "Lower values clean up desynced players faster.")
      long setInterval = 1000L;
      @ConfigDoc(value = "Base knowledge cost used when learning this adaptation.", impact = "Higher values make each level cost more knowledge.")
      int baseCost = 3;
      @ConfigDoc(value = "Knowledge cost required to purchase level 1.", impact = "Higher values make unlocking the first level more expensive.")
      int initialCost = 6;
      @ConfigDoc(
         value = "Maximum level. This adaptation's effect is binary (instant crossing once active), so it is capped at 1.",
         impact = "Keep at 1: there is no additional effect at higher levels."
      )
      int maxLevel = 1;
      @ConfigDoc(value = "Scaling factor applied to higher adaptation levels.", impact = "Higher values increase level-to-level cost growth.")
      double costFactor = 0.6;
      @ConfigDoc(
         value = "Portal standing delay (in ticks) applied while an eligible player is crossing.",
         impact = "1 is effectively instant; vanilla default is 80. Only applied to the world while an eligible player is mid-crossing."
      )
      int fastDelayTicks = 1;

      @Generated
      public Config() {
      }
   }
}

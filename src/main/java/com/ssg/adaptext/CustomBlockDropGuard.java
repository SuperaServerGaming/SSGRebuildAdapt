package com.ssg.adaptext;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;

public final class CustomBlockDropGuard implements Listener {
    private static final Set<Material> PROTECTED_MATERIALS = EnumSet.of(Material.BARRIER, Material.PLAYER_HEAD, Material.PLAYER_WALL_HEAD);
    // Materials known to be used as "fake block" placeholders by datapacks on this server, watched
    // closely with diagnostic logging even when no marker is found, so a failed detection is visible
    // in the log instead of silently letting Adapt's drop-to-inventory adaptations take the items.
    private static final Set<Material> WATCHED_MATERIALS = EnumSet.of(
        Material.BARRIER, Material.PLAYER_HEAD, Material.PLAYER_WALL_HEAD, Material.BARREL, Material.CHISELED_DEEPSLATE
    );
    private static final double MARKER_SEARCH_RADIUS = 1.5;
    // Dropping the items naturally isn't enough on its own: the breaking player is almost always
    // standing right on top of the block, so ordinary vanilla pickup can grab them before the
    // datapack's own once-per-tick cleanup script gets a chance to see and remove its marker item.
    // A short pickup delay guarantees that window without being perceptible as a player.
    private static final int PICKUP_DELAY_TICKS = 10;

    private final Logger logger;

    public CustomBlockDropGuard(Logger logger) {
        this.logger = logger;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent e) {
        Material blockType = e.getBlockState().getType();
        boolean watched = WATCHED_MATERIALS.contains(blockType);

        boolean marker = hasNearbyCustomBlockMarker(e.getBlockState(), watched);
        boolean protect = PROTECTED_MATERIALS.contains(blockType) || marker;

        if (watched) {
            logger.info(
                "[CustomBlockDropGuard] block="
                    + blockType
                    + " loc="
                    + formatLocation(e.getBlockState().getLocation())
                    + " items="
                    + e.getItems().size()
                    + " markerFound="
                    + marker
                    + " protecting="
                    + protect
            );
        }

        if (!protect) {
            return;
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (Item item : e.getItems()) {
            stacks.add(item.getItemStack());
        }

        e.getItems().clear();

        for (ItemStack stack : stacks) {
            if (isThrowawaySignalItem(stack, blockType)) {
                // Some of these datapacks (catenary, pk_items_filter) rely on the block's own
                // *unmodified* vanilla drop as their internal "a player really mined this" signal
                // (a plain barrier, a plain chiseled_deepslate — never the real reward, which they
                // give separately via their own summon/give logic once they notice the block is
                // gone). That signal only works if it's the sole nearby item entity of that exact
                // kind for their once-a-tick poll to find; every extra tick it survives (which our
                // own re-drop + pickup delay was adding) is another chance for the player's normal
                // walking-over-it pickup to beat that poll to it, or for it to physically drift out
                // of the tight single-block search box these scripts use. Simplest fix: don't drop
                // it in the first place. Nothing legitimate is lost — on these datapacks the real
                // reward never comes from this event to begin with.
                if (watched) {
                    logger.info("[CustomBlockDropGuard]   discarding throwaway signal item " + stack.getType());
                }
                continue;
            }

            Item dropped = e.getBlockState().getWorld().dropItem(e.getBlockState().getLocation(), stack);
            dropped.setPickupDelay(PICKUP_DELAY_TICKS);
        }
    }

    // A "plain" (no custom name, no persistent/custom data) item that is the exact same material as
    // the block that was just broken is, on every custom-block system found on this server so far,
    // exactly that block's own internal detection signal rather than a real reward — a genuine
    // reward item is either a different material entirely (a tagged marker item) or, when it does
    // share the block's material (e.g. painting_table's barrel), is always custom-named to carry
    // the crafted item's identity. Only material+name are checked (not custom_data/PDC, which is
    // unreliable to read back for NBT set via raw commands rather than the plugin PDC API) to keep
    // this simple and dependable.
    private boolean isThrowawaySignalItem(ItemStack stack, Material blockType) {
        if (stack.getType() != blockType) {
            return false;
        }
        if (!stack.hasItemMeta()) {
            return true;
        }
        return !stack.getItemMeta().hasCustomName();
    }

    // The "fake block" datapacks on this server (catenary/mc_paint via the eroxified2 framework,
    // pk_painting_table/pk_items_filter/CropAndKettle via the pk_ library) all place an invisible
    // controller/marker entity at the block's position, and every one of them tags that entity
    // with a scoreboard tag containing "custom_block" (e.g. "eroxified2.custom_block.component...",
    // "pk.custom_block") regardless of which vanilla block they use as the physical placeholder
    // (barrier, player_head, a renamed barrel, even an ordinary block like chiseled_deepslate).
    // Detecting that tag directly, instead of hardcoding placeholder materials, covers all of them
    // — including whatever material some future pack picks — without needing to know their internals.
    private boolean hasNearbyCustomBlockMarker(BlockState state, boolean logDetails) {
        Location center = state.getLocation().add(0.5, 0.5, 0.5);
        boolean found = false;

        for (Entity entity : state.getWorld().getNearbyEntities(center, MARKER_SEARCH_RADIUS, MARKER_SEARCH_RADIUS, MARKER_SEARCH_RADIUS)) {
            Set<String> tags = entity.getScoreboardTags();
            boolean matches = false;
            for (String tag : tags) {
                if (tag.toLowerCase(Locale.ROOT).contains("custom_block")) {
                    matches = true;
                    if (!logDetails) {
                        return true;
                    }
                    found = true;
                }
            }

            if (logDetails) {
                logger.info(
                    "[CustomBlockDropGuard]   nearby entity type=" + entity.getType() + " tags=" + tags + (matches ? " <-- MATCH" : "")
                );
            }
        }

        return found;
    }

    private String formatLocation(Location loc) {
        return "(" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ() + " in " + loc.getWorld().getName() + ")";
    }
}

package com.github.jarada.waygates.listeners;

import com.github.jarada.waygates.WaygateManager;
import com.github.jarada.waygates.data.BlockLocation;
import com.github.jarada.waygates.data.DataManager;
import com.github.jarada.waygates.data.Gate;
import com.github.jarada.waygates.data.Msg;
import com.github.jarada.waygates.events.WaygateInteractEvent;
import com.github.jarada.waygates.util.Util;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Damageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.HashMap;

public class WaygateListener implements Listener {

    DataManager dm;
    WaygateManager gm;

    private final HashMap<Player, Location> playerLocationAtEvent = new HashMap<>();

    public WaygateListener() {
        dm = DataManager.getManager();
        gm = WaygateManager.getManager();
    }

    /* Gate Integrity */

    public void verifyGateIntegrity(Player p, Block block) {
        // If broken block is part of a gate
        BlockLocation blockLocation = new BlockLocation(block.getLocation());
        Gate gate = gm.getGateAtLocation(blockLocation);
        if (gate != null) {
            // It is destroyed
            gm.destroyWaygate(p, gate, blockLocation);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        verifyGateIntegrity(e.getPlayer(), e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent e) {
        verifyGateIntegrity(Util.isPlayer(e.getEntity()) ? (Player)e.getEntity() : null, e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        for (Block block : e.blockList())
            verifyGateIntegrity(Util.isPlayer(e.getEntity()) ? (Player)e.getEntity() : null, block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPistonExtendEvent(BlockPistonExtendEvent e) {
        verifyGateIntegrity(null, e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPistonRetractEvent(BlockPistonRetractEvent e) {
        verifyGateIntegrity(null, e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFadeEvent(BlockFadeEvent e) {
        verifyGateIntegrity(null, e.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBurnEvent(BlockBurnEvent e) {
        verifyGateIntegrity(null, e.getBlock());
    }

    /* Disable Vanilla Portal Behaviour */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortalEvent(PlayerPortalEvent e) {
        BlockLocation playerLocation = (playerLocationAtEvent.containsKey(e.getPlayer())) ?
                new BlockLocation(playerLocationAtEvent.get(e.getPlayer())) :
                new BlockLocation(e.getTo());
        if (gm.isGateNearby(playerLocation)) {
            Player p = e.getPlayer();
            e.setCancelled(true);

            // Verify Gate
            Gate gate = gm.getGateAtLocation(playerLocation);
            if (gate == null || !gate.verify(p))
                return;

            // Transport!
            gate.teleport(p);
            dm.saveWaygate(gate, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPortalEvent(EntityPortalEvent e) {
        BlockLocation entityLocation = new BlockLocation(e.getEntity().getLocation());
        if (gm.isGateNearby(entityLocation)) {
            e.setCancelled(true);

            // Verify Gate
            Gate gate = gm.getGateAtLocation(entityLocation);
            if (gate == null || !gate.isActive())
                return;

            // Transport!
            gate.teleportEntity(e.getEntity());
            dm.saveWaygate(gate, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntityPortalEnterEvent(EntityPortalEnterEvent e) {
        if (e.getEntity() instanceof Player) {
            playerLocationAtEvent.put((Player)e.getEntity(), e.getLocation());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCreatureSpawnEvent(CreatureSpawnEvent e) {
        // Verify Spawn
        if (e.getEntityType() != EntityType.PIG_ZOMBIE)
            return;

        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NETHER_PORTAL)
            return;

        // Verify Gate
        if (!gm.isGateNearby(new BlockLocation(e.getLocation())))
            return;

        // Check Settings
        // TODO Future: Pigman Spawn Setting

        // Cancel
        e.setCancelled(true);
    }

    /* Gate Modification */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onWaygateInteract(WaygateInteractEvent event) {
        if ((event.getAction() != Action.LEFT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_BLOCK) ||
                event.getItem() == null)
            return;

        DataManager dm = DataManager.getManager();
        Player player = event.getPlayer();
        Gate gate = event.getWaygate();
        ItemStack is = event.getItem();

        // Only owner can modify gate
        if (gate.getOwner().equals(player.getUniqueId()) || player.hasPermission("wp.admin")) {
            // Check
            Material m = is.getType();
            if (m == Material.WRITTEN_BOOK) {
                BookMeta bm = (BookMeta) is.getItemMeta();

                if (bm.hasDisplayName() || bm.hasLore())
                    return;

                String content = "";

                for (int page = 1; page <= bm.getPageCount(); page++) {
                    content += bm.getPage(page);

                    if (page != bm.getPageCount())
                        content += " ";
                }

                if (content.length() > dm.WG_DESC_MAX_LENGTH)
                    content = content.substring(0, dm.WG_DESC_MAX_LENGTH);

                player.closeInventory();
                gate.setDescription(content);
                Msg.GATE_DESC_UPDATED_BOOK.sendTo(player, gate.getName(), bm.getTitle());
            }  else {
                if (is.hasItemMeta())
                    return;

                gate.setIcon(m);
                Msg.GATE_SET_ICON.sendTo(player, gate.getName(), m.toString());
            }

            is.setAmount(is.getAmount() - 1);
            player.getInventory().setItemInMainHand(is);
            dm.saveWaygate(gate, false);
            Util.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP);
        } else {
            Msg.NO_PERMS.sendTo(player);
        }
    }

    /* Clearance */

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        playerLocationAtEvent.remove(e.getPlayer());
    }
}

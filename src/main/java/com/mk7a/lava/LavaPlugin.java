package com.mk7a.lava;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Furnace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public final class LavaPlugin extends JavaPlugin implements Listener {

    private final HashMap<Location, Inventory> lavaFurnaces = new HashMap<>();
    private final HashMap<Location, Boolean> modifiedSinceBackup = new HashMap<>();

    private static final String LAVA_FURNACE_TITLE = "Lava Furnace";

    private static final Integer[] GUI_SLOTS = {21, 22, 23, 30, 31, 32, 19, 25};
    private static final Integer[] STONE_SLOTS = {21, 22, 23, 30, 31, 32};
    private static final int FUEL_SLOT = 19;
    private static final int BUCKET_SLOT = 25;
    private static final int BUTTON_SLOT = 37;
    private static final int LOCK_SLOT = 28;

    private static final int STONE_COST = 250;
    private static final int COAL_COST = 10;

    private static final int BACKUP_INTERVAL = 60 * 20;


    private static final Material[] ALLOWED_STONE = {
            Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.STONE, Material.SMOOTH_STONE,
            Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
            Material.CHISELED_STONE_BRICKS, Material.ANDESITE, Material.POLISHED_ANDESITE, Material.GRANITE,
            Material.POLISHED_GRANITE, Material.DIORITE, Material.POLISHED_DIORITE,
    };


    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);

        reloadConfig();

        // Backup task
        new BukkitRunnable() {
            @Override
            public void run() {
                saveFurnaceInventories();
                saveConfig();
                resetModifiedStatus();
            }
        }.runTaskTimer(this, 60 * 20, BACKUP_INTERVAL); // delay: first backup 1 min after server start
    }

    @Override
    public void onDisable() {

        saveFurnaceInventories();
    }

    private void saveFurnaceInventories() {
        for (Location furnaceLoc : lavaFurnaces.keySet()) {

            if (modifiedSinceBackup.get(furnaceLoc)) {

                ItemStack[] contents = lavaFurnaces.get(furnaceLoc).getContents();

                // If smelt in progress, fill the bucket to prevent wasted resources
                if (contents[LOCK_SLOT].getType().equals(Material.ORANGE_STAINED_GLASS_PANE)) {
                    contents[BUCKET_SLOT] = new ItemBuilder(Material.LAVA_BUCKET, 1).build();
                }

                // Write items stored in gui slots to backup
                for (int slot : GUI_SLOTS) {
                    getConfig().set(locToYamlKey(furnaceLoc) + "." + slot, contents[slot]);
                }
            }
        }
        saveConfig();
    }

    private void resetModifiedStatus() {
        for (Location loc : modifiedSinceBackup.keySet()) {
            modifiedSinceBackup.replace(loc, false);
        }
    }

    private String locToYamlKey(Location loc) {
        return loc.toString().replace('.', '#');
    }

    @EventHandler
    public void onInvOpen(InventoryOpenEvent event) {

        final Player player = (Player) event.getPlayer();

        if (event.getInventory().getLocation() != null) {
            final Location blockLocation = event.getInventory().getLocation();

            if (isLavaFurnace(blockLocation.getBlock())) {
                event.setCancelled(true);

                // Furnace exists, is loaded
                if (lavaFurnaces.containsKey(blockLocation)) {
                    player.openInventory(lavaFurnaces.get(blockLocation));

                }

                // Furnace exists in config, needs loading
                else if (getConfig().contains(locToYamlKey(blockLocation))) {

                    Inventory getLavaFurnaceInv = createLavaFurnaceInv();
                    ItemStack[] contents = getLavaFurnaceInv.getContents();

                    for (int slot : GUI_SLOTS) {
                        ItemStack item = (ItemStack) getConfig().get(locToYamlKey(blockLocation) + "." + slot);

                        contents[slot] = item;
                    }

                    getLavaFurnaceInv.setContents(contents);
                    lavaFurnaces.put(blockLocation, getLavaFurnaceInv);
                    modifiedSinceBackup.put(blockLocation, false);

                    player.openInventory(getLavaFurnaceInv);

                }

                // Furnace does not exist, create
                else {
                    Inventory newLavaFurnaceInv = createLavaFurnaceInv();
                    lavaFurnaces.put(blockLocation, newLavaFurnaceInv);
                    modifiedSinceBackup.put(blockLocation, false);

                    player.openInventory(newLavaFurnaceInv);
                }
            }
        }
    }

    private Inventory createLavaFurnaceInv() {
        Inventory inv = Bukkit.createInventory(null, 6 * 9, LAVA_FURNACE_TITLE);

        ItemStack fill = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).setName(" ").build();
        for (int i = 0; i <= 53; i++) {
            inv.setItem(i, fill);
        }

        // Clear GUI slots
        for (int i : GUI_SLOTS) {
            inv.setItem(i, null);
        }

        // Instruction GUI items
        ItemStack hopperStone = new ItemBuilder(Material.HOPPER, 1).setName("&6⬇ Stone ⬇")
                .setLore(new String[]{" ", "&7250 blocks,", "&7any type."}).build();
        ItemStack hopperCoal = new ItemBuilder(Material.HOPPER, 1).setName("&6⬇ 10 Coal Blocks ⬇").build();
        ItemStack hopperBucket = new ItemBuilder(Material.HOPPER, 1).setName("&6⬇ Bucket ⬇")
                .setLore(new String[]{" ", "&7Place empty bucket"}).build();
        inv.setItem(13, hopperStone);
        inv.setItem(COAL_COST, hopperCoal);
        inv.setItem(16, hopperBucket);

        ItemStack button = new ItemBuilder(Material.BLAZE_POWDER, 1).setName("&6&lSTART FURNACE").build();
        inv.setItem(37, button);

        ItemStack credits = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1)
                .setName("&7Designed by &bmk7a&7")
                .addEnchantment(Enchantment.ARROW_INFINITE, 1)
                .addFlags(ItemFlag.HIDE_ENCHANTS).build();

        inv.setItem(53, credits);

        return inv;
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {

        final Location eventLocation = event.getBlock().getLocation();
        if (lavaFurnaces.containsKey(eventLocation)) {
            final Inventory furnace = lavaFurnaces.get(eventLocation);

            for (int i : GUI_SLOTS) {
                if (furnace.getItem(i) != null) {
                    eventLocation.getWorld().dropItem(eventLocation, furnace.getItem(i));
                }
            }

            lavaFurnaces.remove(eventLocation);

            String configKey = locToYamlKey(eventLocation);
            if (getConfig().contains(configKey)) {
                getConfig().set(configKey, null);
            }
        }
    }

    @EventHandler
    public void onSpread(BlockSpreadEvent event) {
        if (lavaFurnaces.containsKey(event.getSource().getRelative(BlockFace.UP).getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        final Player player = (Player) event.getWhoClicked();
        final Location loc = player.getLocation();
        final Inventory clickedInv = event.getClickedInventory();
        final Inventory openInv = player.getOpenInventory().getInventory(0);

        if (clickedInv == null || !player.getOpenInventory().getTitle().equalsIgnoreCase(LAVA_FURNACE_TITLE)) {
            return;
        }

        final int slot = event.getRawSlot();

        // Clicks that were cancelled
        boolean nonModifyClick = false;

        if (!Arrays.asList(GUI_SLOTS).contains(slot) && slot <= 53) {
            event.setCancelled(true);
            nonModifyClick = true;
        }

        if (clickedInv.getItem(LOCK_SLOT) != null && clickedInv.getItem(LOCK_SLOT).getType().equals(Material.ORANGE_STAINED_GLASS_PANE)) {
            event.setCancelled(true);
            nonModifyClick = true;

        }

        // Should count as updated when button pressed, as coal removed when starting smelt
        // modified = true   required for backup to set bucket slot to lava
        if (slot == BUTTON_SLOT) {
            nonModifyClick = false;
        }

        Location getLoc = getFurnaceLocation(openInv);

        if (!nonModifyClick && !modifiedSinceBackup.get(getLoc)) {
            modifiedSinceBackup.replace(getLoc, true);
        }


        if (event.getSlot() == BUTTON_SLOT) {

            if (canStartFurnace(clickedInv)) {

                player.playSound(loc, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, COAL_COST, COAL_COST);
                runFurnace(clickedInv, player);

            } else {
                player.playSound(loc, Sound.BLOCK_REDSTONE_TORCH_BURNOUT, 1, 1);
            }
        }
    }

    private void runFurnace(Inventory inventory, Player player) {

        reduceFuel(inventory);

        Location furnaceLocation = getFurnaceLocation(inventory);
        Location effectLocation = furnaceLocation.clone().add(0.5, 0.5, 0.5);
        World world = furnaceLocation.getWorld();

        setFurnaceBurn(furnaceLocation, true);

        ItemStack lockItem = new ItemBuilder(Material.ORANGE_STAINED_GLASS_PANE, 1).setName("&e Melting...").build();
        inventory.setItem(LOCK_SLOT, lockItem);

        for (int i = 0; i <= 120; i++) {
            new BukkitRunnable() {
                @Override
                public void run() {

                    world.spawnParticle(Particle.FLAME, effectLocation, 20, 0.5, 0.5, 0.5, 0);
                    world.spawnParticle(Particle.LAVA, effectLocation, 1, 0.5, 0.5, 0.5, 0);
                    world.playSound(effectLocation, Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 3, 10);
                }
            }.runTaskLater(this, i * 10);
        }

        new BukkitRunnable() {
            @Override
            public void run() {

                consumeStone(inventory);
                fillBucket(inventory, player);
                world.playSound(furnaceLocation, Sound.ENTITY_GENERIC_BURN, 1, 1);

                ItemStack unlock = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE, 1).setName(" ").build();
                inventory.setItem(LOCK_SLOT, unlock);
                setFurnaceBurn(furnaceLocation, false);
            }
        }.runTaskLater(this, 120 * 10 + 1);

    }

    private void setFurnaceBurn(Location furnaceLocation, boolean burning) {

        Block block = furnaceLocation.getBlock();
        Furnace furnace = (Furnace) block.getState();
        furnace.setBurnTime((short) (burning ? 1 : 0));
        block.setBlockData(furnace.getBlockData());
    }

    private void reduceFuel(Inventory inventory) {

        ItemStack fuel = inventory.getItem(FUEL_SLOT);
        int fuelAmount = fuel.getAmount();
        if (fuelAmount == COAL_COST) {
            inventory.setItem(FUEL_SLOT, null);
        } else {
            fuelAmount -= COAL_COST;
            fuel.setAmount(fuelAmount);
            inventory.setItem(FUEL_SLOT, fuel);
        }
    }

    private void consumeStone(Inventory inventory) {

        int stoneCount = 0;

        for (int i : STONE_SLOTS) {
            if (inventory.getItem(i) != null) {

                final ItemStack slotItem = inventory.getItem(i);
                final int slotAmount = slotItem.getAmount();

                if (Arrays.asList(ALLOWED_STONE).contains(slotItem.getType())) {

                    if (stoneCount + slotItem.getAmount() <= STONE_COST) {

                        inventory.setItem(i, null);
                        stoneCount += slotAmount;

                    } else {

                        int remainder = (stoneCount + slotAmount) - STONE_COST;

                        slotItem.setAmount(remainder);
                        inventory.setItem(i, slotItem);
                        break;
                    }
                }
            }
        }
    }

    private void fillBucket(Inventory inventory, Player player) {
        int buckets = inventory.getItem(BUCKET_SLOT).getAmount();
        if (buckets > 1) {
            Location loc = getFurnaceLocation(inventory);
            player.getWorld().dropItem(loc, new ItemBuilder(Material.BUCKET, buckets - 1).build());
        }
        inventory.setItem(BUCKET_SLOT, new ItemBuilder(Material.LAVA_BUCKET, 1).build());
    }


    private boolean canStartFurnace(Inventory inv) {

        final boolean fuel = inv.getItem(FUEL_SLOT) != null && inv.getItem(FUEL_SLOT).getType().equals(Material.COAL_BLOCK)
                && inv.getItem(FUEL_SLOT).getAmount() >= COAL_COST;

        int stoneCount = 0;

        for (int i : STONE_SLOTS) {
            if (inv.getItem(i) != null) {
                final ItemStack slotItem = inv.getItem(i);

                if (Arrays.asList(ALLOWED_STONE).contains(slotItem.getType())) {
                    stoneCount += slotItem.getAmount();
                }
            }
        }

        final boolean hasBucket = inv.getItem(BUCKET_SLOT) != null && inv.getItem(BUCKET_SLOT).getType().equals(Material.BUCKET);

        return fuel && stoneCount >= STONE_COST && hasBucket;
    }

    private boolean isLavaFurnace(Block block) {

        boolean isFurnace = block.getType().equals(Material.FURNACE);
        boolean belowIsFire = block.getRelative(BlockFace.DOWN).getType().equals(Material.FIRE);
        boolean belowIsCampfire = block.getRelative(BlockFace.DOWN).getType().equals(Material.CAMPFIRE);

        return isFurnace && (belowIsFire || belowIsCampfire);
    }

    private Location getFurnaceLocation(Inventory inventory) {

        for (Location loc : lavaFurnaces.keySet()) {
            if (lavaFurnaces.get(loc).equals(inventory)) {
                return loc;
            }
        }
        return null;
    }

    private static class ItemBuilder {

        private final ItemStack stack;
        private final ItemMeta meta;

        private ItemBuilder(Material material, int amount) {
            stack = new ItemStack(material, amount);
            meta = stack.getItemMeta();
        }

        private ItemBuilder setName(String n) {
            meta.setDisplayName(t(n));
            return this;
        }

        private ItemBuilder setLore(String[] lines) {
            List<String> lore = new ArrayList<>();
            Arrays.asList(lines).forEach(line -> {
                lore.add(t(line));
            });
            meta.setLore(lore);
            return this;
        }

        private ItemBuilder addEnchantment(Enchantment enchantment, int level) {
            meta.addEnchant(enchantment, level, true);
            return this;
        }

        private ItemBuilder addFlags(ItemFlag flag) {
            meta.addItemFlags(flag);
            return this;
        }

        private ItemStack build() {
            stack.setItemMeta(meta);
            return stack;
        }
    }

    private static String t(String i) {
        return ChatColor.translateAlternateColorCodes('&', i);
    }
}

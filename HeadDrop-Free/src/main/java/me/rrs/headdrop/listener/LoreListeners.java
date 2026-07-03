package me.rrs.headdrop.listener;

import com.google.gson.JsonParseException;
import me.rrs.headdrop.HeadDrop;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.block.TileState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class LoreListeners implements Listener {

    private final NamespacedKey loreKey;
    private final NamespacedKey nameKey;

    public LoreListeners() {
        this.loreKey = new NamespacedKey(HeadDrop.getInstance(), "headdrop_lore");
        this.nameKey = new NamespacedKey(HeadDrop.getInstance(), "headdrop_name");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return;

        Block block = event.getBlockPlaced();
        if (!(block.getState() instanceof TileState tileState)) return;

        PersistentDataContainer container = tileState.getPersistentDataContainer();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        boolean updated = false;

        if (meta.hasLore() && meta.lore() != null) {
            try {
                String lore = meta.lore().stream()
                        .map(component -> GsonComponentSerializer.gson().serialize(component))
                        .collect(Collectors.joining("\n"));
                container.set(loreKey, PersistentDataType.STRING, lore);
                updated = true;
            } catch (Exception ignored) {
            }
        }

        if (meta.hasDisplayName() && meta.displayName() != null) {
            try {
                String name = GsonComponentSerializer.gson().serialize(meta.displayName());
                container.set(nameKey, PersistentDataType.STRING, name);
                updated = true;
            } catch (Exception ignored) {
            }
        }

        if (updated) {
            tileState.update();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getPlayer().getGameMode() == GameMode.CREATIVE) return;

        Block block = event.getBlock();
        if (!(block.getState() instanceof Skull skull)) return;

        PersistentDataContainer container = skull.getPersistentDataContainer();
        boolean hasLore = container.has(loreKey, PersistentDataType.STRING);
        boolean hasName = container.has(nameKey, PersistentDataType.STRING);

        if (!hasLore && !hasName) return;

        event.setDropItems(false); // Prevent default drops
        Collection<ItemStack> drops = block.getDrops(event.getPlayer().getInventory().getItemInMainHand());

        for (ItemStack drop : drops) {
            if (drop.getType() != Material.PLAYER_HEAD) continue;

            ItemMeta meta = drop.getItemMeta();
            if (meta == null) continue;

            if (hasName) {
                String nameString = container.get(nameKey, PersistentDataType.STRING);
                if (nameString != null) {
                    try {
                        meta.displayName(GsonComponentSerializer.gson().deserialize(nameString));
                    } catch (Exception ignored) {
                    }
                }
            }

            if (hasLore) {
                String loreString = container.get(loreKey, PersistentDataType.STRING);
                if (loreString != null) {
                    try {
                        String delimiter = loreString.contains("\n") ? "\n" : "§";
                        List<Component> loreComponents = Arrays.stream(loreString.split(delimiter))
                                .map(json -> {
                                    try {
                                        return GsonComponentSerializer.gson().deserialize(json);
                                    } catch (JsonParseException e) {
                                        return Component.empty();
                                    }
                                })
                                .collect(Collectors.toList());
                        meta.lore(loreComponents);
                    } catch (Exception ignored) {
                    }
                }
            }
            drop.setItemMeta(meta);
            block.getWorld().dropItemNaturally(block.getLocation(), drop);
        }
    }
}
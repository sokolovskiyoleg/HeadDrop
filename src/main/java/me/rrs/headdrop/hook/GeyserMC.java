package me.rrs.headdrop.hook;

import me.rrs.headdrop.database.EntityHead;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.api.GeyserApi;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.event.EventRegistrar;
import org.geysermc.geyser.api.event.lifecycle.GeyserDefineCustomSkullsEvent;

import java.util.ArrayList;
import java.util.List;

public class GeyserMC implements EventRegistrar {

    public static void onDefineCustomSkulls(GeyserDefineCustomSkullsEvent event) {
        for (EntityHead head : EntityHead.values()) {
            event.register(head.getHeadHash(), GeyserDefineCustomSkullsEvent.SkullTextureType.PROFILE);
        }
    }

    public static boolean isBedrockPlayer(Player player) {
        if (player == null) return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("Geyser-Spigot")) return false;
        try {
            return GeyserApi.api().isBedrockPlayer(player.getUniqueId());
        } catch (Throwable e) {
            return false;
        }
    }

    public static void openHeadGUI(Player player) {
        openHeadGUI(player, List.of(EntityHead.values()), 0);
    }

    public static void openHeadGUI(Player player, List<EntityHead> allHeads, int page) {
        if (!isBedrockPlayer(player)) return;

        GeyserConnection connection = GeyserApi.api().connectionByUuid(player.getUniqueId());
        if (connection == null) return;

        int itemsPerPage = 10;
        int totalPages = (int) Math.ceil((double) allHeads.size() / itemsPerPage);
        if (totalPages == 0) totalPages = 1;

        final int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        int start = currentPage * itemsPerPage;
        int end = Math.min(start + itemsPerPage, allHeads.size());

        SimpleForm.Builder builder = SimpleForm.builder()
                .title("Head Collection (" + (currentPage + 1) + "/" + totalPages + ")")
                .content("Total Heads: " + allHeads.size() + "\nSelect a head to claim:");

        List<Runnable> actions = new ArrayList<>();

        if (currentPage > 0) {
            builder.button("◀ Previous Page");
            actions.add(() -> openHeadGUI(player, allHeads, currentPage - 1));
        }

        for (int i = start; i < end; i++) {
            EntityHead head = allHeads.get(i);
            String displayName = head.name().replace("_", " ");
            builder.button(displayName);
            actions.add(() -> {
                if (player.hasPermission("headdrop.head")) {
                    player.getInventory().addItem(head.getSkull());
                    player.sendMessage("§aClaimed " + displayName + "!");
                } else {
                    player.sendMessage("§cYou don't have permission to claim heads!");
                }
            });
        }

        if (currentPage < totalPages - 1) {
            builder.button("▶ Next Page");
            actions.add(() -> openHeadGUI(player, allHeads, currentPage + 1));
        }

        builder.validResultHandler((form, response) -> {
            int idx = response.clickedButtonId();
            if (idx >= 0 && idx < actions.size()) {
                actions.get(idx).run();
            }
        });

        connection.sendForm(builder);
    }
}

package me.rrs.headdrop.util;

import me.rrs.discordutils.DiscordUtils;
import me.rrs.headdrop.HeadDrop;
import net.dv8tion.jda.api.EmbedBuilder;

public class Embed {
    public void msg(String title, String description, String footer) {
        try {
            String channelId = HeadDrop.getInstance().getConfiguration().getString("Bot.Channel-ID", "");
            if (channelId.isBlank()) {
                return;
            }
            if (DiscordUtils.getInstance() == null || DiscordUtils.getInstance().getJda() == null) {
                return;
            }
            var channel = DiscordUtils.getInstance().getJda().getTextChannelById(channelId);
            if (channel != null) {
                EmbedBuilder builder = new EmbedBuilder()
                        .setTitle(title)
                        .setDescription(description)
                        .setFooter(footer, null);
                channel.sendMessageEmbeds(builder.build()).queue();
            }
        } catch (NoClassDefFoundError ignore) {
            HeadDrop.getInstance().getLogger().severe("You need to install DiscordUtils for Discord notify to work!");
        } catch (Exception e) {
            HeadDrop.getInstance().getLogger().warning("Failed to send Discord embed notification: " + e.getMessage());
        }
    }
}

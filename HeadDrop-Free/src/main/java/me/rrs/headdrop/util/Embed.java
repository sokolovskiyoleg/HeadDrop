package me.rrs.headdrop.util;

import me.rrs.discordutils.DiscordUtils;
import me.rrs.headdrop.HeadDrop;
import net.dv8tion.jda.api.EmbedBuilder;

public class Embed {
    public void msg(String title, String description, String footer) {
        try {
            EmbedBuilder builder = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setFooter(footer, null);
            DiscordUtils.getInstance().getJda().getTextChannelById(HeadDrop.getInstance().getConfiguration().getString("Bot.Channel-ID")).sendMessageEmbeds(builder.build()).queue();
        } catch (NoClassDefFoundError ignore) {
            HeadDrop.getInstance().getLogger().severe("You need to install DiscordUtils for Discord notify to work!");
        }
    }
}

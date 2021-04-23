package corebot;

import arc.*;
import arc.util.*;
import arc.util.serialization.*;
import corebot.Net.*;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.events.guild.member.*;
import net.dv8tion.jda.api.events.guild.member.update.*;
import net.dv8tion.jda.api.events.message.*;
import net.dv8tion.jda.api.events.user.update.UserUpdateNameEvent;
import net.dv8tion.jda.api.hooks.*;
import net.dv8tion.jda.api.requests.*;
import net.dv8tion.jda.api.utils.*;
import net.dv8tion.jda.api.utils.cache.*;

import java.awt.*;
import java.time.*;
import java.util.Timer;
import java.util.*;

import static corebot.CoreBot.*;

public class Messages extends ListenerAdapter{
    JDA jda;
    TextChannel channel;
    User lastUser;
    Message lastMessage;
    Message lastSentMessage;
    Guild guild;
    Color normalColor = Color.decode("#FAB462");
    Color errorColor = Color.decode("#ff3838");
	private static final int[][] allowedRanges =
	    { { 0x0020, 0x007E }, { 0x00A7, 0x00A7 }, { 0x00BC, 0x00BE }, { 0x0400, 0x045F } };
	private static final int maxNickLength = 32;
	private static final String invalidNicknameMessage = "Your nickname contains characters that are not allowed." +
		" Allowed are all printable ASCII and Cyrillic characters. Your nickname has been changed to ";

    public Messages(){
        String token = System.getenv("CORE_BOT_TOKEN");
        Log.info("Found token: @", token != null);

        try{
            jda = JDABuilder.createDefault(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_EMOJIS, GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_PRESENCES)
                .setMemberCachePolicy(MemberCachePolicy.ALL).disableCache(CacheFlag.VOICE_STATE).build();
            jda.awaitReady();
            jda.addEventListener(this);
            guild = jda.getGuildById(guildID);
            
            Log.info("Started validating nicknames.");
            guild.loadMembers(this::validateNickname);

            Log.info("Discord bot up.");
            Core.net = new arc.Net();

            //mod listings are broken until further notice
            //the format is incompatible and should be enabled with the v6 update
            /*
            //mod list updater
            Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
                Core.net.httpGet("https://raw.githubusercontent.com/Anuken/MindustryMods/master/mods.json", response -> {
                    if(response.getStatus() != HttpStatus.OK){
                        return;
                    }

                    Seq<ModListing> listings = json.fromJson(Array.class, ModListing.class, response.getResultAsString());
                    listings.sort(Structs.comparing(list -> Date.from(Instant.parse(list.lastUpdated))));
                    listings.reverse();
                    listings.truncate(20);
                    listings.reverse();

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(normalColor);
                    embed.setTitle("Last Updated Mods");
                    embed.setFooter(Strings.format("Last Updated: @", DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss ZZZZ").format(ZonedDateTime.now())));
                    for(ModListing listing : listings){
                        embed.addField(listing.repo + "  " + listing.stars + "★ | "
                        + "*Updated " + durFormat(Duration.between(Instant.parse(listing.lastUpdated), Instant.now()))+ " ago*",
                        Strings.format("**[@](@)**\n@\n\n_\n_",
                        Strings.stripColors(listing.name),
                        "https://github.com/" + listing.repo,
                        Strings.stripColors(listing.description)), false);
                    }

                    guild.getTextChannelById(modChannelID).editMessageById(663246057660219413L, embed.build()).queue();
                }, Log::err);
            }, 0, 20, TimeUnit.MINUTES);
            */
        }catch(Exception e){
            throw new RuntimeException(e);
        }
    }

    private static String durFormat(Duration duration){
        if(duration.toDays() > 0) return duration.toDays() + "d";
        if(duration.toHours() > 0) return duration.toHours() + "h";
        return duration.toMinutes() + "m";
    }
    
    @Override
    public void onMessageReceived(MessageReceivedEvent event){
        try{
            commands.handle(event.getMessage());
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event){
        StringBuilder builder = new StringBuilder();
        try{
            event.getUser().openPrivateChannel().complete().sendMessage(
            "**Welcome to the Mindustry #social Discord.**" +
            "\n\n*Make sure you read #rules and the channel topics before posting.*\n\n" +
            "Note that this server has different moderation than the official Mindustry Discord. ***Your experience may vary.***"
            ).queue();
        }catch(Exception ignored){
            //may not be able to send messages to this user, ignore
        }
        builder.append("Welcome <@");
	    builder.append(event.getUser().getId());
	    builder.append("> to the #social Discord!");
        guild.getTextChannelById(CoreBot.generalChannelID).sendMessage(builder.toString()).queue();
        validateNickname(event.getMember());
    }
    
    @Override
    public void onGuildMemberUpdateNickname(GuildMemberUpdateNicknameEvent event){
        validateNickname(event.getMember());
    }

    @Override
    public void onUserUpdateName(UserUpdateNameEvent event){
        guild.retrieveMember(event.getUser()).queue(this::validateNickname, t -> {});
    }
    
    public void validateNickname(Member member){
        char[] nick = member.getEffectiveName().toCharArray();
        if(!fixNickname(nick) && guild.getSelfMember().canInteract(member)) {
            String newNick = new String(nick);
	        member.modifyNickname(newNick).queue();
            member.getUser().openPrivateChannel()
                .flatMap(c -> c.sendMessage(invalidNicknameMessage + newNick))
                .queue(m -> {}, t -> {});
    	}
    }
    
    public static boolean fixNickname(char[] nickname) {
        boolean allowed = true;
        for (int i = 0; i < nickname.length; i++) {
            boolean allowedCharacter = false;
            for (int[] range : allowedRanges) {
                if (nickname[i] >= range[0] && nickname[i] <= range[1]) {
                    allowedCharacter = true;
                    break;
                }
            }
            if (!allowedCharacter) {
                allowed = false;
                nickname[i] = '?';
            }
        }
        return allowed;
    }
    
    public void sendUpdate(VersionInfo info){
        /*String text = info.description;
        int maxLength = 2000;
        while(true){
            String current = text.substring(0, Math.min(maxLength, text.length()));
            guild
            .getTextChannelById(announcementsChannelID)
            .sendMessage(new EmbedBuilder()
            .setColor(normalColor).setTitle(info.name)
            .setDescription(current).build()).queue();

            if(text.length() < maxLength){
                return;
            }

            text = text.substring(maxLength);
        }*/
    }

    public void deleteMessages(){
        Message last = lastMessage, lastSent = lastSentMessage;

        new Timer().schedule(new TimerTask(){
            @Override
            public void run(){
                last.delete().queue();
                lastSent.delete().queue();
            }
        }, CoreBot.messageDeleteTime);
    }

    public void deleteMessage(){
        Message last = lastSentMessage;

        new Timer().schedule(new TimerTask(){
            @Override
            public void run(){
                last.delete().queue();
            }
        }, CoreBot.messageDeleteTime);
    }

    public void sendCrash(JsonValue value){

        StringBuilder builder = new StringBuilder();
        value = value.child;
        while(value != null){
            builder.append("**");
            builder.append(value.name);
            builder.append("**");
            builder.append(": ");
            if(value.name.equals("trace")){
                builder.append("```xl\n"); //xl formatting looks nice
                builder.append(value.asString().replace("\\n", "\n").replace("\t", "  "));
                builder.append("```");
            }else{
                builder.append(value.asString());
            }
            builder.append("\n");
            value = value.next;
        }
        guild.getTextChannelById(CoreBot.crashReportChannelID).sendMessage(builder.toString()).queue();
    }

    public void text(String text, Object... args){
        lastSentMessage = channel.sendMessage(format(text, args)).complete();
    }

    public void info(String title, String text, Object... args){
        MessageEmbed object = new EmbedBuilder()
        .addField(title, format(text, args), true).setColor(normalColor).build();

        lastSentMessage = channel.sendMessage(object).complete();
    }

    public void err(String text, Object... args){
        err("Error", text, args);
    }

    public void err(String title, String text, Object... args){
        MessageEmbed e = new EmbedBuilder()
        .addField(title, format(text, args), true).setColor(errorColor).build();
        lastSentMessage = channel.sendMessage(e).complete();
    }

    private String format(String text, Object... args){
        return Strings.format(text, args);
    }
}

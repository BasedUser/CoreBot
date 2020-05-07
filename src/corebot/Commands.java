package corebot;

import arc.files.*;
import arc.math.*;
import arc.struct.*;
import arc.util.*;
import arc.util.CommandHandler.*;
import arc.util.io.*;
import arc.util.serialization.*;
import arc.util.serialization.Jval.*;
import corebot.ContentHandler.Map;
import mindustry.*;
import mindustry.game.*;
import mindustry.type.*;
import net.dv8tion.jda.api.*;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.Message.*;
import net.dv8tion.jda.api.events.message.react.*;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

import javax.imageio.*;
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.net.*;
import java.util.List;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

import static corebot.CoreBot.*;

public class Commands{
    private final String prefix = "!";
    private CommandHandler handler = new CommandHandler(prefix);
    private CommandHandler adminHandler = new CommandHandler(prefix);
    private String[] warningStrings = {"once", "twice", "thrice", "too many times"};
    private Pattern invitePattern = Pattern.compile("(discord\\.gg/\\w|discordapp\\.com/invite/\\w)");

    Commands(){
        handler.register("help", "Displays all bot commands.", args -> {
            if(messages.lastMessage.getChannel().getName().equalsIgnoreCase("multiplayer")){
                messages.err("Use this command in #bots.");
                messages.deleteMessages();
                return;
            }

            StringBuilder builder = new StringBuilder();
            for(Command command : handler.getCommandList()){
                builder.append(prefix);
                builder.append("**");
                builder.append(command.text);
                builder.append("**");
                if(command.params.length > 0){
                    builder.append(" *");
                    builder.append(command.paramText);
                    builder.append("*");
                }
                builder.append(" - ");
                builder.append(command.description);
                builder.append("\n");
            }

            messages.info("Commands", builder.toString());
        });

        handler.register("ping", "<ip>", "Pings a server.", args -> {
            if(!messages.lastMessage.getChannel().getName().equalsIgnoreCase("bots")){
                messages.err("Use this command in #bots.");
                messages.deleteMessages();
                return;
            }

            net.pingServer(args[0], result -> {
                if(result.name != null){
                    messages.info("Server Online", "Host: @\nPlayers: @\nMap: @\nWave: @\nVersion: @\nPing: @ms",
                    result.name, result.players, result.mapname, result.wave, result.version, result.ping);
                }else{
                    messages.err("Server Offline", "Timed out.");
                }
            });
        });

        handler.register("info", "<topic>", "Displays information about a topic.", args -> {
            try{
                Info info = Info.valueOf(args[0]);
                messages.info(info.title, info.text);
            }catch(IllegalArgumentException e){
                e.printStackTrace();
                messages.err("Error", "Invalid topic '@'.\nValid topics: *@*", args[0], Arrays.toString(Info.values()));
                messages.deleteMessages();
            }
        });


        handler.register("postplugin", "<github-url>", "Post a plugin via Github repository URL.", args -> {
            if(!args[0].startsWith("https") || !args[0].contains("github")){
                messages.err("That's not a valid Github URL.");
            }else{
                try{
                    Document doc = Jsoup.connect(args[0]).get();

                    EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).
                    setColor(messages.normalColor)
                    .setAuthor(messages.lastUser.getName(), messages.lastUser.getAvatarUrl(), messages.lastUser.getAvatarUrl()).setTitle(doc.select("strong[itemprop=name]").text());

                    Elements elem = doc.select("span[itemprop=about]");
                    if(!elem.isEmpty()){
                        builder.addField("About", elem.text(), false);
                    }

                    builder.addField("Link", args[0], false);

                    builder.addField("Downloads", args[0] + (args[0].endsWith("/") ? "" : "/") + "releases", false);

                    messages.channel.getGuild().getTextChannelById(pluginChannelID).sendMessage(builder.build()).queue();

                    messages.text("*Plugin posted.*");
                }catch(IOException e){
                    e.printStackTrace();
                    messages.err("Failed to fetch plugin info from URL.");
                }
            }
        });

        handler.register("postmap", "Post a .msav file to the #maps channel.", args -> {
            Message message = messages.lastMessage;

            if(message.getAttachments().size() != 1 || !message.getAttachments().get(0).getFileName().endsWith(".msav")){
                messages.err("You must have one .msav file in the same message as the command!");
                messages.deleteMessages();
                return;
            }

            Attachment a = message.getAttachments().get(0);

            try{
                Map map = contentHandler.parseMap(net.download(a.getUrl()));
                new File("maps/").mkdir();
                File mapFile = new File("maps/" + a.getFileName());
                File imageFile = new File("maps/image_" + a.getFileName().replace(".msav", ".png"));
                Streams.copy(net.download(a.getUrl()), new FileOutputStream(mapFile));
                ImageIO.write(map.image, "png", imageFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).setColor(messages.normalColor)
                .setImage("attachment://" + imageFile.getName())

                .setAuthor(messages.lastUser.getName(), messages.lastUser.getAvatarUrl(), messages.lastUser.getAvatarUrl()).setTitle(map.name == null ? a.getFileName().replace(".msav", "") : map.name);

                if(map.description != null) builder.setFooter(map.description);

                messages.channel.getGuild().getTextChannelById(mapsChannelID).sendFile(mapFile).addFile(imageFile).embed(builder.build()).queue();

                messages.text("*Map posted successfully.*");
            }catch(Exception e){
                e.printStackTrace();
                messages.err("Error parsing map.", Strings.parseException(e, true));
                messages.deleteMessages();
            }
        });

        handler.register("google", "<phrase...>", "Let me google that for you.", args -> {
            try{
                messages.text("http://lmgtfy.com/?q=@", URLEncoder.encode(args[0], "UTF-8"));
            }catch(UnsupportedEncodingException e){
                e.printStackTrace();
            }
        });

        handler.register("addserver", "<IP>", "Add your server to list. Must be online and 24/7.", args -> {
            Array<String> servers = prefs.getArray("servers");
            if(servers.contains(args[0])){
                messages.err("That server is already in the list.");
            }else{
                TextChannel channel = messages.channel;
                Member mem = messages.lastMessage.getMember();
                net.pingServer(args[0], res -> {
                    if(res.name != null){
                        servers.add(args[0]);
                        prefs.putArray("servers", servers);
                        prefs.put("owner-" + args[0], mem.getId());
                        channel.sendMessage("*Server added.*").queue();
                    }else{
                        channel.sendMessage("*That server is offline or cannot be reached.*").queue();
                    }
                });
            }
        });

        handler.register("getposter", "<IP>", "Get who posted a server. This may not necessarily be the owner.", args -> {
            Array<String> servers = prefs.getArray("servers");
            String key = "owner-" + args[0];
            if(!servers.contains(args[0])){
                messages.err("That server doesn't exist.");
                messages.deleteMessages();
            }else if(prefs.get(key, null) == null){
                messages.err("That server doesn't have a registered poster or maintainer.");
                messages.deleteMessages();
            }else{
                User user = messages.jda.getUserById(prefs.get(key, null));
                if(user != null){
                    messages.info("Owner of: " + args[0], "@#@", user.getName(), user.getDiscriminator());
                }else{
                    messages.err("Use lookup failed. Internal error, or the user may have left the server.");
                    messages.deleteMessages();
                }
            }
        });

        handler.register("cleanmod", "Clean up a modded zip archive. Changes json into hjson and formats code.", args -> {
            Message message = messages.lastMessage;

            if(message.getAttachments().size() != 1 || !message.getAttachments().get(0).getFileName().endsWith(".zip")){
                messages.err("You must have one .zip file in the same message as the command!");
                messages.deleteMessages();
                return;
            }

            Attachment a = message.getAttachments().get(0);

            if(a.getSize() > 1024 * 1024 * 6){
                messages.err("Zip files may be no more than 6 MB.");
                messages.deleteMessages();
            }

            try{
                new File("mods/").mkdir();
                File baseFile = new File("mods/" + a.getFileName());
                Fi destFolder = new Fi("dest_mod" + a.getFileName());
                Fi destFile = new Fi("mods/" + new Fi(baseFile).nameWithoutExtension() + "-cleaned.zip");

                if(destFolder.exists()) destFolder.deleteDirectory();
                if(destFile.exists()) destFile.delete();

                Streams.copy(net.download(a.getUrl()), new FileOutputStream(baseFile));
                ZipFi zip = new ZipFi(new Fi(baseFile.getPath()));
                zip.walk(file -> {
                    Fi output = destFolder.child(file.extension().equals("json") ? file.pathWithoutExtension() + ".hjson" : file.path());
                    output.parent().mkdirs();

                    if(file.extension().equals("json") || file.extension().equals("hjson")){
                        output.writeString(fixJval(Jval.read(file.readString())).toString(Jformat.hjson));
                    }else{
                        file.copyTo(output);
                    }
                });

                try(OutputStream fos = destFile.write(false, 2048); ZipOutputStream zos = new ZipOutputStream(fos)){
                    for(Fi add : destFolder.findAll(f -> true)){
                        if(add.isDirectory()) continue;
                        zos.putNextEntry(new ZipEntry(add.path().substring(destFolder.path().length())));
                        Streams.copy(add.read(), zos);
                        zos.closeEntry();
                    }

                }

                messages.channel.sendFile(destFile.file()).queue();

                messages.text("*Mod converted successfully.*");
            }catch(Throwable e){
                e.printStackTrace();
                messages.err("Error parsing mod.", Strings.parseException(e, false));
                messages.deleteMessages();
            }
        });

        adminHandler.register("delete", "<amount>", "Delete some messages.", args -> {
            try{
                int number = Integer.parseInt(args[0]) + 1;
                MessageHistory hist = messages.channel.getHistoryBefore(messages.lastMessage, number).complete();
                messages.channel.deleteMessages(hist.getRetrievedHistory()).queue();
                Log.info("Deleted @ messages.", number);
            }catch(NumberFormatException e){
                messages.err("Invalid number.");
            }
        });

        adminHandler.register("listservers", "List servers pinged automatically.", args -> {
            messages.text("**Servers:** @", prefs.getArray("servers").toString().replace("[", "").replace("]", ""));
        });

        adminHandler.register("removeserver", "<IP>", "Remove server from list.", args -> {
            Array<String> servers = prefs.getArray("servers");
            boolean removed = servers.remove(args[0], false);
            prefs.putArray("servers", servers);
            if(removed){
                messages.text("*Server removed.*");
            }else{
                messages.err("Server not found!");
            }
        });

        adminHandler.register("warn", "<@user> [reason...]", "Warn a user.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);
            try{
                long l = Long.parseLong(author);
                User user = messages.jda.getUserById(l);
                int warnings = prefs.getInt("warnings-" + l, 0) + 1;
                messages.text("**@**, you've been warned *@*.", user.getAsMention(), warningStrings[Mathf.clamp(warnings - 1, 0, warningStrings.length - 1)]);
                prefs.put("warnings-" + l, warnings + "");
                if(warnings >= 3){
                    messages.lastMessage.getGuild().getTextChannelById(moderationChannelID)
                    .sendMessage("User " + user.getAsMention() + " has been warned 3 or more times!").queue();
                }
            }catch(Exception e){
                e.printStackTrace();
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });

        adminHandler.register("warnings", "<@user>", "Get number of warnings a user has.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);
            try{
                long l = Long.parseLong(author);
                User user = messages.jda.getUserById(l);
                int warnings = prefs.getInt("warnings-" + l, 0);
                messages.text("User '@' has **@** @.", user.getName(), warnings, warnings == 1 ? "warning" : "warnings");
            }catch(Exception e){
                e.printStackTrace();
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });

        adminHandler.register("clearwarnings", "<@user>", "Clear number of warnings for a person.", args -> {
            String author = args[0].substring(2, args[0].length() - 1);
            if(author.startsWith("!")) author = author.substring(1);
            try{
                long l = Long.parseLong(author);
                User user = messages.jda.getUserById(l);
                prefs.put("warnings-" + l, 0 + "");
                messages.text("Cleared warnings for user '@'.", user.getName());
            }catch(Exception e){
                e.printStackTrace();
                messages.err("Incorrect name format.");
                messages.deleteMessages();
            }
        });
    }

    private Jval fixJval(Jval val){
        if(val.isArray()){
            Array<Jval> list = val.asArray().copy();
            for(Jval child : list){
                if(child.isObject() && (child.has("item")) && child.has("amount")){
                    val.asArray().remove(child);
                    val.asArray().add(Jval.valueOf(child.getString("item", child.getString("liquid", "")) + "/" + child.getInt("amount", 0)));
                }else{
                    fixJval(child);
                }
            }
        }else if(val.isObject()){
            Array<String> keys = val.asObject().keys().toArray();

            for(String key : keys){
                Jval child = val.get(key);
                if(child.isObject() && (child.has("item")) && child.has("amount")){
                    val.remove(key);
                    val.add(key, Jval.valueOf(child.getString("item", child.getString("liquid", "")) + "/" + child.getInt("amount", 0)));
                }else{
                    fixJval(child);
                }
            }
        }

        return val;
    }

    boolean isAdmin(User user){
        try{
            return messages.guild.getMember(user).getRoles().stream().anyMatch(role -> role.getName().equals("Developer") || role.getName().equals("Moderator"));
        }catch(Exception e){
            return false; //I don't care enough to fix this
        }
    }

    void sendReportTemplate(Message message){
        messages.err("**Do not send messages here unless you are reporting an issue!**\nTo report an issue, follow the template provided in **!info bugs**.\nTo report a crash, send the crash report text file.");
        messages.deleteMessages();
    }

    void checkForReport(Message message){
        if(!message.getAttachments().isEmpty()){

            if(emptyText(message)){
                messages.err("Please do not send images or other unrelated files in this channel.");
                messages.deleteMessages();
            }else{
                checkForIssue(message);
            }
        }else if(emptyText(message)){
            sendReportTemplate(message);
        }else{
            checkForIssue(message);
        }
    }

    void checkForIssue(Message message){
        String text = message.getContentRaw();
        String[] required = {"Platform:", "Build:", "Issue:"};
        String[] split = text.split("\n");

        if(split.length == 0){
            sendReportTemplate(message);
            return;
        }

        //get used entries
        Array<String> arr = Array.with(required);
        for(String s : split){
            for(String req : required){
                if(s.toLowerCase().startsWith(req.toLowerCase()) && s.length() > req.length()){
                    arr.remove(req, false);

                    //special case: don't let people report a build as a version such as 4.0/3.5
                    if(s.toLowerCase().startsWith("build:")){
                        String buildText = s.substring("Build:".length());
                        String test = buildText.toLowerCase().trim();
                        String errormessage = null;
                        if(buildText.contains("4.") || buildText.contains("3.") || buildText.toLowerCase().contains("latest")){
                            errormessage = "The build you specified is incorrect!\nWrite **only the build/commit number in the bottom left corner of the menu**, not the version. *(for example, build 47, not 4.0)*.\n*Copy and re-send your message with a corrected report.*";
                        }else if(test.equals("be") || test.equals("bleeding edge") || test.equals("bleedingedge")){
                            errormessage = "Invalid bleeding edge version!\n**Only write the bleeding edge commit number displayed in the bottom left corner of the menu (or the or Jenkins build number).**\n*Copy and re-send your message with a corrected report.*";
                        }

                        if(errormessage != null){
                            messages.err(errormessage);
                            messages.deleteMessages();
                            return;
                        }
                    }else if(s.toLowerCase().startsWith("platform:")){
                        String platformText = s.substring("Platform:".length()).toLowerCase().trim();
                        if(!(platformText.contains("windows") || platformText.contains("mac") || platformText.contains("osx") || platformText.contains("linux") || platformText.contains("android") || platformText.contains("ios") || platformText.contains("iphone"))){
                            messages.err("**Invalid platform: '" + platformText + "'**.\nPlatform must be one of the following: `windows/linux/mac/osx/android/ios`.\n*Copy and re-send your message with a corrected report.*");
                            messages.deleteMessages();
                            return;
                        }
                    }
                }
            }
        }

        //validate entries
        if(arr.size == required.length){
            sendReportTemplate(message);
            return;
        }

        //validate all entries present
        if(arr.size != 0){
            messages.err("Error", "Your issue report is incomplete. You have not provided: *@*.\n*Copy and re-send your message with a corrected report.*", arr.toString());
            messages.deleteMessages();
            return;
        }

        //validate template text
        if(text.contains("<Android/iOS/Mac/Windows/Linux>") || text.contains("<Post the build number in the bottom left corner of main menu>")
        || text.contains("<What goes wrong. Be specific!>") || text.contains("<Provide details on what you were doing when this bug occurred, as well as any other helpful information.>")){
            messages.err("You have not filled in your issue report! Make sure you've replaced all template text properly.\n*Copy and re-send your message with a corrected report.*");
            messages.deleteMessages();
            return;
        }

        messages.text("*Issue reported successfully.*");
        messages.deleteMessage();
    }

    boolean emptyText(Message message){
        return message.getContentRaw() == null || message.getContentRaw().isEmpty();
    }

    boolean checkInvite(Message message){
        if(message.getContentRaw() != null && invitePattern.matcher(message.getContentRaw()).find() && !isAdmin(message.getAuthor())){
            Log.warn("User @ just sent a discord invite in @.", message.getAuthor().getName(), message.getChannel().getName());
            message.delete().queue();
            message.getAuthor().openPrivateChannel().complete().sendMessage("Do not send invite links in the Mindustry Discord server! Read the rules.").queue();
            return true;
        }
        return false;
    }

    void edited(Message message){
        messages.logTo("------\n**@#@** just edited a message.\n\n*From*: \"@\"\n*To*: \"@\"", message.getAuthor().getName(), message.getAuthor().getDiscriminator(), "<broken>", message.getContentRaw());
        checkInvite(message);
    }

    void deleted(Message message){
        if(message == null || message.getAuthor() == null) return;
        messages.logTo("------\n**@#@** just deleted a message.\n *Text:* \"@\"", message.getAuthor().getName(), message.getAuthor().getDiscriminator(), message.getContentRaw());
    }

    void handleBugReact(MessageReactionAddEvent event){
        EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor);
        String url = Strings.format("https://discordapp.com/channels/@/@/@",
            event.getGuild().getId(), event.getChannel().getId(), event.getMessageId());

        String emoji = event.getReaction().getReactionEmote().getName();
        Log.info("Recieved react emoji -> @, message @", emoji, url);
        boolean valid = true, delete = false;
        if(emoji.equals("✅")){
            Log.info("| Solved.");
            builder.setColor(Color.decode("#87FF4B"));
            builder.setDescription("[Your bug report](" + url + ") in the Mindustry Discord has been marked as solved.");
        }else if(emoji.equals("❌")){
            Log.info("| Not a bug.");
            builder.setColor(messages.errorColor);
            builder.setDescription("[Your bug report]("+url+") in the Mindustry Discord has been marked as **not a bug** (intentional or unfixable behavior).");
        }else if(emoji.equals("\uD83C\uDDE9")){
            Log.info("| Duplicate.");
            builder.setColor(messages.errorColor);
            builder.setDescription("Your bug report in the Mindustry Discord has been marked as a **duplicate**: Someone has reported this issue before.\nYour report has been removed to clean up the channel.\n\nReport deleted: ```" +
                event.getChannel().retrieveMessageById(event.getMessageId()).complete().getContentStripped() + "```");
            delete = true;
        }else{
            Log.info("| Unknown reaction.");
            valid = false;
        }

        if(valid){
            event.getChannel().retrieveMessageById(event.getMessageId()).complete().getAuthor()
            .openPrivateChannel().complete().sendMessage(builder.build()).queue();
        }

        if(delete){
            event.getChannel().deleteMessageById(event.getMessageId()).queue();
        }
    }

    void handle(Message message){
        if(message.getAuthor().isBot()) return;

        if(checkInvite(message)){
            return;
        }

        if(isAdmin(message.getAuthor()) && message.getChannel().getIdLong() == commandChannelID){
            server.send(message.getContentRaw());
            return;
        }

        if(message.getChannel().getIdLong() == bugReportChannelID && !message.getAuthor().isBot() && !isAdmin(message.getAuthor())){
            messages.channel = message.getTextChannel();
            messages.lastUser = message.getAuthor();
            messages.lastMessage = message;
            checkForReport(message);
            return;
        }

        if(message.getChannel().getIdLong() == screenshotsChannelID && message.getAttachments().isEmpty()){
            message.delete().queue();
            try{
                message.getAuthor().openPrivateChannel().complete().sendMessage("Don't send messages without images in the #screenshots channel.").queue();
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }

        String text = message.getContentRaw();

        if(message.getContentRaw().startsWith(prefix)){
            messages.channel = message.getTextChannel();
            messages.lastUser = message.getAuthor();
            messages.lastMessage = message;
        }

        //schematic preview
        if((message.getContentRaw().startsWith(ContentHandler.schemHeader) && message.getAttachments().isEmpty()) ||
        (message.getAttachments().size() == 1 && message.getAttachments().get(0).getFileExtension() != null && message.getAttachments().get(0).getFileExtension().equals(Vars.schematicExtension))){
            try{
                Schematic schem = message.getAttachments().size() == 1 ? contentHandler.parseSchematicURL(message.getAttachments().get(0).getUrl()) : contentHandler.parseSchematic(message.getContentRaw());
                BufferedImage preview = contentHandler.previewSchematic(schem);

                File previewFile = new File("img_" + UUID.randomUUID().toString() + ".png");
                File schemFile = new File(schem.name() + "." + Vars.schematicExtension);
                Schematics.write(schem, new Fi(schemFile));
                ImageIO.write(preview, "png", previewFile);

                EmbedBuilder builder = new EmbedBuilder().setColor(messages.normalColor).setColor(messages.normalColor)
                .setImage("attachment://" + previewFile.getName())
                .setAuthor(message.getAuthor().getName(), message.getAuthor().getAvatarUrl(), message.getAuthor().getAvatarUrl()).setTitle(schem.name());

                StringBuilder field = new StringBuilder();

                for(ItemStack stack : schem.requirements()){
                    List<Emote> emotes = messages.guild.getEmotesByName(stack.item.name.replace("-", ""), true);
                    Emote result = emotes.isEmpty() ? messages.guild.getEmotesByName("ohno", true).get(0) : emotes.get(0);

                    field.append(result.getAsMention()).append(stack.amount).append("  ");
                }
                builder.addField("Requirements", field.toString(), false);

                message.getChannel().sendFile(schemFile).addFile(previewFile).embed(builder.build()).queue();
                message.delete().queue();
            }catch(Throwable e){
                if(message.getTextChannel().getIdLong() == schematicsChannelID){
                    message.delete().queue();
                    try{
                        message.getAuthor().openPrivateChannel().complete().sendMessage("Invalid schematic: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " (" + e.getMessage() + ")")).queue();
                    }catch(Exception e2){
                        e2.printStackTrace();
                    }
                }

                Log.err("Failed to parse schematic, skipping.");
                Log.err(e);
            }
        }else if(message.getTextChannel().getIdLong() == schematicsChannelID && !isAdmin(message.getAuthor())){
            message.delete().queue();
            try{
                message.getAuthor().openPrivateChannel().complete().sendMessage("Only send valid schematics in the #schematics channel. You may send them either as clipboard text or as a schematic file.").queue();
            }catch(Exception e){
                e.printStackTrace();
            }
            return;
        }

        if(isAdmin(message.getAuthor())){
            boolean unknown = handleResponse(adminHandler.handleMessage(text), false);
            handleResponse(handler.handleMessage(text), !unknown);
        }else{
            handleResponse(handler.handleMessage(text), true);
        }
    }

    boolean handleResponse(CommandResponse response, boolean logUnknown){
        if(response.type == ResponseType.unknownCommand){
            if(logUnknown){
                messages.err("Unknown command. Type !help for a list of commands.");
                messages.deleteMessages();
            }
            return false;
        }else if(response.type == ResponseType.manyArguments || response.type == ResponseType.fewArguments){
            if(response.command.params.length == 0){
                messages.err("Invalid arguments.", "Usage: @@", prefix, response.command.text);
                messages.deleteMessages();
            }else{
                messages.err("Invalid arguments.", "Usage: @@ *@*", prefix, response.command.text, response.command.paramText);
                messages.deleteMessages();
            }
            return false;
        }
        return true;
    }

}

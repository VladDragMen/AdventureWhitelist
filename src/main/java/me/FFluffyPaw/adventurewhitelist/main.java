package me.FFluffyPaw.adventurewhitelist;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.ChatColor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class main extends JavaPlugin implements Listener {

    private File whitelistFile;
    private FileConfiguration whitelistConfig;
    private Set<String> unreadApprovals = new HashSet<>();
    private Set<String> remindedPlayers = new HashSet<>();
    private boolean whitelistEnabled = true;

    private final List<Material> functionalBlocks = Arrays.asList(
            Material.CHEST,
            Material.FURNACE,
            Material.BLAST_FURNACE,
            Material.SMOKER,
            Material.BARREL,
            Material.DISPENSER,
            Material.DROPPER,
            Material.HOPPER,
            Material.BREWING_STAND,
            Material.ANVIL,
            Material.CHIPPED_ANVIL,
            Material.DAMAGED_ANVIL,
            Material.ENCHANTING_TABLE,
            Material.ENDER_CHEST,
            Material.SHULKER_BOX,
            Material.BLACK_SHULKER_BOX,
            Material.BLUE_SHULKER_BOX,
            Material.BROWN_SHULKER_BOX,
            Material.CYAN_SHULKER_BOX,
            Material.GRAY_SHULKER_BOX,
            Material.GREEN_SHULKER_BOX,
            Material.LIGHT_BLUE_SHULKER_BOX,
            Material.LIGHT_GRAY_SHULKER_BOX,
            Material.LIME_SHULKER_BOX,
            Material.MAGENTA_SHULKER_BOX,
            Material.ORANGE_SHULKER_BOX,
            Material.PINK_SHULKER_BOX,
            Material.PURPLE_SHULKER_BOX,
            Material.RED_SHULKER_BOX,
            Material.WHITE_SHULKER_BOX,
            Material.YELLOW_SHULKER_BOX,
            Material.LECTERN,
            Material.SMITHING_TABLE,
            Material.GRINDSTONE,
            Material.STONECUTTER,
            Material.LOOM,
            Material.CARTOGRAPHY_TABLE,
            Material.COMPOSTER,
            Material.BEACON,
            Material.CAULDRON,
            Material.FLOWER_POT
    );

    @Override
    public void onEnable() {
        createWhitelistFile();
        saveDefaultConfig();
        whitelistEnabled = getConfig().getBoolean("whitelist-enabled", true);
        unreadApprovals.addAll(getConfig().getStringList("unread-approvals"));
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("awl").setExecutor(this);
        startReminderTask();
        getLogger().info("AdventureWhitelist v1.4 успешно запущен!");
        getLogger().info("Текущий статус: " + (whitelistEnabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН"));
    }

    @Override
    public void onDisable() {
        getConfig().set("whitelist-enabled", whitelistEnabled);
        getConfig().set("unread-approvals", new ArrayList<>(unreadApprovals));
        saveConfig();
    }

    private void createWhitelistFile() {
        whitelistFile = new File(getDataFolder(), "whitelist.yml");
        if (!whitelistFile.exists()) {
            whitelistFile.getParentFile().mkdirs();
            saveResource("whitelist.yml", false);
        }
        whitelistConfig = YamlConfiguration.loadConfiguration(whitelistFile);
    }

    private void startReminderTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!whitelistEnabled) return;

                for (Player player : getServer().getOnlinePlayers()) {
                    String name = player.getName().toLowerCase();
                    if (!isWhitelisted(name) && !remindedPlayers.contains(name)) {
                        sendReminderMessage(player);
                        remindedPlayers.add(name);
                    }
                }
            }
        }.runTaskTimer(this, 0, 20 * 10);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        String playerName = player.getName().toLowerCase();

        if (!whitelistEnabled) {
            player.setGameMode(GameMode.SURVIVAL);
            return;
        }

        if (isWhitelisted(playerName)) {
            if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
            }

            if (unreadApprovals.contains(playerName)) {
                player.sendMessage(getMessage("add-admin-awl"));
                unreadApprovals.remove(playerName);
            }
        } else {
            player.setGameMode(GameMode.ADVENTURE);
            player.sendMessage(getMessage("join-not-whitelisted"));
            remindedPlayers.remove(playerName);
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (!whitelistEnabled) return;

        Player player = e.getPlayer();
        if (!isWhitelisted(player.getName().toLowerCase())) {
            e.setCancelled(true);
            player.sendMessage(getMessage("chat-blocked"));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent e) {
        if (!whitelistEnabled) return;

        if (e.getDamager() instanceof Player) {
            Player player = (Player) e.getDamager();
            if (!isWhitelisted(player.getName().toLowerCase())) {
                e.setCancelled(true);
                player.sendMessage(getMessage("combat-blocked"));
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (!whitelistEnabled) return;

        Player player = e.getPlayer();
        if (isWhitelisted(player.getName().toLowerCase())) return;

        Block clickedBlock = e.getClickedBlock();
        if (clickedBlock == null || !functionalBlocks.contains(clickedBlock.getType())) return;

        if (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        e.setCancelled(true);
        sendInteractionBlockedMessage(player);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("awl")) {
            if (!sender.hasPermission("awl.manage")) {
                sender.sendMessage(getMessage("no-permission"));
                return true;
            }

            if (args.length == 0) {
                sender.sendMessage(getMessage("command-usage"));
                return true;
            }

            String action = args[0].toLowerCase();
            switch (action) {
                case "on":
                    whitelistEnabled = true;
                    sender.sendMessage(getMessage("whitelist-on"));
                    saveConfig();
                    break;

                case "off":
                    whitelistEnabled = false;
                    sender.sendMessage(getMessage("whitelist-off"));
                    for (Player player : getServer().getOnlinePlayers()) {
                        player.setGameMode(GameMode.SURVIVAL);
                    }
                    saveConfig();
                    break;

                case "status":
                    sender.sendMessage("§7Статус вайтлиста: " + (whitelistEnabled ? "§aВКЛЮЧЕН" : "§cВЫКЛЮЧЕН"));
                    break;

                case "add":
                    if (args.length < 2) {
                        sender.sendMessage(getMessage("command-usage"));
                        return true;
                    }
                    addToWhitelist(args[1].toLowerCase(), sender);
                    break;

                case "remove":
                    if (args.length < 2) {
                        sender.sendMessage(getMessage("command-usage"));
                        return true;
                    }
                    removeFromWhitelist(args[1].toLowerCase());
                    sender.sendMessage(getMessage("removed-from-whitelist").replace("{player}", args[1]));
                    break;

                case "list":
                    sender.sendMessage("§eИгроки в вайтлисте: §a" + String.join(", ", getWhitelist()));
                    break;

                default:
                    sender.sendMessage(getMessage("command-usage"));
            }
            return true;
        }
        return false;
    }

    private void addToWhitelist(String playerName, CommandSender sender) {
        List<String> players = whitelistConfig.getStringList("players");
        if (!players.contains(playerName)) {
            players.add(playerName);
            whitelistConfig.set("players", players);
            saveWhitelist();

            Player target = getServer().getPlayerExact(playerName);
            if (target != null) {
                target.setGameMode(GameMode.SURVIVAL);
                target.sendMessage(getMessage("add-admin-awl"));
            } else {
                unreadApprovals.add(playerName);
            }
            sender.sendMessage(getMessage("added-to-whitelist").replace("{player}", playerName));
        }
    }

    private void removeFromWhitelist(String playerName) {
        List<String> players = whitelistConfig.getStringList("players");
        if (players.remove(playerName)) {
            whitelistConfig.set("players", players);
            saveWhitelist();

            Player target = getServer().getPlayerExact(playerName);
            if (target != null && whitelistEnabled) {
                target.setGameMode(GameMode.ADVENTURE);
                target.sendMessage(getMessage("join-not-whitelisted"));
            }
        }
    }

    private boolean isWhitelisted(String playerName) {
        return whitelistConfig.getStringList("players").contains(playerName);
    }

    private List<String> getWhitelist() {
        return whitelistConfig.getStringList("players");
    }

    private void saveWhitelist() {
        try {
            whitelistConfig.save(whitelistFile);
        } catch (IOException e) {
            getLogger().severe("Ошибка сохранения whitelist.yml: " + e.getMessage());
        }
    }

    private String getMessage(String path) {
        return getConfig().getString("messages." + path, "§c[Ошибка: сообщение не найдено]");
    }

    private void sendInteractionBlockedMessage(Player player) {
        TextComponent message = new TextComponent(getMessage("interaction-blocked"));
        message.setColor(ChatColor.RED);
        player.spigot().sendMessage(message);
    }

    private void sendReminderMessage(Player player) {
        String reminderText = getMessage("reminder");
        TextComponent message = new TextComponent(reminderText);

        if (reminderText.contains("https://") || reminderText.contains("t.me/")) {
            int linkStart = reminderText.indexOf("http");
            int linkEnd = reminderText.length();
            String link = reminderText.substring(linkStart, linkEnd);

            message = new TextComponent(reminderText.substring(0, linkStart));
            TextComponent linkComponent = new TextComponent(link);
            linkComponent.setColor(ChatColor.GOLD);
            linkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, link));
            message.addExtra(linkComponent);
        }

        player.spigot().sendMessage(message);
    }
}
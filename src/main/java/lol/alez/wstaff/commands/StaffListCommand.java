package lol.alez.wstaff.commands;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class StaffListCommand implements CommandExecutor {

    private final LuckPerms luckPerms;
    private final Plugin plugin;
    private final Map<String, Integer> roleHierarchy = new HashMap<>();
    private final Map<String, String> roleColors = new HashMap<>();
    private final Map<String, String> roleDisplayNames = new HashMap<>();
    private final Map<String, String> rolePermissions = new HashMap<>();
    private boolean useGroups;
    private boolean hideVanished;

    public StaffListCommand(Plugin plugin) {
        this.plugin = plugin;

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
        } else {
            luckPerms = null;
        }

        loadConfig();
    }

    private void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        useGroups = config.getBoolean("detection-method.use-groups", true);
        hideVanished = config.getBoolean("vanish.hide-from-list", true);

        ConfigurationSection rolesSection = config.getConfigurationSection("staff-roles");

        if (rolesSection != null) {
            for (String key : rolesSection.getKeys(false)) {
                ConfigurationSection roleSection = rolesSection.getConfigurationSection(key);
                if (roleSection != null) {
                    String groupName = roleSection.getString("group-name", key);
                    String permission = roleSection.getString("permission", "staff." + key);
                    int priority = roleSection.getInt("priority", 0);
                    String color = roleSection.getString("color", "WHITE");
                    String displayName = roleSection.getString("display-name", key);

                    roleHierarchy.put(groupName.toLowerCase(), priority);
                    roleColors.put(groupName.toLowerCase(), color);
                    roleDisplayNames.put(groupName.toLowerCase(), displayName);
                    rolePermissions.put(groupName.toLowerCase(), permission);
                }
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("sl")) {
            return false;
        }

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();

        Map<String, List<Player>> staffByRole = new TreeMap<>((role1, role2) -> {
            return Integer.compare(roleHierarchy.getOrDefault(role2, 0), roleHierarchy.getOrDefault(role1, 0));
        });

        for (Player player : onlinePlayers) {
            if (hideVanished && isVanished(player)) {
                continue;
            }
            String role = useGroups ? getPlayerGroupRole(player) : getPlayerPermissionRole(player);
            if (role != null) {
                staffByRole.computeIfAbsent(role, k -> new ArrayList<>()).add(player);
            }
        }

        if (staffByRole.isEmpty()) {
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.header", "&6Lista dello staff online:")));
            sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("messages.no-staff", "&7Nessun membro dello staff è attualmente online.")));
            return true;
        }

        sender.sendMessage(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("messages.header", "&6Lista dello staff online:")));

        for (Map.Entry<String, List<Player>> entry : staffByRole.entrySet()) {
            String role = entry.getKey();
            List<Player> players = entry.getValue();

            String colorCode = roleColors.getOrDefault(role, "WHITE");
            ChatColor roleColor = getChatColor(colorCode);
            String displayName = roleDisplayNames.getOrDefault(role, role);

            StringBuilder roleHeader = new StringBuilder();
            roleHeader.append(ChatColor.GRAY).append("▪ ");
            roleHeader.append(roleColor).append(displayName);
            roleHeader.append(ChatColor.GRAY).append(" » ");

            for (int i = 0; i < players.size(); i++) {
                Player player = players.get(i);
                if (i > 0) {
                    roleHeader.append(ChatColor.GRAY).append(", ");
                }
                roleHeader.append(ChatColor.WHITE).append(player.getDisplayName());
            }

            sender.sendMessage(roleHeader.toString());
        }

        return true;
    }

    private String getPlayerGroupRole(Player player) {
        if (luckPerms == null) {
            return null;
        }

        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) {
            return null;
        }

        Collection<Group> groups = user.getInheritedGroups(user.getQueryOptions());

        String highestRole = null;
        int highestPriority = -1;

        for (Group group : groups) {
            String groupName = group.getName().toLowerCase();

            if (roleHierarchy.containsKey(groupName)) {
                int priority = roleHierarchy.get(groupName);
                if (priority > highestPriority) {
                    highestPriority = priority;
                    highestRole = groupName;
                }
            }
        }

        return highestRole;
    }

    private String getPlayerPermissionRole(Player player) {
        String highestRole = null;
        int highestPriority = -1;

        for (Map.Entry<String, String> entry : rolePermissions.entrySet()) {
            String roleName = entry.getKey();
            String permission = entry.getValue();

            if (player.hasPermission(permission) || (roleName.equals("amministratore") && player.isOp())) {
                int priority = roleHierarchy.getOrDefault(roleName, 0);
                if (priority > highestPriority) {
                    highestPriority = priority;
                    highestRole = roleName;
                }
            }
        }

        return highestRole;
    }

    private ChatColor getChatColor(String colorName) {
        try {
            return ChatColor.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChatColor.WHITE;
        }
    }

    private boolean isVanished(Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
        }
        return false;
    }
}
package lol.alez.wstaff;

import lol.alez.wstaff.commands.StaffListCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class WStaff extends JavaPlugin {

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getCommand("sl").setExecutor(new StaffListCommand(this));
    }

    @Override
    public void onDisable() {
    }
}

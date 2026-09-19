package org.leng.commands;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.leng.Lengbanlist;

import java.util.Collections;
import java.util.List;

public class FeatureCommand extends Command {

    private final Lengbanlist plugin;
    private final String feature;
    private final CommandExecutor executor;
    private final TabCompleter tabCompleter;

    public FeatureCommand(Lengbanlist plugin, String name, String feature, String permission,
                          String description, String usage, List<String> aliases,
                          CommandExecutor executor, TabCompleter tabCompleter) {
        super(name, description, usage, aliases);
        this.plugin = plugin;
        this.feature = feature;
        this.executor = executor;
        this.tabCompleter = tabCompleter;
        setPermission(permission);
    }

    public String getFeature() {
        return feature;
    }

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!plugin.isEnabled()) {
            return true;
        }
        if (!testPermission(sender)) {
            return true;
        }
        if (!plugin.isFeatureEnabled(feature)) {
            plugin.sendFeatureDisabled(sender);
            return true;
        }
        return executor.onCommand(sender, this, label, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
        return complete(sender, alias, args);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String alias, String[] args, Location location) {
        return complete(sender, alias, args);
    }

    private List<String> complete(CommandSender sender, String alias, String[] args) {
        if (tabCompleter == null || !plugin.isEnabled() || !plugin.isFeatureEnabled(feature)
                || !testPermissionSilent(sender)) {
            return Collections.emptyList();
        }
        List<String> completions = tabCompleter.onTabComplete(sender, this, alias, args);
        return completions == null ? Collections.emptyList() : completions;
    }
}

package de.hopperfilter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class HopperFilterPlugin extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey filterKey;

    @Override
    public void onEnable() {
        filterKey = new NamespacedKey(this, "filter");
        getServer().getPluginManager().registerEvents(this, this);
        var cmd = getCommand("hopperfilter");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    /* ---------------------------- Events ---------------------------- */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent event) {
        Set<Material> filter = readFilter(event.getDestination().getHolder(false));
        if (filter != null && !filter.contains(event.getItem().getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(InventoryPickupItemEvent event) {
        Set<Material> filter = readFilter(event.getInventory().getHolder(false));
        if (filter != null && !filter.contains(event.getItem().getItemStack().getType())) {
            event.setCancelled(true);
        }
    }

    /* ------------------------- Filter-Speicher ------------------------ */

    /** Liefert den Filter oder null, wenn keiner gesetzt ist. */
    private Set<Material> readFilter(InventoryHolder holder) {
        if (holder instanceof DoubleChest dc) {
            Set<Material> left = readFilter(dc.getLeftSide(false));
            Set<Material> right = readFilter(dc.getRightSide(false));
            if (left == null) return right;
            if (right == null) return left;
            Set<Material> both = EnumSet.copyOf(left);
            both.addAll(right);
            return both;
        }
        if (holder instanceof TileState tile) {
            String raw = tile.getPersistentDataContainer().get(filterKey, PersistentDataType.STRING);
            return parse(raw);
        }
        return null;
    }

    private Set<Material> parse(String raw) {
        if (raw == null || raw.isEmpty()) return null;
        Set<Material> set = EnumSet.noneOf(Material.class);
        for (String s : raw.split(",")) {
            Material m = Material.matchMaterial(s);
            if (m != null) set.add(m);
        }
        return set.isEmpty() ? null : set;
    }

    private void writeFilter(Container container, Set<Material> set) {
        if (set == null || set.isEmpty()) {
            container.getPersistentDataContainer().remove(filterKey);
        } else {
            String raw = set.stream().map(Enum::name).collect(Collectors.joining(","));
            container.getPersistentDataContainer().set(filterKey, PersistentDataType.STRING, raw);
        }
        container.update();
    }

    /* ----------------------------- Befehl ----------------------------- */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Dieser Befehl geht nur im Spiel.");
            return true;
        }
        if (args.length == 0) {
            msg(player, "Benutzung: /hf <set|add|remove|clear|info> [Item]", NamedTextColor.YELLOW);
            return true;
        }

        Block block = player.getTargetBlockExact(6);
        if (block == null || !(block.getState() instanceof Container container)) {
            msg(player, "Schau auf einen Trichter, eine Kiste, ein Fass o. ae. (max. 6 Bloecke).", NamedTextColor.RED);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        Set<Material> current = parse(container.getPersistentDataContainer().get(filterKey, PersistentDataType.STRING));
        if (current == null) current = EnumSet.noneOf(Material.class);

        switch (sub) {
            case "info" -> {
                if (current.isEmpty()) msg(player, "Kein Filter gesetzt.", NamedTextColor.YELLOW);
                else msg(player, "Erlaubt: " + names(current), NamedTextColor.GREEN);
            }
            case "clear" -> {
                writeFilter(container, null);
                msg(player, "Filter entfernt.", NamedTextColor.GREEN);
            }
            case "set", "add", "remove" -> {
                Material mat = resolveMaterial(player, args);
                if (mat == null) {
                    msg(player, "Item nicht gefunden. Halte ein Item in der Hand oder gib den Namen an.", NamedTextColor.RED);
                    return true;
                }
                if (sub.equals("set")) current = EnumSet.of(mat);
                else if (sub.equals("add")) current.add(mat);
                else current.remove(mat);
                writeFilter(container, current);
                msg(player, current.isEmpty() ? "Filter entfernt." : "Erlaubt: " + names(current), NamedTextColor.GREEN);
            }
            default -> msg(player, "Unbekannter Unterbefehl.", NamedTextColor.RED);
        }
        return true;
    }

    private Material resolveMaterial(Player player, String[] args) {
        if (args.length >= 2) {
            Material m = Material.matchMaterial(args[1]);
            return (m != null && m.isItem()) ? m : null;
        }
        Material hand = player.getInventory().getItemInMainHand().getType();
        return hand.isAir() ? null : hand;
    }

    private String names(Set<Material> set) {
        return set.stream().map(m -> m.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", "));
    }

    private void msg(Player p, String text, NamedTextColor color) {
        p.sendMessage(Component.text("[HopperFilter] ", NamedTextColor.GOLD).append(Component.text(text, color)));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("set", "add", "remove", "clear", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && List.of("set", "add", "remove").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Material m : Material.values()) {
                if (!m.isItem() || m.isLegacy()) continue;
                String n = m.name().toLowerCase(Locale.ROOT);
                if (n.startsWith(prefix)) out.add(n);
                if (out.size() >= 50) break;
            }
            return out;
        }
        return List.of();
    }
}

package org.pytenix;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.w3c.dom.Text;

public class TestMessageCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player)) {
            sender.sendMessage("Nur für Spieler!");
            return true;
        }

        Player player = (Player) sender;

        Component brutalMessage = Component.text("Du hast tief in der Höhle ein ")
                .color(NamedTextColor.GRAY)

                .append(Component.text("Magisches Schwert")
                        .color(NamedTextColor.GOLD)
                        .decorate(TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Macht 100 Schaden\n").color(NamedTextColor.YELLOW)
                                        .append(Component.text("Seltenheit: Episch").color(NamedTextColor.LIGHT_PURPLE))
                        ))
                )

                .append(Component.text(" gefunden! Möchtest du es direkt ausrüsten? ")
                        .color(NamedTextColor.GRAY)
                )

                .append(Component.text("[JA, AUSRÜSTEN]")
                        .color(NamedTextColor.GREEN)
                        .decorate(TextDecoration.BOLD)
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Klicke hier, um das Schwert anzulegen!").color(NamedTextColor.WHITE)
                        ))
                        .clickEvent(ClickEvent.runCommand("/say Der Klick-Befehl hat den Proxy überlebt!"))
                )

                .append(Component.text(" (Diese Aktion kann nicht rückgängig gemacht werden.)")
                        .color(NamedTextColor.DARK_GRAY)
                        .decorate(TextDecoration.ITALIC)
                );

        player.sendMessage(brutalMessage);

        return true;
    }
}
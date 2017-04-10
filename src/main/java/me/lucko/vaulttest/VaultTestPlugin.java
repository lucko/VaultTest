/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.vaulttest;

import net.milkbowl.vault.chat.Chat;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A plugin to test a Vault Permission/Chat implementation using commands to execute Vault methods with reflection
 */
public class VaultTestPlugin extends JavaPlugin {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vaulttest.use")) {
            msg(sender, "&cNo permission.");
            return false;
        }

        List<String> arguments = new ArrayList<>(Arrays.asList(args));
        if (arguments.size() < 2) {
            sendUsage(sender);
            return true;
        }

        String type = arguments.remove(0).toLowerCase();
        Class clazz;
        if (type.startsWith("c")) {
            clazz = Chat.class;
        } else if (type.startsWith("p")) {
            clazz = Permission.class;
        } else {
            sendUsage(sender);
            return true;
        }

        // Try permissions
        msg(sender, "&eAttempting Vault call.");
        try {
            Object result = handleMethodCall(clazz, arguments);

            if (result.getClass().isArray()) {
                msg(sender, "&aGot result: &f" + Arrays.deepToString((Object[]) result));
            } else {
                msg(sender, "&aGot result: &f" + result.toString());
            }

        } catch (Exception e) {
            msg(sender, "&cError: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        msg(sender, "&cUsage: /vaulttest <chat|permission> <method> [parameters...]");
        msg(sender, "&ePlayer &c--> &ep=?");
        msg(sender, "&eWorld &c--> &ew=?");
        msg(sender, "&eOfflinePlayer &c--> &eop=?");
    }

    private Object handleMethodCall(Class clazz, List<String> args) {
        if (!getServer().getServicesManager().isProvidedFor(clazz)) {
            throw new RuntimeException("Vault Permissions not provided for.");
        }

        Object instance = getServer().getServicesManager().load(clazz);
        String methodName = args.remove(0);

        List<Object> parameters = new ArrayList<>();
        for (String a : args) {
            Object o;

            if (a.toLowerCase().startsWith("w=")) {
                o = getServer().getWorld(a.substring(2));
                if (o == null) {
                    throw new RuntimeException("World " + a.substring(2) + " does not exist.");
                }
            } else if (a.toLowerCase().startsWith("p=")) {
                try {
                    UUID u = UUID.fromString(a.substring(2));
                    o = getServer().getPlayer(u);
                } catch (IllegalArgumentException e) {
                    o = getServer().getPlayerExact(a.substring(2));
                }
                if (o == null) {
                    throw new RuntimeException("Player " + a.substring(2) + " could not be found.");
                }
            } else if (a.toLowerCase().startsWith("op=")) {
                try {
                    UUID u = UUID.fromString(a.substring(2));
                    o = getServer().getOfflinePlayer(u);
                } catch (IllegalArgumentException e) {
                    o = getServer().getOfflinePlayer(a.substring(2));
                }
                if (o == null) {
                    throw new RuntimeException("OfflinePlayer " + a.substring(2) + " could not be found.");
                }
            } else {
                o = a;
            }

            parameters.add(o);
        }

        List<Method> methods = new ArrayList<>(Arrays.asList(clazz.getDeclaredMethods()));
        for (Method m : methods) {
            if (m.getName().equalsIgnoreCase(methodName)) {
                try {
                    return m.invoke(instance, parameters.toArray());
                } catch (Throwable t) {
                    if (t instanceof IllegalArgumentException && (
                            t.getMessage().equalsIgnoreCase("wrong number of arguments") ||
                            t.getMessage().equalsIgnoreCase("argument type mismatch"))) {
                        continue;
                    }
                    throw new RuntimeException(t);
                }
            }
        }

        List<String> availableMethods = new ArrayList<>();
        availableMethods.add("&eAvailable:");

        for (Method m : methods) {
            StringBuilder sb = new StringBuilder("&7").append(m.getReturnType().getSimpleName()).append(" &f").append(m.getName());

            Parameter[] paras = m.getParameters();
            for (Parameter p : paras) {
                sb.append(" &7").append(p.getType().getSimpleName());
            }
            availableMethods.add(sb.toString());
        }

        String usage = availableMethods.stream().collect(Collectors.joining("\n"));
        throw new RuntimeException("Couldn't find method with name \"" + methodName + "\". \n" + usage);
    }

    private static void msg(CommandSender sender, String msg) {
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));
    }
}

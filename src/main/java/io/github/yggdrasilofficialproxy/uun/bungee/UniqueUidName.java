package io.github.yggdrasilofficialproxy.uun.bungee;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.connection.PendingConnection;
import net.md_5.bungee.api.event.LoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.event.EventHandler;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@SuppressWarnings({"unused", "RedundantSuppression"})
public class UniqueUidName extends Plugin {
    private static Map<String, UUID> name2uuid;
    private static Map<UUID, String> uuid2name;
    private static final Object accessLock = new Object();

    @Override
    public void onEnable() {
        File data = new File(getDataFolder(), "storage.bin");
        if (data.isFile()) {
            try (FileInputStream fileInputStream = new FileInputStream(data);
                 BufferedInputStream bufferedInputStream = new BufferedInputStream(fileInputStream);
                 GZIPInputStream gzipInputStream = new GZIPInputStream(bufferedInputStream);
                 DataInputStream bin = new DataInputStream(gzipInputStream)
            ) {
                int size = bin.readInt();
                name2uuid = new HashMap<>(size);
                uuid2name = new HashMap<>(size);
                while (size-- > 0) {
                    UUID uid = new UUID(bin.readLong(), bin.readLong());
                    String usr = bin.readUTF();
                    name2uuid.put(usr, uid);
                    uuid2name.put(uid, usr);
                }
            } catch (Throwable any) {
                getLogger().log(Level.SEVERE, "Exception in reading bin database.");
            }
        } else {
            name2uuid = new HashMap<>(255);
            uuid2name = new HashMap<>(255);
        }
        Plugin p = this;
        getProxy().getPluginManager().registerListener(this, new LoginListener());
        // Auto save
        getProxy().getScheduler().schedule(this, this::save, 10, 10, TimeUnit.MINUTES);
    }

    public static class LoginListener implements Listener {
        @EventHandler
        public void listen(LoginEvent event) {
            PendingConnection connection = event.getConnection();
            synchronized (accessLock) {
                UUID sourceUUID = connection.getUniqueId();
                String sourceName = connection.getName();

                UUID storageUUID = name2uuid.get(sourceName);
                String storageName = uuid2name.get(sourceUUID);
                if (sourceName.equals(storageName)) {
                    // UUID match and Name Match.
                    return; // allowed
                } else if (storageName == null) {
                    // New to server
                    if (storageUUID != null) {
                        event.setCancelled(true);
                        event.setCancelReason(
                                new ComponentBuilder(
                                        "Oops. Here is a other account with "
                                ).color(ChatColor.RED)
                                        .append(sourceName, ComponentBuilder.FormatRetention.NONE)
                                        .color(ChatColor.GOLD)
                                        .append(" was registered to this server. Please change your username and retry.", ComponentBuilder.FormatRetention.NONE)
                                        .color(ChatColor.RED)
                                        .create()
                        );
                    } else {
                        // Welcome to our server.
                        name2uuid.put(sourceName, sourceUUID);
                        uuid2name.put(sourceUUID, sourceName);
                    }
                } else {
                    // This account was renamed.
                    if (storageUUID == null) {
                        // Target name not used.
                        name2uuid.put(sourceName, sourceUUID);
                        uuid2name.put(sourceUUID, sourceName);
                        name2uuid.remove(storageName); // name updated.
                    } else {
                        event.setCancelled(true);
                        event.setCancelReason(
                                new ComponentBuilder(
                                        "Oops. Here is a other account with "
                                ).color(ChatColor.RED)
                                        .append(sourceName, ComponentBuilder.FormatRetention.NONE)
                                        .color(ChatColor.GOLD)
                                        .append(" was registered to this server. Please change your username and retry.", ComponentBuilder.FormatRetention.NONE)
                                        .color(ChatColor.RED)
                                        .create()
                        );
                    }
                }
            }
        }
    }

    @Override
    public void onDisable() {
        save();
    }

    private void save() {
        Map<UUID, String> copied;
        synchronized (accessLock) {
            copied = new HashMap<>(uuid2name);
        }
        try {
            File data = new File(getDataFolder(), "storage.bin");
            getDataFolder().mkdirs();
            try (FileOutputStream fileOutputStream = new FileOutputStream(data);
                 BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
                 GZIPOutputStream gzipOutputStream = new GZIPOutputStream(bufferedOutputStream);
                 DataOutputStream bin = new DataOutputStream(gzipOutputStream)
            ) {
                bin.writeInt(copied.size());
                for (Map.Entry<UUID, String> entry : copied.entrySet()) {
                    UUID uid = entry.getKey();
                    bin.writeLong(uid.getMostSignificantBits());
                    bin.writeLong(uid.getLeastSignificantBits());
                    bin.writeUTF(entry.getValue());
                }
            }
        } catch (IOException ioException) {
            getLogger().log(Level.SEVERE, "Exception in saving storage.");
        }
    }
}


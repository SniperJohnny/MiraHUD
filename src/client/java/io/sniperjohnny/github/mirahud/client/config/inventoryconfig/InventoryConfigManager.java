package io.sniperjohnny.github.mirahud.client.config.inventoryconfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.sniperjohnny.github.mirahud.MiraHUD;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class InventoryConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final File CONFIG_FILE = FabricLoader.getInstance()
            .getConfigDir()
            .resolve(MiraHUD.MOD_ID)
            .resolve("configs")
            .resolve(MiraHUD.MOD_ID + "_inventoryconfig.json")
            .toFile();

    private static Inventoryconfig currentInvConfig;

    public static Inventoryconfig getConfig() {
        if (currentInvConfig == null) {
            load();
        }
        return currentInvConfig;
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            currentInvConfig = new Inventoryconfig();
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            currentInvConfig = GSON.fromJson(reader, Inventoryconfig.class);
            if (currentInvConfig == null) {
                currentInvConfig = new Inventoryconfig();
            }
        } catch (IOException e) {
            System.err.println("[" + MiraHUD.MOD_ID + "] Failed to load client config, using defaults.");
            currentInvConfig = new Inventoryconfig();
        }
    }

    public static void save() {
        if (currentInvConfig == null) {
            currentInvConfig = new Inventoryconfig();
        }

        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(currentInvConfig, writer);
        } catch (IOException e) {
            System.err.println("[" + MiraHUD.MOD_ID + "] Failed to save client config.");
        }
    }
}
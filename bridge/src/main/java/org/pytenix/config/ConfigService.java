package org.pytenix.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.SneakyThrows;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigService {
    private static final Logger LOGGER = Logger.getLogger(ConfigService.class.getName());
    private final Gson gson;

    public ConfigService() {
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
    }


    @SneakyThrows
    public void saveConfig(String fileName, Object config) {

        File file = new File("plugins/AITranslator/" + fileName);
        if (file.getParentFile() != null) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        try (Writer writer = new FileWriter(file)) {
            gson.toJson(config, writer);
            LOGGER.info("Config erfolgreich gespeichert: " + file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Speichern der Config: " + e.getMessage(), e);

        }
    }


    public <T> T loadConfig(String fileName, Class<T> clazz) {
        File file = new File("plugins/AITranslator/" + fileName);
        if (!file.exists()) {
            LOGGER.info("Config-Datei nicht gefunden, erstelle neue Standard-Config.");
            return createInstance(clazz);
        }

        try (Reader reader = new FileReader(file)) {
            T config = gson.fromJson(reader, clazz);
            if (config == null) {
                return createInstance(clazz);
            }
            LOGGER.info("Config erfolgreich geladen.");
            return config;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Fehler beim Laden der Config: " + e.getMessage(), e);
            return createInstance(clazz);
        }
    }


    public boolean exists(String fileName)
    {
        return new File("plugins/AITranslator/" + fileName).exists();
    }


    private <T> T createInstance(Class<T> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Konnte keine neue Instanz von " + clazz.getName() + " erstellen. Hat sie einen leeren Konstruktor?", e);
        }
    }
}

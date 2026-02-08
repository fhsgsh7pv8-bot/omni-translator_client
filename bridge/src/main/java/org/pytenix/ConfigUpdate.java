package org.pytenix;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;

@Setter @Getter @AllArgsConstructor
public class ConfigUpdate {

    private String type = "CONFIG_UPDATE";

    String licenseKey;
    HashMap<String,Boolean> modules;


    public ConfigUpdate()
    {

    }



    public static ConfigUpdate createDefault(String licenseKey)
    {
        ConfigUpdate configUpdate = new ConfigUpdate();


        HashMap<String,Boolean> hash = new HashMap<>();
        for (Module value : Module.values()) {
            hash.put(value.getModuleName(),true);
        }

        configUpdate.setModules(hash);
        configUpdate.setLicenseKey(licenseKey);

        return configUpdate;
    }


    @Getter @AllArgsConstructor @NoArgsConstructor
    enum Module
    {
        LIVE_CHAT("live_chat"),
        GUI("gui"),
        HOLOGRAM("hologram"),
        PLUGIN_CHAT("plugin_chat"),
        SIGNS("signs");


        String moduleName;


    }

}

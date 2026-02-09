package org.pytenix;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

@Setter @Getter @AllArgsConstructor
public class ServerConfiguration {

    private String type = "CONFIG_UPDATE";

    String licenseKey;
    HashMap<String,Boolean> modules;


    Set<String> blacklistedWords;


    public ServerConfiguration()
    {

    }



    public static ServerConfiguration createDefault(String licenseKey)
    {
        ServerConfiguration serverConfiguration = new ServerConfiguration();


        HashMap<String,Boolean> hash = new HashMap<>();
        for (Module value : Module.values()) {
            hash.put(value.getModuleName(),true);
        }

        serverConfiguration.setModules(hash);
        serverConfiguration.setLicenseKey(licenseKey);
        serverConfiguration.setBlacklistedWords(new HashSet<>());

        return serverConfiguration;
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

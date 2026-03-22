package org.pytenix.motd;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.pytenix.entity.ServerConfiguration;
import org.pytenix.VelocityTranslator;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class MotDEvent {


    final VelocityTranslator translator;
    final GeoService geoService;


    LegacyComponentSerializer legacyComponentSerializer = LegacyComponentSerializer.builder()
            .character('§')
            .extractUrls()
            .hexColors()
            .flattener(ComponentFlattener.basic())
            .build();


    public MotDEvent(VelocityTranslator translator) {
        this.translator = translator;
        this.geoService = translator.getGeoService();


    }


    @Subscribe
    public EventTask onPing(ProxyPingEvent event) {
        System.out.println("ONPING!");
        if(translator.getVelocityBridge().getServerConfiguration() == null)
        {
            return null;
        }

        //COLORCODE FIXXEN
        //TODO

        if (!translator.getVelocityBridge().getServerConfiguration().getModules().getOrDefault(ServerConfiguration.Module.MOTD.getModuleName(), true)) {
            return null;
        }

        String ipAddress = event.getConnection().getRemoteAddress().getAddress().getHostAddress();

        final UUID uuid = UUID.randomUUID();


        String finalIpAddress = getTestIps().get(new Random().nextInt(getTestIps().size()));
        return EventTask.async(() -> {

            CompletableFuture<String> localeFuture = geoService.sendGeoRequest(
                    uuid,
                    finalIpAddress
            );

            try {
                String locale = localeFuture.get(2, TimeUnit.SECONDS);

                String originalMotdText = legacyComponentSerializer
                        .serialize(event.getPing().getDescriptionComponent());


                CompletableFuture<String> translationFuture = translator.getTranslatorService()
                        .translate( originalMotdText, locale, ServerConfiguration.Module.MOTD.getModuleName());

                String translatedMotd = translationFuture.get(2, TimeUnit.SECONDS);

                ServerPing ping = event.getPing();
                ServerPing.Builder builder = ping.asBuilder();

                builder.description(Component.text(translatedMotd));
                event.setPing(builder.build());

            } catch (Exception e) {
                System.out.println("MOTD Translation failed: " + e.getMessage());
            }
        });
    }


    public static List<String> getTestIps() {
        return new ArrayList<>(Arrays.asList(
                // Deutschland / Europa
                "194.25.134.1", "80.156.12.1", "217.237.150.1", "185.15.244.1", "91.198.174.1",
                "185.163.136.1", "5.1.80.1", "46.165.200.1", "31.171.160.1", "193.169.176.1",
                // USA / Nordamerika
                "8.8.8.8", "64.233.160.1", "199.36.158.1", "208.67.222.222", "198.51.100.1",
                "34.200.0.1", "52.0.0.1", "23.235.40.1", "104.16.0.1", "172.217.0.1",
                // Spanien
                "80.24.0.1", "95.16.0.1", "37.14.0.1", "91.126.0.1", "85.48.0.1",
                // Frankreich
                "193.252.122.1", "194.2.0.1", "212.198.0.1", "80.12.0.1", "92.184.0.1",
                // Italien
                "151.1.0.1", "2.224.0.1", "93.32.0.1", "62.101.0.1", "79.0.0.1",
                // UK
                "212.58.244.1", "151.200.0.1", "81.2.0.1", "92.40.0.1", "193.60.0.1",
                // Polen
                "212.77.100.1", "91.210.0.1", "195.117.0.1", "85.232.0.1", "31.0.0.1",
                // Brasilien
                "200.147.0.1", "177.1.0.1", "189.1.0.1", "191.0.0.1", "187.0.0.1",
                // China / Japan / Korea
                "202.108.0.1", "114.114.114.114", "210.72.0.1", "219.0.0.1", "106.185.0.1",
                "203.178.0.1", "133.0.0.1", "210.128.0.1", "110.1.0.1", "211.233.0.1",
                // ... (Hier kannst du das Muster mit weiteren Adressblöcken ergänzen)
                // Um 100 voll zu machen, kannst du in deinem Code einfach eine Schleife nutzen:
                "1.1.1.1", "1.0.0.1", "9.9.9.9", "149.112.112.112", "185.228.168.168",
                "77.88.8.8", "77.88.8.1", "8.20.247.20", "8.26.56.26", "208.67.220.220",
                "103.86.96.100", "103.86.99.100", "198.18.0.1", "198.19.0.1", "192.0.2.1",
                "198.51.100.2", "203.0.113.1", "45.90.28.0", "45.90.30.0", "185.228.169.9",
                "94.140.14.14", "94.140.15.15", "1.1.1.2", "1.0.0.2", "8.8.4.4",
                "35.80.0.1", "54.240.0.1", "204.246.160.1", "205.251.192.1", "52.94.0.1",
                "13.32.0.1", "23.227.38.1", "108.138.0.1", "143.204.0.1", "13.224.0.1",
                "64.4.0.1", "157.55.0.1", "20.184.0.1", "40.74.0.1", "52.114.0.1"
        ));
    }
}
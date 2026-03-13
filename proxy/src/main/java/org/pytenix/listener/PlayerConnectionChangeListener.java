package org.pytenix.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import org.pytenix.VelocityBridge;
import org.pytenix.VelocityTranslator;

public class PlayerConnectionChangeListener {


    final VelocityTranslator translator;
    final VelocityBridge velocityBridge;


    public PlayerConnectionChangeListener(VelocityTranslator translator) {
        this.translator = translator;
        this.velocityBridge = translator.getVelocityBridge();
    }




    @Subscribe
    public void onJoin(ServerPostConnectEvent event) {
        velocityBridge.getPlaceholderService().getPlayernameProtector().addPlayer(event.getPlayer().getUsername());
    }

    @Subscribe
    public void onQuit(DisconnectEvent event) {
        velocityBridge.getPlaceholderService().getPlayernameProtector().removePlayer(event.getPlayer().getUsername());
    }
}

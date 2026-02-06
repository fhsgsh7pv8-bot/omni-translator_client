package org.pytenix.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;

public class TaskScheduler {

    private final Plugin plugin;
    private final boolean isFolia;

    public TaskScheduler(Plugin plugin) {
        this.plugin = plugin;

        boolean foliaCheck = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaCheck = true;
        } catch (ClassNotFoundException e) {
            foliaCheck = false;
        }
        this.isFolia = foliaCheck;
    }


    public void runForEntity(Entity entity, Runnable task) {
        if (isFolia) {
            entity.getScheduler().run(plugin, (t) -> task.run(), null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }


    public void runAtLocation(Location loc, Runnable task) {
        if (isFolia) {
            Bukkit.getRegionScheduler().run(plugin, loc, (t) -> task.run());
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }


    public void runAsync(Runnable task) {
        if (isFolia) {
            Bukkit.getAsyncScheduler().runNow(plugin, (t) -> task.run());
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task);
        }
    }
    public void runAsyncLater(Runnable task, int ticks) {
        if (isFolia) {

            long delayMs = ticks * 50L;
            Bukkit.getAsyncScheduler().runDelayed(plugin, (t) -> task.run(), delayMs, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, ticks);
        }
    }

    public void runTimerAsync(Runnable task, int delayTicks, int periodTicks) {
        if (isFolia) {

            long delayMs = delayTicks * 50L;
            long periodMs = periodTicks * 50L;


            Bukkit.getAsyncScheduler().runAtFixedRate(plugin, (t) -> task.run(), delayMs, periodMs, TimeUnit.MILLISECONDS);
        } else {
            Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks);
        }
    }
}

package com.ishland.c2me.common.config.updater;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.LinkedList;
import java.util.Queue;
import java.util.function.Consumer;

public class Updaters {

    static final Logger LOGGER = LogManager.getLogger("C2ME Config Updater");
    private static final Long2ObjectOpenHashMap<Consumer<Config>> updaters = new Long2ObjectOpenHashMap<>();

    static {
        updaters.put(0, source -> {
            Queue<Runnable> pendingActions = new LinkedList<>();
            Queue<Config> pendingConfigs = new LinkedList<>();
            pendingConfigs.add(source);
            while (!pendingConfigs.isEmpty()) {
                final Config config = pendingConfigs.poll();
                for (Config.Entry entry : config.entrySet()) {
                    if (entry.getValue() instanceof Config) pendingConfigs.add(entry.getValue());
                    else pendingActions.add(() -> entry.setValue("default"));
                }
            }
            pendingActions.forEach(Runnable::run);
        });
        updaters.put(1, source -> {
            final Object _ioSystem = source.get("asyncIO");
            if (_ioSystem instanceof CommentedConfig ioSystem) {
                // rename asyncIO to ioSystem
                source.remove("asyncIO");
                source.add("ioSystem", ioSystem);

                final Object _async = ioSystem.get("enabled");
                if (_async instanceof Boolean async) {
                    // rename enabled to async
                    ioSystem.remove("enabled");
                    ioSystem.add("async", async);
                }
            }
        });
    }

    public static void update(Config config) {
        while (true) {
            final long version = config.getLongOrElse("version", 0);
            final Consumer<Config> updater = updaters.get(version);
            if (updater != null) {
                LOGGER.info("Updating config from {} to {}", version, version + 1);
                updater.accept(config);
                config.set("version", version + 1);
            } else {
                break;
            }
        }
    }

}

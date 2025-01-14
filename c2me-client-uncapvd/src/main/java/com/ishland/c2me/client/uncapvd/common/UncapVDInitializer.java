package com.ishland.c2me.client.uncapvd.common;

import com.ishland.c2me.base.common.config.ModStatuses;
import com.ishland.c2me.client.uncapvd.ModuleEntryPoint;
import net.fabricmc.api.ClientModInitializer;

public class UncapVDInitializer implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        if (ModuleEntryPoint.enabled && ModStatuses.fabric_networking_api_v1) {
            ClientExtNetworking.registerListeners();
        }
    }

}

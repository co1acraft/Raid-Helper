package com.example.raidaddon;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;


/**
 * Meteor addon that on load:
 * - Enables KillAura set to only attack monsters.
 * - Enables AutoEat & AutoTotem.
 * - Watches raid status; when a raid finishes in victory, consumes an Ominous Bottle if present.
 *
 * Note: Actual field names / settings may differ between Meteor versions.
 */
public class RaidHelperAddon extends MeteorAddon {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    @Override
    public void onInitialize() {
        // Register our Raid Helper module (no default keybind; disabled by default)
        Modules.get().add(new RaidHelperModule());

    }

    @Override
    public void onRegisterCategories() {
        // No custom categories required
    }

    @Override
    public String getPackage() {
        return "com.example.raidaddon";
    }

    // No module logic here; the RaidHelperModule contains all behavior.
}

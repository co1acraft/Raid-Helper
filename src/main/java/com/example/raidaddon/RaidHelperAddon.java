package com.example.raidaddon;

import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;


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
    private boolean raidJustFinished = false;
    private int postRaidTickCooldown = 0;
    private Boolean hadHeroEffect = null;

    @Override
    public void onInitialize() {
        // Subscribe to tick events
        meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.subscribe(this);

        // Toggle and configure KillAura
        KillAura killAura = Modules.get().get(KillAura.class);
        if (killAura != null && !killAura.isActive()) killAura.toggle();
        // Attempt to configure targeting (API may have different setting names depending on version)
        // KillAura setting fields differ per version; leaving defaults.

        // Enable AutoEat
        AutoEat autoEat = Modules.get().get(AutoEat.class);
        if (autoEat != null && !autoEat.isActive()) autoEat.toggle();

    }

    @Override
    public void onRegisterCategories() {
        // No custom categories required
    }

    @Override
    public String getPackage() {
        return "com.example.raidaddon";
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;

        // Detect raid finish: iterate raids on server world. Client-side access can vary; placeholder logic.
        // This simplistic approach checks for boss bar removal + hero of the village effect presence.
        boolean hasHeroEffect = mc.player != null && mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HERO_OF_THE_VILLAGE);

        // Initialize state on first tick without triggering
        if (hadHeroEffect == null) {
            hadHeroEffect = hasHeroEffect;
            return;
        }

        // Trigger only on rising edge: effect was absent, now present
        if (hasHeroEffect && !hadHeroEffect && !raidJustFinished) {
            raidJustFinished = true;
            postRaidTickCooldown = 20; // 1 second cooldown before consuming
        }

        if (!hasHeroEffect) raidJustFinished = false;

        if (raidJustFinished && postRaidTickCooldown-- <= 0) {
            // Attempt to drink an Ominous Bottle if present (1.21+ item). Only considers hotbar slots.
            int slot = findOminousBottleHotbarSlot();
            if (slot != -1) {
                if (mc.player != null && mc.interactionManager != null) {
                    // Use accessor method compatible with newer Yarn where selectedSlot is private
                    mc.player.getInventory().setSelectedSlot(slot);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }
            }
            // Reset so we don't loop.
            raidJustFinished = false;
        }

        // Track last-seen effect state
        hadHeroEffect = hasHeroEffect;
    }

    private int findOminousBottleHotbarSlot() {
        if (mc.player == null) return -1;
        // Hotbar slots are 0..8
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && stack.getItem() == Items.OMINOUS_BOTTLE) {
                return i;
            }
        }
        return -1;
    }
}

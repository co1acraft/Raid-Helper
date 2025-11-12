package com.example.raidaddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

public class RaidHelperModule extends Module {
    private final MinecraftClient mc = MinecraftClient.getInstance();

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> autoEnableKillAura = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-enable-killaura")
        .description("Enable KillAura when this module is activated.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> autoEnableAutoEat = sgGeneral.add(new BoolSetting.Builder()
        .name("auto-enable-autoeat")
        .description("Enable AutoEat when this module is activated.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> drinkOminousBottle = sgGeneral.add(new BoolSetting.Builder()
        .name("drink-ominous-bottle")
        .description("Automatically drink an Ominous Bottle after raid victory.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> postRaidDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("post-raid-delay-ticks")
        .description("Delay in ticks after raid victory before drinking.")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build());

    private Boolean hadHeroEffect = null;
    private boolean raidJustFinished = false;
    private int postRaidTickCooldown = 0;

    public RaidHelperModule() {
        super(Categories.Misc, "Raid Helper", "Assists after raids and enables helpful modules.");
    }

    @Override
    public void onActivate() {
        // Optionally enable KillAura and AutoEat
        if (autoEnableKillAura.get()) {
            KillAura ka = Modules.get().get(KillAura.class);
            if (ka != null && !ka.isActive()) ka.toggle();
        }
        if (autoEnableAutoEat.get()) {
            AutoEat ae = Modules.get().get(AutoEat.class);
            if (ae != null && !ae.isActive()) ae.toggle();
        }
        hadHeroEffect = null;
        raidJustFinished = false;
        postRaidTickCooldown = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;
        if (!drinkOminousBottle.get()) return;
        if (mc.world == null || mc.player == null) return;

        boolean hasHeroEffect = mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HERO_OF_THE_VILLAGE);

        // Initialize state on first tick without triggering
        if (hadHeroEffect == null) {
            hadHeroEffect = hasHeroEffect;
            return;
        }

        // Trigger only on rising edge: effect was absent, now present
        if (hasHeroEffect && !hadHeroEffect && !raidJustFinished) {
            raidJustFinished = true;
            postRaidTickCooldown = postRaidDelayTicks.get();
        }

        if (!hasHeroEffect) raidJustFinished = false;

        if (raidJustFinished && postRaidTickCooldown-- <= 0) {
            int slot = findOminousBottleHotbarSlot();
            if (slot != -1) {
                if (mc.player != null && mc.interactionManager != null) {
                    mc.player.getInventory().setSelectedSlot(slot);
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                }
            }
            raidJustFinished = false;
        }

        hadHeroEffect = hasHeroEffect;
    }

    private int findOminousBottleHotbarSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && stack.getItem() == Items.OMINOUS_BOTTLE) return i;
        }
        return -1;
    }
}

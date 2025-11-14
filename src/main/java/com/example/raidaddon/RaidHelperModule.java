package com.example.raidaddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.combat.KillAura;
import meteordevelopment.meteorclient.systems.modules.player.AutoEat;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
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

    private final Setting<Boolean> triggerOnHeroEffect = sgGeneral.add(new BoolSetting.Builder()
        .name("trigger-on-hero-effect")
        .description("Trigger when Hero of the Village is gained (original behavior).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> triggerOnRaidBossbarEnd = sgGeneral.add(new BoolSetting.Builder()
        .name("trigger-on-raid-bossbar-end")
        .description("Trigger when the Raid bossbar disappears (close to victory banner).")
        .defaultValue(true)
        .build());

    private final Setting<Integer> postRaidDelayTicks = sgGeneral.add(new IntSetting.Builder()
        .name("post-raid-delay-ticks")
        .description("Delay in ticks after raid victory before drinking.")
        .defaultValue(20)
        .min(0)
        .sliderMax(100)
        .build());

    private final Setting<Boolean> strictVictoryWithHero = sgGeneral.add(new BoolSetting.Builder()
        .name("require-hero-with-victory")
        .description("When using bossbar triggers, also require Hero of the Village to be active.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> debugLogs = sgGeneral.add(new BoolSetting.Builder()
        .name("debug-logs")
        .description("Log raid bossbar titles and trigger decisions to chat.")
        .defaultValue(false)
        .build());

    private final Setting<String> victoryKeywords = sgGeneral.add(new StringSetting.Builder()
        .name("victory-keywords")
        .description("Comma-separated keywords that indicate victory in bossbar titles.")
        .defaultValue("victory, won")
        .build());

    private final Setting<Boolean> retryDrink = sgGeneral.add(new BoolSetting.Builder()
        .name("retry-drink")
        .description("Retry starting the drink until the use animation begins.")
        .defaultValue(true)
        .build());

    private final Setting<Integer> maxDrinkAttemptTicks = sgGeneral.add(new IntSetting.Builder()
        .name("max-drink-attempt-ticks")
        .description("Maximum ticks to retry initiating bottle use before giving up.")
        .defaultValue(40)
        .min(5)
        .sliderMax(100)
        .build());

    private final Setting<Integer> retryAttemptIntervalTicks = sgGeneral.add(new IntSetting.Builder()
        .name("retry-attempt-interval-ticks")
        .description("Ticks to wait between each retry attempt while starting the drink.")
        .defaultValue(5)
        .min(1)
        .sliderMax(40)
        .build());

    private final Setting<Boolean> forceHoldUseSetting = sgGeneral.add(new BoolSetting.Builder()
        .name("force-hold-use")
        .description("Force holding right-click (use key) while drinking to ensure completion.")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> stopWhenHasRaidOmen = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-has-raid-omen")
        .description("Stop attempting to drink if Raid Omen (Bad Omen) effect is already active.")
        .defaultValue(true)
        .build());

    private final Setting<RaidOmenSwitchMode> switchOnRaidOmen = sgGeneral.add(new EnumSetting.Builder<RaidOmenSwitchMode>()
        .name("switch-on-raid-omen")
        .description("How to handle hotbar switching when Raid Omen is present: Off, OnceOnGain (rising edge only), or Continuous (every tick while active).")
        .defaultValue(RaidOmenSwitchMode.Off)
        .build());

    private final Setting<SlotRestoreMode> slotRestoreMode = sgGeneral.add(new EnumSetting.Builder<SlotRestoreMode>()
        .name("slot-restore-mode")
        .description("How to restore hotbar slot after drinking: Previous (before bottle), Specific slot, or None.")
        .defaultValue(SlotRestoreMode.Previous)
        .build());

    private final Setting<Integer> targetHotbarSlot = sgGeneral.add(new IntSetting.Builder()
        .name("target-hotbar-slot")
        .description("Hotbar slot to switch to after drinking (1-9, only used when mode is Specific).")
        .defaultValue(1)
        .min(1)
        .max(9)
        .sliderMax(9)
        .visible(() -> slotRestoreMode.get() == SlotRestoreMode.Specific)
        .build());

    private final Setting<Boolean> reSwitchAfterIdle = sgGeneral.add(new BoolSetting.Builder()
        .name("re-switch-after-idle")
        .description("If the player manually changes slot, wait 10 seconds then switch back to the configured slot.")
        .defaultValue(false)
        .build());

    private final Setting<Boolean> chainRaids = sgGeneral.add(new BoolSetting.Builder()
        .name("chain-raids")
        .description("Automatically drink Ominous Bottle when no Raid Omen and no raid bossbar (to start another raid).")
        .defaultValue(false)
        .build());

    public enum SlotRestoreMode {
        Previous,
        Specific,
        None
    }

    public enum RaidOmenSwitchMode {
        Off,
        OnceOnGain,
        Continuous
    }

    private Boolean hadHeroEffect = null;
    private Boolean hadRaidBossBar = null;
    private Boolean hadRaidOmen = null;
    private boolean announcedVictory = false;
    private boolean raidJustFinished = false;
    private int postRaidTickCooldown = 0;
    // Drinking state machine
    private boolean drinking = false;
    private int drinkAttemptTicks = 0;
    private int drinkProgressTicks = 0;
    private int retryAttemptCooldown = 0;
    private boolean forceHoldUse = false;
    private boolean prevUseKeyPressed = false;
    private boolean announcedOmenSkip = false;
    private int prevSelectedHotbarSlot = -1;
    private int idleTicksOffTarget = 0;
    private int lastExpectedSlot = -1;
    private boolean autoEatWasActive = false;

    public RaidHelperModule() {
        super(Categories.Misc, "Raid Helper", "Assists after raids and enables helpful modules.");
    }

    @Override
    public void onActivate() {
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
        hadRaidBossBar = null;
        announcedVictory = false;
        drinking = false;
        drinkAttemptTicks = 0;
        drinkProgressTicks = 0;
        retryAttemptCooldown = 0;
        forceHoldUse = false;
        prevUseKeyPressed = false;
        announcedOmenSkip = false;
        prevSelectedHotbarSlot = -1;
        idleTicksOffTarget = 0;
        lastExpectedSlot = -1;
        autoEatWasActive = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;
        if (!drinkOminousBottle.get()) return;
        if (mc.world == null || mc.player == null) return;

        boolean hasHeroEffect = mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HERO_OF_THE_VILLAGE);
        boolean hasRaidOmen = hasRaidOmen();
        // If Raid Omen is already active, don't attempt to drink. Abort any ongoing drink.
        if (stopWhenHasRaidOmen.get() && hasRaidOmen) {
            if (!announcedOmenSkip) {
                announceClient("Raid Omen active — skipping bottle drinking.");
                announcedOmenSkip = true;
            }
            if (drinking) abortDrink("Raid Omen already active");
            raidJustFinished = false;
            postRaidTickCooldown = 0;
            return;
        } else {
            // Reset once effect is gone so we can notify next time it returns
            announcedOmenSkip = false;
        }

        java.util.List<net.minecraft.client.gui.hud.ClientBossBar> bossBars = getClientBossBars();
        boolean hasRaidBossBar = bossBars.stream().anyMatch(b -> safeTitle(b).toLowerCase().contains("raid"));
        boolean hasRaidVictory = hasRaidVictoryInBossBar(bossBars);

        if (hadHeroEffect == null || hadRaidBossBar == null || hadRaidOmen == null) {
            hadHeroEffect = hasHeroEffect;
            hadRaidBossBar = hasRaidBossBar;
            hadRaidOmen = hasRaidOmen;
            return;
        }

        // Handle switching hotbar based on Raid Omen mode
        RaidOmenSwitchMode omenMode = switchOnRaidOmen.get();
        if (omenMode != RaidOmenSwitchMode.Off && hasRaidOmen) {
            boolean shouldSwitch = false;
            if (omenMode == RaidOmenSwitchMode.OnceOnGain && !hadRaidOmen) {
                shouldSwitch = true;
            } else if (omenMode == RaidOmenSwitchMode.Continuous) {
                shouldSwitch = true;
            }

            if (shouldSwitch && mc.player != null && mc.player.getInventory() != null) {
                int targetSlot = -1;
                switch (slotRestoreMode.get()) {
                    case Previous:
                        if (prevSelectedHotbarSlot >= 0 && prevSelectedHotbarSlot <= 8) {
                            targetSlot = prevSelectedHotbarSlot;
                        }
                        break;
                    case Specific:
                        int s = targetHotbarSlot.get() - 1;
                        if (s >= 0 && s <= 8) {
                            targetSlot = s;
                        }
                        break;
                    case None:
                        break;
                }
                if (targetSlot >= 0) {
                    try {
                        int currentSlot = mc.player.getInventory().getSelectedSlot();
                        if (currentSlot != targetSlot) {
                            mc.player.getInventory().setSelectedSlot(targetSlot);
                            if (debugLogs.get() && omenMode == RaidOmenSwitchMode.OnceOnGain) {
                                announceClient("Switched to slot " + (targetSlot + 1) + " due to Raid Omen gain.");
                            }
                        }
                    } catch (Throwable ignored) {
                        // Fallback: use reflection to read selectedSlot
                        try {
                            java.lang.reflect.Field f = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
                            f.setAccessible(true);
                            Object v = f.get(mc.player.getInventory());
                            if (v instanceof Integer currentSlot && currentSlot != targetSlot) {
                                mc.player.getInventory().setSelectedSlot(targetSlot);
                                if (debugLogs.get() && omenMode == RaidOmenSwitchMode.OnceOnGain) {
                                    announceClient("Switched to slot " + (targetSlot + 1) + " due to Raid Omen gain.");
                                }
                            }
                        } catch (Throwable ignored2) {}
                    }
                }
            }
        }

        if (triggerOnHeroEffect.get() && hasHeroEffect && !hadHeroEffect && !raidJustFinished) {
            raidJustFinished = true;
            postRaidTickCooldown = postRaidDelayTicks.get();
            if (debugLogs.get()) announceClient("Triggered by Hero of the Village rising edge.");
        }

        if (triggerOnRaidBossbarEnd.get() && !hasRaidBossBar && hadRaidBossBar && !raidJustFinished
            && (!strictVictoryWithHero.get() || hasHeroEffect)) {
            raidJustFinished = true;
            postRaidTickCooldown = postRaidDelayTicks.get();
            if (!announcedVictory) announceClient("Raid bossbar ended - drinking soon.");
            announcedVictory = true;
            if (debugLogs.get()) announceClient("Triggered by bossbar end (strict=" + strictVictoryWithHero.get() + ").");
        }

        if (triggerOnRaidBossbarEnd.get() && hasRaidVictory && !raidJustFinished
            && (!strictVictoryWithHero.get() || hasHeroEffect)) {
            raidJustFinished = true;
            postRaidTickCooldown = postRaidDelayTicks.get();
            if (!announcedVictory) announceClient("Raid victory detected - drinking soon.");
            announcedVictory = true;
            if (debugLogs.get()) announceClient("Triggered by bossbar 'victory' (strict=" + strictVictoryWithHero.get() + ").");
        }

        if (!hasHeroEffect && !hasRaidBossBar) raidJustFinished = false;

        // Raid chaining: if no Raid Omen and no raid bossbar, start drinking to trigger next raid
        if (chainRaids.get() && !hasRaidOmen && !hasRaidBossBar && !raidJustFinished && !drinking) {
            raidJustFinished = true;
            postRaidTickCooldown = postRaidDelayTicks.get();
            if (debugLogs.get()) announceClient("Chain raid: no Omen + no bossbar, initiating bottle drink.");
        }

        if (raidJustFinished && !drinking && postRaidTickCooldown-- <= 0) {
            int slot = findOminousBottleHotbarSlot();
            if (slot != -1 && mc.player != null && mc.interactionManager != null) {
                // Pause AutoEat if it's active
                AutoEat ae = Modules.get().get(AutoEat.class);
                if (ae != null && ae.isActive()) {
                    autoEatWasActive = true;
                    ae.toggle();
                    if (debugLogs.get()) announceClient("Paused AutoEat for bottle drinking.");
                } else {
                    autoEatWasActive = false;
                }
                // Remember previous selection before switching to the bottle.
                try {
                    prevSelectedHotbarSlot = mc.player.getInventory().getSelectedSlot();
                } catch (Throwable ignored) {
                    // Fallback: try reflective access to 'selectedSlot' field
                    try {
                        java.lang.reflect.Field f = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
                        f.setAccessible(true);
                        Object v = f.get(mc.player.getInventory());
                        if (v instanceof Integer i) prevSelectedHotbarSlot = i;
                    } catch (Throwable ignored2) { prevSelectedHotbarSlot = -1; }
                }
                mc.player.getInventory().setSelectedSlot(slot);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                drinking = true;
                drinkAttemptTicks = 0;
                drinkProgressTicks = 0;
                retryAttemptCooldown = 0;
                if (forceHoldUseSetting.get() && mc.options != null && mc.options.useKey != null) {
                    prevUseKeyPressed = mc.options.useKey.isPressed();
                    mc.options.useKey.setPressed(true);
                    forceHoldUse = true;
                }
                if (debugLogs.get()) announceClient("Drink attempt started (slot=" + slot + ")");
            } else {
                finishDrink(false);
            }
        }

        if (drinking) {
            if (mc.player == null) { abortDrink("player null"); return; }
            boolean isUsing = mc.player.isUsingItem();
            boolean usingBottle = isUsing && mc.player.getActiveItem() != null && mc.player.getActiveItem().getItem() == Items.OMINOUS_BOTTLE;

            if (usingBottle) {
                drinkProgressTicks++;
                if (forceHoldUseSetting.get() && forceHoldUse && mc.options != null && mc.options.useKey != null) {
                    // Reinforce hold every tick to avoid release.
                    mc.options.useKey.setPressed(true);
                }
                if (debugLogs.get() && drinkProgressTicks % 10 == 0) announceClient("Drinking progress=" + drinkProgressTicks);
                if (drinkProgressTicks >= 34 && !mc.player.isUsingItem()) {
                    announceClient("Ominous Bottle drink completed.");
                    finishDrink(true);
                }
            } else {
                if (drinkProgressTicks == 0 && retryDrink.get() && drinkAttemptTicks < maxDrinkAttemptTicks.get()) {
                    if (retryAttemptCooldown-- <= 0) {
                        int slot = findOminousBottleHotbarSlot();
                        if (slot != -1) {
                            mc.player.getInventory().setSelectedSlot(slot);
                            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                            drinkAttemptTicks++;
                            retryAttemptCooldown = retryAttemptIntervalTicks.get();
                            if (debugLogs.get()) announceClient("Retry drink attempt=" + drinkAttemptTicks + " (interval=" + retryAttemptIntervalTicks.get() + ")");
                        } else {
                            abortDrink("bottle missing");
                        }
                    }
                } else {
                    if (drinkProgressTicks > 0) {
                        announceClient("Bottle use ended early after progress=" + drinkProgressTicks + " ticks.");
                    } else if (retryDrink.get()) {
                        announceClient("Failed to begin drinking after attempts=" + drinkAttemptTicks);
                    }
                    finishDrink(false);
                }
            }
        }

        // Re-switch timer: if player manually changes slot, wait 10s then switch back
        if (reSwitchAfterIdle.get() && mc.player != null && mc.player.getInventory() != null) {
            int targetSlot = -1;
            switch (slotRestoreMode.get()) {
                case Previous:
                    if (prevSelectedHotbarSlot >= 0 && prevSelectedHotbarSlot <= 8) {
                        targetSlot = prevSelectedHotbarSlot;
                    }
                    break;
                case Specific:
                    int s = targetHotbarSlot.get() - 1;
                    if (s >= 0 && s <= 8) {
                        targetSlot = s;
                    }
                    break;
                case None:
                    break;
            }
            if (targetSlot >= 0) {
                try {
                    int currentSlot = mc.player.getInventory().getSelectedSlot();
                    if (currentSlot != targetSlot) {
                        idleTicksOffTarget++;
                        if (idleTicksOffTarget >= 200) { // 10 seconds = 200 ticks
                            mc.player.getInventory().setSelectedSlot(targetSlot);
                            idleTicksOffTarget = 0;
                            if (debugLogs.get()) announceClient("Re-switched to slot " + (targetSlot + 1) + " after 10s idle.");
                        }
                    } else {
                        idleTicksOffTarget = 0;
                    }
                } catch (Throwable ignored) {
                    // Fallback reflection if needed
                    try {
                        java.lang.reflect.Field f = mc.player.getInventory().getClass().getDeclaredField("selectedSlot");
                        f.setAccessible(true);
                        Object v = f.get(mc.player.getInventory());
                        if (v instanceof Integer currentSlot && currentSlot != targetSlot) {
                            idleTicksOffTarget++;
                            if (idleTicksOffTarget >= 200) {
                                mc.player.getInventory().setSelectedSlot(targetSlot);
                                idleTicksOffTarget = 0;
                                if (debugLogs.get()) announceClient("Re-switched to slot " + (targetSlot + 1) + " after 10s idle.");
                            }
                        } else {
                            idleTicksOffTarget = 0;
                        }
                    } catch (Throwable ignored2) {}
                }
            }
        }

        if (debugLogs.get() && hadRaidBossBar != hasRaidBossBar) {
            if (hasRaidBossBar) {
                java.util.List<String> raidTitles = bossBars.stream().map(this::safeTitle).filter(t -> t.toLowerCase().contains("raid")).toList();
                announceClient("Bossbar present (titles: " + String.join(", ", raidTitles) + ") size=" + bossBars.size());
            }
            else announceClient("Bossbar absent (size=" + bossBars.size() + ")");
        }
        hadHeroEffect = hasHeroEffect;
        hadRaidBossBar = hasRaidBossBar;
        hadRaidOmen = hasRaidOmen;
    }

    private int findOminousBottleHotbarSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack != null && stack.getItem() == Items.OMINOUS_BOTTLE) return i;
        }
        return -1;
    }

    private java.util.List<net.minecraft.client.gui.hud.ClientBossBar> getClientBossBars() {
        java.util.List<net.minecraft.client.gui.hud.ClientBossBar> list = new java.util.ArrayList<>();
        if (mc.inGameHud == null) return list;
        Object bossBarHud = mc.inGameHud.getBossBarHud();
        if (bossBarHud == null) return list;
        for (java.lang.reflect.Method m : bossBarHud.getClass().getDeclaredMethods()) {
            if (m.getParameterCount() == 0 && java.util.Map.class.isAssignableFrom(m.getReturnType())) {
                try {
                    m.setAccessible(true);
                    Object mapObj = m.invoke(bossBarHud);
                    if (mapObj instanceof java.util.Map<?, ?> map) {
                        for (Object o : map.values()) {
                            if (o instanceof net.minecraft.client.gui.hud.ClientBossBar bar) list.add((net.minecraft.client.gui.hud.ClientBossBar) o);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        for (java.lang.reflect.Field f : bossBarHud.getClass().getDeclaredFields()) {
            if (java.util.Map.class.isAssignableFrom(f.getType())) {
                try {
                    f.setAccessible(true);
                    Object mapObj = f.get(bossBarHud);
                    if (mapObj instanceof java.util.Map<?, ?> map) {
                        for (Object o : map.values()) {
                            if (o instanceof net.minecraft.client.gui.hud.ClientBossBar bar && !list.contains(o)) list.add((net.minecraft.client.gui.hud.ClientBossBar) o);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return list;
    }

    private boolean hasRaidVictoryInBossBar(java.util.List<net.minecraft.client.gui.hud.ClientBossBar> bars) {
        java.util.Set<String> keywords = java.util.Arrays.stream(victoryKeywords.get().split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(String::toLowerCase)
            .collect(java.util.stream.Collectors.toSet());
        for (net.minecraft.client.gui.hud.ClientBossBar bar : bars) {
            String lower = safeTitle(bar).toLowerCase();
            if (lower.contains("raid")) {
                for (String kw : keywords) if (lower.contains(kw)) return true;
            }
        }
        return false;
    }

    private String safeTitle(net.minecraft.client.gui.hud.ClientBossBar bar) {
        try { return bar.getName() != null ? bar.getName().getString() : ""; } catch (Throwable ignored) { return ""; }
    }

    private void announceClient(String message) {
        if (!debugLogs.get()) return;
        if (mc.inGameHud != null) {
            try {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[Raid Helper] " + message));
            } catch (Throwable ignored) {
            }
        }
    }

    private void abortDrink(String reason) {
        if (debugLogs.get()) announceClient("Drink aborted: " + reason);
        finishDrink(false);
    }

    private void finishDrink(boolean success) {
        // Release forced hold if we were holding.
        if (forceHoldUse && mc.options != null && mc.options.useKey != null) {
            mc.options.useKey.setPressed(prevUseKeyPressed);
        }
        // Re-enable AutoEat if we paused it
        if (autoEatWasActive) {
            AutoEat ae = Modules.get().get(AutoEat.class);
            if (ae != null && !ae.isActive()) {
                ae.toggle();
                if (debugLogs.get()) announceClient("Resumed AutoEat after drinking.");
            }
            autoEatWasActive = false;
        }
        // Restore hotbar slot based on user preference (both on success and abort).
        if (mc.player != null && mc.player.getInventory() != null) {
            int targetSlot = -1;
            switch (slotRestoreMode.get()) {
                case Previous:
                    if (prevSelectedHotbarSlot >= 0 && prevSelectedHotbarSlot <= 8) {
                        targetSlot = prevSelectedHotbarSlot;
                    }
                    break;
                case Specific:
                    // targetHotbarSlot is 1-9, convert to 0-8
                    int s = targetHotbarSlot.get() - 1;
                    if (s >= 0 && s <= 8) {
                        targetSlot = s;
                    }
                    break;
                case None:
                    // Do not restore slot
                    break;
            }
            if (targetSlot >= 0) {
                mc.player.getInventory().setSelectedSlot(targetSlot);
                if (debugLogs.get()) {
                    announceClient("Restored hotbar slot to " + (targetSlot + 1) + " (" + (success ? "success" : "abort") + ")");
                }
            }
        }
        drinking = false;
        raidJustFinished = false;
        announcedVictory = false;
        drinkAttemptTicks = 0;
        drinkProgressTicks = 0;
        retryAttemptCooldown = 0;
        forceHoldUse = false;
        announcedOmenSkip = false;
        prevSelectedHotbarSlot = -1;
    }

    private boolean hasRaidOmen() {
        try {
            for (net.minecraft.entity.effect.StatusEffectInstance inst : mc.player.getStatusEffects()) {
                if (inst == null) continue;
                net.minecraft.registry.entry.RegistryEntry<net.minecraft.entity.effect.StatusEffect> effEntry = inst.getEffectType();
                if (effEntry == null || effEntry.value() == null) continue;
                String key = String.valueOf(effEntry.value().getTranslationKey());
                if (key.endsWith(".bad_omen") || key.endsWith(".raid_omen")) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @Override
    public void onDeactivate() {
        // Safety: ensure key is restored when module deactivates.
        if (forceHoldUse && mc.options != null && mc.options.useKey != null) {
            mc.options.useKey.setPressed(prevUseKeyPressed);
        }
        forceHoldUse = false;
    }
}

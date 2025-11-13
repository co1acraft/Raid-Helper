package com.example.raidaddon;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
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

    private Boolean hadHeroEffect = null;
    private Boolean hadRaidBossBar = null;
    private boolean announcedVictory = false;
    private boolean raidJustFinished = false;
    private int postRaidTickCooldown = 0;
    // Drinking state machine
    private boolean drinking = false;
    private int drinkAttemptTicks = 0;
    private int drinkProgressTicks = 0;

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
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (!isActive()) return;
        if (!drinkOminousBottle.get()) return;
        if (mc.world == null || mc.player == null) return;

        boolean hasHeroEffect = mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.HERO_OF_THE_VILLAGE);
        java.util.List<net.minecraft.client.gui.hud.ClientBossBar> bossBars = getClientBossBars();
        boolean hasRaidBossBar = bossBars.stream().anyMatch(b -> safeTitle(b).toLowerCase().contains("raid"));
        boolean hasRaidVictory = hasRaidVictoryInBossBar(bossBars);

        if (hadHeroEffect == null || hadRaidBossBar == null) {
            hadHeroEffect = hasHeroEffect;
            hadRaidBossBar = hasRaidBossBar;
            return;
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

        if (raidJustFinished && !drinking && postRaidTickCooldown-- <= 0) {
            int slot = findOminousBottleHotbarSlot();
            if (slot != -1 && mc.player != null && mc.interactionManager != null) {
                mc.player.getInventory().setSelectedSlot(slot);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                drinking = true;
                drinkAttemptTicks = 0;
                drinkProgressTicks = 0;
                if (debugLogs.get()) announceClient("Drink attempt started (slot=" + slot + ")");
            } else {
                finishDrink();
            }
        }

        if (drinking) {
            if (mc.player == null) { abortDrink("player null"); return; }
            boolean isUsing = mc.player.isUsingItem();
            boolean usingBottle = isUsing && mc.player.getActiveItem() != null && mc.player.getActiveItem().getItem() == Items.OMINOUS_BOTTLE;

            if (usingBottle) {
                drinkProgressTicks++;
                if (debugLogs.get() && drinkProgressTicks % 10 == 0) announceClient("Drinking progress=" + drinkProgressTicks);
                if (drinkProgressTicks >= 34 && !mc.player.isUsingItem()) {
                    announceClient("Ominous Bottle drink completed.");
                    finishDrink();
                }
            } else {
                if (drinkProgressTicks == 0 && retryDrink.get() && drinkAttemptTicks < maxDrinkAttemptTicks.get()) {
                    int slot = findOminousBottleHotbarSlot();
                    if (slot != -1) {
                        mc.player.getInventory().setSelectedSlot(slot);
                        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                        drinkAttemptTicks++;
                        if (debugLogs.get() && drinkAttemptTicks % 5 == 0) announceClient("Retry drink attempt=" + drinkAttemptTicks);
                    } else {
                        abortDrink("bottle missing");
                    }
                } else {
                    if (drinkProgressTicks > 0) {
                        announceClient("Bottle use ended early after progress=" + drinkProgressTicks + " ticks.");
                    } else if (retryDrink.get()) {
                        announceClient("Failed to begin drinking after attempts=" + drinkAttemptTicks);
                    }
                    finishDrink();
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
        if (mc.inGameHud != null) {
            try {
                mc.inGameHud.getChatHud().addMessage(Text.literal("[Raid Helper] " + message));
            } catch (Throwable ignored) {
            }
        }
    }

    private void abortDrink(String reason) {
        if (debugLogs.get()) announceClient("Drink aborted: " + reason);
        finishDrink();
    }

    private void finishDrink() {
        drinking = false;
        raidJustFinished = false;
        announcedVictory = false;
        drinkAttemptTicks = 0;
        drinkProgressTicks = 0;
    }
}

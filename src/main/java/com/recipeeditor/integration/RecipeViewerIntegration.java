package com.recipeeditor.integration;

import com.recipeeditor.recipe.CustomRecipeDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.recipe.RecipeEntry;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class RecipeViewerIntegration {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "RecipeViewer-Debounce");
        thread.setDaemon(true);
        return thread;
    });

    private static ScheduledFuture<?> pendingTask = null;
    private static final AtomicBoolean isReloading = new AtomicBoolean(false);
    private static long lastReloadStartTime = 0;
    private static final long RELOAD_TIMEOUT_MS = 8000;
    private static final long DEBOUNCE_DELAY_MS = 350;

    private static boolean reiPredicateRegistered = false;

    // Cached MethodHandle for REI getDisplayOrigin – set once, then reused without reflection overhead
    private static MethodHandle cachedGetDisplayOrigin = null;

    /**
     * Initializes runtime hooks for recipe viewers (e.g. REI DisplayVisibilityPredicate).
     */
    public static void init() {
        registerReiVisibilityPredicate();
    }

    /**
     * Schedules a debounced reload of recipe viewers after a recipe is saved/deleted.
     * The workstation guard ensures it won't fire while a crafting UI is open.
     */
    public static void updateRecipeInViewers(Object savedRecipe, String deletedKey) {
        reloadRecipeViewers();
    }

    public static void removeRecipeFromViewers(String deletedKey) {
        reloadRecipeViewers();
    }

    private static synchronized void registerReiVisibilityPredicate() {
        if (reiPredicateRegistered || !FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) return;
        try {
            Class<?> displayRegistryClass = Class.forName("me.shedaniel.rei.api.client.registry.display.DisplayRegistry");
            Object registry = displayRegistryClass.getMethod("getInstance").invoke(null);
            if (registry == null) return;

            Class<?> predicateClass = Class.forName("me.shedaniel.rei.api.client.registry.display.visibility.DisplayVisibilityPredicate");
            Class<?> eventResultClass = Class.forName("dev.architectury.event.EventResult");
            Object resultPass = eventResultClass.getMethod("pass").invoke(null);
            Object resultInterruptFalse = eventResultClass.getMethod("interruptFalse").invoke(null);

            // Build a fast MethodHandle for getDisplayOrigin – avoid per-call reflection cost
            for (Method m : displayRegistryClass.getMethods()) {
                if (m.getName().equals("getDisplayOrigin") && m.getParameterCount() == 1) {
                    try {
                        MethodHandle mh = MethodHandles.lookup().unreflect(m);
                        // Bind the registry instance so calls only need (display) arg
                        cachedGetDisplayOrigin = mh.bindTo(registry);
                    } catch (Throwable t) {
                        // MethodHandles.lookup() may lack access to impl classes; fall back to null
                        cachedGetDisplayOrigin = null;
                    }
                    break;
                }
            }

            // If MethodHandle failed, try via impl method directly
            if (cachedGetDisplayOrigin == null) {
                for (Method m : registry.getClass().getMethods()) {
                    if (m.getName().equals("getDisplayOrigin") && m.getParameterCount() == 1) {
                        m.setAccessible(true);
                        final Object reg = registry;
                        final Method origin = m;
                        // Wrap as closure captured in the proxy – no extra field needed
                        cachedGetDisplayOrigin = MethodHandles.lookup()
                                .unreflect(origin)
                                .bindTo(reg);
                        break;
                    }
                }
            }

            final MethodHandle originHandle = cachedGetDisplayOrigin;

            Object proxy = Proxy.newProxyInstance(
                    predicateClass.getClassLoader(),
                    new Class<?>[]{predicateClass},
                    (p, method, args) -> {
                        String name = method.getName();

                        if (name.equals("handleDisplay") && args != null && args.length == 2) {
                            Object display = args[1];
                            if (display != null && originHandle != null) {
                                try {
                                    Object origin = originHandle.invoke(display);
                                    if (origin instanceof RecipeEntry<?> re
                                            && CustomRecipeDispatcher.isRecipeOverridden(re)) {
                                        return resultInterruptFalse;
                                    }
                                } catch (Throwable ignored) {}
                            }
                            return resultPass;

                        } else if (name.equals("getPriority")) {
                            return 100.0;

                        } else if (name.equals("compareTo")) {
                            // Compare by priority: we return 0 (equal) for anything since we only
                            // need to be first enough to hide overridden recipes.
                            if (args != null && args.length == 1 && args[0] != null) {
                                try {
                                    Method getPrio = args[0].getClass().getMethod("getPriority");
                                    double otherPrio = (double) getPrio.invoke(args[0]);
                                    return Double.compare(otherPrio, 100.0); // reversed: higher priority first
                                } catch (Throwable ignored) {}
                            }
                            return 0;

                        } else if (name.equals("equals")) {
                            return p == args[0];

                        } else if (name.equals("hashCode")) {
                            return System.identityHashCode(p);

                        } else if (name.equals("toString")) {
                            return "RecipeEditorVisibilityPredicate";
                        }

                        return null;
                    }
            );

            Method registerMethod = null;
            for (Method m : registry.getClass().getMethods()) {
                if (m.getName().equals("registerVisibilityPredicate") && m.getParameterCount() == 1) {
                    registerMethod = m;
                    break;
                }
            }
            if (registerMethod == null) {
                registerMethod = displayRegistryClass.getMethod("registerVisibilityPredicate", predicateClass);
            }
            registerMethod.invoke(registry, proxy);
            reiPredicateRegistered = true;
        } catch (Throwable ignored) {}
    }

    /**
     * Schedules a debounced full reload of recipe viewers (used for full resets).
     * Protected by Workstation Guard: will NEVER execute while a workstation (HandledScreen) is open!
     */
    public static synchronized void reloadRecipeViewers() {
        scheduleReload(DEBOUNCE_DELAY_MS);
    }

    private static synchronized void scheduleReload(long delayMs) {
        if (pendingTask != null && !pendingTask.isDone()) {
            pendingTask.cancel(false);
        }
        pendingTask = SCHEDULER.schedule(RecipeViewerIntegration::checkAndDispatchReload, delayMs, TimeUnit.MILLISECONDS);
    }

    private static void checkAndDispatchReload() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            return;
        }

        client.execute(() -> {
            if (client.world == null) {
                return;
            }

            // WORKSTATION GUARD: If the player has a container / crafting table / furnace open,
            // DO NOT reload now to prevent UI lag. Wait until they close the screen.
            if (client.currentScreen instanceof HandledScreen) {
                scheduleReload(500);
                return;
            }

            long now = System.currentTimeMillis();
            boolean currentlyBusy = isReloading.get() && (now - lastReloadStartTime < RELOAD_TIMEOUT_MS);
            if (!currentlyBusy && isAnyViewerReloading()) {
                currentlyBusy = true;
            }

            if (currentlyBusy) {
                scheduleReload(300);
                return;
            }

            performReload();
        });
    }

    private static void performReload() {
        isReloading.set(true);
        lastReloadStartTime = System.currentTimeMillis();

        if (FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
            try {
                Class<?> reiRuntimeClass = Class.forName("me.shedaniel.rei.api.client.REIRuntime");
                Object instance = reiRuntimeClass.getMethod("getInstance").invoke(null);
                if (instance != null) {
                    Method startReloadMethod = reiRuntimeClass.getMethod("startReload");
                    Object result = startReloadMethod.invoke(instance);
                    if (result instanceof CompletableFuture<?> future) {
                        future.whenComplete((res, err) -> isReloading.set(false));
                    } else {
                        isReloading.set(false);
                    }
                } else {
                    isReloading.set(false);
                }
            } catch (Throwable ignored) {
                isReloading.set(false);
            }
        } else {
            isReloading.set(false);
        }
    }

    private static boolean isAnyViewerReloading() {
        if (FabricLoader.getInstance().isModLoaded("roughlyenoughitems")) {
            try {
                Class<?> pmClass = Class.forName("me.shedaniel.rei.api.common.plugins.PluginManager");
                try {
                    Method areAnyMethod = pmClass.getMethod("areAnyReloading");
                    Object res = areAnyMethod.invoke(null);
                    if (res instanceof Boolean b && b) return true;
                } catch (NoSuchMethodException e) {
                    Method getInstanceMethod = pmClass.getMethod("getInstance");
                    Object pmInstance = getInstanceMethod.invoke(null);
                    if (pmInstance != null) {
                        Method areAnyMethod = pmInstance.getClass().getMethod("areAnyReloading");
                        Object res = areAnyMethod.invoke(pmInstance);
                        if (res instanceof Boolean b && b) return true;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return false;
    }
}

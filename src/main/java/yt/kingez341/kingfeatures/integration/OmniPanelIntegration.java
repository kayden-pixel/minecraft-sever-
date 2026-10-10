package yt.kingez341.kingfeatures.integration;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class OmniPanelIntegration {
    private final KingFeaturesPlugin plugin;
    private volatile Object service;
    private volatile String status = "not-connected";
    private BukkitTask monitor;
    private BukkitTask heartbeat;

    public OmniPanelIntegration(KingFeaturesPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("omnipanel.enabled", true)) {
            status = "disabled";
            return;
        }
        attemptConnection();
        long seconds = Math.max(5L, plugin.getConfig().getLong("omnipanel.reconnect-seconds", 10L));
        monitor = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (service == null) attemptConnection();
        }, seconds * 20L, seconds * 20L);
    }

    public void stop() {
        if (monitor != null) monitor.cancel();
        if (heartbeat != null) heartbeat.cancel();
        monitor = null;
        heartbeat = null;
        Object current = service;
        service = null;
        if (current != null) {
            tryInvoke(current, "disconnectAddon", plugin);
        }
    }

    public String status() { return status; }
    public boolean isConnected() { return service != null; }

    /** Read a value from OmniPanel's public placeholder API without linking against OmniPanel classes. */
    public String resolvePlaceholder(Player player, String key) {
        Object current = service;
        if (current == null || player == null || key == null) return null;
        Object value = tryInvoke(current, "resolvePlaceholder", player, key);
        return value == null ? null : String.valueOf(value);
    }

    private void attemptConnection() {
        try {
            Plugin omni = Bukkit.getPluginManager().getPlugin("OmniPanel");
            if (omni == null || !omni.isEnabled()) {
                status = "OmniPanel not installed/enabled";
                service = null;
                return;
            }
            ClassLoader loader = omni.getClass().getClassLoader();
            Class<?> serviceType = Class.forName("yt.kingez341.omnipanel.OmniPanelService", true, loader);
            Class<?> servicesType = Class.forName("yt.kingez341.omnipanel.OmniPanelServices", true, loader);
            Object descriptor = createDescriptor(loader);
            Object connection = connect(servicesType, descriptor);
            if (connection == null || !readBoolean(connection, "connected")) {
                status = "connection rejected" + describeConnection(connection);
                service = null;
                return;
            }
            Object resolved = invokeNoArg(connection, "service");
            if (resolved instanceof Optional<?> optional) resolved = optional.orElse(null);
            if (resolved == null || !serviceType.isInstance(resolved)) {
                status = "connection returned no service";
                service = null;
                return;
            }
            service = resolved;
            boolean button = registerButton(loader, resolved);
            registerDisconnectHandler(resolved);
            status = "connected api=" + String.valueOf(tryInvoke(resolved, "getApiVersion")) + " button=" + button;
            plugin.getLogger().info("OmniPanel integration: " + status);
            startHeartbeat();
        } catch (Throwable throwable) {
            service = null;
            status = throwable.getClass().getSimpleName() + ": " + rootMessage(throwable);
            plugin.getLogger().warning("OmniPanel integration unavailable: " + status);
        }
    }

    private Object connect(Class<?> servicesType, Object descriptor) throws Exception {
        Set<String> required = Set.of("main-panel", "addon-lifecycle");
        if (descriptor != null) {
            Method m3 = findMethod(servicesType, "connect", 3, true);
            if (m3 != null) return invoke(null, m3, plugin, descriptor, required);
            Method m2 = findMethod(servicesType, "connect", 2, true);
            if (m2 != null) return invoke(null, m2, plugin, descriptor);
        }
        Method m1 = findMethod(servicesType, "connect", 1, true);
        if (m1 == null) throw new NoSuchMethodException("OmniPanelServices.connect");
        return invoke(null, m1, plugin);
    }

    private Object createDescriptor(ClassLoader loader) {
        try {
            Class<?> type = Class.forName("yt.kingez341.omnipanel.OmniPanelService$AddonDescriptor", true, loader);
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (constructor.getParameterCount() != 7) continue;
                Class<?>[] p = constructor.getParameterTypes();
                if (p[0] == String.class && p[1] == String.class && p[2] == String.class && p[3] == String.class
                        && Material.class.isAssignableFrom(p[4]) && p[5] == String.class && Set.class.isAssignableFrom(p[6])) {
                    constructor.setAccessible(true);
                    return constructor.newInstance(
                            "kingfeatures",
                            "KingFeatures / Troll Lab",
                            plugin.getDescription().getVersion(),
                            "SMP feature hub: Troll Lab, King's Favor bottles and resource-pack delivery",
                            Material.EXPERIENCE_BOTTLE,
                            "kingfeatures.admin",
                            Set.of("main-panel", "addon-lifecycle", "addon-health")
                    );
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean registerButton(ClassLoader loader, Object currentService) {
        try {
            Class<?> panelButton = Class.forName("yt.kingez341.omnipanel.OmniPanelService$PanelButton", true, loader);
            Object button = null;
            for (Constructor<?> constructor : panelButton.getDeclaredConstructors()) {
                if (constructor.getParameterCount() != 8) continue;
                Class<?>[] p = constructor.getParameterTypes();
                if (p[0] == String.class && p[1] == String.class && Material.class.isAssignableFrom(p[2])
                        && List.class.isAssignableFrom(p[3]) && p[4] == String.class
                        && (p[5] == int.class || p[5] == Integer.class) && (p[6] == int.class || p[6] == Integer.class)
                        && List.class.isAssignableFrom(p[7])) {
                    constructor.setAccessible(true);
                    button = constructor.newInstance(
                            "kingfeatures_troll_lab",
                            "&d&lTroll Lab",
                            Material.BREWING_STAND,
                            List.of("&7Advanced staff prank controls", "&7Search, target and audit actions", "", "&dClick to open"),
                            "kingfeatures.trolllab",
                            plugin.getConfig().getInt("omnipanel.button-slot", 45),
                            plugin.getConfig().getInt("omnipanel.button-priority", 110),
                            List.of("troll", "lab", "kingfeatures", "prank")
                    );
                    break;
                }
            }
            if (button == null) return false;

            Method detailed = findMethod(currentService.getClass(), "registerMainPanelCommandButtonDetailed", 5, false);
            if (detailed == null) detailed = findMethodOnInterfaces(currentService.getClass(), "registerMainPanelCommandButtonDetailed", 5);
            if (detailed != null) {
                Object result = invoke(currentService, detailed, plugin, button, "kingfeatures troll", false, true);
                return registrationSuccess(result);
            }
            Method legacy = findMethod(currentService.getClass(), "registerMainPanelCommandButton", 4, false);
            if (legacy == null) legacy = findMethodOnInterfaces(currentService.getClass(), "registerMainPanelCommandButton", 4);
            if (legacy != null) {
                Object result = invoke(currentService, legacy, plugin, button, "kingfeatures troll", false);
                return result instanceof Boolean b ? b : result != null;
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("OmniPanel Troll Lab button registration failed: " + rootMessage(throwable));
        }
        return false;
    }

    private void registerDisconnectHandler(Object currentService) {
        Method method = findMethodOnInterfaces(currentService.getClass(), "registerDisconnectHandler", 2);
        if (method == null) method = findMethod(currentService.getClass(), "registerDisconnectHandler", 2, false);
        if (method == null) return;
        try {
            invoke(currentService, method, plugin, (Runnable) () -> {
                service = null;
                status = "disconnected by OmniPanel";
            });
        } catch (Throwable ignored) {}
    }

    private void startHeartbeat() {
        if (heartbeat != null) heartbeat.cancel();
        heartbeat = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Object current = service;
            if (current == null) return;
            Object result = tryInvoke(current, "heartbeat", plugin);
            if (result instanceof Boolean b && !b) {
                service = null;
                status = "heartbeat rejected";
            }
        }, 20L * 30L, 20L * 30L);
    }

    private static boolean registrationSuccess(Object result) {
        if (result == null) return false;
        if (result instanceof Boolean b) return b;
        for (String method : new String[]{"success", "registered", "accepted"}) {
            try {
                Object value = invokeNoArg(result, method);
                if (value instanceof Boolean b) return b;
            } catch (Throwable ignored) {}
        }
        return true;
    }

    private static String describeConnection(Object result) {
        if (result == null) return "";
        Object state = null;
        Object message = null;
        try { state = invokeNoArg(result, "state"); } catch (Throwable ignored) {}
        try { message = invokeNoArg(result, "message"); } catch (Throwable ignored) {}
        return " state=" + state + " message=" + message;
    }

    private static boolean readBoolean(Object object, String method) {
        try {
            Object value = invokeNoArg(object, method);
            return value instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Object invokeNoArg(Object target, String name) throws Exception {
        Method method = findMethodOnInterfaces(target.getClass(), name, 0);
        if (method == null) method = findMethod(target.getClass(), name, 0, false);
        if (method == null) throw new NoSuchMethodException(name);
        return invoke(target, method);
    }

    private static Object tryInvoke(Object target, String name, Object... args) {
        try {
            Method method = findMethodOnInterfaces(target.getClass(), name, args.length);
            if (method == null) method = findMethod(target.getClass(), name, args.length, false);
            if (method == null) return null;
            return invoke(target, method, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethodOnInterfaces(Class<?> type, String name, int count) {
        for (Class<?> iface : type.getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == count) return method;
            }
            Method nested = findMethodOnInterfaces(iface, name, count);
            if (nested != null) return nested;
        }
        Class<?> parent = type.getSuperclass();
        return parent == null ? null : findMethodOnInterfaces(parent, name, count);
    }

    private static Method findMethod(Class<?> type, String name, int count, boolean staticOnly) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == count && (!staticOnly || Modifier.isStatic(method.getModifiers()))) return method;
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == count && (!staticOnly || Modifier.isStatic(method.getModifiers()))) return method;
        }
        return null;
    }

    private static Object invoke(Object target, Method method, Object... args) throws Exception {
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        return cursor.getMessage() == null ? cursor.getClass().getSimpleName() : cursor.getMessage().replace('\n', ' ');
    }
}

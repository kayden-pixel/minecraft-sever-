package yt.kingez341.kingfeatures.economy;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;

public final class UltimateEconomyBridge {
    public record MoneyResult(boolean success, double balance, String message) {}

    private final KingFeaturesPlugin plugin;
    private volatile Object api;
    private volatile String status = "not-connected";

    public UltimateEconomyBridge(KingFeaturesPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        try {
            Plugin uep = Bukkit.getPluginManager().getPlugin("UltimateEconomyPlus");
            if (uep == null || !uep.isEnabled()) {
                api = null;
                status = "UltimateEconomyPlus not installed/enabled";
                return false;
            }
            ClassLoader loader = uep.getClass().getClassLoader();
            Class<?> services = Class.forName("kingez341yt.plugin.api.UltimateEconomyServices", true, loader);
            Method get = findStatic(services, "require", 0);
            if (get == null) get = findStatic(services, "get", 0);
            if (get == null) throw new NoSuchMethodException("UltimateEconomyServices.require/get");
            get.setAccessible(true);
            Object resolved = get.invoke(null);
            if (resolved instanceof java.util.Optional<?> optional) resolved = optional.orElse(null);
            if (resolved == null) throw new IllegalStateException("UEP API service is not ready");
            api = resolved;
            status = "connected:" + resolved.getClass().getName();
            return true;
        } catch (Throwable throwable) {
            api = null;
            status = throwable.getClass().getSimpleName() + ": " + safeMessage(throwable);
            plugin.getLogger().warning("UEP integration unavailable: " + status);
            return false;
        }
    }

    public boolean isConnected() {
        return api != null;
    }

    public String status() {
        return status;
    }

    public MoneyResult withdraw(Player player, double amount) {
        if (amount < 0) return new MoneyResult(false, getBalance(player), "Invalid amount");
        Object current = api;
        if (current == null && !connect()) return new MoneyResult(false, 0.0, "Ultimate Economy Plus is unavailable");
        current = api;
        try {
            Method method = findCompatible(current.getClass(), "withdraw", Player.class, double.class);
            if (method == null) method = findCompatible(current.getClass(), "withdraw", Player.class, Double.class);
            if (method == null) throw new NoSuchMethodException("withdraw(Player,double)");
            Object result = invoke(current, method, player, amount);
            boolean ok = extractSuccess(result);
            double balance = extractBalance(result, getBalance(player));
            return new MoneyResult(ok, balance, ok ? "ok" : extractMessage(result, "Transaction declined"));
        } catch (Throwable throwable) {
            return new MoneyResult(false, getBalance(player), safeMessage(throwable));
        }
    }

    public MoneyResult deposit(Player player, double amount) {
        Object current = api;
        if (current == null && !connect()) return new MoneyResult(false, 0.0, "Ultimate Economy Plus is unavailable");
        current = api;
        try {
            Method method = findCompatible(current.getClass(), "deposit", Player.class, double.class);
            if (method == null) method = findCompatible(current.getClass(), "deposit", Player.class, Double.class);
            if (method == null) throw new NoSuchMethodException("deposit(Player,double)");
            Object result = invoke(current, method, player, amount);
            boolean ok = extractSuccess(result);
            double balance = extractBalance(result, getBalance(player));
            return new MoneyResult(ok, balance, ok ? "ok" : extractMessage(result, "Deposit failed"));
        } catch (Throwable throwable) {
            return new MoneyResult(false, getBalance(player), safeMessage(throwable));
        }
    }

    public double getBalance(Player player) {
        Object current = api;
        if (current == null && !connect()) return 0.0;
        current = api;
        try {
            Method method = findCompatible(current.getClass(), "getBalance", Player.class);
            if (method == null) return 0.0;
            Object value = invoke(current, method, player);
            return value instanceof Number number ? number.doubleValue() : 0.0;
        } catch (Throwable ignored) {
            return 0.0;
        }
    }

    private static boolean extractSuccess(Object result) {
        if (result == null) return false;
        if (result instanceof Boolean b) return b;
        for (String name : new String[]{"transactionSuccess", "success", "isSuccess", "successful"}) {
            try {
                Method method = result.getClass().getMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(result);
                if (value instanceof Boolean b) return b;
            } catch (ReflectiveOperationException ignored) {}
        }
        return false;
    }

    private static double extractBalance(Object result, double fallback) {
        if (result == null) return fallback;
        for (String name : new String[]{"balance", "getBalance", "newBalance"}) {
            try {
                Method method = result.getClass().getMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(result);
                if (value instanceof Number n) return n.doubleValue();
            } catch (ReflectiveOperationException ignored) {}
        }
        try {
            var field = result.getClass().getField("balance");
            field.setAccessible(true);
            Object value = field.get(result);
            if (value instanceof Number n) return n.doubleValue();
        } catch (ReflectiveOperationException ignored) {}
        return fallback;
    }

    private static String extractMessage(Object result, String fallback) {
        if (result == null) return fallback;
        for (String name : new String[]{"message", "getMessage", "error"}) {
            try {
                Method method = result.getClass().getMethod(name);
                method.setAccessible(true);
                Object value = method.invoke(result);
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
            } catch (ReflectiveOperationException ignored) {}
        }
        return fallback;
    }

    private static Method findStatic(Class<?> type, String name, int count) {
        for (Method method : type.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name) && method.getParameterCount() == count) return method;
        }
        for (Method method : type.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers()) && method.getName().equals(name) && method.getParameterCount() == count) return method;
        }
        return null;
    }

    private static Method findCompatible(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> cursor : new Class<?>[]{type}) {
            for (Method method : cursor.getMethods()) {
                if (matches(method, name, parameters)) return method;
            }
            for (Method method : cursor.getDeclaredMethods()) {
                if (matches(method, name, parameters)) return method;
            }
        }
        for (Class<?> iface : type.getInterfaces()) {
            for (Method method : iface.getMethods()) {
                if (matches(method, name, parameters)) return method;
            }
        }
        return null;
    }

    private static boolean matches(Method method, String name, Class<?>... parameters) {
        if (!method.getName().equals(name) || method.getParameterCount() != parameters.length) return false;
        Class<?>[] actual = method.getParameterTypes();
        for (int i = 0; i < actual.length; i++) {
            Class<?> expected = wrap(parameters[i]);
            Class<?> got = wrap(actual[i]);
            if (!(got.isAssignableFrom(expected) || expected.isAssignableFrom(got))) return false;
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == double.class) return Double.class;
        if (type == int.class) return Integer.class;
        if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static Object invoke(Object target, Method method, Object... args) throws ReflectiveOperationException {
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static String safeMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) cursor = cursor.getCause();
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? cursor.getClass().getSimpleName() : message.replace('\n', ' ').toLowerCase(Locale.ROOT);
    }
}

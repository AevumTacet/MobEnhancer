package com.mobenhancer.integration;

import net.momirealms.craftengine.bukkit.item.BukkitItemManager;
import net.momirealms.craftengine.core.util.Key;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hook de integración con CraftEngine (softdependency).
 *
 * Uso:
 *   CraftEngineHook.initialize(plugin);   // en onEnable(), antes de cualquier uso
 *   CraftEngineHook.resolveItem("namespace:id");   // desde cualquier punto del plugin
 *
 * Si CraftEngine no está instalado, resolveItem() devuelve null para items con namespace
 * distinto de "minecraft", sin lanzar excepción.
 *
 * Nota sobre el delay de inicialización:
 * CraftEngine puede tardar en registrar su ItemManager durante el startup del servidor.
 * Por eso initialize() programa una verificación retrasada (60 ticks = 3 segundos) en
 * lugar de comprobar en el mismo tick que se llama.
 */
public class CraftEngineHook {

    private static boolean available = false;
    public static boolean initialized = false;
    private static JavaPlugin pluginRef;

    private CraftEngineHook() {}

    /**
     * Llama a este método desde MobEnhancer.onEnable().
     * Programa una verificación retrasada para dar tiempo a CraftEngine a cargar.
     */
    public static void initialize(JavaPlugin plugin) {
        pluginRef = plugin;

        // Verificación inmediata — puede que ya esté cargado si el orden de plugins lo permite
        if (isCraftEnginePresent()) {
            available = true;
            initialized = true;
            plugin.getLogger().info("[CraftEngineHook] CraftEngine detectado. Integración activa.");
            return;
        }

        // CraftEngine no estaba listo en el primer tick — programar reintento con delay
        // 60 ticks = 3 segundos, suficiente para que CraftEngine termine su onEnable()
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (isCraftEnginePresent()) {
                available = true;
                plugin.getLogger().info("[CraftEngineHook] CraftEngine detectado tras delay. Integración activa.");
            } else {
                available = false;
                plugin.getLogger().info("[CraftEngineHook] CraftEngine no encontrado. " +
                        "Los items con namespace custom serán ignorados.");
            }
            initialized = true;
        }, 60L);
    }

    /**
     * @return true si CraftEngine está disponible y su API está operativa.
     */
    public static boolean isAvailable() {
        return available;
    }

    /**
     * Resuelve un identificador de item a un ItemStack.
     *
     * Formatos aceptados:
     *   "DIAMOND"                  → Material vanilla (sin namespace)
     *   "minecraft:diamond"        → Material vanilla (namespace minecraft)
     *   "mynamespace:custom_item"  → Item de CraftEngine
     *
     * @param identifier El identificador del item tal como aparece en config.yml
     * @return ItemStack resuelto, o null si no se pudo resolver
     */
    public static ItemStack resolveItem(String identifier) {
        return resolveItem(identifier, 1);
    }

    /**
     * Resuelve un identificador de item a un ItemStack con cantidad específica.
     *
     * @param identifier El identificador del item
     * @param amount     Cantidad del item
     * @return ItemStack resuelto, o null si no se pudo resolver
     */
    public static ItemStack resolveItem(String identifier, int amount) {
        if (identifier == null || identifier.isBlank()) return null;

        String trimmed = identifier.trim();

        // ── Sin namespace: tratar como Material vanilla directamente ──────────
        if (!trimmed.contains(":")) {
            Material material = Material.getMaterial(trimmed.toUpperCase());
            if (material == null) {
                logWarn("Material vanilla no encontrado: " + trimmed);
                return null;
            }
            return new ItemStack(material, amount);
        }

        // ── Con namespace ─────────────────────────────────────────────────────
        String[] parts = trimmed.split(":", 2);
        String namespace = parts[0].toLowerCase();
        String path      = parts[1].toLowerCase();

        // Namespace "minecraft": resolver como Material vanilla
        if (namespace.equals("minecraft")) {
            // Convertir "minecraft:diamond" → "DIAMOND" para getMaterial()
            Material material = Material.getMaterial(path.toUpperCase());
            if (material == null) {
                logWarn("Material vanilla no encontrado para: " + trimmed);
                return null;
            }
            return new ItemStack(material, amount);
        }

        // Cualquier otro namespace: delegar a CraftEngine
        if (!available) {
            // CraftEngine no disponible — ignorar silenciosamente
            // (el warning ya se emitió en initialize())
            return null;
        }

        try {
            Key itemId = Key.of(namespace, path);
            ItemStack stack = BukkitItemManager.instance().buildItemStack(itemId, null);

            if (stack == null) {
                logWarn("CraftEngine no encontró el item: " + trimmed);
                return null;
            }

            // buildItemStack devuelve con amount = 1; ajustar si se pidió más
            if (amount != 1) {
                stack = stack.clone();
                stack.setAmount(amount);
            }

            return stack;

        } catch (Exception e) {
            logWarn("Error al resolver item de CraftEngine '" + trimmed + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Comprueba si el plugin CraftEngine está presente y su ItemManager operativo.
     * Separado de initialize() para poder llamarlo en el reintento con delay.
     */
    private static boolean isCraftEnginePresent() {
        // Primero: ¿está el plugin registrado en Bukkit?
        if (Bukkit.getPluginManager().getPlugin("CraftEngine") == null) return false;
        if (!Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) return false;

        // Segundo: ¿su API está operativa?
        // BukkitItemManager.instance() puede ser null si CraftEngine no terminó su init
        try {
            return BukkitItemManager.instance() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static void logWarn(String message) {
        if (pluginRef != null) {
            pluginRef.getLogger().warning("[CraftEngineHook] " + message);
        }
    }
}
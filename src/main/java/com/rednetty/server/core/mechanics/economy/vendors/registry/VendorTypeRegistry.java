package com.rednetty.server.core.mechanics.economy.vendors.registry;

import com.rednetty.server.core.mechanics.economy.vendors.interfaces.VendorType;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Registry for all vendor types in the system
 * 
 * This class manages the registration, lookup, and organization of all vendor types,
 * providing a centralized system for vendor type management.
 */
public class VendorTypeRegistry {
    private static VendorTypeRegistry instance;
    
    private final Map<String, VendorType> registeredTypes = new ConcurrentHashMap<>();
    private final Map<VendorType.VendorCategory, List<VendorType>> typesByCategory = new ConcurrentHashMap<>();
    private final Logger logger;
    
    private VendorTypeRegistry(Logger logger) {
        this.logger = logger;
        initializeCategories();
    }
    
    public static void initialize(Logger logger) {
        if (instance == null) {
            instance = new VendorTypeRegistry(logger);
        }
    }
    
    public static VendorTypeRegistry getInstance() {
        if (instance == null) {
            throw new IllegalStateException("VendorTypeRegistry not initialized!");
        }
        return instance;
    }
    
    /**
     * Register a new vendor type
     * 
     * @param vendorType The vendor type to register
     * @throws IllegalArgumentException if a type with the same name already exists
     */
    public void registerVendorType(VendorType vendorType) {
        if (vendorType == null) {
            throw new IllegalArgumentException("Vendor type cannot be null");
        }
        
        String typeName = vendorType.getTypeName();
        if (typeName == null || typeName.trim().isEmpty()) {
            throw new IllegalArgumentException("Vendor type name cannot be null or empty");
        }
        
        // Normalize type name
        typeName = typeName.toLowerCase().trim();
        
        if (registeredTypes.containsKey(typeName)) {
            logger.warning("Vendor type '" + typeName + "' is already registered. Replacing with new implementation.");
        }
        
        registeredTypes.put(typeName, vendorType);
        
        // Add to category
        VendorType.VendorCategory category = vendorType.getCategory();
        typesByCategory.computeIfAbsent(category, k -> new ArrayList<>()).add(vendorType);
        
        logger.info("Registered vendor type: " + typeName + " (category: " + category + ")");
    }
    
    /**
     * Unregister a vendor type
     * 
     * @param typeName The name of the vendor type to unregister
     * @return true if the type was unregistered, false if it wasn't registered
     */
    public boolean unregisterVendorType(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            return false;
        }
        
        typeName = typeName.toLowerCase().trim();
        VendorType removed = registeredTypes.remove(typeName);
        
        if (removed != null) {
            // Remove from category
            VendorType.VendorCategory category = removed.getCategory();
            List<VendorType> categoryList = typesByCategory.get(category);
            if (categoryList != null) {
                categoryList.remove(removed);
                if (categoryList.isEmpty()) {
                    typesByCategory.remove(category);
                }
            }
            
            logger.info("Unregistered vendor type: " + typeName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Get a vendor type by name
     * 
     * @param typeName The name of the vendor type
     * @return The vendor type, or null if not found
     */
    public VendorType getVendorType(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            return null;
        }
        
        return registeredTypes.get(typeName.toLowerCase().trim());
    }
    
    /**
     * Check if a vendor type is registered
     * 
     * @param typeName The name of the vendor type
     * @return true if the type is registered
     */
    public boolean isRegistered(String typeName) {
        if (typeName == null || typeName.trim().isEmpty()) {
            return false;
        }
        
        return registeredTypes.containsKey(typeName.toLowerCase().trim());
    }
    
    /**
     * Get all registered vendor types
     * 
     * @return Collection of all registered vendor types
     */
    public Collection<VendorType> getAllVendorTypes() {
        return new ArrayList<>(registeredTypes.values());
    }
    
    /**
     * Get all registered vendor type names
     * 
     * @return Set of all registered vendor type names
     */
    public Set<String> getAllVendorTypeNames() {
        return new HashSet<>(registeredTypes.keySet());
    }
    
    /**
     * Get vendor types by category
     * 
     * @param category The category to filter by
     * @return List of vendor types in the specified category
     */
    public List<VendorType> getVendorTypesByCategory(VendorType.VendorCategory category) {
        return new ArrayList<>(typesByCategory.getOrDefault(category, Collections.emptyList()));
    }
    
    /**
     * Get all available categories
     * 
     * @return Set of all categories that have registered vendor types
     */
    public Set<VendorType.VendorCategory> getAvailableCategories() {
        return new HashSet<>(typesByCategory.keySet());
    }
    
    /**
     * Get vendor types accessible by a specific player
     * 
     * @param player The player to check access for
     * @return List of vendor types the player can access
     */
    public List<VendorType> getAccessibleVendorTypes(Player player) {
        return registeredTypes.values().stream()
                .filter(type -> type.canAccess(player))
                .sorted(Comparator.comparingInt(VendorType::getPriority))
                .collect(Collectors.toList());
    }
    
    /**
     * Get vendor types accessible by a specific player in a category
     * 
     * @param player The player to check access for
     * @param category The category to filter by
     * @return List of vendor types the player can access in the category
     */
    public List<VendorType> getAccessibleVendorTypesByCategory(Player player, VendorType.VendorCategory category) {
        return typesByCategory.getOrDefault(category, Collections.emptyList()).stream()
                .filter(type -> type.canAccess(player))
                .sorted(Comparator.comparingInt(VendorType::getPriority))
                .collect(Collectors.toList());
    }
    
    /**
     * Get enabled vendor types only
     * 
     * @return List of enabled vendor types
     */
    public List<VendorType> getEnabledVendorTypes() {
        return registeredTypes.values().stream()
                .filter(VendorType::isEnabled)
                .sorted(Comparator.comparingInt(VendorType::getPriority))
                .collect(Collectors.toList());
    }
    
    /**
     * Get registry statistics
     * 
     * @return Registry statistics
     */
    public RegistryStats getStats() {
        int totalTypes = registeredTypes.size();
        int enabledTypes = (int) registeredTypes.values().stream().filter(VendorType::isEnabled).count();
        int categories = typesByCategory.size();
        
        return new RegistryStats(totalTypes, enabledTypes, categories);
    }
    
    /**
     * Validate all registered vendor types
     * 
     * @return List of validation errors
     */
    public List<String> validateRegistry() {
        List<String> errors = new ArrayList<>();
        
        for (Map.Entry<String, VendorType> entry : registeredTypes.entrySet()) {
            String typeName = entry.getKey();
            VendorType vendorType = entry.getValue();
            
            try {
                // Validate required fields
                if (vendorType.getDisplayName() == null) {
                    errors.add("Vendor type '" + typeName + "' has null display name");
                }
                
                if (vendorType.getDescription() == null) {
                    errors.add("Vendor type '" + typeName + "' has null description");
                }
                
                if (vendorType.getIcon() == null) {
                    errors.add("Vendor type '" + typeName + "' has null icon");
                }
                
                if (vendorType.getCategory() == null) {
                    errors.add("Vendor type '" + typeName + "' has null category");
                }
                
                if (vendorType.getPriority() < 0) {
                    errors.add("Vendor type '" + typeName + "' has negative priority");
                }
                
                // Try to create a menu (this will validate the implementation)
                // We'll skip this for now as it requires a player instance
                
            } catch (Exception e) {
                errors.add("Vendor type '" + typeName + "' validation failed: " + e.getMessage());
            }
        }
        
        return errors;
    }
    
    /**
     * Initialize category mappings
     */
    private void initializeCategories() {
        for (VendorType.VendorCategory category : VendorType.VendorCategory.values()) {
            typesByCategory.put(category, new ArrayList<>());
        }
    }
    
    /**
     * Clear all registered vendor types (for testing/cleanup)
     */
    public void clear() {
        registeredTypes.clear();
        typesByCategory.clear();
        initializeCategories();
        logger.info("Cleared all vendor type registrations");
    }
    
    /**
     * Registry statistics
     */
    public static class RegistryStats {
        private final int totalTypes;
        private final int enabledTypes;
        private final int categories;
        
        public RegistryStats(int totalTypes, int enabledTypes, int categories) {
            this.totalTypes = totalTypes;
            this.enabledTypes = enabledTypes;
            this.categories = categories;
        }
        
        public int getTotalTypes() { return totalTypes; }
        public int getEnabledTypes() { return enabledTypes; }
        public int getCategories() { return categories; }
        public int getDisabledTypes() { return totalTypes - enabledTypes; }
        
        @Override
        public String toString() {
            return String.format("RegistryStats{totalTypes=%d, enabledTypes=%d, disabledTypes=%d, categories=%d}",
                totalTypes, enabledTypes, getDisabledTypes(), categories);
        }
    }
}
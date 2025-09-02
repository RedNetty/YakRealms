# Gradient Color System for YakRealms

## Overview

The new Gradient Color System enhances T6 (Legendary) and Unique rarity items with beautiful gradient colors, giving them that special premium feeling that sets them apart from lower tiers.

## Features

### T6 (Legendary) Items
- **Color**: Gold-to-amber gradient (`#FFD700` → `#FFBF00`)
- **Feel**: Premium legendary aesthetic
- **Usage**: Automatically applied to all Tier 6 items

### Unique Rarity Items  
- **Color**: Yellow-to-orange gradient (`#FFFF00` → `#FFA500`)
- **Feel**: Special unique aesthetic
- **Usage**: Automatically applied to all Unique rarity items

### Ultra Premium Combination
- **T6 + Unique**: Special mixed gradient for the ultimate items
- **Effect**: Creates the most premium visual experience possible

## Core Classes

### `GradientColors.java`
Main utility class providing gradient color functionality:

```java
// Basic gradients
String t6Text = GradientColors.getT6Gradient("LEGENDARY WEAPON");
String uniqueText = GradientColors.getUniqueGradient("MYTHICAL ARTIFACT");

// Special effects
String shimmer = GradientColors.getT6Shimmer(text, alternatePhase);
String pulse = GradientColors.getT6Pulse(text, intensity);
```

### `ItemDisplayFormatter.java`
High-level formatting for complete item display:

```java
// Format complete item names
String itemName = ItemDisplayFormatter.formatCompleteItemName("Excalibur", 6, 4);

// Format individual components
String tierDisplay = ItemDisplayFormatter.formatTierDisplay(6, false);
String rarityDisplay = ItemDisplayFormatter.formatRarityDisplay(4);

// Apply to ItemStack
ItemDisplayFormatter.formatItemStack(itemStack, tier, rarity);
```

### Enhanced Config Classes
- `TierConfig.java`: Now supports gradient text methods
- `RarityConfig.java`: Now supports gradient text methods

## Integration Guide

### 1. Item Creation
```java
// Old way
meta.setDisplayName(ChatColor.GOLD + itemName);

// New way (automatically handles gradients)
String displayName = ItemDisplayFormatter.formatCompleteItemName(itemName, tier, rarity);
meta.setDisplayName(displayName);
```

### 2. Loot Notifications
```java
// Premium item notifications with gradients
if (ItemDisplayFormatter.isPremiumItem(tier, rarity)) {
    String notification = "§6§l✦ LEGENDARY DROP! ✦\n" + 
        ItemDisplayFormatter.formatTierItemName(itemName, tier);
    player.sendMessage(notification);
}
```

### 3. GUI/Menu Items
```java
// Apply gradient formatting to menu items
ItemStack displayItem = ItemDisplayFormatter.createMenuDisplayItem(originalItem, tier, rarity);
```

### 4. Chat Messages
```java
// Enhanced global announcements
if (tier == 6) {
    String announcement = "§f" + playerName + " §7found " + 
        GradientColors.getT6Gradient(itemName) + "!";
    broadcastMessage(announcement);
}
```

## Configuration Updates

### `tier_config.yml`
```yaml
6:
  color: GOLD # Fallback color - actual display uses gradient
  gradient: true # Special flag indicating gradient usage
  dropRate: 15
  eliteDropRate: 18
  crateDropRate: 1
  materials:
    weapon: NETHERITE
    armor: NETHERITE
```

### `rarity_config.yml`
```yaml
4:
  name: Unique
  color: YELLOW # Fallback color - actual display uses gradient
  gradient: true # Special flag indicating gradient usage
  dropChance: 2
  statMultipliers:
    damage: 2.0
    armor: 2.2
    health: 2.0
    energyRegen: 1.8
    hpRegen: 2.0
```

## Testing

### Test Command: `/gradienttest`
```
/gradienttest basic    - Show basic gradient colors
/gradienttest shimmer  - Show shimmer effects with live demo
/gradienttest pulse    - Show pulse effects with live demo  
/gradienttest items    - Show item formatting examples
/gradienttest all      - Show everything
```

### Permission: `yakrealms.admin` or `yakrealms.gradienttest`

## Visual Examples

### Before vs After

**Before (Standard):**
- T6: `§6LEGENDARY SWORD`  
- Unique: `§eUNIQUE GEM`

**After (Gradient):**
- T6: `[Gold→Amber gradient]LEGENDARY SWORD`
- Unique: `[Yellow→Orange gradient]UNIQUE GEM`  
- T6+Unique: `[Mixed gradient]EXCALIBUR` (Ultra Premium)

## Special Effects

### Shimmer Effect
- Alternates between two gradient variations
- Creates animated shimmer appearance
- Perfect for special events or rare spawns

### Pulse Effect  
- Varies gradient intensity over time
- Creates breathing/pulsing appearance
- Great for important notifications

## Technical Details

### Color Calculation
- Uses RGB interpolation for smooth gradients
- Character-by-character color transitions
- Compatible with all Minecraft color systems

### Performance
- Minimal performance impact
- Colors calculated once and cached when possible
- Graceful fallback to standard colors if needed

### Compatibility
- Works with existing color code systems
- Backwards compatible with legacy ChatColor
- Supports both Bukkit and Adventure API

## Migration Guide

### Existing Code
Most existing code will continue to work unchanged. The gradient system is applied at the display level through the enhanced config classes.

### Optional Enhancements
To take full advantage of gradients, replace direct color code usage with the new formatting methods:

```java
// Replace this:
String text = ChatColor.GOLD + itemName;

// With this:
String text = ItemDisplayFormatter.formatTierItemName(itemName, tier);
```

## Future Enhancements

### Planned Features
- Animated rainbow gradients for special events
- Custom gradient colors for specific items
- Seasonal gradient themes
- Guild-specific gradient colors
- Achievement-based gradient unlocks

### Extensibility
The system is designed to easily support:
- New gradient color combinations
- Custom gradient patterns
- Plugin-specific gradient themes
- Event-based gradient modifications

## Support

### Debug Information
Use `/gradienttest` to verify gradient functionality and preview all effects.

### Common Issues
1. **Gradients not showing**: Ensure client supports RGB colors (1.16+)
2. **Performance concerns**: Gradients are optimized but avoid excessive real-time updates
3. **Color compatibility**: System includes fallback to standard colors

### Best Practices
1. Use gradients sparingly for maximum impact
2. Reserve premium gradients for truly special items
3. Test gradient visibility in different lighting conditions
4. Consider colorblind accessibility when designing gradients

---

*This gradient system transforms ordinary T6 and Unique items into visually stunning premium content that players will truly treasure.*
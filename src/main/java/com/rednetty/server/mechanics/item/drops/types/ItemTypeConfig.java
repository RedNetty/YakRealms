package com.rednetty.server.mechanics.item.drops.types;

public class ItemTypeConfig {
    private int typeId;
    private String name;
    private String materialSuffix;
    private boolean isWeapon;

    public ItemTypeConfig() {
    }

    public ItemTypeConfig(int typeId, String name, String materialSuffix, boolean isWeapon) {
        this.typeId = typeId;
        this.name = name;
        this.materialSuffix = materialSuffix;
        this.isWeapon = isWeapon;
    }

    public int getTypeId() {
        return typeId;
    }

    public void setTypeId(int typeId) {
        this.typeId = typeId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMaterialSuffix() {
        return materialSuffix;
    }

    public void setMaterialSuffix(String materialSuffix) {
        this.materialSuffix = materialSuffix;
    }

    public boolean isWeapon() {
        return isWeapon;
    }

    public void setIsWeapon(boolean isWeapon) {
        this.isWeapon = isWeapon;
    }

}

package dev.shun.litematica.extra.nbt;

import net.minecraft.nbt.NbtCompound;

import java.util.*;

public class NbtScanResult {
    private final Map<String, Integer> intValues = new HashMap<>();
    private final Map<String, NbtCompound> compoundValues = new HashMap<>();

    void putIntValue(String key, int value) {
        intValues.put(key, value);
    }

    void putCompoundValue(String key, NbtCompound compound) {
        compoundValues.put(key, compound);
    }

    public Integer getInt(String key) {
        return intValues.get(key);
    }

    public NbtCompound getCompound(String key) {
        return compoundValues.get(key);
    }
}

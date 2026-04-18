/*
 * This file is part of the Litematica Extra project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2026  shuangshun and contributors
 *
 * Litematica Extra is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Litematica Extra is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Litematica Extra.  If not, see <https://www.gnu.org/licenses/>.
 */

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

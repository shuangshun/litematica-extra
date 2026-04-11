package dev.shun.litematica.extra.nbt;

import net.minecraft.nbt.*;
import static fi.dy.masa.malilib.util.Constants.NBT.*;

import java.io.*;
import java.util.*;

public class NbtStreamScanner {

    private final DataInputStream dis;
    private boolean rootParsed = false;

    public NbtStreamScanner(InputStream inputStream) {
        this.dis = new DataInputStream(inputStream);
    }

    private void ensureRootParsed() throws IOException {
        if (!rootParsed) {
            byte tagId = dis.readByte();

            if (tagId != TAG_COMPOUND) {
                throw new IOException("Invalid NBT format: root is not a Compound tag, found tag ID: " + tagId);
            }

            skipString();
            rootParsed = true;
        }
    }

    public NbtScanResult scan(String... fieldNames) throws IOException {
        if (fieldNames == null || fieldNames.length == 0) {
            return new NbtScanResult();
        }

        ensureRootParsed();

        NbtScanResult result = new NbtScanResult();
        Set<String> targetFields = fieldNames.length == 1 ?
                Collections.singleton(fieldNames[0]) : Set.of(fieldNames);
        int remainingTargets = targetFields.size();

        while (remainingTargets > 0) {
            byte tagId = dis.readByte();

            if (tagId == TAG_END) {
                break;
            }

            String key = dis.readUTF();

            if (targetFields.contains(key)) {
                if (tagId == TAG_INT) {
                    int value = dis.readInt();
                    result.putIntValue(key, value);
                } else if (tagId == TAG_COMPOUND) {
                    NbtCompound compound = readFullCompound();
                    result.putCompoundValue(key, compound);
                } else {
                    skipTag(tagId);
                }

                remainingTargets--;
            } else {
                skipTag(tagId);
            }
        }

        return result;
    }

    private NbtCompound readFullCompound() throws IOException {
        NbtCompound compound = new NbtCompound();

        while (true) {
            byte tagId = dis.readByte();

            if (tagId == TAG_END) {
                break;
            }

            String key = dis.readUTF();
            NbtElement element = readFullTag(tagId);
            compound.put(key, element);
        }

        return compound;
    }

    private NbtElement readFullTag(byte tagId) throws IOException {
        switch (tagId) {
            case TAG_BYTE:
                return NbtByte.of(dis.readByte());
            case TAG_SHORT:
                return NbtShort.of(dis.readShort());
            case TAG_INT:
                return NbtInt.of(dis.readInt());
            case TAG_LONG:
                return NbtLong.of(dis.readLong());
            case TAG_FLOAT:
                return NbtFloat.of(dis.readFloat());
            case TAG_DOUBLE:
                return NbtDouble.of(dis.readDouble());
            case TAG_BYTE_ARRAY: {
                int length = dis.readInt();
                byte[] bytes = new byte[length];
                dis.readFully(bytes);
                return new NbtByteArray(bytes);
            }
            case TAG_STRING:
                return NbtString.of(dis.readUTF());
            case TAG_LIST: {
                byte listType = dis.readByte();
                int length = dis.readInt();
                NbtList list = new NbtList();
                for (int i = 0; i < length; i++) {
                    list.add(readFullTag(listType));
                }
                return list;
            }
            case TAG_COMPOUND:
                return readFullCompound();
            case TAG_INT_ARRAY: {
                int length = dis.readInt();
                int[] ints = new int[length];
                for (int i = 0; i < length; i++) {
                    ints[i] = dis.readInt();
                }
                return new NbtIntArray(ints);
            }
            case TAG_LONG_ARRAY: {
                int length = dis.readInt();
                long[] longs = new long[length];
                for (int i = 0; i < length; i++) {
                    longs[i] = dis.readLong();
                }
                return new NbtLongArray(longs);
            }
            default:
                throw new IOException("Unknown tag type: " + tagId);
        }
    }

    private void skipTag(byte tagId) throws IOException {
        switch (tagId) {
            case TAG_BYTE:
                dis.readByte();
                break;
            case TAG_SHORT:
                dis.readShort();
                break;
            case TAG_INT:
                dis.readInt();
                break;
            case TAG_LONG:
                dis.readLong();
                break;
            case TAG_FLOAT:
                dis.readFloat();
                break;
            case TAG_DOUBLE:
                dis.readDouble();
                break;
            case TAG_BYTE_ARRAY: {
                int length = dis.readInt();
                skipBytes(length);
                break;
            }
            case TAG_STRING:
                skipString();
                break;
            case TAG_LIST: {
                byte listType = dis.readByte();
                int length = dis.readInt();
                for (int i = 0; i < length; i++) {
                    skipTag(listType);
                }
                break;
            }
            case TAG_COMPOUND:
                skipCompound();
                break;
            case TAG_INT_ARRAY: {
                int length = dis.readInt();
                skipBytes(length * 4);
                break;
            }
            case TAG_LONG_ARRAY: {
                int length = dis.readInt();
                skipBytes(length * 8);
                break;
            }
            default:
                throw new IOException("Unknown tag type: " + tagId);
        }
    }

    private void skipCompound() throws IOException {
        while (true) {
            byte tagId = dis.readByte();
            if (tagId == TAG_END) {
                break;
            }
            skipString();
            skipTag(tagId);
        }
    }

    private void skipString() throws IOException {
        int length = dis.readUnsignedShort();
        skipBytes(length);
    }

    private void skipBytes(int n) throws IOException {
        if (n <= 0) return;

        byte[] skipBuffer = new byte[Math.min(n, 8192)];
        int remaining = n;

        while (remaining > 0) {
            int toRead = Math.min(remaining, skipBuffer.length);
            int read = dis.read(skipBuffer, 0, toRead);
            if (read == -1) {
                throw new EOFException("Unable to skip " + n + " bytes, reached end of stream after skipping " + (n - remaining));
            }
            remaining -= read;
        }
    }
}

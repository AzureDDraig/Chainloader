package net.minecraft.nbt;

public class CompoundTag implements Tag {
    public CompoundTag() {
    }

    public boolean contains(String key, int type) {
        return false;
    }

    public boolean contains(String key) {
        return false;
    }

    public CompoundTag getCompound(String key) {
        return null;
    }

    public Tag put(String key, Tag value) {
        return null;
    }

    public void putInt(String key, int value) {
    }

    public int getInt(String key) {
        return 0;
    }

    public java.util.Set<String> getAllKeys() {
        return null;
    }

    public Tag get(String key) {
        return null;
    }

    public void putByte(String key, byte value) {
    }

    public void putShort(String key, short value) {
    }

    public void putLong(String key, long value) {
    }

    public void putFloat(String key, float value) {
    }

    public void putDouble(String key, double value) {
    }

    public void putString(String key, String value) {
    }

    public void putByteArray(String key, byte[] value) {
    }

    public void putIntArray(String key, int[] value) {
    }

    public void putLongArray(String key, long[] value) {
    }

    public void putBoolean(String key, boolean value) {
    }

    public void remove(String key) {
    }

    public void putUUID(String key, java.util.UUID value) {
    }

    public boolean isEmpty() {
        return false;
    }

    @Override
    public CompoundTag copy() {
        return null;
    }
}

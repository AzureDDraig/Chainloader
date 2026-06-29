package net.minecraft.server.packs.resources;

import java.io.IOException;

@FunctionalInterface
public interface IoSupplier<T> {
    T get() throws IOException;
}

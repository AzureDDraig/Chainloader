package net.minecraft.server.packs.metadata.pack;

import net.minecraft.network.chat.Component;
import java.util.Optional;

public record PackMetadataSection(Component description, int packFormat, Optional<?> supportedFormats) {
    public PackMetadataSection(Component description, int packFormat) {
        this(description, packFormat, Optional.empty());
    }
}

package net.minecraft.server.packs;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.KnownPack;
import java.util.Optional;

public record PackLocationInfo(
    String id,
    Component title,
    PackSource source,
    Optional<KnownPack> knownPackInfo
) {
}

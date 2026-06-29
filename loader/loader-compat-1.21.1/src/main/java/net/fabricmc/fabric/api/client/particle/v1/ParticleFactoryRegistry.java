package net.fabricmc.fabric.api.client.particle.v1;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;

public interface ParticleFactoryRegistry {
    @FunctionalInterface
    interface PendingParticleFactory<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet spriteSet);
    }

    static ParticleFactoryRegistry getInstance() {
        return new ParticleFactoryRegistry() {
            @Override
            public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider) {
                System.out.println("[ChainLoader] ParticleProvider registered for type: " + type);
            }
            @Override
            public <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory) {
                System.out.println("[ChainLoader] PendingParticleFactory registered for type: " + type);
            }
        };
    }

    <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider);
    <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory);
}

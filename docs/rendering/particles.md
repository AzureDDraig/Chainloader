# Particle Providers & Particle Registration

In Minecraft 1.21.1, particles are managed by the client-side particle engine. Modders register custom particle factories (`ParticleProvider`) associated with a specific `ParticleType` to handle particle rendering, texture mapping, and physics ticks.

ChainLoader provides compatibility layers for Fabric's client-side particle registries, allowing legacy particle registrations to route to the game's engine.

---

## 1. Fabric Particle Registration API

Legacy Fabric mods register particle factories using `ParticleFactoryRegistry`. ChainLoader provides a fully implemented shim of this class in the compat layer:

```java
package net.fabricmc.fabric.api.client.particle.v1;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;

public interface ParticleFactoryRegistry {
    
    interface PendingParticleFactory<T extends ParticleOptions> {
        ParticleProvider<T> create(SpriteSet spriteSet);
    }

    static ParticleFactoryRegistry getInstance() {
        return new ParticleFactoryRegistry() {
            @Override
            public <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider) {
                System.out.println("[ChainLoader] ParticleProvider registered for type: " + type);
                // Register directly into Minecraft's ParticleEngine
                registerModern(type, provider);
            }

            @Override
            public <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory) {
                System.out.println("[ChainLoader] PendingParticleFactory registered for type: " + type);
                // Register pending sprite factory
                registerModernPending(type, factory);
            }
        };
    }

    <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider);
    <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory);
}
```

---

## 2. ParticleProvider & SpriteSet Mappings

*   **`ParticleProvider`**: Responsible for creating new particle instances based on position, velocity, and optional parameters.
*   **`PendingParticleFactory`**: Used when particles require textures from a custom sprite sheet. The game passes a `SpriteSet` containing the sheet sprites, which the factory uses to initialize and animate the particle's texture coordinates.

To bridge these, ChainLoader maps the legacy interfaces to modern signatures:

```java
// net.minecraft.client.particle.ParticleProvider (Shim)
package net.minecraft.client.particle;

import net.minecraft.core.particles.ParticleOptions;

public interface ParticleProvider<T extends ParticleOptions> {
    // Declares matching modern interface signature for runtime class link validation
}
```

---

## 3. Dynamic Registration Hooks

At runtime, the registration commands are mapped directly to the `net.minecraft.client.particle.ParticleEngine` instance. 

When the game initializes:
1.  **Mod Setup**: Mods invoke `ParticleFactoryRegistry.getInstance().register()`.
2.  **Engine Fetch**: ChainLoader accesses the active `ParticleEngine` instance from `Minecraft.getInstance().particleEngine` (obfuscated as field `ch` or type name).
3.  **Factory Injection**: Using reflection, the registered providers and pending sprite factories are added to the internal maps of the particle engine, allowing the engine to spawn custom particles when requested by the server/client world events.
4.  **Logging**: All particle registrations are traced to the `logs/debug.log` file, allowing easy diagnostics of missing or failed particle factories.

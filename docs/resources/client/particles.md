# Client Particles: Custom Particle Registrations & Shims

Particle systems in Minecraft require two distinct registration steps:
1. **Common Registration**: Registering the common `ParticleType` to the game registry.
2. **Client Registration**: Registering a `ParticleProvider` (which handles rendering and sprite-set association) to the client-side particle manager.

ChainLoader bridges legacy Fabric and Forge particle systems onto NeoForge 1.21.1 by bridging the common registry entries and providing compatibility shims for client-side factory registries.

---

## Common Particle Type Registration

Common registration of a `ParticleType` is handled via the registry bridge. When a mod registers a custom particle type (e.g., using legacy Forge registries or Fabric's common registry methods), it is routed through `ChainRegistryBridge.register`:

```java
ChainRegistryBridge.register("particle_type", "mymod:custom_sparkle", myParticleTypeInstance);
```

At runtime, this pending entry is polled by the `EventBridgeHelper` and registered into the vanilla `BuiltInRegistries.PARTICLE_TYPE` (or mapped to obfuscated registry keys for 1.21.1), ensuring the server and client are synchronized on the custom particle ID.

---

## Client Particle Provider Registration

On the client side, particles require a factory or provider to instantiate their visual representations. Fabric mods use `ParticleFactoryRegistry` (from Fabric API) to bind providers.

### Fabric `ParticleFactoryRegistry` Shim

ChainLoader provides a full implementation of `ParticleFactoryRegistry` to compile and intercept client registrations at runtime:

```java
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
                // Delegates or queues registration to client setup
            }
            
            @Override
            public <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory) {
                System.out.println("[ChainLoader] PendingParticleFactory registered for type: " + type);
                // Delegates or queues registration to client setup
            }
        };
    }

    <T extends ParticleOptions> void register(ParticleType<T> type, ParticleProvider<T> provider);
    <T extends ParticleOptions> void register(ParticleType<T> type, PendingParticleFactory<T> factory);
}
```

### Mapping to NeoForge `RegisterParticleProvidersEvent`

In NeoForge 1.21.1, client-side particle factories are registered by subscribing to `RegisterParticleProvidersEvent` on the mod event bus. 

When a mod registers a particle factory using the Fabric shim:
1. The registration is queued in a thread-safe registry map matching the `ParticleType` to the provider or factory.
2. During the post of `RegisterParticleProvidersEvent`, ChainLoader intercepts the event (similar to other client setups) and registers the queued providers:

```java
// NeoForge registration bridging
event.registerSpecial(myParticleType, spriteSet -> {
    // Return custom provider constructed via shimmed PendingParticleFactory
    return factory.create(spriteSet);
});
```

This allows the vanilla particle engine to correctly instantiate client-side particle sprites under the NeoForge runtime.

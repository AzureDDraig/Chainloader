# Block Entity Renderers

In Minecraft 1.21.1, Block Entity Renderers (BERs) must be registered through the client setup event loop. Legacy static registries used by older Fabric and Forge mods will fail. 

This document describes how ChainLoader redirects Block Entity Renderer registrations and bridges their rendering contexts.

---

## 1. Block Entity Renderer Registration

Legacy mods register custom block renderers (e.g. for chests, signs, or custom tech blocks) by calling `BlockEntityRenderers.register` statically during client setup:
```java
// Legacy Mod Block Entity Renderer Registration
BlockEntityRenderers.register(MyBlockEntityType, MyBlockEntityRenderer::new);
```
In NeoForge, this must occur during the `EntityRenderersEvent.RegisterRenderers` event.

### 1.1 Access Widening (`transformBlockEntityRenderers`)
To prevent package-private and protected method linkage issues:
1. **Public Widening**: The classloader transforms `net.minecraft.client.renderer.blockentity.BlockEntityRenderers` (`dqj`).
2. **Access Adjustments**: It changes the class accessibility and the registration method `a` (or `register`) to `public`.

### 1.2 Event Registration Bridge
When `BlockEntityRenderers.register(...)` is called:
1. ChainLoader intercepts the registration parameters and caches the `BlockEntityType` and `BlockEntityRendererProvider` mapping.
2. During the client lifecycle event post, ChainLoader fires the NeoForge `EntityRenderersEvent.RegisterRenderers` event.
3. It iterates over the captured map and registers the block entity renderers using reflection:
   ```java
   Class<?> renderersClass = Class.forName("net.minecraft.client.renderer.blockentity.BlockEntityRenderers");
   Method registerMethod = renderersClass.getMethod("register", BlockEntityType.class, BlockEntityRendererProvider.class);
   registerMethod.setAccessible(true);
   registerMethod.invoke(null, entry.getKey(), entry.getValue());
   ```

---

## 2. Rendering Context Redirections

During rendering cycles, the block entity renderer's `render` method receives rendering parameters:
`render(BlockEntity blockEntity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay)`

To maintain compatibility with legacy mods:
1. **PoseStack Bridging**: Re-targets legacy `PoseStack` classes to the modern `com.mojang.blaze3d.vertex.PoseStack` path.
2. **Buffer Sources**: Maps custom render buffers (`MultiBufferSource`) to handle modern color and overlay coordinates.
3. **Data Synchronization**: Ensures block entity data packet updates (like updating block states or NBT tags) synchronize correctly between the server and the client using modern packet structures.

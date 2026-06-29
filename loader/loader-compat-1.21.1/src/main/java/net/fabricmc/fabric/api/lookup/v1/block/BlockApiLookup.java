package net.fabricmc.fabric.api.lookup.v1.block;

public interface BlockApiLookup<A, C> {
    static <A, C> BlockApiLookup<A, C> get(net.minecraft.resources.ResourceLocation identifier, Class<A> apiClass, Class<C> contextClass) {
        return new BlockApiLookup<A, C>() {
            @Override
            public void registerForBlocks(BlockApiProvider<A, C> provider, Object... blocks) {}
            @Override
            public void registerForBlockEntities(BlockApiProvider<A, C> provider, Object... blockEntityTypes) {}
            @Override
            public void registerSelf(Object... blockEntityTypes) {}
            @Override
            public void registerFallback(BlockApiProvider<A, C> fallbackProvider) {}
        };
    }

    void registerForBlocks(BlockApiProvider<A, C> provider, Object... blocks);
    void registerForBlockEntities(BlockApiProvider<A, C> provider, Object... blockEntityTypes);
    void registerSelf(Object... blockEntityTypes);
    void registerFallback(BlockApiProvider<A, C> fallbackProvider);

    interface BlockApiProvider<A, C> {
    }
}

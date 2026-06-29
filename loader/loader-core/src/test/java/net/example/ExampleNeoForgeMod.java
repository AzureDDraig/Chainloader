package net.example;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.LayerDefinition;

public class ExampleNeoForgeMod {

    public ExampleNeoForgeMod(IEventBus modEventBus) {
        System.out.println("[ExampleNeoForgeMod] Constructor invoked! Registering mod event listeners...");
        modEventBus.register(this);
    }

    @SubscribeEvent
    public void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        System.out.println("[ExampleNeoForgeMod] Received EntityRenderersEvent.RegisterRenderers!");
        
        // Register pig renderer (using null provider for mock test)
        try {
            event.registerEntityRenderer((EntityType) EntityType.PIG, provider -> null);
            System.out.println("[ExampleNeoForgeMod] Registered dummy entity renderer for PIG.");
        } catch (Throwable t) {
            System.err.println("[ExampleNeoForgeMod] Failed to register entity renderer: " + t.getMessage());
        }

        // Register chest block entity renderer (using null provider for mock test)
        try {
            event.registerBlockEntityRenderer((BlockEntityType) BlockEntityType.CHEST, provider -> null);
            System.out.println("[ExampleNeoForgeMod] Registered dummy block entity renderer for CHEST.");
        } catch (Throwable t) {
            System.err.println("[ExampleNeoForgeMod] Failed to register block entity renderer: " + t.getMessage());
        }
    }

    @SubscribeEvent
    public void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        System.out.println("[ExampleNeoForgeMod] Received EntityRenderersEvent.RegisterLayerDefinitions!");
        
        ModelLayerLocation layerLoc = new ModelLayerLocation(new ResourceLocation("example", "test_layer"), "main");
        try {
            event.registerLayerDefinition(layerLoc, () -> {
                // Return null since we are just mocking and testing registration capture
                return null;
            });
            System.out.println("[ExampleNeoForgeMod] Registered layer definition for: " + layerLoc);
        } catch (Throwable t) {
            System.err.println("[ExampleNeoForgeMod] Failed to register layer definition: " + t.getMessage());
        }
    }

    @SubscribeEvent
    public void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        System.out.println("[ExampleNeoForgeMod] Received ModelEvent.RegisterAdditional!");
        ResourceLocation loc = new ResourceLocation("example", "test_model");
        event.register(loc);
        System.out.println("[ExampleNeoForgeMod] Registered additional model: " + loc);
    }

    @SubscribeEvent
    public void onBakingCompleted(ModelEvent.BakingCompleted event) {
        System.out.println("[ExampleNeoForgeMod] Received ModelEvent.BakingCompleted!");
    }

    @SubscribeEvent
    public void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        System.out.println("[ExampleNeoForgeMod] Received ModelEvent.ModifyBakingResult!");
    }
}

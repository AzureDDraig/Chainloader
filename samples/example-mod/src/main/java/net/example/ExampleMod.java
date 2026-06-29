package net.example;

import java.util.function.Supplier;

// ChainLoader API Imports
import net.chainloader.api.registry.ChainRegistry;
import net.chainloader.api.registry.RegistryEntry;
import net.chainloader.api.event.PlayerBlockBreakCallback;

// Minecraft Mappings Imports (Mixing Yarn block/entity mappings with Mojang registry keys)
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

/**
 * Example mod demonstrating the use of the ChainLoader MDK.
 * Registered as the main entry point in chainmod.json.
 */
public class ExampleMod {
    public static final String MOD_ID = "example-mod";

    // Obtain the ResourceKey for the Block registry using the Mojang mappings format
    @SuppressWarnings("unchecked")
    private static final ResourceKey<Registry<Block>> BLOCK_REGISTRY_KEY = 
        (ResourceKey<Registry<Block>>) (Object) ResourceKey.createRegistryKey(new ResourceLocation("minecraft", "block"));

    // Create a unified ChainRegistry for registering Blocks
    public static final ChainRegistry<Block> BLOCKS = new ChainRegistry<>(BLOCK_REGISTRY_KEY, MOD_ID);

    // Register a custom block with ID: example-mod:example_block
    public static final RegistryEntry<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(
            Block.Settings.create()
                    .strength(1.5F)
                    .requiresTool()
    ));

    /**
     * Entry point method called during ChainLoader initialization.
     */
    public void onInitialize() {
        System.out.println("==================================================");
        System.out.println("  Initializing " + MOD_ID + " via ChainLoader MDK!");
        System.out.println("  Registered Custom Block: " + EXAMPLE_BLOCK.getId());
        System.out.println("==================================================");

        // Register a listener for the Player Block Break Event
        PlayerBlockBreakCallback.EVENT.register(ExampleMod::onBlockBreak);
    }

    /**
     * Listener callback invoked before a player breaks a block in the world.
     *
     * @param player      The player breaking the block.
     * @param world       The world instance.
     * @param pos         The coordinates of the block.
     * @param state       The BlockState of the block being broken.
     * @param blockEntity The BlockEntity associated with the block, if any.
     * @return ActionResult PASS to allow execution, FAIL or SUCCESS to cancel/consume it.
     */
    private static ActionResult onBlockBreak(PlayerEntity player, World world, BlockPos pos, BlockState state, BlockEntity blockEntity) {
        // Output block break details to console
        String playerName = player.getName().getString();
        String blockName = state.getBlock().toString();
        String blockPosStr = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();

        System.out.println("[ExampleMod] Player '" + playerName + "' broke block: " + blockName + " at [" + blockPosStr + "]");

        // Return PASS to permit the block break and let other listeners process the event
        return ActionResult.PASS;
    }
}

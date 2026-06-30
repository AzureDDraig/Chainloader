package scratch;

import net.chainloader.loader.transformer.BytecodeTransformer;

public class test_remapper {
    public static void main(String[] args) {
        try {
            System.out.println("Initializing BytecodeTransformer...");
            BytecodeTransformer transformer = BytecodeTransformer.getInstance();
            System.out.println("Loading client mappings...");
            transformer.loadMojangMappings("lib/client_mappings.txt");
            
            // Try remapping translatable with 2 arguments
            String owner = "net/minecraft/network/chat/Component";
            String name = "translatable";
            String descriptor = "(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;";
            
            System.out.println("Mapping Component.translatable with args...");
            String mapped = transformer.mapMethodName(owner, name, descriptor);
            System.out.println("Mapped to: " + mapped);
            
            // Try remapping translatable with 1 argument
            String descriptor1 = "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;";
            System.out.println("Mapping Component.translatable with 1 arg...");
            String mapped1 = transformer.mapMethodName(owner, name, descriptor1);
            System.out.println("Mapped to: " + mapped1);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

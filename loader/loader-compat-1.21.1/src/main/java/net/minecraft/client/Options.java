package net.minecraft.client;

public class Options {
    public OptionInstance<Integer> renderDistance = new OptionInstance<>(12);
    public OptionInstance<Integer> simulationDistance = new OptionInstance<>(8);
    public OptionInstance<Integer> framerateLimit = new OptionInstance<>(60);
    public OptionInstance<Boolean> bobView = new OptionInstance<>(true);
    public KeyMapping[] keyMappings;

    public void load() {}
    public void save() {}
}

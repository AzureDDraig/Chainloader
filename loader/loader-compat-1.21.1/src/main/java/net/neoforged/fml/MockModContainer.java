package net.neoforged.fml;

public class MockModContainer extends ModContainer {
    private final String modId;

    public MockModContainer(String modId) {
        this.modId = modId;
    }

    @Override
    public String getModId() {
        return modId;
    }
}

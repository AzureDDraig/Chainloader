package net.chainloader.loader.compat;

public abstract class Chainlink1_16_Base extends Chainlink1_17_Base {

    @Override
    public String getSupportedVersionRange() {
        return "[1.16, 1.16.5]";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Chainlink 1.16 Base] Waking up compat module for loader: " + getSupportedLoaderType());
        super.onWakeUp(classLoader);
    }
}

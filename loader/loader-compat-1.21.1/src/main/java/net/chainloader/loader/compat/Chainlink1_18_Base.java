package net.chainloader.loader.compat;

public abstract class Chainlink1_18_Base extends Chainlink1_19_Base {

    @Override
    public String getSupportedVersionRange() {
        return "[1.18, 1.18.2]";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Chainlink 1.18 Base] Waking up compat module for loader: " + getSupportedLoaderType());
        super.onWakeUp(classLoader);
    }
}

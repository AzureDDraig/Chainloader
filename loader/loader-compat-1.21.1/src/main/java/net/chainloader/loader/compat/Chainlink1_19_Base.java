package net.chainloader.loader.compat;

public abstract class Chainlink1_19_Base extends Chainlink1_19_3_Base {

    @Override
    public String getSupportedVersionRange() {
        return "[1.19, 1.19.2]";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Chainlink 1.19 Base] Waking up compat module for loader: " + getSupportedLoaderType());
        super.onWakeUp(classLoader);
    }
}

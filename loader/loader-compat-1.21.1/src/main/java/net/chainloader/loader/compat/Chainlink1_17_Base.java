package net.chainloader.loader.compat;

import java.util.Collection;
import java.util.List;

public abstract class Chainlink1_17_Base extends Chainlink1_18_Base {

    @Override
    public String getSupportedVersionRange() {
        return "[1.17, 1.17.1]";
    }

    @Override
    public void onWakeUp(ClassLoader classLoader) {
        System.out.println("[Chainlink 1.17 Base] Waking up compat module for loader: " + getSupportedLoaderType());
        super.onWakeUp(classLoader);
    }
}

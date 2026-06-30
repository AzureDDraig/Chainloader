package net.chainloader.loader.core.world;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;

public class ChainWorldgenBridge {
    public static class FeatureRegistration {
        public final String registryKey;
        public final String featureId;
        public final Object featureInstance;

        public FeatureRegistration(String registryKey, String featureId, Object featureInstance) {
            this.registryKey = registryKey;
            this.featureId = featureId;
            this.featureInstance = featureInstance;
        }
    }

    private static final Queue<FeatureRegistration> pendingFeatures = new ConcurrentLinkedQueue<>();

    public static void registerFeature(String registryKey, String featureId, Object featureInstance) {
        pendingFeatures.add(new FeatureRegistration(registryKey, featureId, featureInstance));
    }

    public static Queue<FeatureRegistration> getPendingFeatures() {
        return pendingFeatures;
    }
}

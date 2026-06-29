package net.chainloader.loader.compat.bridge;

public class RunNetworkTearDownTest {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println("  Running NetworkTearDownTest Main Runner");
        System.out.println("==================================================");
        NetworkTearDownTest test = new NetworkTearDownTest();
        try {
            test.setUp();
            test.testClientConnectionTeardown();
            System.out.println("  -> testClientConnectionTeardown: PASSED");

            test.setUp();
            test.testServerConnectionTeardown();
            System.out.println("  -> testServerConnectionTeardown: PASSED");

            System.out.println("==================================================");
            System.out.println("  ALL NETWORK TEARDOWN TESTS PASSED!");
            System.out.println("==================================================");
        } catch (Throwable t) {
            System.err.println("  -> TEST FAILED!");
            t.printStackTrace();
            System.exit(1);
        }
    }
}


import java.lang.reflect.Method;

public class InspectContext {
    public static void main(String[] args) {
        try {
            Class<?> clazz = Class.forName("net.minecraftforge.network.NetworkEvent$Context");
            System.out.println("Class loaded: " + clazz.getName());
            for (Method m : clazz.getDeclaredMethods()) {
                System.out.println("  Method: " + m.getName() + " returning " + m.getReturnType().getName() + " (" + m.toString() + ")");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

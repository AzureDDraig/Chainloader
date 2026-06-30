
package net.chainloader.loader.core;
import java.lang.reflect.*;
public class InspectCTB {
    public static void main(String[] args) {
        try {
            Class<?> clazz = Class.forName("ctb");
            System.out.println("Fields in ctb:");
            for (Field f : clazz.getDeclaredFields()) {
                System.out.println(Modifier.toString(f.getModifiers()) + " " + f.getType().getName() + " " + f.getName());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

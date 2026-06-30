package net.chainloader.loader.core;

import java.lang.reflect.*;

public class InspectPackets {
    public static void main(String[] args) throws Exception {
        printClass("zn");
        printClass("aab");
    }

    private static void printClass(String name) throws Exception {
        Class<?> clazz = Class.forName(name);
        System.out.println("Class: " + name);
        for (Constructor<?> c : clazz.getDeclaredConstructors()) {
            System.out.println("  Constructor: " + c);
        }
        for (Method m : clazz.getDeclaredMethods()) {
            System.out.println("  Method: " + m);
        }
        for (Field f : clazz.getDeclaredFields()) {
            System.out.println("  Field: " + f);
        }
    }
}

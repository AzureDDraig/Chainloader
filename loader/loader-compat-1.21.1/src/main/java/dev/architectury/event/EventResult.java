package dev.architectury.event;

public class EventResult {
    private static final EventResult PASS = new EventResult();

    public static EventResult interruptTrue() {
        return PASS;
    }

    public static EventResult interruptFalse() {
        return PASS;
    }

    public static EventResult interruptNull() {
        return PASS;
    }

    public static EventResult pass() {
        return PASS;
    }
}

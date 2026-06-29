package dev.emi.emi.api.stack;

/**
 * Mockup of EMI's EmiStack class.
 */
public class EmiStack implements EmiIngredient {
    private final Object itemStack;

    private EmiStack(Object itemStack) {
        this.itemStack = itemStack;
    }

    public static EmiStack of(Object itemStack) {
        return new EmiStack(itemStack);
    }

    public Object getItemStack() {
        return itemStack;
    }
}

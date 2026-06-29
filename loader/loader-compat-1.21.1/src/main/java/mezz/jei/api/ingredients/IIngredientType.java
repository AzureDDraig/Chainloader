package mezz.jei.api.ingredients;

/**
 * Mockup of JEI's IIngredientType interface.
 */
public interface IIngredientType<T> {
    Class<? extends T> getIngredientClass();
}

package foo

public class RecipeService {
    
    private static final Set<String> availableIngredients = [
        "flour", "sugar", "butter", "milk", "vanilla", "chocolate", "salt", 
        "baking powder", "olive oil", "garlic", "onion", "tomatoes", "cheese", 
        "chicken", "beef", "rice", "basil", "oregano"
    ].toSet()

    public String isIngredientAvailable(String ingredientName) {

        boolean isAvailable = availableIngredients.contains(ingredientName)

        String response = "Ingredient '${ingredientName}' is ${isAvailable ? 'available' : 'not available'} in inventory."

        System.out.println("!!!!!!!!!!!!!!! " + response)

        return response
    }
}   
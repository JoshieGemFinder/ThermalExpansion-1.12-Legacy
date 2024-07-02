package cofh.thermalexpansion.util.managers.machine;

import cofh.core.inventory.ComparableItemStack;
import cofh.core.inventory.ComparableItemStackValidated;
import cofh.core.inventory.OreValidator;
import cofh.core.util.helpers.ItemHelper;
import cofh.thermalexpansion.util.parsers.ConstantParser;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.item.ItemFood;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.FurnaceRecipes;
import net.minecraftforge.oredict.OreDictionary;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.Nullable;

public class FurnaceManager {

	private static Map<ComparableItemStackValidated, FurnaceRecipe> recipeMap = new Object2ObjectOpenHashMap<>();
	private static Map<ComparableItemStackValidated, FurnaceRecipe> recipeMapPyrolysis = new Object2ObjectOpenHashMap<>();
	private static Map<ComparableItemStack, Boolean> oreOverrideMap = new Object2BooleanOpenHashMap<ComparableItemStack>(); // Went with unvalidated because less overhead and also I expect someone would want to add a whole OreDict entry/entries that aren't ore or dust.
	private static Map<ComparableItemStackValidated, Boolean> foodOverrideMap = new Object2BooleanOpenHashMap<ComparableItemStackValidated>(); // Seperate from foodSet because foodSet gets reset in reload(). Validated just to reduce overhead with the isFood check (ew, inconsistency with all the other overrides).
	private static Set<ComparableItemStackValidated> foodSet = new ObjectOpenHashSet<>();
	
	private static OreValidator oreValidator = new OreValidator();

	static {
		oreValidator.addPrefix(ComparableItemStack.ORE);
		oreValidator.addPrefix(ComparableItemStack.DUST);
		oreValidator.addPrefix("log");
	}

	public static final int DEFAULT_ENERGY = 2000;

	public static FurnaceRecipe getRecipe(ItemStack input, boolean pyrolysis) {

		if (input.isEmpty()) {
			return null;
		}
		ComparableItemStackValidated query = convertInput(input);
		FurnaceRecipe recipe;

		if (pyrolysis) {
			recipe = recipeMapPyrolysis.get(query);
			if (recipe == null) {
				query.metadata = OreDictionary.WILDCARD_VALUE;
				recipe = recipeMapPyrolysis.get(query);
			}
			return recipe;
		}
		recipe = recipeMap.get(query);
		if (recipe == null) {
			query.metadata = OreDictionary.WILDCARD_VALUE;
			recipe = recipeMap.get(query);
		}
		return recipe;
	}

	public static boolean recipeExists(ItemStack input, boolean pyrolysis) {

		return getRecipe(input, pyrolysis) != null;
	}

	public static FurnaceRecipe[] getRecipeList(boolean pyrolysis) {

		if (pyrolysis) {
			return recipeMapPyrolysis.values().toArray(new FurnaceRecipe[0]);
		}
		return recipeMap.values().toArray(new FurnaceRecipe[0]);
	}

	public static void initialize() {

		/* GENERAL SCAN */
		Map<ItemStack, ItemStack> smeltingList = FurnaceRecipes.instance().getSmeltingList();
		ItemStack output;
		int energy;

		for (ItemStack key : smeltingList.keySet()) {
			if (key.isEmpty() || recipeExists(key, false)) {
				continue;
			}
			output = smeltingList.get(key);
			if (ConstantParser.hasOre(ItemHelper.getOreName(output))) {
				output = ItemHelper.cloneStack(ConstantParser.getOre(ItemHelper.getOreName(output)), output.getCount());
			}
			energy = DEFAULT_ENERGY;
			/* FOOD */
			if (output.getItem() instanceof ItemFood) {
				foodSet.add(convertInput(key));
				energy /= 2;
			}
			/* DUST */
			if (ItemHelper.isDust(key) && ItemHelper.isIngot(output)) {
				addRecipe(energy * 3 / 4, key, output);
				/* STANDARD */
			} else {
				if (ItemHelper.getItemDamage(key) == OreDictionary.WILDCARD_VALUE) {
					ItemStack testKey = ItemHelper.cloneStack(key);
					testKey.setItemDamage(0);
					if (ItemHelper.hasOreName(testKey) && oreValidator.validate(ItemHelper.getOreName(testKey))) {
						addRecipe(energy, testKey, output);
						continue;
					}
				}
				addRecipe(energy, key, output);
			}
		}
	}

	public static void refresh() {

		Map<ComparableItemStackValidated, FurnaceRecipe> tempMap = new Object2ObjectOpenHashMap<>(recipeMap.size());
		Map<ComparableItemStackValidated, FurnaceRecipe> tempMapPyrolysis = new Object2ObjectOpenHashMap<>(recipeMapPyrolysis.size());
		Set<ComparableItemStackValidated> tempFood = new ObjectOpenHashSet<>();
		FurnaceRecipe tempRecipe;

		for (Entry<ComparableItemStackValidated, FurnaceRecipe> entry : recipeMap.entrySet()) {
			tempRecipe = entry.getValue();
			tempMap.put(convertInput(tempRecipe.input), tempRecipe);
		}
		for (Entry<ComparableItemStackValidated, FurnaceRecipe> entry : recipeMapPyrolysis.entrySet()) {
			tempRecipe = entry.getValue();
			tempMapPyrolysis.put(convertInput(tempRecipe.input), tempRecipe);
		}
		for (ComparableItemStackValidated entry : foodSet) {
			ComparableItemStackValidated food = convertInput(new ItemStack(entry.item, entry.stackSize, entry.metadata));
			tempFood.add(food);
		}
		recipeMap.clear();
		recipeMap = tempMap;

		recipeMapPyrolysis.clear();
		recipeMapPyrolysis = tempMapPyrolysis;

		foodSet.clear();
		foodSet = tempFood;
	}

	/* ADD RECIPES */
	public static FurnaceRecipe addRecipe(int energy, ItemStack input, ItemStack output) {

		if (input.isEmpty() || output.isEmpty() || energy <= 0 || recipeExists(input, false)) {
			return null;
		}
		FurnaceRecipe recipe = new FurnaceRecipe(input, output, energy);
		recipeMap.put(convertInput(input), recipe);
		return recipe;
	}

	public static FurnaceRecipe addRecipePyrolysis(int energy, ItemStack input, ItemStack output, int creosote) {

		if (input.isEmpty() || output.isEmpty() || energy <= 0 || recipeExists(input, true)) {
			return null;
		}
		FurnaceRecipe recipe = new FurnaceRecipe(input, output, energy, creosote);
		recipeMapPyrolysis.put(convertInput(input), recipe);
		return recipe;
	}

	/* REMOVE RECIPES */
	public static FurnaceRecipe removeRecipe(ItemStack input) {

		return recipeMap.remove(convertInput(input));
	}

	public static FurnaceRecipe removeRecipePyrolysis(ItemStack input) {

		return recipeMapPyrolysis.remove(convertInput(input));
	}

	/* HELPERS */
	public static ComparableItemStackValidated convertInput(ItemStack stack) {

		return new ComparableItemStackValidated(stack, oreValidator);
	}
	
	public static boolean isFood(ItemStack input) {

		if (input.isEmpty()) {
			return false;
		}
		ComparableItemStackValidated query = convertInput(input);

		if(foodOverrideMap.containsKey(query)) {
			return foodOverrideMap.get(query);
		}
		
		// Store because the entire wildcard could be blacklisted.
		boolean defaultReturn = foodSet.contains(query);
		query.metadata = OreDictionary.WILDCARD_VALUE;
		
		if(foodOverrideMap.containsKey(query)) {
			return foodOverrideMap.get(query);
		}
		
		return defaultReturn || foodSet.contains(query);
	}

	public static boolean isOre(ItemStack stack) {
		ComparableItemStack query = new ComparableItemStack(stack);
		if(oreOverrideMap.containsKey(query)) return oreOverrideMap.get(query);
		query.metadata = OreDictionary.WILDCARD_VALUE;
		if(oreOverrideMap.containsKey(query)) return oreOverrideMap.get(query);
		
		return ItemHelper.isOre(stack) || ItemHelper.isCluster(stack);
	}
	
	/* ORE OVERRIDES */

	@Nullable
	/**
	 * Adds an override for an {@code ItemStack} to be in the set of foods accepted by the Trivection Chamber
	 * @param stack	The {@code ItemStack} to add the override for. Can use {@link OreDictionary.WILDCARD_VALUE wildcard metadata}.
	 * @param value The override value
	 * @return The previous value associated with {@code stack}, or {@code null} if there was no mapping for {@code stack}.
	 * @see Map#put(Object, Object)
	 */
	public static Boolean addFoodOverride(ItemStack stack, boolean value) {
		ComparableItemStackValidated query = convertInput(stack);
		return foodOverrideMap.put(query, value);
	}

	@Nullable
	/**
	 * Removes the override for an {@code ItemStack} in the set of foods accepted by the Trivection Chamber
	 * @param stack	The {@code ItemStack} to remove the override for. Can use {@link OreDictionary.WILDCARD_VALUE wildcard metadata}.
	 * @return Whether the remove operation was successful.
	 * @see Map#remove(Object, Object)
	 */
	public static Boolean removeFoodOverride(ItemStack stack) {
		ComparableItemStackValidated query = convertInput(stack);
		return foodOverrideMap.remove(query);
	}

	/**
	 * Checks if there is any override for an {@code ItemStack} for the Trivection Chamber
	 * @param stack	The {@code ItemStack} to remove the override for. Can use {@link OreDictionary.WILDCARD_VALUE wildcard metadata}.
	 * @return {@code true} if there is an override for {@code stack}.
	 * @see Map#containsKey(Object, Object)
	 */
	public static boolean hasFoodOverride(ItemStack stack) {
		ComparableItemStackValidated query = convertInput(stack);
		return foodOverrideMap.containsKey(query);
	}

	@Nullable
	/**
	 * Adds an override for an {@code ItemStack} to be in the set of ores accepted by the Flux Anodizer
	 * @param stack	The {@code ItemStack} to add the override for. Can use {@link OreDictionary.WILDCARD_VALUE wildcard metadata}.
	 * @param value The override value
	 * @return The previous value associated with {@code stack}, or {@code null} if there was no mapping for {@code stack}.
	 * @see Map#put(Object, Object)
	 */
	public static Boolean addOreOverride(ItemStack stack, boolean value) {
		ComparableItemStack query = new ComparableItemStack(stack);
		return oreOverrideMap.put(query, value);
	}

	@Nullable
	/**
	 * Removes the override for an {@code ItemStack} in the set of ores accepted by the Flux Anodizer
	 * @param stack	The {@code ItemStack} to remove the override for. Can use {@link OreDictionary.WILDCARD_VALUE wildcard metadata}.
	 * @return Whether the remove operation was successful.
	 * @see Map#remove(Object, Object)
	 */
	public static Boolean removeOreOverride(ItemStack stack) {
		ComparableItemStack query = new ComparableItemStack(stack);
		return oreOverrideMap.remove(query);
	}

	/**
	 * Checks if there is any override for an {@code ItemStack} for the Flux Anodizer
	 * @param stack	The {@code ItemStack} to remove the override for. Can use {@link OreDictionary.WILDCARD_VALUE wildcard metadata}.
	 * @return {@code true} if there is an override for {@code stack}.
	 * @see Map#containsKey(Object, Object)
	 */
	public static boolean hasOreOverride(ItemStack stack) {
		ComparableItemStack query = new ComparableItemStack(stack);
		return oreOverrideMap.containsKey(query);
	}

	/* RECIPE CLASS */
	public static class FurnaceRecipe {

		final ItemStack input;
		final ItemStack output;
		final int energy;
		final int creosote;

		FurnaceRecipe(ItemStack input, ItemStack output, int energy) {

			this(input, output, energy, 0);
		}

		FurnaceRecipe(ItemStack input, ItemStack output, int energy, int creosote) {

			this.input = input;
			this.output = output;
			this.energy = energy;
			this.creosote = creosote;
		}

		public ItemStack getInput() {

			return input;
		}

		public ItemStack getOutput() {

			return output;
		}

		public int getEnergy() {

			return energy;
		}

		public int getCreosote() {

			return creosote;
		}
	}

}

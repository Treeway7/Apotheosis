package shadows.apotheosis.ench.table;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import com.google.common.collect.Lists;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedEntry.IntrusiveBase;
import net.minecraft.util.random.WeightedRandom;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import shadows.apotheosis.ench.asm.EnchHooks;
import shadows.apotheosis.ench.table.ApothEnchantContainer.Arcana;

public class RealEnchantmentHelper {

	/**
	 * Determines the level of the given enchantment table slot.
	 * An item with 0 enchantability cannot be enchanted, so this method returns zero.
	 * Slot 2 (the highest level slot) always receives a level equal to power * 2.
	 * Slot 1 recieves between 60% and 80% of Slot 2.
	 * Slot 0 receives between 20% and 40% of Slot 2.
	 * @param rand Pre-seeded random.
	 * @param num Enchantment Slot Number [0-2]
	 * @param eterna Enchantment Power (Eterna Level)
	 * @param stack Itemstack to be enchanted.
	 * @return The level that the table will use for this specific slot.
	 */
	public static int calcSlotLevel(Random rand, int num, float eterna, ItemStack stack) {
		int ench = stack.getItemEnchantability();
		if (ench <= 0) return 0;
		int level = (int) (eterna * 2);
		if (num == 2) return level;
		float lowBound = 0.6F - 0.4F * (1 - num);
		float highBound = 0.8F - 0.4F * (1 - num);
		return Math.max(1, (int) (level * Mth.nextFloat(rand, lowBound, highBound)));
	}

	/**
	 * Creates a list of enchantments for a specific slot given various variables.
	 * @param rand Pre-seeded random.
	 * @param stack Itemstack to be enchanted.
	 * @param level Enchanting Slot XP Level
	 * @param quanta Quanta Level
	 * @param arcana Arcana Level
	 * @param treasure If treasure enchantments can show up.
	 * @return A list of enchantments based on the seed, item, and eterna/quanta/arcana levels.
	 */
	public static List<EnchantmentInstance> buildEnchantmentList(Random rand, ItemStack stack, int level, float quanta, float arcana, float rectification, boolean treasure) {
		List<EnchantmentInstance> chosenEnchants = Lists.newArrayList();
		int enchantability = stack.getItemEnchantability();
		int srcLevel = level;
		if (enchantability > 0) {
			float quantaFactor = 1 + Mth.nextFloat(rand, -1F + rectification / 100F, 1F) * quanta / 100F; //The randomly selected value to multiply the level by, within range [-Q+Q*QR, +Q]
			level = Mth.clamp(Math.round(level * quantaFactor), 1, (int) (EnchantingStatManager.getAbsoluteMaxEterna() * 4));
			Arcana arcanaVals = Arcana.getForThreshold(arcana);
			List<EnchantmentInstance> allEnchants = getEnchantmentDatas(level, stack, treasure);
			Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
			allEnchants.removeIf(e -> enchants.containsKey(e.enchantment)); //Remove duplicates.
			List<ArcanaEnchantmentData> possibleEnchants = allEnchants.stream().map(d -> new ArcanaEnchantmentData(arcanaVals, d)).collect(Collectors.toList());
			if (!possibleEnchants.isEmpty()) {
				chosenEnchants.add(WeightedRandom.getRandomItem(rand, possibleEnchants).get().data);
				removeIncompatible(possibleEnchants, Util.lastOf(chosenEnchants));

				if (arcana >= 25F && !possibleEnchants.isEmpty()) {
					chosenEnchants.add(WeightedRandom.getRandomItem(rand, possibleEnchants).get().data);
					removeIncompatible(possibleEnchants, Util.lastOf(chosenEnchants));
				}

				if (arcana >= 75F && !possibleEnchants.isEmpty()) {
					chosenEnchants.add(WeightedRandom.getRandomItem(rand, possibleEnchants).get().data);
				}

				int randomBound = 50;
				if (level > 45) {
					level = (int) (srcLevel * 1.15F);
				}

				while (rand.nextInt(randomBound) <= level) {
					if (!chosenEnchants.isEmpty()) removeIncompatible(possibleEnchants, Util.lastOf(chosenEnchants));

					if (possibleEnchants.isEmpty()) {
						break;
					}

					chosenEnchants.add(WeightedRandom.getRandomItem(rand, possibleEnchants).get().data);
					level /= 2;
				}
			}
		}
		return ((IEnchantableItem) stack.getItem()).selectEnchantments(chosenEnchants, rand, stack, srcLevel, quanta, arcana, treasure);
	}

	/**
	 * Removes all enchantments from the list that are incompatible with the passed enchantment.
	 */
	public static void removeIncompatible(List<ArcanaEnchantmentData> list, EnchantmentInstance data) {
		Iterator<ArcanaEnchantmentData> iterator = list.iterator();

		while (iterator.hasNext()) {
			if (!data.enchantment.isCompatibleWith(iterator.next().data.enchantment)) {
				iterator.remove();
			}
		}

	}

	/**
	 * Gets all enchantments that can be applied to the given item at the respective power level.
	 */
	public static List<EnchantmentInstance> getEnchantmentDatas(int power, ItemStack stack, boolean treasure) {
		return EnchHooks.getEnchantmentDatas(power, stack, treasure);
	}

	private static class ArcanaEnchantmentData extends IntrusiveBase {
		EnchantmentInstance data;

		private ArcanaEnchantmentData(Arcana arcana, EnchantmentInstance data) {
			super(arcana.getRarities()[data.enchantment.getRarity().ordinal()]);
			this.data = data;
		}
	}
}
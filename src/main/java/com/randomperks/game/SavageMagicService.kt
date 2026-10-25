package com.randomperks.game

import com.randomperks.mixin.VillagerFillRecipesInvoker
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.minecraft.entity.Entity
import net.minecraft.entity.passive.VillagerEntity
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKey
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.server.world.ServerWorld
import net.minecraft.sound.SoundCategory
import net.minecraft.sound.SoundEvents
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.village.VillagerProfession

/**
 * "野人魔了" (紫 · 秘藏):
 * 手持**对应职业方块**右键**傻子村民**, 消耗一个方块, 把它变成该职业的**大师级**村民。
 *
 * <p>交易列表按 1→5 级逐级填充 —— 直接设置 5 级只会生成大师级的两条交易。
 */
object SavageMagicService {

    const val PERK_ID = "seeker_savage_magic"

    /** 职业方块 (物品) → 村民职业 */
    private val BLOCK_TO_PROFESSION: Map<Item, RegistryKey<VillagerProfession>> = mapOf(
        Items.COMPOSTER to VillagerProfession.FARMER,
        Items.BARREL to VillagerProfession.FISHERMAN,
        Items.LOOM to VillagerProfession.SHEPHERD,
        Items.FLETCHING_TABLE to VillagerProfession.FLETCHER,
        Items.LECTERN to VillagerProfession.LIBRARIAN,
        Items.CARTOGRAPHY_TABLE to VillagerProfession.CARTOGRAPHER,
        Items.BREWING_STAND to VillagerProfession.CLERIC,
        Items.BLAST_FURNACE to VillagerProfession.ARMORER,
        Items.GRINDSTONE to VillagerProfession.WEAPONSMITH,
        Items.SMITHING_TABLE to VillagerProfession.TOOLSMITH,
        Items.SMOKER to VillagerProfession.BUTCHER,
        Items.CAULDRON to VillagerProfession.LEATHERWORKER,
    )

    fun register() {
        UseEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (world.isClient || hand != Hand.MAIN_HAND || player !is ServerPlayerEntity) {
                return@register ActionResult.PASS
            }
            convert(player, entity)
        }
    }

    private fun convert(player: ServerPlayerEntity, entity: Entity): ActionResult {
        if (!PerkSession.isEnabled()) return ActionResult.PASS
        if (!PerkBoostQuery.hasSavageMagic(player)) return ActionResult.PASS

        val villager = entity as? VillagerEntity ?: return ActionResult.PASS
        if (villager.isBaby) return ActionResult.PASS
        // 只对傻子村民生效
        if (villager.villagerData.profession.key.orElse(null) != VillagerProfession.NITWIT) {
            return ActionResult.PASS
        }

        val stack = player.getStackInHand(Hand.MAIN_HAND)
        val professionKey = BLOCK_TO_PROFESSION[stack.item] ?: return ActionResult.PASS
        val world = player.world as? ServerWorld ?: return ActionResult.PASS

        val profession = Registries.VILLAGER_PROFESSION.getOrThrow(professionKey)

        // 逐级填充: 先 1 级建立交易列表, 再依次补上 2~5 级
        villager.setVillagerData(
            villager.villagerData.withProfession(profession).withLevel(1)
        )
        villager.getOffers()
        for (level in 2..5) {
            villager.setVillagerData(villager.villagerData.withLevel(level))
            (villager as VillagerFillRecipesInvoker).invokeFillRecipes()
        }
        villager.setExperience(250)

        if (!player.abilities.creativeMode) {
            stack.decrement(1)
        }

        world.playSound(
            null,
            villager.blockPos,
            SoundEvents.ENTITY_VILLAGER_YES,
            SoundCategory.NEUTRAL,
            1.0f,
            1.0f,
        )
        return ActionResult.SUCCESS
    }
}

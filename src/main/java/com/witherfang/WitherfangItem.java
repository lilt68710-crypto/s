package com.witherfang;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Right click          -> fires a wither skull
 * Sneak + right click  -> summons wither skeleton minions
 */
public class WitherfangItem extends Item {
	private static final int SKULL_COOLDOWN_TICKS = 12;
	private static final int SUMMON_COOLDOWN_TICKS = 400; // 20 seconds
	private static final int MINION_COUNT = 4;
	private static final double SKULL_SPEED = 1.6;

	public WitherfangItem(Item.Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = player.getItemInHand(hand);

		if (player.isShiftKeyDown()) {
			summonMinions(serverLevel, player);
			player.getCooldowns().addCooldown(stack, SUMMON_COOLDOWN_TICKS);
		} else {
			shootSkull(serverLevel, player);
			player.getCooldowns().addCooldown(stack, SKULL_COOLDOWN_TICKS);
		}

		return InteractionResult.SUCCESS;
	}

	private static void shootSkull(ServerLevel level, Player player) {
		// "var" on purpose: avoids hard-coding the WitherSkull package, which Mojang has moved around
		var skull = EntityType.WITHER_SKULL.create(level, EntitySpawnReason.TRIGGERED);
		if (skull == null) {
			return;
		}

		Vec3 look = player.getLookAngle();
		skull.setOwner(player);
		skull.setPos(player.getX() + look.x, player.getEyeY() - 0.2 + look.y, player.getZ() + look.z);
		skull.setDeltaMovement(look.scale(SKULL_SPEED));
		skull.setRot(player.getYRot(), player.getXRot());
		level.addFreshEntity(skull);

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.8F, 1.2F);
	}

	private static void summonMinions(ServerLevel level, Player player) {
		// Replace any previous minions so they can't pile up
		List<Mob> old = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64.0),
				mob -> WitherfangEvents.isMinionOf(mob, player));
		old.forEach(Mob::discard);

		for (int i = 0; i < MINION_COUNT; i++) {
			double angle = (Math.PI * 2.0 * i) / MINION_COUNT;
			BlockPos pos = player.blockPosition().offset(
					(int) Math.round(Math.cos(angle) * 2.5), 0, (int) Math.round(Math.sin(angle) * 2.5));

			var skeleton = EntityType.WITHER_SKELETON.spawn(level, pos, EntitySpawnReason.MOB_SUMMONED);
			if (skeleton == null) {
				continue;
			}
			WitherfangEvents.markMinion(skeleton, player);

			level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
					skeleton.getX(), skeleton.getY() + 1.0, skeleton.getZ(), 15, 0.3, 0.6, 0.3, 0.03);
		}

		level.playSound(null, player.getX(), player.getY(), player.getZ(),
				SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 1.0F, 0.8F);
	}
}


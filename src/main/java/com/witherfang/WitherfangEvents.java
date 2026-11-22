package com.witherfang;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;

public final class WitherfangEvents {
	// ---- tuning ----
	/** Wither skeletons within this many blocks of the wielder count toward the damage bonus. */
	private static final double SURROUND_RADIUS = 5.0;
	/** Extra damage per surrounding wither skeleton (2.0 = one heart). */
	private static final double BONUS_PER_SKELETON = 2.0;
	/** Skeletons counted at most. 10 * 2.0 = up to +20 damage. */
	private static final int MAX_COUNTED = 10;
	/** Minions vanish after this many ticks (600 = 30 s). */
	private static final int MINION_LIFETIME_TICKS = 600;
	/** How long a hit target stays "marked" for the minions (200 = 10 s). */
	private static final int TARGET_MEMORY_TICKS = 200;

	// ---- tags stored on the minion entity (saved with the entity) ----
	private static final String MINION_TAG = "witherfang_minion";
	private static final String OWNER_TAG_PREFIX = "witherfang_owner_";

	private static final Identifier BONUS_ID = Identifier.fromNamespaceAndPath(Witherfang.MOD_ID, "surround_bonus");

	private record Marked(UUID target, long expiresAt) {
	}

	private static final Map<UUID, Marked> MARKED_TARGETS = new HashMap<>();

	private static String ownerTag(UUID owner) {
		return OWNER_TAG_PREFIX + owner;
	}

	public static void markMinion(Entity minion, Player owner) {
		minion.addTag(MINION_TAG);
		minion.addTag(ownerTag(owner.getUUID()));
	}

	public static boolean isMinionOf(Entity entity, Player owner) {
		return entity.getType() == EntityType.WITHER_SKELETON
				&& entity.getTags().contains(MINION_TAG)
				&& entity.getTags().contains(ownerTag(owner.getUUID()));
	}

	private static boolean isMinion(Entity entity) {
		return entity.getTags().contains(MINION_TAG);
	}

	private static boolean holdingWitherfang(Player player) {
		return player.getMainHandItem().is(ModItems.WITHERFANG);
	}

	private static int countSurrounding(ServerLevel level, Player player) {
		List<Mob> skeletons = level.getEntitiesOfClass(Mob.class,
				player.getBoundingBox().inflate(SURROUND_RADIUS),
				mob -> mob.getType() == EntityType.WITHER_SKELETON && mob.isAlive());
		return Math.min(skeletons.size(), MAX_COUNTED);
	}

	public static void register() {
		// 1) On attack: bonus damage + tell the minions who to go after
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (hand != InteractionHand.MAIN_HAND
					|| !(level instanceof ServerLevel serverLevel)
					|| !(entity instanceof LivingEntity target)
					|| !holdingWitherfang(player)
					|| isMinionOf(target, player)) {
				return InteractionResult.PASS;
			}

			MARKED_TARGETS.put(player.getUUID(),
					new Marked(target.getUUID(), serverLevel.getGameTime() + TARGET_MEMORY_TICKS));

			// A temporary attack-damage modifier is applied just before vanilla works out the hit,
			// and removed again at the end of the tick.
			AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
			if (damage != null) {
				damage.removeModifier(BONUS_ID);
				double bonus = countSurrounding(serverLevel, player) * BONUS_PER_SKELETON;
				if (bonus > 0) {
					damage.addTransientModifier(
							new AttributeModifier(BONUS_ID, bonus, AttributeModifier.Operation.ADD_VALUE));
				}
			}
			return InteractionResult.PASS;
		});

		// 2) No friendly fire: minions never hurt their owner, and the owner's skulls never hurt the owner or minions
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			Entity attacker = source.getEntity();
			if (attacker != null && isMinion(attacker) && entity instanceof Player victim
					&& attacker.getTags().contains(ownerTag(victim.getUUID()))) {
				return false;
			}

			Entity direct = source.getDirectEntity();
			if (direct != null && direct.getType() == EntityType.WITHER_SKULL
					&& direct instanceof Projectile projectile
					&& projectile.getOwner() instanceof Player owner) {
				if (entity == owner || entity.getTags().contains(ownerTag(owner.getUUID()))) {
					return false;
				}
			}
			return true;
		});

		// 3) Minions just vanish when killed (no skull/XP farming)
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (isMinion(entity) && entity.level() instanceof ServerLevel serverLevel) {
				serverLevel.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() + 1.0, entity.getZ(),
						12, 0.3, 0.6, 0.3, 0.02);
				entity.discard();
				return false;
			}
			return true;
		});

		// 4) Per-tick upkeep
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
				if (damage != null) {
					damage.removeModifier(BONUS_ID);
				}
			}

			if (server.getTickCount() % 10 != 0) {
				return;
			}
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				if (player.level() instanceof ServerLevel level) {
					manageMinions(level, player);
				}
			}
		});

		ServerLifecycleEvents.SERVER_STOPPED.register(server -> MARKED_TARGETS.clear());
	}

	private static void manageMinions(ServerLevel level, ServerPlayer owner) {
		List<Mob> minions = level.getEntitiesOfClass(Mob.class, owner.getBoundingBox().inflate(64.0),
				mob -> isMinionOf(mob, owner));
		if (minions.isEmpty()) {
			return;
		}

		LivingEntity desired = null;
		Marked marked = MARKED_TARGETS.get(owner.getUUID());
		if (marked != null) {
			if (level.getGameTime() < marked.expiresAt()
					&& level.getEntity(marked.target()) instanceof LivingEntity living && living.isAlive()) {
				desired = living;
			} else {
				MARKED_TARGETS.remove(owner.getUUID());
			}
		}

		for (Mob minion : minions) {
			if (minion.tickCount > MINION_LIFETIME_TICKS) {
				level.sendParticles(ParticleTypes.SOUL, minion.getX(), minion.getY() + 1.0, minion.getZ(),
						12, 0.3, 0.6, 0.3, 0.02);
				minion.discard();
				continue;
			}

			if (desired != null) {
				if (minion.getTarget() != desired) {
					minion.setTarget(desired);
				}
			} else if (minion.getTarget() instanceof Player) {
				// default wither skeleton AI wants to attack players; the owner (and everyone else) is off limits
				minion.setTarget(null);
			}

			// Stick close to the owner while idle
			if (minion.getTarget() == null) {
				double distance = minion.distanceTo(owner);
				if (distance > 32.0) {
					minion.setPos(owner.getX(), owner.getY(), owner.getZ());
				} else if (distance > 6.0) {
					minion.getNavigation().moveTo(owner, 1.2);
				}
			}
		}
	}

	private WitherfangEvents() {
	}
}

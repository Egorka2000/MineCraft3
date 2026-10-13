package io.github.egorka2000.tf2mc.weapon;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.movement.UnitConversion;
import io.github.egorka2000.tf2mc.net.Tf2Networking;
import io.github.egorka2000.tf2mc.physics.ExplosionMath;
import io.github.egorka2000.tf2mc.team.Teams;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Взрыв ракеты: урон и отбрасывание по правилам TF2, блоки не разрушаются.
 * Математика — в {@link ExplosionMath}, здесь поиск целей и связь с игрой.
 */
public final class RocketBlast {
	/** Урон ракеты в центре взрыва на среднем расстоянии от стрелка. */
	public static final double DAMAGE = 90.0;

	/** Радиус взрыва для всех, кроме стрелка, u. */
	public static final double RADIUS = 146.0;

	/** Радиус взрыва для самого стрелка, u. */
	public static final double RADIUS_SELF = 121.0;

	private RocketBlast() {
	}

	/**
	 * Взрывает ракету.
	 *
	 * @param position  точка взрыва
	 * @param rocket    сама ракета: источник урона
	 * @param owner     стрелок; может отсутствовать
	 * @param directHit цель прямого попадания, получает полный урон; может отсутствовать
	 */
	public static void explode(ServerLevel level, Vec3 position, Entity rocket, Entity owner, Entity directHit) {
		double unitsPerBlock = Tf2Mc.config().current().unitsPerBlock();
		double reach = UnitConversion.toBlocks(RADIUS, unitsPerBlock);

		level.sendParticles(ParticleTypes.EXPLOSION, position.x, position.y, position.z, 1, 0.0, 0.0, 0.0, 0.0);
		level.playSound(null, position.x, position.y, position.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 2.0F, 1.0F);

		// Запас на размер хитбокса: расстояние считается до центра цели.
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new AABB(position, position).inflate(reach + 2.0), LivingEntity::isAlive)) {
			boolean self = target == owner;
			Vec3 centre = target.getBoundingBox().getCenter();
			double distance = target == directHit ? 0.0 : UnitConversion.toUnits(centre.distanceTo(position), unitsPerBlock);
			// Урон по чужим зависит от расстояния между стрелком и целью в момент попадания.
			double base = self || owner == null
					? DAMAGE
					: DAMAGE * ExplosionMath.rangeMultiplier(UnitConversion.toUnits(target.distanceTo(owner), unitsPerBlock));
			double damage = ExplosionMath.splashDamage(base, distance, self ? RADIUS_SELF : RADIUS);

			if (damage <= 0.0 || target != directHit && isBlocked(level, position, centre, rocket)) {
				continue;
			}

			if (self) {
				hitOwner(level, position, rocket, target, damage, unitsPerBlock);
			} else if (canHarm(owner, target)) {
				hitOther(level, position, rocket, owner, target, damage, unitsPerBlock);
			}
		}
	}

	/** Стрелка задел собственный взрыв: толчок по правилам ракетного прыжка и сниженный в воздухе урон. */
	private static void hitOwner(ServerLevel level, Vec3 position, Entity rocket, LivingEntity owner, double damage, double unitsPerBlock) {
		boolean airborne = !owner.onGround();
		ExplosionMath.Vector push = ExplosionMath.selfPush(
				toUnits(position, unitsPerBlock), toUnits(owner.getBoundingBox().getCenter(), unitsPerBlock), damage, airborne, owner.isCrouching());

		// Источник без стрелка: иначе игра не пропустит урон по себе у игрока, состоящего в команде.
		DamageSource source = level.damageSources().source(Tf2Weapons.BLAST, rocket, null);
		boolean inWater = owner.isInWater();
		owner.hurtServer(level, source, (float) (inWater ? damage : ExplosionMath.selfDamage(damage, airborne)));
		push(owner, push, unitsPerBlock);
	}

	private static void hitOther(ServerLevel level, Vec3 position, Entity rocket, Entity owner, LivingEntity target, double damage, double unitsPerBlock) {
		ExplosionMath.Vector push = ExplosionMath.otherPush(
				toUnits(position, unitsPerBlock), toUnits(target.getBoundingBox().getCenter(), unitsPerBlock), damage);

		if (target.hurtServer(level, level.damageSources().source(Tf2Weapons.BLAST, rocket, owner), (float) damage)) {
			push(target, push, unitsPerBlock);
		}
	}

	/** Может ли стрелок ранить цель: свои по команде и нейтральные игроки взрывом не задеваются. */
	private static boolean canHarm(Entity owner, LivingEntity target) {
		if (!(owner instanceof Player shooter) || !(target instanceof Player victim)) {
			return true;
		}

		return Teams.canFight(shooter, victim);
	}

	/** Закрыта ли цель от взрыва блоками. */
	private static boolean isBlocked(ServerLevel level, Vec3 from, Vec3 to, Entity rocket) {
		return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, rocket)).getType() != HitResult.Type.MISS;
	}

	/** Добавляет толчок к скорости цели. Движение игрока считает его клиент, поэтому игроку толчок уходит по сети. */
	private static void push(LivingEntity target, ExplosionMath.Vector push, double unitsPerBlock) {
		// Оси TF2: z вверх. Скорость игры — блоки за тик.
		Vec3 impulse = new Vec3(
				UnitConversion.toBlocksPerTick(push.x(), unitsPerBlock),
				UnitConversion.toBlocksPerTick(push.z(), unitsPerBlock),
				UnitConversion.toBlocksPerTick(push.y(), unitsPerBlock));

		if (target instanceof ServerPlayer player) {
			Tf2Networking.push(player, impulse);
		} else {
			target.setDeltaMovement(target.getDeltaMovement().add(impulse));
		}
	}

	private static ExplosionMath.Vector toUnits(Vec3 blocks, double unitsPerBlock) {
		return new ExplosionMath.Vector(
				UnitConversion.toUnits(blocks.x, unitsPerBlock),
				UnitConversion.toUnits(blocks.z, unitsPerBlock),
				UnitConversion.toUnits(blocks.y, unitsPerBlock));
	}
}

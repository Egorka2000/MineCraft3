package io.github.egorka2000.tf2mc.weapon;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.movement.UnitConversion;
import io.github.egorka2000.tf2mc.physics.ExplosionMath;
import io.github.egorka2000.tf2mc.physics.PelletSpread;
import io.github.egorka2000.tf2mc.team.Teams;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Дробовик Soldier, второе оружие: 10 дробин по 6 урона, попадание мгновенное, по лучу от глаз.
 * Обойма, перезарядка и доставание — общие для оружия с обоймой ({@link ClipWeapon}).
 *
 * <p>Числа — TF2 Wiki, страница Shotgun (docs/05-sources.md): урон дробины 6, в упор 150%, вдали
 * 52,8%; разброс 0,0675; первая дробина летит точно в прицел ({@link PelletSpread}).
 */
public final class Shotgun {
	/** Дробин в одном выстреле. */
	public static final int PELLETS = 10;

	/** Урон одной дробины на среднем расстоянии. */
	public static final double PELLET_DAMAGE = 6.0;

	/** Разброс дроби. */
	public static final double SPREAD = 0.0675;

	/** Дальность луча, u: дальше дробь не летит. */
	public static final double RANGE = 8192.0;

	/** Оружие: предмет, боезапас, стрельба. */
	public static final ClipWeapon WEAPON = new ClipWeapon("shotgun", "shotgun_ammo", Magazine.Rules.SHOTGUN,
			Tf2Sounds.SHOTGUN_SHOOT, Tf2Sounds.SHOTGUN_RELOAD, Shotgun::shoot);

	/** Предмет-дробовик. Выдаётся вместе с классом. */
	public static final Item ITEM = WEAPON.item();

	private Shotgun() {
	}

	/**
	 * Выстрел дробью. Урон всех дробин по одной цели складывается и наносится разом: одно попадание,
	 * один звук урона. Ванильного отбрасывания у этого типа урона нет.
	 */
	static void shoot(ServerPlayer player) {
		ServerLevel level = player.level();
		double unitsPerBlock = Tf2Mc.config().current().unitsPerBlock();
		Vec3 eye = player.getEyePosition();
		Vec3 forward = player.getLookAngle();
		Vec3 right = forward.cross(new Vec3(0.0, 1.0, 0.0));
		right = right.lengthSqr() < 1.0e-6 ? new Vec3(1.0, 0.0, 0.0) : right.normalize();
		Vec3 up = right.cross(forward).normalize();
		double range = RANGE / unitsPerBlock;
		Map<LivingEntity, Double> damage = new LinkedHashMap<>();
		Set<BlockPos> struck = new HashSet<>();

		for (double[] offset : PelletSpread.offsets(PELLETS, SPREAD, player.getRandom()::nextDouble)) {
			Vec3 direction = forward.add(right.scale(offset[0])).add(up.scale(offset[1])).normalize();
			HitResult hit = trace(level, player, eye, eye.add(direction.scale(range)));

			if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity target) {
				double distance = UnitConversion.toUnits(player.distanceTo(target), unitsPerBlock);
				damage.merge(target, PELLET_DAMAGE * ExplosionMath.rangeMultiplier(distance, ExplosionMath.RANGE_NEAR_BULLET), Double::sum);
			} else if (hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK) {
				impact(level, blockHit, struck.add(blockHit.getBlockPos()));
			}
		}

		damage.forEach((target, amount) -> target.hurtServer(level, level.damageSources().source(Tf2Weapons.BULLET, player, player), amount.floatValue()));
	}

	/**
	 * Дробина попала в блок: крошка блока в точке попадания и звук удара по его материалу, как
	 * следы пуль в TF2. Звук — один на блок, даже если в него попало несколько дробин.
	 */
	private static void impact(ServerLevel level, BlockHitResult hit, boolean sound) {
		BlockState state = level.getBlockState(hit.getBlockPos());
		Vec3 at = hit.getLocation();
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), at.x, at.y, at.z, 4, 0.05, 0.05, 0.05, 0.1);

		if (sound) {
			SoundType type = state.getSoundType();
			level.playSound(null, at.x, at.y, at.z, type.getHitSound(), SoundSource.PLAYERS, type.getVolume(), type.getPitch());
		}
	}

	/** Что встретила дробина: первая живая цель до блока, блок или ничего. */
	private static HitResult trace(ServerLevel level, ServerPlayer shooter, Vec3 from, Vec3 to) {
		BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
		Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(shooter, from, end, new AABB(from, end).inflate(1.0),
				entity -> canHit(shooter, entity), from.distanceToSqr(end));
		return hit != null ? hit : block;
	}

	/** Дробь не задевает стрелка, наблюдателей и тех, кого он ранить не может: свою команду и нейтральных. */
	private static boolean canHit(ServerPlayer shooter, Entity entity) {
		if (entity == shooter || !(entity instanceof LivingEntity living) || !living.isAlive() || entity.isSpectator() || !entity.isPickable()) {
			return false;
		}

		return !(entity instanceof Player target) || Teams.canFight(shooter, target);
	}
}

package io.github.egorka2000.tf2mc.weapon;

import io.github.egorka2000.tf2mc.Tf2Mc;
import io.github.egorka2000.tf2mc.movement.UnitConversion;
import io.github.egorka2000.tf2mc.team.Teams;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Ракета Soldier: летит по прямой с постоянной скоростью и взрывается при первом столкновении.
 *
 * <p>За тик ракета проходит больше блока, поэтому столкновения проверяются по всему отрезку
 * пути, а не только в конечной точке. Считает только сервер; клиент получает положение
 * штатной рассылкой сущностей.
 */
public class RocketEntity extends Projectile {
	/** Скорость ракеты, u/s. */
	public static final double SPEED = 1100.0;

	/** На сколько блоков точка взрыва отодвигается от поверхности, в которую попала ракета. */
	private static final double SURFACE_OFFSET = 0.01;

	public RocketEntity(EntityType<? extends RocketEntity> type, Level level) {
		super(type, level);
	}

	/** Ракета летит по прямой: гравитация на неё не действует. */
	@Override
	protected double getDefaultGravity() {
		return 0.0;
	}

	/**
	 * Выпускает ракету из глаз стрелка по направлению взгляда.
	 *
	 * @return выпущенная ракета
	 */
	public static RocketEntity fire(ServerLevel level, LivingEntity shooter) {
		RocketEntity rocket = new RocketEntity(Tf2Weapons.ROCKET, level);
		Vec3 direction = shooter.getLookAngle();
		double speed = UnitConversion.toBlocksPerTick(SPEED, Tf2Mc.config().current().unitsPerBlock());

		rocket.setOwner(shooter);
		rocket.setPos(shooter.getEyePosition());
		rocket.setDeltaMovement(direction.scale(speed));
		rocket.setYRot(shooter.getYRot());
		rocket.setXRot(shooter.getXRot());
		level.addFreshEntity(rocket);
		return rocket;
	}

	@Override
	public void tick() {
		super.tick();

		if (!(level() instanceof ServerLevel level)) {
			return;
		}

		Vec3 movement = getDeltaMovement();
		HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);

		if (hit.getType() != HitResult.Type.MISS) {
			Entity direct = hit instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;

			// Точка взрыва чуть отодвинута от поверхности навстречу полёту: иначе проверка,
			// закрыта ли цель блоками, начиналась бы на самой грани блока.
			Vec3 position = hit.getLocation().subtract(movement.normalize().scale(SURFACE_OFFSET));

			RocketBlast.explode(level, position, this, getOwner(), direct);
			discard();
			return;
		}

		Vec3 next = position().add(movement);

		if (!canFlyTo(level, next)) {
			discard();
			return;
		}

		setPos(next);
		// Своей модели у ракеты пока нет: путь видно по дыму.
		level.sendParticles(ParticleTypes.SMOKE, getX(), getY(), getZ(), 2, 0.0, 0.0, 0.0, 0.0);
	}

	/**
	 * Может ли ракета лететь дальше. Как в TF2, срока жизни у неё нет: летит, пока во что-нибудь
	 * не попадёт. Исчезает она, только когда попасть уже не во что:
	 * <ul>
	 * <li>выше предела высоты мира или ниже его дна — там нет блоков, как за скайбоксом TF2;</li>
	 * <li>за пределами области, где сервер обновляет сущности, то есть дальше прорисовки
	 * игроков. Ракета не подгружает мир: в незагруженной области она бы просто замерла.</li>
	 * </ul>
	 */
	public static boolean canFlyTo(ServerLevel level, Vec3 position) {
		if (position.y < level.getMinY() || position.y > level.getMaxY() + 1) {
			return false;
		}

		return level.isPositionEntityTicking(BlockPos.containing(position));
	}

	/** Ракета пролетает сквозь стрелка и сквозь тех, кого он ранить не может: свою команду и нейтральных. */
	@Override
	protected boolean canHitEntity(Entity entity) {
		Entity owner = getOwner();

		if (entity == owner || !super.canHitEntity(entity)) {
			return false;
		}

		if (owner instanceof Player shooter && entity instanceof Player target) {
			return Teams.canFight(shooter, target);
		}

		return true;
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
	}
}

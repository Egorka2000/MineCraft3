package io.github.egorka2000.tf2mc.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Блочная сущность спецблоков, которые клиент рисует моделью TF2 из расширительного пака:
 * аптечек, патронов и шкафов пополнения. Данных не хранит; на клиенте помнит, открыты ли
 * дверцы шкафа и когда они начали открываться или закрываться.
 */
public final class PropBlockEntity extends BlockEntity {
	private boolean open;
	private float changedAt = Float.NEGATIVE_INFINITY;

	public PropBlockEntity(BlockPos pos, BlockState state) {
		super(Tf2Blocks.PROP, pos, state);
	}

	/** Открыты ли дверцы шкафа (только на клиенте). */
	public boolean open() {
		return open;
	}

	/** Когда дверцы начали открываться или закрываться, секунды игрового времени. */
	public float changedAt() {
		return changedAt;
	}

	/** Дверцы начинают открываться или закрываться в момент {@code now}, если их состояние меняется. */
	public void setOpen(boolean value, float now) {
		if (value != open) {
			open = value;
			changedAt = now;
		}
	}
}

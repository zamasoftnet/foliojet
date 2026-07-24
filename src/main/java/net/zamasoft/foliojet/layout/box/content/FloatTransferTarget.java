package net.zamasoft.foliojet.layout.box.content;

/**
 * {@code Container.splitFloatings}の呼び出し側が指定する、移動float台帳の
 * 行き先です(2026-07-24新設、排除域P2のP2-4。
 * {@code docs/consultations/consult-exclusion-p2-design-codex.txt}§2.2の型)。
 * 旧APIのnullableな{@code Container nextBox}引数のsentinel
 * (null / this / 既存コンテナ)を置き換えます。
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface FloatTransferTarget {
	/** 行き先コンテナ未定(旧 null)。移動があれば新しいFlowContainerを生成する。 */
	FloatTransferTarget KEEP = new Keep();

	/** owner自身が丸ごと次のフラグメントへ移動する文脈(旧 this)。 */
	FloatTransferTarget MOVE_OWNER = new MoveOwner();

	record Keep() implements FloatTransferTarget {
	}

	record MoveOwner() implements FloatTransferTarget {
	}

	/**
	 * 既存の次フラグメント側コンテナへ装着します(旧 nextBox指定)。
	 *
	 * @param container 次フラグメント側のコンテナ
	 */
	record Existing(FlowContainer container) implements FloatTransferTarget {
	}
}

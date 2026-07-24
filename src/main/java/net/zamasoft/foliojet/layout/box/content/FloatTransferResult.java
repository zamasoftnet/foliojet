package net.zamasoft.foliojet.layout.box.content;

/**
 * {@code Container.splitFloatings}の型付き結果です(2026-07-24新設、
 * 排除域P2のP2-4。{@code docs/consultations/consult-exclusion-p2-design-codex.txt}
 * §2.2の型)。旧APIの返り値sentinel(nextBoxそのまま / this / 新コンテナ)を
 * 置き換えます。分岐表の正本:
 * {@code docs/history/2026-07-24-p2-splitfloatings-branch-table.md}の
 * 「public 3引数版」の表。
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface FloatTransferResult {
	/**
	 * 移動するfloatなし(旧「引数nextBoxをそのまま返す」)。呼び出し側は
	 * 自分の{@link FloatTransferTarget}が指すコンテナをそのまま使い続ける。
	 */
	FloatTransferResult KEEP_OWNER = new KeepOwner();

	/**
	 * owner自身が丸ごと次のフラグメントへ移動する(旧 this)。
	 * {@code MOVE_OWNER}指定時のMoveAll、および「空コンテナ全体をfloatごと
	 * 移動する特例」({@code KEEP}指定・非FIRST・innerPageExtent&lt;=0)。
	 * float台帳はownerに付いたまま。
	 */
	FloatTransferResult MOVE_OWNER = new MoveOwner();

	record KeepOwner() implements FloatTransferResult {
	}

	record MoveOwner() implements FloatTransferResult {
	}

	/**
	 * 移動float台帳を装着したコンテナです(旧「nextBoxまたは新
	 * FlowContainerを返す」)。{@link FloatTransferTarget.Existing}指定時は
	 * その同じコンテナ、それ以外は新しいFlowContainer。
	 *
	 * @param container 移動float台帳を装着したコンテナ
	 */
	record Remainder(FlowContainer container) implements FloatTransferResult {
	}
}

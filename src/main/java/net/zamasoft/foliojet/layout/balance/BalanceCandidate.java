package net.zamasoft.foliojet.layout.balance;

import java.util.List;

import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.impl.MulticolumnBlockBox;

/**
 * M6cバランスプローブの1候補の観測結果です(2026-07-24新設、排除域P2の
 * M6c-2——codex設計§1.2)。
 *
 * <p>
 * 候補は完全な新品({@link BalanceProbeSession#build})であり、live側の
 * 何にも接続されていない。fitの判定は論理的なコンテンツ量ではなく、
 * 生成された実カラム数と各カラムの実測extentだけで行う(§1.7)。
 * </p>
 */
public final class BalanceCandidate {

	/**
	 * 収まり判定の許容差です。{@code LayoutUtils.compare}の0.5ptはこの
	 * 用途には粗すぎる(偽の最小容量に収束し、行境界に整列しないボックス
	 * 高さが下流の切断位置を変えてしまう)ため使わない。
	 */
	public static final double TOLERANCE = 0.01;

	private final double requestedCapacity;
	private final double committedCapacity;
	private final int actualColumns;
	private final List<Double> usedExtents;
	private final MulticolumnBlockBox candidateBox;
	private boolean containerTaken = false;

	BalanceCandidate(final double requestedCapacity, final double committedCapacity, final int actualColumns,
			final List<Double> usedExtents, final MulticolumnBlockBox candidateBox) {
		this.requestedCapacity = requestedCapacity;
		this.committedCapacity = committedCapacity;
		this.actualColumns = actualColumns;
		this.usedExtents = List.copyOf(usedExtents);
		this.candidateBox = candidateBox;
	}

	/** 組んだ試行容量です。 */
	public double requestedCapacity() {
		return this.requestedCapacity;
	}

	/**
	 * 実測の最大カラム内容寸法(maxUsed)です。commit時にはこの実測境界へ
	 * 容量をスナップする——切断は内容境界でしか起きないため、境界間の
	 * 容量は同じレイアウトに落ちる(codex設計§1.7)。
	 */
	public double committedCapacity() {
		return this.committedCapacity;
	}

	/** 生成された実カラム数です。 */
	public int actualColumns() {
		return this.actualColumns;
	}

	/** 各カラムの実測内容寸法です(カラム順)。 */
	public List<Double> usedExtents() {
		return this.usedExtents;
	}

	/** 候補shell(新品の{@code MulticolumnBlockBox})です。 */
	public MulticolumnBlockBox candidateBox() {
		return this.candidateBox;
	}

	/**
	 * 指定段数・試行容量に収まったかを判定します(実カラム数と実測extent
	 * だけで判定——論理コンテンツ量は見ない)。
	 */
	public boolean fits(final int columnCount) {
		return this.actualColumns <= columnCount && this.committedCapacity <= this.requestedCapacity + TOLERANCE;
	}

	/**
	 * 候補のコンテナを取り出します。<b>一回だけ</b>呼べる——winnerの
	 * ownerへの接続(M6c-4)を正確に一回に限定するため、二回目は
	 * {@link IllegalStateException}を投げる。
	 */
	public Container takeContainer() {
		if (this.containerTaken) {
			throw new IllegalStateException("candidate container was already taken");
		}
		this.containerTaken = true;
		return this.candidateBox.getContainer();
	}
}

package net.zamasoft.foliojet.layout.box.impl;

import net.zamasoft.foliojet.layout.box.AbstractBlockBox;
import net.zamasoft.foliojet.layout.box.content.Container;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.FlowPos;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

public class MulticolumnBlockBox extends FlowBlockBox {
	public MulticolumnBlockBox(BlockParams params, FlowPos pos) {
		super(params, pos);
	}

	protected MulticolumnBlockBox(BlockParams params, FlowPos pos, Dimension size, Dimension minSize,
			AbsoluteRectFrame frame, Container container) {
		super(params, pos, size, minSize, frame, container);
	}

	public int getColumnCount() {
		return LayoutUtils.getColumnCount(this);
	}

	public net.zamasoft.foliojet.layout.fragment.FragmentRecipe fragmentRecipe() {
		final BlockParams params = this.getBlockParams();
		final FlowPos pos = this.getFlowPos();
		return (state, container) -> new MulticolumnBlockBox(params, pos, state.nextSize(), state.nextMinSize(),
				state.nextFrame(), container);
	}

	/**
	 * このlive ownerの解決済み物理形状を凍結します(2026-07-24新設、
	 * 排除域P2のM6c-2)。M6cバランスプローブの候補shellはこのスナップ
	 * ショットから作られる({@link #newBalanceProbeShell})——raw CSSから
	 * 親レイアウトを再現するのではなく、live構築で既に解決済みの値を
	 * コピーする(codex設計§1.3)。
	 */
	public net.zamasoft.foliojet.layout.balance.BalanceBoxSnapshot snapshotBalanceGeometry() {
		final net.zamasoft.foliojet.layout.part.AbsoluteInsets margin = new net.zamasoft.foliojet.layout.part.AbsoluteInsets();
		margin.set(this.frame.margin);
		final net.zamasoft.foliojet.layout.part.AbsoluteInsets padding = new net.zamasoft.foliojet.layout.part.AbsoluteInsets();
		padding.set(this.frame.padding);
		return new net.zamasoft.foliojet.layout.balance.BalanceBoxSnapshot(this.params, this.pos, this.size,
				this.minSize, this.frame.frame, margin, padding, this.width, this.height, this.minPageAxis,
				this.specifiedPageAxis, this.resolvedAlign);
	}

	/**
	 * バランスプローブのwinnerをこのownerへ接続します(2026-07-24新設、
	 * 排除域P2のM6c-4——codex設計§1.7)。commitは一回だけ:
	 * <ol>
	 * <li>ownerのcontainer identityがプローブ開始時から変わっていないことを
	 * 検証(変わっていれば{@code ContinuationInvariantViolationException}
	 * ——ここから先の例外は握り潰してlegacy再実行してはいけない。変換全体を
	 * 中断する)</li>
	 * <li>ownerの容量・contentSizeをwinnerの実測値
	 * ({@code committedCapacity}=maxUsed、内容境界へスナップ済み)へ更新</li>
	 * <li>{@code winner.takeContainer()}(一回制)をownerへ接続し、
	 * コンテナ木のboxを全てownerへ付け替える</li>
	 * <li>candidate shellはどこにも保持しない({@code takeContainer()}が
	 * 参照を解放済み)</li>
	 * </ol>
	 */
	public void commitBalance(final net.zamasoft.foliojet.layout.balance.BalanceCommit commit) {
		if (this.container != commit.expectedOwnerContainer()) {
			throw new net.zamasoft.foliojet.layout.fragment.ContinuationInvariantViolationException(
					"owner container changed before balance commit");
		}
		final net.zamasoft.foliojet.layout.balance.BalanceCandidate winner = commit.winner();
		final double capacity = winner.committedCapacity();
		if (this.getBlockParams().flow.isVertical()) {
			this.maxPageAxis = this.width = capacity;
		} else {
			this.maxPageAxis = this.height = capacity;
		}
		this.contentSize = capacity;
		final Container next = winner.takeContainer();
		this.container = next;
		if (next instanceof net.zamasoft.foliojet.layout.box.content.ColumnsContainer columns) {
			columns.adoptBox(this);
		} else {
			next.setBox(this);
		}
	}

	/**
	 * M6cバランスプローブの候補shellを新品で作ります(2026-07-24新設、
	 * 排除域P2のM6c-2)。スナップショットの解決済み物理形状をコピーし、
	 * ページ方向の寸法だけを試行容量{@code capacity}で上書きする
	 * (既存balanceの{@code maxPageAxis = width|height = pageSize}と同じ
	 * 契約)。コンテナは新品の{@link FlowContainer}。
	 *
	 * @param geometry live ownerの凍結済み物理形状
	 * @param capacity ページ方向の試行容量
	 * @return 新品の候補shell
	 */
	public static MulticolumnBlockBox newBalanceProbeShell(
			final net.zamasoft.foliojet.layout.balance.BalanceBoxSnapshot geometry, final double capacity) {
		final AbsoluteRectFrame frame = new AbsoluteRectFrame(geometry.frameSpec());
		frame.margin.set(geometry.margin());
		frame.padding.set(geometry.padding());
		final MulticolumnBlockBox shell = new MulticolumnBlockBox(geometry.params(), geometry.pos(), geometry.size(),
				geometry.minSize(), frame, new net.zamasoft.foliojet.layout.box.content.FlowContainer());
		shell.width = geometry.width();
		shell.height = geometry.height();
		shell.minPageAxis = geometry.minPageAxis();
		shell.specifiedPageAxis = geometry.specifiedPageAxis();
		shell.resolvedAlign = geometry.resolvedAlign();
		if (geometry.params().flow.isVertical()) {
			shell.maxPageAxis = shell.width = capacity;
		} else {
			shell.maxPageAxis = shell.height = capacity;
		}
		return shell;
	}
}

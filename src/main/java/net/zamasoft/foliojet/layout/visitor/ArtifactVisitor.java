package net.zamasoft.foliojet.layout.visitor;

import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.draw.Drawer;

/**
 * 副作用のない{@link Visitor}です(2026-07-25新設、救済分割・増分5。
 * {@code docs/consultations/consult-rescue-split-codex.md} §3)。
 *
 * <p>
 * 救済分割の<b>継続断片</b>({@code offset > 0})を描くあいだだけ実Visitorの
 * 代わりに渡します。継続断片は「見た目は内容、意味の上では先頭断片に属する」
 * ものなので、リンク・フォーム部品・ページ参照・{@code string-set}・
 * しおりといった<b>文書レベルの副作用</b>を二度発行してはいけません。
 * </p>
 *
 * <p>
 * {@code ua.impl.NopVisitor}は使えません——あちらは{@code AbstractVisitor}を
 * 継承しており、{@code visitBox()}でまさにそれらの副作用を処理するためです
 * (答申§3)。ここは本当に何もしません。
 * </p>
 *
 * <p>
 * {@link #startPage()}・{@link #endPage()}は救済断片の描画中には呼ばれ
 * ませんが(ページの開始・終了はページ単位の処理)、契約上no-opにして
 * あります。
 * </p>
 */
public final class ArtifactVisitor implements Visitor {

	/** 状態を持たないため共有できます。 */
	public static final ArtifactVisitor INSTANCE = new ArtifactVisitor();

	private ArtifactVisitor() {
		// singleton
	}

	public void startPage() {
		// 何もしない
	}

	public void visitBox(final AffineTransform transform, final IBox box, final Drawer drawer, final double x,
			final double y) {
		// 何もしない
	}

	public void endPage() {
		// 何もしない
	}
}

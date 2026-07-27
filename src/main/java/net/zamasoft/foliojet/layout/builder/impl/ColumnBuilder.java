package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.IPageBreakableBox;
import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.AutoBreakMode;
import net.zamasoft.foliojet.layout.box.content.BreakMode.ForceBreakMode;
import net.zamasoft.foliojet.layout.builder.LayoutStack;

public class ColumnBuilder extends BreakableBuilder {
	public ColumnBuilder(LayoutStack layoutStack, AbstractContainerBox contextBox) {
		super(layoutStack, contextBox, MODE_AUTO);
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * 段組の中では、最後の手段は{@link #pageBreak}=この段組への改段である。
	 * したがって{@code column-count}を使い切ったら<b>もう断片は作れない</b>。
	 * </p>
	 *
	 * <p>
	 * <b>入れ子の段組を当てにしてはいけない</b>(2026-07-28、50,000シードの
	 * 掃過で実測)。当初は{@code findColumnBreak() != null}も真として
	 * いたが、{@code flowStack}に改段できるボックスが<b>あっても</b>
	 * {@code columnBreak()}は{@code Keep}/{@code Move}で失敗しうる。
	 * 失敗すると{@link #pageBreak}へ落ち、そこで段を使い切っていれば
	 * {@code false}が返って{@link BreakableBuilder#flush()}の
	 * {@code assert this.textBuilder != null}が発火する
	 * (strict 3件 + wild 5件。seed 2928が最小)。<b>この判定は
	 * 「試せば必ず成功する」ではなく「失敗しても壊れない」を保証する
	 * 側に倒す</b>——見送った改段のぶん内容はその場であふれるだけで、
	 * 消えることも紙面外へ飛ぶこともない。
	 * </p>
	 */
	@Override
	protected final boolean canFragmentFurther() {
		return this.contextFlow.box.canColumnBreak();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * <b>段組の中では「改ページ」は改段である。</b>ここは
	 * {@code BreakableBuilder.autoBreak()}が改段点を見つけられなかった
	 * ときの最後の手段であり、{@code contextFlow}(=段組ボックス自身)へ
	 * 無条件に段を足していた。{@link BreakableBuilder#findColumnBreak()}が
	 * {@code flowStack}しか見ないのに対し、段組のownerは
	 * {@code contextFlow}であって{@code flowStack}にいないため、
	 * <b>{@code column-count}の上限を見る場所がこの経路には1つもなかった</b>
	 * (2026-07-28)。
	 * </p>
	 *
	 * <p>
	 * 段は<b>行方向</b>に並ぶ(段{@code i}は{@code i×(段幅+段間)})。行方向は
	 * 分割できないので、段が増えるほど内容はまっすぐ紙の外へ出ていく——
	 * seed 46577では{@code column-count:4}が<b>14段</b>になり、842ptの紙に
	 * 対して y=2,835 へ描かれていた。
	 * </p>
	 *
	 * <p>
	 * <b>自動改ページのときだけ</b>上限を守る。強制改ページ
	 * ({@link ForceBreakMode})は<b>作者が段を要求した</b>ものなので数えない
	 * ——{@code ContinuationStats.guardBreakProgress}が自動改ページだけを
	 * 見張るのと同じ理由である。
	 * </p>
	 *
	 * <p>
	 * {@code false}を返す際は{@link BreakableBuilder#beginBreak()}を
	 * <b>必ず先に呼ぶ</b>。これは{@code RootBuilder.pageBreak()}が
	 * 「改ページ点なし」で{@code false}を返すときと同じ契約で、
	 * {@code breakFloats}が空になることが
	 * {@link BreakableBuilder#endFlowBlock()}の浮動体切断ループの停止条件に
	 * なっている(空にせずに{@code false}を返すと<b>無限ループ</b>する。
	 * 2026-07-28に実測)。
	 * </p>
	 */
	protected final boolean pageBreak(BreakMode mode, byte flags) {
		if (mode instanceof AutoBreakMode && !this.contextFlow.box.canColumnBreak()) {
			// 段を使い切った。ここで段を足すと行方向へ紙の外まで伸びるので、
			// 最後の段の中であふれさせる(ブラウザが column-count を
			// 使い切ったときと同じ振る舞い)
			this.beginBreak();
			return false;
		}
		int depth;
		if (this.flowStack != null && !this.flowStack.isEmpty()) {
			depth = this.flowStack.size() + 1;
		} else {
			depth = 1;
		}
		flags |= IPageBreakableBox.FLAGS_LAST;
		final double lastFrame = this.lastFrame(this.contextFlow, depth);
		return this.columnBreak(this.contextFlow, mode, flags, lastFrame, depth);
	}

	public final void finish() {
		assert this.flowStack == null || this.flowStack.isEmpty();
		this.requireNoOpenTextBuilder("(no context)");
	}
}

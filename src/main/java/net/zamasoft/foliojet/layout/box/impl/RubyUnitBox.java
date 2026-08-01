package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.GetTextStep;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.draw.AbstractDrawable;
import net.zamasoft.foliojet.layout.draw.Drawer;
import net.zamasoft.foliojet.layout.part.AbsoluteRectFrame;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.font.FontStyleImpl;
import net.zamasoft.pdfg2d.gc.paint.Color;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextImpl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;

/**
 * ルビ1単位です(注釈付きテキスト方式、2026-07-25新設——仕様裁定は
 * docs/history/2026-07-25-ruby-annotation-spec-decision.md)。
 *
 * <p>
 * 親文字列+ふりがな文字列のペアを、行内で分割不可のatomic inline
 * (インラインブロック扱い)として組みます。幅はmax(親文字幅,
 * ふりがな幅)で、狭い方は単位内で均等配置。ふりがなは親の半分の
 * フォントサイズで、横書きは行の上側、縦書きは行の右側に付きます。
 * </p>
 *
 * <p>
 * 単位の寸法(=行高への寄与)は<b>親文字のみ</b>です(2026-07-25
 * 仕様修正——F-2品質確認で「ルビを含む行だけ行送りが広がる」ことが
 * 日本語組版の原則(行送り一定、ルビは行間に置く)に反すると裁定
 * された)。ふりがなは箱の外(横書きは上端の上、縦書きは右端の右=
 * 行間の余白)へはみ出して描かれます。行間の確保はデザイナー責任
 * (ルビを使う文書はline-heightを広めに取る)。基底線は
 * {@code TextBuilder}のBLOCK経路が{@code getLastDescent()}で親文字の
 * 基底線に合わせるため、行送りは周囲のテキストと完全に一致します。
 * </p>
 *
 * <p>
 * 中身は子ボックスではなく、構築時に整形済みのグリフ列(親文字+
 * 半サイズの注釈)を自前で描画します。コンテナは空のまま
 * ({@code InlineBlockBox}の分割・finishLayout等の既存機構と衝突
 * しない)。
 * </p>
 *
 * <p>
 * {@code params.element}は<b>null</b>です。この箱はDOM要素に対応する
 * 箱ではなく、ルビ範囲の文字から合成されたものだからです。ルビ要素
 * 自身のidentity(id・ハイパーリンク・Tagged PDFロール)は、通常の
 * インラインとして残る外側の{@code InlineBox}が持ちます
 * (codex独立レビュー 2026-07-25の設計裁定(d))。rb/rt個別の
 * アンカーやPDFのRuby/RB/RT構造型は、将来の専用メタデータの課題です。
 * </p>
 */
public class RubyUnitBox extends InlineBlockBox {

	/** 単位内の均等配置で許容する最小の余りです(これ未満は配分しない)。 */
	private static final double DISTRIBUTE_EPSILON = 0.0001;

	private final TextImpl[] baseTexts;

	private final TextImpl[] rubyTexts;

	/** 親文字の色です(nullなら継承色のまま)。 */
	private final Color baseColor;

	/** ふりがなの色です(nullなら継承色のまま)。 */
	private final Color rubyColor;

	/**
	 * 書字方向です。ふりがなを置く側(=行の「上」側)の決定に使います。
	 * 縦書き({@link WritingMode#RL}/{@link WritingMode#LR})では、本エンジンは
	 * 基底線の左に descent・右に ascent を取る({@code TextBuilder}の
	 * 縦書き経路がRL/LRを同一に扱う)ため、どちらも<b>+x側</b>が文字の
	 * 上側になります。ふりがなはその上側=+x方向へ置きます。
	 */
	private final WritingMode flow;

	/** 親文字の基底線上側(縦書きは右側)の寸法です。 */
	private final double baseAscent;

	/** 親文字の基底線下側(縦書きは左側)の寸法です。 */
	private final double baseDescent;

	/**
	 * ふりがな行の基底線上側(縦書きは右側)の寸法です(ふりがな無しなら
	 * 0)。単位の寸法には算入されない(行間の余白へはみ出して描かれる)。
	 */
	private final double rubyAscent;

	/**
	 * ふりがな行の基底線下側(縦書きは左側)の寸法です(ふりがな無しなら
	 * 0)。単位の寸法には算入されない(行間の余白へはみ出して描かれる)。
	 */
	private final double rubyDescent;

	/** テキスト抽出・禁則判定用の文字列(親文字、無ければふりがな)です。 */
	private final String text;

	/**
	 * この単位が消費したソース文字の範囲です(生成内容など出所が無ければ
	 * どちらも-1)。改ページの部分再生で「単位の途中から再開しない」ことを
	 * 保証するために使います({@code AbstractTextBox.lastCharEnd()}/
	 * {@code firstCharOffset()}・配達済み終端の前進)。
	 */
	private final int sourceStart, sourceEnd;

	private RubyUnitBox(final BlockParams params, final InlinePos pos, final RubyUnitContainer container,
			final TextImpl[] baseTexts, final TextImpl[] rubyTexts, final Color baseColor, final Color rubyColor,
			final WritingMode flow, final double lineExtent, final double baseAscent, final double baseDescent,
			final double rubyAscent, final double rubyDescent, final String text, final int sourceStart,
			final int sourceEnd) {
		super(params, pos, Dimension.AUTO_DIMENSION, Dimension.ZERO_DIMENSION,
				new AbsoluteRectFrame(RectFrame.NULL_FRAME), container);
		this.baseTexts = baseTexts;
		this.rubyTexts = rubyTexts;
		this.baseColor = baseColor;
		this.rubyColor = rubyColor;
		this.flow = flow;
		this.baseAscent = baseAscent;
		this.baseDescent = baseDescent;
		this.rubyAscent = rubyAscent;
		this.rubyDescent = rubyDescent;
		this.text = text;
		this.sourceStart = sourceStart;
		this.sourceEnd = sourceEnd;
		// ページ方向の寸法=親文字のみ(仕様修正2026-07-25: 行送り一定。
		// ふりがなは箱の外——行間の余白——へはみ出して描く)
		final double pageExtent = baseDescent + baseAscent;
		if (flow.isVertical()) {
			// 縦書き: 行方向=縦、ページ方向=横。左からbaseDescent, 基底線,
			// baseAscent。ふりがな列は右端の右外
			this.width = pageExtent;
			this.height = lineExtent;
		} else {
			// 横書き: 上から親文字のascent、基底線、親文字のdescent。
			// ふりがな行は上端の上外
			this.width = lineExtent;
			this.height = pageExtent;
		}
		container.setup(baseAscent, baseDescent);
	}

	/**
	 * 常にtrueです。ルビ単位は構築時に整形済みで寸法が確定しており、
	 * shrink-to-fitの実測(ネストしたビルダー)を必要としません。
	 */
	public boolean isPreMeasured() {
		return true;
	}

	/**
	 * この単位が消費したソース文字の先頭オフセットです(無ければ-1)。
	 */
	public int getSourceStart() {
		return this.sourceStart;
	}

	/**
	 * この単位が消費したソース文字の終端(exclusive)です(無ければ-1)。
	 */
	public int getSourceEnd() {
		return this.sourceEnd;
	}

	/**
	 * ルビ単位を組み立てます。親文字・ふりがなの両方が空の場合はnullを
	 * 返します(単位を生成しない)。
	 *
	 * @param container   ルビコンテナ(ruby要素)のインラインパラメータ
	 *                    (構造的な文脈——フォント管理・書字方向・禁則——の
	 *                    出所)
	 * @param baseText    親文字列(空可)
	 * @param baseParams  親文字の書式(空のときは{@code container})
	 * @param baseOffset  親文字列のソース文字オフセット(生成内容は-1)
	 * @param rubyText    ふりがな文字列(空可)
	 * @param rubyParams  ふりがなの書式(空のときは{@code container})
	 * @param rubyOffset  ふりがな文字列のソース文字オフセット
	 * @param sourceStart 単位が消費したソースの先頭(無ければ-1)
	 * @param sourceEnd   単位が消費したソースの終端(無ければ-1)
	 */
	public static RubyUnitBox create(final InlineParams container, final String baseText, final InlineParams baseParams,
			final int baseOffset, final String rubyText, final InlineParams rubyParams, final int rubyOffset,
			final int sourceStart, final int sourceEnd) {
		if (baseText.isEmpty() && rubyText.isEmpty()) {
			return null;
		}
		final InlineParams bp = baseParams == null ? container : baseParams;
		final InlineParams rp = rubyParams == null ? container : rubyParams;
		final FontStyle baseFs = bp.fontStyle;
		final FontStyle rubyBaseFs = rp.fontStyle;
		// ふりがなは親文字の半分のサイズ。ファミリ・字面・featureはrt側の指定に従う
		final FontStyle rubyFs = new FontStyleImpl(rubyBaseFs.getFamily(), baseFs.getSize() / 2.0,
				rubyBaseFs.getStyle(), rubyBaseFs.getWeight(), rubyBaseFs.getDirection(), rubyBaseFs.getPolicy(),
				rubyBaseFs.getFeatures());

		final TextImpl[] baseTexts = shape(bp, baseFs, baseText, baseOffset);
		final TextImpl[] rubyTexts = rubyText.isEmpty() ? new TextImpl[0] : shape(rp, rubyFs, rubyText, rubyOffset);

		final double baseAdvance = totalAdvance(baseTexts);
		final double rubyAdvance = totalAdvance(rubyTexts);
		final double lineExtent = Math.max(baseAdvance, rubyAdvance);
		if (lineExtent <= 0) {
			return null;
		}
		// 狭い方を単位内で均等配置する(両端に半分ずつの余白、字間に
		// 等分の余白——旧box方式のtext-align: -cssj-justify-centerに相当)
		distribute(baseTexts, lineExtent - baseAdvance);
		distribute(rubyTexts, lineExtent - rubyAdvance);

		// ascent/descentは実際に整形されたラン(実フォント)から取る。
		// FontListMetricsのmax(フォールバックリスト全体の最大)を使うと、
		// 実フォントのmetricsで行高へ寄与する周囲テキストと僅かにずれ、
		// ルビを含む行だけ行高が微増して行送りが乱れる(2026-07-25検分で
		// 実測0.1pt差→ページ当たり行数減を確認)。
		final double baseAscent;
		final double baseDescent;
		if (baseTexts.length > 0) {
			baseAscent = maxAscent(baseTexts);
			baseDescent = maxDescent(baseTexts);
		} else {
			// 親文字が空の単位(rt先行等)はフォールバックリストの値
			final FontListMetrics baseFlm = bp.fontManager.getFontListMetrics(baseFs);
			baseAscent = baseFlm.getMaxAscent();
			baseDescent = baseFlm.getMaxDescent();
		}
		final double rubyAscent;
		final double rubyDescent;
		if (rubyTexts.length > 0) {
			rubyAscent = maxAscent(rubyTexts);
			rubyDescent = maxDescent(rubyTexts);
		} else {
			// ふりがなの無い単位(rtの後の余り等)は注釈行を持たない
			rubyAscent = rubyDescent = 0;
		}

		final BlockParams params = new BlockParams();
		// DOM要素ではない合成箱(identityは外側のruby InlineBoxが持つ)
		params.element = null;
		params.opacity = bp.opacity;
		params.fontStyle = baseFs;
		// 書式は親文字側(base)の指定に従う。禁則・言語依存の処理も
		// 親文字が主体(ふりがなは単位内に閉じて分割されない)
		params.fontManager = bp.fontManager;
		params.lineBreakRules = bp.lineBreakRules;
		params.direction = bp.direction;
		// 書字方向だけは行の文脈(=ルビコンテナ)に従う
		params.flow = container.flow;
		params.color = bp.color;
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		// 行送りは行側のline-heightに従う(TextBuilderのBLOCK経路は
		// pos.lineHeightと行のline-heightのmaxを適用する)
		params.lineHeight = 0;

		final InlinePos pos = new InlinePos();
		pos.lineHeight = 0;

		final String text = baseText.isEmpty() ? rubyText : baseText;
		return new RubyUnitBox(params, pos, new RubyUnitContainer(), baseTexts, rubyTexts, bp.color, rp.color,
				container.flow, lineExtent, baseAscent, baseDescent, rubyAscent, rubyDescent, text, sourceStart,
				sourceEnd);
	}

	/** 自己完結整形です(2026-08-01にRunCollector+TrimmedRunsへ一本化)。 */
	private static TextImpl[] shape(final InlineParams src, final FontStyle fontStyle, final String text,
			final int charOffset) {
		return net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns.shape(src.fontManager, fontStyle, text,
				charOffset, src.textSpacingTrimOff);
	}


	private static double totalAdvance(final TextImpl[] texts) {
		double advance = 0;
		for (final TextImpl text : texts) {
			advance += text.getAdvance();
		}
		return advance;
	}

	private static double maxAscent(final TextImpl[] texts) {
		double ascent = 0;
		for (final TextImpl text : texts) {
			ascent = Math.max(ascent, text.getAscent());
		}
		return ascent;
	}

	private static double maxDescent(final TextImpl[] texts) {
		double descent = 0;
		for (final TextImpl text : texts) {
			descent = Math.max(descent, text.getDescent());
		}
		return descent;
	}

	/**
	 * 狭い方の列へ余り{@code extra}を均等配分します。各グリフの手前へ
	 * 等分の余白を挿入し、先頭は半分(両端に半余白)。xadvanceは
	 * 各フォント実装がグリフの手前に適用するため({@code CIDKeyedFont}
	 * 等)、先頭グリフのxadvance=余白/2で開始位置のオフセットも兼ねる。
	 */
	private static void distribute(final TextImpl[] texts, final double extra) {
		if (extra <= DISTRIBUTE_EPSILON) {
			return;
		}
		int glyphCount = 0;
		for (final TextImpl text : texts) {
			glyphCount += text.getGlyphCount();
		}
		if (glyphCount <= 0) {
			return;
		}
		final double per = extra / glyphCount;
		boolean first = true;
		for (final TextImpl text : texts) {
			final double[] xadvances = text.getXAdvances(true);
			for (int i = 0; i < xadvances.length; ++i) {
				xadvances[i] = first ? per / 2.0 : per;
				first = false;
			}
		}
	}

	/**
	 * テキスト抽出で<b>親文字</b>を返します(ふりがなは読みの注釈であり
	 * 本文ではないため出さない——リンクの代替テキスト・string-setの
	 * content()・ブックマーク見出し・target-text()に共通の方針)。
	 * 親文字が無い単位(malformed)だけはふりがなを本文の代わりに出します。
	 *
	 * <p>
	 * コンテナ({@code FlowContainer})は空なので、抽出はこの上書きだけが
	 * 担います。
	 * </p>
	 */
	public void pushGetTextSteps(final StringBuilder textBuff, final java.util.Deque<GetTextStep> worklist) {
		textBuff.append(this.text);
	}

	public void pushDrawSteps(final PageBox pageBox, final Drawer drawer, final Visitor visitor, final Shape clip,
			AffineTransform transform, final double contextX, final double contextY, double x, double y,
			final java.util.Deque<DrawStep> worklist) {
		x += this.offsetX;
		y += this.offsetY;
		transform = this.transform(transform, x, y);
		visitor.visitBox(transform, this, drawer, x, y);
		if (this.params.opacity == 0) {
			return;
		}
		drawer.visitDrawable(new RubyUnitDrawable(pageBox, clip, transform, this), x, y);
	}

	/**
	 * 親文字グリフ列+半サイズの注釈グリフ列を描画します。(x, y)は
	 * 単位ボックスの左上です。
	 */
	protected static class RubyUnitDrawable extends AbstractDrawable {
		private final RubyUnitBox box;

		RubyUnitDrawable(final PageBox pageBox, final Shape clip, final AffineTransform transform,
				final RubyUnitBox box) {
			super(pageBox, clip, box.params.opacity, transform);
			this.box = box;
		}

		public String describe() {
			final StringBuilder base = new StringBuilder();
			for (final TextImpl text : this.box.baseTexts) {
				base.append(text.getChars(), 0, text.getCharCount());
			}
			final StringBuilder ruby = new StringBuilder();
			for (final TextImpl text : this.box.rubyTexts) {
				ruby.append(text.getChars(), 0, text.getCharCount());
			}
			return String.format(java.util.Locale.ROOT, "RubyUnit[\"%s\" ruby=\"%s\" w=%.2f h=%.2f]", base, ruby,
					this.box.getWidth(), this.box.getHeight());
		}

		public void innerDraw(final GC gc, final double x, final double y) throws GraphicsException {
			final RubyUnitBox box = this.box;
			try (final var gcState = gc.begin()) {
				if (box.flow.isVertical()) {
					// 縦書き: 親文字列は左からbaseDescentの縦基準線上。
					// ふりがな列は箱の右端(x+width)の右外=行間の余白
					// (RL/LRとも本エンジンは+x側を文字の上側に取る)
					final double baseX = x + box.baseDescent;
					this.drawRun(gc, box.baseTexts, box.baseColor, baseX, y, true);
					final double rubyX = x + box.baseDescent + box.baseAscent + box.rubyDescent;
					this.drawRun(gc, box.rubyTexts, box.rubyColor, rubyX, y, true);
				} else {
					// 横書き: 親文字行が箱の中、ふりがな行は箱の上端(y)の
					// 上外=行間の余白(基底線はy-rubyDescent)
					this.drawRun(gc, box.rubyTexts, box.rubyColor, x, y - box.rubyDescent, false);
					this.drawRun(gc, box.baseTexts, box.baseColor, x, y + box.baseAscent, false);
				}
			}
		}

		private void drawRun(final GC gc, final TextImpl[] texts, final Color color, final double x, final double y,
				final boolean vertical) throws GraphicsException {
			if (texts.length == 0) {
				return;
			}
			try (final var gcState = gc.begin()) {
				if (color != null) {
					gc.setFillPaint(color);
				}
				double xx = x, yy = y;
				for (final TextImpl text : texts) {
					gc.drawText(text, xx, yy);
					if (vertical) {
						yy += text.getAdvance();
					} else {
						xx += text.getAdvance();
					}
				}
			}
		}
	}

	/**
	 * 空のコンテナです。基底線
	 * ({@code getFirstAscent()}/{@code getLastDescent()})は単位の親文字
	 * 基底線を返します(インラインブロックの既存機構——
	 * {@code TextBuilder}のBLOCK経路——がそのまま基底線を合わせられる
	 * ように)。
	 */
	protected static class RubyUnitContainer extends FlowContainer {
		private double firstAscent, lastDescent;

		void setup(final double firstAscent, final double lastDescent) {
			this.firstAscent = firstAscent;
			this.lastDescent = lastDescent;
		}

		public double getFirstAscent() {
			return this.firstAscent;
		}

		public double getLastDescent() {
			return this.lastDescent;
		}
	}
}

package net.zamasoft.foliojet.layout.box.impl;

import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.DrawStep;
import net.zamasoft.foliojet.layout.box.content.FlowContainer;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.Dimension;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.InlinePos;
import net.zamasoft.foliojet.layout.box.params.RectFrame;
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
 * 注釈付きテキスト方式のルビ1単位です(2026-07-25新設、F-1試作。
 * {@code text.ruby=annotation}のときだけ生成される——仕様裁定は
 * docs/history/2026-07-25-ruby-annotation-spec-decision.md)。
 *
 * <p>
 * 親文字列+ふりがな文字列のペアを、行内で分割不可のatomic inline
 * (インラインブロック扱い)として組みます。幅はmax(親文字幅,
 * ふりがな幅)で、狭い方は単位内で均等配置。ふりがなは親の半分の
 * フォントサイズ・同フォントで、横書きは行の上側、縦書きは行の右側に
 * 付きます。
 * </p>
 *
 * <p>
 * 単位の寸法(=行高への寄与)は<b>親文字のみ</b>です(2026-07-25
 * 仕様修正——F-2品質確認で「ルビを含む行だけ行送りが広がる」ことが
 * 日本語組版の原則(行送り一定、ルビは行間に置く)に反すると裁定
 * された)。ふりがなは箱の外(横書きは上端の上、縦書きは右端の右=
 * 行間の余白)へはみ出して描かれます。行間の確保はデザイナー責任
 * (ルビを使う文書はline-heightを広めに取る)。祖先のoverflowクリップ
 * で切れる可能性は現行box方式の食い込み描画と同等で許容。基底線は
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
 */
public class RubyUnitBox extends InlineBlockBox {

	/** 単位内の均等配置で許容する最小の余りです(これ未満は配分しない)。 */
	private static final double DISTRIBUTE_EPSILON = 0.0001;

	private final TextImpl[] baseTexts;

	private final TextImpl[] rubyTexts;

	private final boolean vertical;

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

	private RubyUnitBox(final BlockParams params, final InlinePos pos, final RubyUnitContainer container,
			final TextImpl[] baseTexts, final TextImpl[] rubyTexts, final boolean vertical, final double lineExtent,
			final double baseAscent, final double baseDescent, final double rubyAscent, final double rubyDescent,
			final String text) {
		super(params, pos, Dimension.AUTO_DIMENSION, Dimension.ZERO_DIMENSION,
				new AbsoluteRectFrame(RectFrame.NULL_FRAME), container);
		this.baseTexts = baseTexts;
		this.rubyTexts = rubyTexts;
		this.vertical = vertical;
		this.baseAscent = baseAscent;
		this.baseDescent = baseDescent;
		this.rubyAscent = rubyAscent;
		this.rubyDescent = rubyDescent;
		this.text = text;
		// ページ方向の寸法=親文字のみ(仕様修正2026-07-25: 行送り一定。
		// ふりがなは箱の外——行間の余白——へはみ出して描く)
		final double pageExtent = baseDescent + baseAscent;
		if (vertical) {
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
	 * ルビ単位を組み立てます。親文字・ふりがなの両方が空の場合はnullを
	 * 返します(単位を生成しない)。
	 *
	 * @param src        ルビコンテナ(ruby要素)のインラインパラメータ
	 *                   (親文字は周囲のスタイルを継承する——単位内は
	 *                   この単一スタイル)
	 * @param baseText   親文字列(空可)
	 * @param baseOffset 親文字列のソース文字オフセット(生成内容は-1)
	 * @param rubyText   ふりがな文字列(空可)
	 * @param rubyOffset ふりがな文字列のソース文字オフセット
	 */
	public static RubyUnitBox create(final InlineParams src, final String baseText, final int baseOffset,
			final String rubyText, final int rubyOffset) {
		if (baseText.isEmpty() && rubyText.isEmpty()) {
			return null;
		}
		final FontStyle baseFs = src.fontStyle;
		final FontStyle rubyFs = new FontStyleImpl(baseFs.getFamily(), baseFs.getSize() / 2.0, baseFs.getStyle(),
				baseFs.getWeight(), baseFs.getDirection(), baseFs.getPolicy());

		final TextImpl[] baseTexts = shape(src, baseFs, baseText, baseOffset);
		final TextImpl[] rubyTexts = rubyText.isEmpty() ? new TextImpl[0] : shape(src, rubyFs, rubyText, rubyOffset);

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
			final FontListMetrics baseFlm = src.fontManager.getFontListMetrics(baseFs);
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
		params.element = src.element;
		params.opacity = src.opacity;
		params.fontStyle = baseFs;
		params.fontManager = src.fontManager;
		params.lineBreakRules = src.lineBreakRules;
		params.flow = src.flow;
		params.direction = src.direction;
		params.color = src.color;
		params.whiteSpace = AbstractTextParams.WHITE_SPACE_NOWRAP;
		// 行送りは行側のline-heightに従う(TextBuilderのBLOCK経路は
		// pos.lineHeightと行のline-heightのmaxを適用する)
		params.lineHeight = 0;

		final InlinePos pos = new InlinePos();
		pos.lineHeight = 0;

		final String text = baseText.isEmpty() ? rubyText : baseText;
		return new RubyUnitBox(params, pos, new RubyUnitContainer(), baseTexts, rubyTexts, src.flow.isVertical(),
				lineExtent, baseAscent, baseDescent, rubyAscent, rubyDescent, text);
	}

	/**
	 * 文字列を単一スタイルで整形し、フォントフォールバックのラン列を
	 * 返します。live構築と同じ{@code TextShaper}を使うため、字形・幅は
	 * 通常のテキストと一致します。
	 */
	private static TextImpl[] shape(final InlineParams src, final FontStyle fontStyle, final String text,
			final int charOffset) {
		if (text.isEmpty()) {
			return new TextImpl[0];
		}
		final List<TextImpl> runs = new ArrayList<>();
		final GlyphHandler collector = new GlyphHandler() {
			private TextImpl current = null;

			public void startTextRun(final int co, final FontStyle fs, final FontMetrics fm) {
				this.current = new TextImpl(co, fs, fm);
			}

			public void glyph(final int co, final char[] ch, final int coff, final byte clen, final int gid) {
				this.current.appendGlyph(ch, coff, clen, gid);
			}

			public void endTextRun() {
				if (this.current.getGlyphCount() > 0) {
					this.current.pack();
					runs.add(this.current);
				}
				this.current = null;
			}

			public void control(final TextControl control) {
				// 単位内に制御コードは流れない(組み立て時に除去済み)
			}

			public void flush() {
			}

			public void close() {
			}
		};
		final TextShaper shaper = src.fontManager.getTextShaper();
		shaper.setGlyphHandler(collector);
		shaper.fontStyle(fontStyle);
		final char[] ch = text.toCharArray();
		shaper.characters(charOffset, ch, 0, ch.length);
		shaper.close();
		return runs.toArray(new TextImpl[0]);
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
	 * 禁則判定({@code InlineBlockQuad.getString()})・テキスト抽出用に
	 * 親文字列を返します。コンテナ経由の抽出
	 * ({@code pushGetTextSteps}——{@code FlowContainer}側でfinal)は
	 * 空になるため、直接の{@code getText}呼び出しだけがこの内容を得ます
	 * (F-1の割り切り)。
	 */
	public void getText(final StringBuilder textBuff) {
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
		final int structCount = pageBox.beginStruct(drawer, this.params.element, x, y);
		drawer.visitDrawable(new RubyUnitDrawable(pageBox, clip, transform, this), x, y);
		pageBox.endStruct(drawer, this.params.element, structCount, x, y);
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
				final Color color = box.params.color;
				if (color != null) {
					gc.setFillPaint(color);
				}
				if (box.vertical) {
					// 縦書き: 親文字列は左からbaseDescentの縦基準線上。
					// ふりがな列は箱の右端(x+width)の右外=行間の余白
					final double baseX = x + box.baseDescent;
					double yy = y;
					for (final TextImpl text : box.baseTexts) {
						gc.drawText(text, baseX, yy);
						yy += text.getAdvance();
					}
					final double rubyX = x + box.baseDescent + box.baseAscent + box.rubyDescent;
					yy = y;
					for (final TextImpl text : box.rubyTexts) {
						gc.drawText(text, rubyX, yy);
						yy += text.getAdvance();
					}
				} else {
					// 横書き: 親文字行が箱の中、ふりがな行は箱の上端(y)の
					// 上外=行間の余白(基底線はy-rubyDescent)
					final double rubyY = y - box.rubyDescent;
					double xx = x;
					for (final TextImpl text : box.rubyTexts) {
						gc.drawText(text, xx, rubyY);
						xx += text.getAdvance();
					}
					final double baseY = y + box.baseAscent;
					xx = x;
					for (final TextImpl text : box.baseTexts) {
						gc.drawText(text, xx, baseY);
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

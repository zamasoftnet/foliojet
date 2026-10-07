package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.text.InlineParamsStack;

import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.css.impl.lang.CSSJTextUnitizer;
import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.AbstractReplacedBox;
import net.zamasoft.foliojet.layout.box.IAbsoluteBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBlockBox;
import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.impl.RubyUnitBox;
import net.zamasoft.foliojet.layout.box.impl.WarichuUnitBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.InlineParams;
import net.zamasoft.foliojet.layout.box.params.LayoutFontStyle;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.util.TextUtils;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.font.FontFeatureSet;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.foliojet.layout.text.Quad;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;
import net.zamasoft.pdfg2d.gc.text.layout.control.LineBreak;
import net.zamasoft.pdfg2d.gc.text.layout.control.Tab;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;

// TODO ブロックの末尾のスペースをつぶす
public class StyledTextUnitizer {

	private final Builder builder;

	private final List<AbstractTextParams> textParamsStack = new ArrayList<AbstractTextParams>();

	/**
	 * 使用する予定のInlineEndQuadのスタック。
	 */
	private final List<Quad> inlineQuadStack = new ArrayList<Quad>();

	private BuilderGlyphHandler gh;

	/**
	 * スペースのつぶし、LFコードの処理、折り返し。
	 */
	private boolean collapseSpaces, lineFeed;
	/**
	 * 直前の文字
	 */
	private char followingChar;

	/**
	 * word-spacingプロパティによるスペース幅です。
	 */
	private double wordSpacing;

	private TextShaper textShaper = null;
	private CSSJTextUnitizer textUnitizer;

	/** 縦中横の文字数が確定するまで保持する文字イベントです。 */
	private record TextCombineChars(int charOffset, char[] chars, boolean lineFeed) {
	}

	private List<TextCombineChars> textCombineChars = null;
	private int textCombineCharCount;

	/**
	 * ルビ単位バッファです(注釈付きテキスト方式、2026-07-25仕様裁定)。
	 * ルビコンテナ({@code rubyRole == RUBY_CONTAINER}のINLINE)の開始で
	 * 生成され、コンテナ終了で単位を配達して破棄されます。非null中は
	 * ルビ範囲内のインライン・文字イベントを横取りします。
	 */
	private RubyUnitCollector rubyCollector = null;

	/**
	 * ルビの親字の中の縦中横の字を渡す先(2026-10-06、jigensha の報告)。ルビの単位は字だけを持ち、縦中横の
	 * インラインブロックは捨てられるので、字が消えていた(「2ちゃんねる」が「にちゃんねる」)。縦中横の中の字を
	 * 全角にして、親字を集めている親の字の処理へ渡す(1em に詰める合成はしない近似)。
	 */
	private StyledTextUnitizer rubyTextCombineTarget = null;

	/** ルビの親字を集めている最中か。 */
	public boolean isCollectingRuby() {
		return this.rubyCollector != null;
	}

	/**
	 * この字の処理(ルビの親字の中の縦中横のインラインブロック)の字を、全角にして{@code parent}のルビの親字へ
	 * 渡します。
	 */
	public void forwardTextCombineToRuby(final StyledTextUnitizer parent) {
		this.rubyTextCombineTarget = parent;
		this.textCombineChars = null;
	}

	private WarichuCollector warichuCollector = null;

	/** 行末側の隣接文字が確定するまで張り出し判定を保留する直前のルビ。 */
	private RubyUnitBox pendingRubyEnd = null;
	private InlineQuad pendingRubyQuad = null;

	/**
	 * 末尾の張り出しが決まるまで行へ渡さずに持っておく制御です(ルビの箱と、その後のインラインの開始・終わり。
	 * 2026-10-06)。行は制御を受け取った時点の送りで長さを数えるので、渡した後で箱を広げると数え漏れ、
	 * 行に流し込むときに広げた分が次の行の余地になった(jigensha の報告: 親字より長いルビのある行に br で
	 * 続く行が、句読点を含むと約 2.5mm 長くなった)。
	 */
	private final List<Quad> pendingRubyControls = new ArrayList<Quad>();

	/** 制御を行へ渡します。ルビの箱の張り出しが決まる前なら、決まるまで持っておく。 */
	private void control(final Quad quad) {
		if (this.pendingRubyControls.isEmpty()) {
			this.textShaper.control(quad);
		} else {
			this.pendingRubyControls.add(quad);
		}
	}

	/** 持っておいた制御を順に行へ渡します。 */
	private void emitPendingRubyControls() {
		if (this.pendingRubyControls.isEmpty()) {
			return;
		}
		this.requireTextShaper();
		for (final Quad quad : this.pendingRubyControls) {
			this.textShaper.control(quad);
		}
		this.pendingRubyControls.clear();
	}

	public StyledTextUnitizer(Builder builder) {
		this.builder = builder;
	}

	private AbstractTextParams getTextParams() {
		return (AbstractTextParams) this.textParamsStack.get(this.textParamsStack.size() - 1);
	}

	public void requireTextShaper() {
		if (this.textShaper != null) {
			return;
		}
		final AbstractTextParams params = this.getTextParams();
		final InlineParamsStack inlineContext = new InlineParamsStack(params);
		final CSSJTextUnitizer textUnitizer = new CSSJTextUnitizer(inlineContext);
		final WordHyphenator wordHyphenator = new WordHyphenator(inlineContext);
		this.textUnitizer = textUnitizer;
		if (this.builder instanceof BlockBuilder blockBuilder) {
			blockBuilder.pendingText = measurement -> wordHyphenator.deliverPending(measurement, textUnitizer::deliverText);
		}
		wordHyphenator.setGlyphHandler(this.gh);
		textUnitizer.setGlyphHandler(wordHyphenator);
		this.textShaper = params.fontManager.getTextShaper();
		this.textShaper.setGlyphHandler(textUnitizer);
		this.textShaper.fontStyle(params.fontStyle);
	}

	private void changeTextState(AbstractTextParams params) {
		this.wordSpacing = params.wordSpacing;
		switch (params.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_PRE:
			this.collapseSpaces = false;
			this.lineFeed = true;
			break;

		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			this.collapseSpaces = true;
			this.lineFeed = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NORMAL:
			this.collapseSpaces = true;
			this.lineFeed = false;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			this.collapseSpaces = true;
			this.lineFeed = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			this.collapseSpaces = false;
			this.lineFeed = true;
			break;
		default:
			throw new IllegalStateException();
		}
	}

	public void startContainer() {
		this.followingChar = '\u0020';
		final BlockParams params = this.builder.getFlowBox().getBlockParams();
		this.textParamsStack.add(params);
		if (this.gh == null) {
			this.gh = new BuilderGlyphHandler(builder);
		} else {
			if (this.textParamsStack.size() > 1) {
				this.gh.startTextBox(params);
			} else {
				this.gh.updateText();
			}
		}
		this.changeTextState(params);
		if (params.textCombine == net.zamasoft.foliojet.css.value.TextCombineValue.ALL) {
			// hwid/twid/qwidの選択にはrun全体の文字数が必要なので、
			// SAXの文字イベント境界を越えてコンテナ終端まで保持する。
			this.textCombineChars = new ArrayList<TextCombineChars>();
			this.textCombineCharCount = 0;
		}
	}

	/**
	 * 配達済みソース文字の終端オフセットを返します(M6b v3)。
	 */
	public int getDeliveredCharEnd() {
		return this.gh == null ? 0 : this.gh.getDeliveredCharEnd();
	}

	public void flushText() {
		if (this.textCombineChars != null) {
			return;
		}
		if (this.textShaper != null) {
			// 張り出しはこの後の字で決める(従来どおり)。持っておいた制御だけ先に渡す
			this.emitPendingRubyControls();
			this.textShaper.flush();
		}
	}

	public void endContainer() {
		this.emitTextCombineChars();
		if (this.warichuCollector != null) {
			this.warichuCollector.drain();
		}
		if (this.rubyCollector != null) {
			// 防御: コンテナが閉じる前にルビが閉じていない(malformed——
			// ルビの中にブロックが現れた等)場合は、たまっている分を
			// その場で配達する。ただしコレクタは<b>捨てない</b>——
			// 深さの追跡を続けないと、内側のインラインの終了が通常の
			// インラインスタックを誤popしてスタックを壊す
			this.rubyCollector.drain();
		}
		this.resolvePendingRubyEnd(false);
		final AbstractTextParams params = (AbstractTextParams) this.textParamsStack
				.remove(this.textParamsStack.size() - 1);
		if (this.textShaper != null) {
			this.textShaper.close();
			this.textShaper = null;
			if (this.builder instanceof BlockBuilder blockBuilder) {
				blockBuilder.pendingText = measurement -> { };
			}
			this.gh.builder.endTextBlock();
		}
		if (this.textParamsStack.size() >= 1) {
			this.gh.endTextBox();
		}
	}

	public void startInline(InlineBox inlineBox) {
		this.disableTextCombineWidthVariant();
		final InlineParams inlineParams = inlineBox.getInlineParams();
		if (this.warichuCollector != null) {
			this.warichuCollector.startInline(inlineParams);
			return;
		}
		if (this.rubyCollector != null) {
			// ルビ範囲内のマークアップは箱にせず、深さとスタイルだけを数える
			// (仕様: ルビ内は文字のみ)
			this.rubyCollector.startInline(inlineParams);
			return;
		}
		AbstractContainerBox containerBox = this.gh.builder.getFlowBox();
		inlineBox.firstPassLayout(containerBox);
		this.requireTextShaper();

		Quad end = InlineQuad.createInlineBoxEndQuad(inlineBox);
		this.inlineQuadStack.add(end);
		AbstractTextParams params = inlineBox.getInlineParams();
		this.textParamsStack.add(params);
		this.textShaper.fontStyle(params.fontStyle);
		Quad start = InlineQuad.createInlineBoxStartQuad(inlineBox);
		this.control(start);
		this.changeTextState(params);

		if (inlineParams.rubyRole == AbstractTextParams.RUBY_CONTAINER) {
			// ルビコンテナ(ruby要素)は通常のインラインとして残したうえで
			// (idやハイパーリンク等のidentityはこのInlineBoxが持つ)、
			// 以降の内側の文字を単位バッファへ横取りする。設計裁定(d)
			// ——codex独立レビュー2026-07-25
			this.rubyCollector = new RubyUnitCollector(inlineParams,
					(base, ruby) -> this.emitRubyUnit(inlineParams, base, ruby));
		} else if (inlineParams.warichu) {
			this.warichuCollector = new WarichuCollector(inlineParams,
					segment -> this.emitWarichu(inlineParams, segment));
		}
	}

	public void endInline() {
		if (this.warichuCollector != null) {
			if (!this.warichuCollector.endInline()) {
				return;
			}
			this.warichuCollector = null;
		} else if (this.rubyCollector != null) {
			if (!this.rubyCollector.endInline()) {
				return;
			}
			// ルビコンテナの終了: 残りの単位は配達済み。以降は通常の
			// インライン終了処理(ruby要素のInlineBoxを閉じる)
			this.rubyCollector = null;
		}
		// ブロックでインラインが寸断されて復帰した直後にインラインが終わるときに、ここを実行する
		this.requireTextShaper();

		Quad end = (InlineEndQuad) this.inlineQuadStack.remove(this.inlineQuadStack.size() - 1);
		this.control(end);
		this.textParamsStack.remove(this.textParamsStack.size() - 1);
		AbstractTextParams params = this.getTextParams();
		this.textShaper.fontStyle(params.fontStyle);
		this.changeTextState(params);
	}

	public void addInlineReplaced(AbstractReplacedBox inlineReplacedBox) {
		this.disableTextCombineWidthVariant();
		if (this.warichuCollector != null || this.rubyCollector != null) {
			// ルビ単位内は文字のみ(仕様)——置換要素は捨てる(F-1)
			return;
		}
		this.resolvePendingRubyEnd(false);
		this.requireTextShaper();
		Quad quad = InlineQuad.createReplacedBoxQuad(inlineReplacedBox);
		this.textShaper.control(quad);
		this.followingChar = 'x';
	}

	public void addInlineBlock(InlineBlockBox inlineBlockBox) {
		this.disableTextCombineWidthVariant();
		if (this.warichuCollector != null || this.rubyCollector != null) {
			// ルビ単位内は文字のみ(仕様)——インラインブロックは捨てる(F-1)
			return;
		}
		this.resolvePendingRubyEnd(false);
		this.requireTextShaper();
		final Quad quad = InlineQuad.createInlineBlockBoxQuad(inlineBlockBox);
		this.textShaper.control(quad);
		this.followingChar = 'x';
	}

	public void addInlineAbsolute(final IAbsoluteBox absoluteBox) {
		this.disableTextCombineWidthVariant();
		if (this.warichuCollector != null || this.rubyCollector != null) {
			// ルビ単位内は文字のみ(仕様)——絶対配置は捨てる(F-1)
			return;
		}
		this.resolvePendingRubyEnd(false);
		this.requireTextShaper();
		final Quad quad = InlineQuad.createInlineAbsoluteBoxQuad(absoluteBox);
		this.textShaper.control(quad);
	}

	/**
	 * {@code leader()}を配達します(leader() L1——
	 * consult-codex-2026-07-31-leader.txt)。パターンを現在のスタイルで
	 * 自己完結shapeし({@code RubyUnitBox.shape}と同型)、可変幅の
	 * {@link net.zamasoft.foliojet.layout.text.LeaderQuad}を制御として
	 * 流す。駆動のたびに新規生成する(記録再生間で割り付け幅を共有
	 * しない)。
	 */
	public void leader(final String pattern) {
		this.disableTextCombineWidthVariant();
		if (this.warichuCollector != null || this.rubyCollector != null) {
			// ルビ単位内は文字のみ(仕様)
			return;
		}
		this.resolvePendingRubyEnd(false);
		this.requireTextShaper();
		final AbstractTextParams params = this.getTextParams();
		// 一本化(2026-08-01): 自己完結shapeはRunCollector+TrimmedRunsへ
		final net.zamasoft.pdfg2d.gc.text.TextImpl[] runs = net.zamasoft.foliojet.layout.text.spacing.TrimmedRuns
				.shape(params.fontManager, params.fontStyle, pattern, -1, false);
		if (runs.length == 0) {
			// どのフォントにもグリフがない——埋め物なし
			return;
		}
		this.textShaper.control(new net.zamasoft.foliojet.layout.text.LeaderQuad(runs));
		this.followingChar = 'x';
	}



	/**
	 * 対応がついたルビ単位を1つ、atomic inline({@code RubyUnitBox}を
	 * インラインブロック扱いのquadに載せる)として下流へ配達します
	 * (2026-07-25、注釈付きテキスト方式)。
	 */
	private void emitRubyUnit(final InlineParams container, final RubyUnitCollector.Segment base,
			final List<RubyUnitCollector.Annotation> rubies) {
		// ルビ同士は注釈が同じ行間を占めるため、境界で相互に張り出さない。
		this.resolvePendingRubyEnd(false);
		final String baseText = base == null ? "" : base.text();
		int sourceStart = -1, sourceEnd = -1;
		if (base != null && base.charOffset() >= 0) {
			sourceStart = base.charOffset();
			sourceEnd = base.charEnd();
		}
		final List<RubyUnitBox.AnnotationInput> annotations = new ArrayList<RubyUnitBox.AnnotationInput>();
		for (final RubyUnitCollector.Annotation ruby : rubies) {
			final RubyUnitCollector.Segment segment = ruby.segment();
			annotations.add(new RubyUnitBox.AnnotationInput(segment.text(), segment.params(), segment.charOffset(),
					ruby.level()));
			if (segment.charOffset() >= 0) {
				sourceStart = sourceStart < 0 ? segment.charOffset() : Math.min(sourceStart, segment.charOffset());
				sourceEnd = Math.max(sourceEnd, segment.charEnd());
			}
		}
		final RubyUnitBox box = RubyUnitBox.create(container, baseText, base == null ? null : base.params(),
				base == null ? -1 : base.charOffset(), annotations, sourceStart, sourceEnd);
		if (box == null) {
			return;
		}
		if (!isSafeRubyOverhangNeighbor(this.followingChar)) {
			box.reserveStartOverhang();
		}
		this.requireTextShaper();
		final InlineQuad quad = InlineQuad.createInlineBlockBoxQuad(box);
		// 末尾の張り出しが決まるまで行へ渡さない(resolvePendingRubyEnd)
		this.pendingRubyControls.add(quad);
		this.pendingRubyEnd = box;
		this.pendingRubyQuad = quad;
		this.followingChar = 'x';
	}

	private void emitWarichu(final InlineParams container, final WarichuCollector.Segment segment) {
		this.resolvePendingRubyEnd(false);
		final List<WarichuUnitBox> boxes = WarichuUnitBox.createFragments(container, segment.text(), segment.params(),
				segment.sourceStart(), segment.sourceStart(), segment.sourceEnd());
		if (boxes.isEmpty()) {
			return;
		}
		this.requireTextShaper();
		for (final WarichuUnitBox box : boxes) {
			this.textShaper.control(InlineQuad.createInlineBlockBoxQuad(box));
		}
		this.followingChar = 'x';
	}

	public void characters(int charOffset, char[] ch, final int off, final int len, boolean lineFeed) {
		assert len > 0;
		if (this.rubyTextCombineTarget != null && this.rubyTextCombineTarget.rubyCollector != null) {
			final char[] wide = new char[len];
			for (int i = 0; i < len; ++i) {
				wide[i] = TextTransforms.fullWidth(ch[off + i]);
			}
			this.rubyTextCombineTarget.rubyCollector.characters(charOffset, wide, 0, len);
			return;
		}
		if (this.textCombineChars != null) {
			final char[] copy = java.util.Arrays.copyOfRange(ch, off, off + len);
			this.textCombineChars.add(new TextCombineChars(charOffset, copy, lineFeed));
			this.textCombineCharCount += Character.codePointCount(copy, 0, copy.length);
			if (this.textCombineCharCount > 4) {
				this.disableTextCombineWidthVariant();
			}
			return;
		}
		if (this.warichuCollector != null) {
			this.warichuCollector.characters(charOffset, ch, off, len);
			return;
		}
		if (this.rubyCollector != null) {
			// ルビ範囲内の文字は単位バッファへためる(F-1)
			this.rubyCollector.characters(charOffset, ch, off, len);
			return;
		}
		this.resolvePendingRubyEnd(isSafeRubyOverhangNeighbor(Character.codePointAt(ch, off, off + len)));
		final AbstractTextParams params = this.getTextParams();

		// テキスト処理
		int ooff = 0;
		FontListMetrics flm = params.getFontListMetrics();
		for (int i = 0; i < len; ++i) {
			char c = ch[i + off];
			if (TextUtils.isControl(c)) {
				TextControl quad = null;
				switch (c) {
				case '\n':
					// 改行コード
					if (lineFeed || this.lineFeed) {
						quad = new LineBreak(flm, charOffset + i);
					} else if (this.collapseSpaces) {
						UnicodeBlock block = UnicodeBlock.of(this.followingChar);
						if (block == UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
								|| block == UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS || block == UnicodeBlock.HIRAGANA
								|| block == UnicodeBlock.KATAKANA) {
							// 1文字削除
							if (i > ooff) {
								this._characters(charOffset + ooff, ch, off + ooff, i - ooff);
							}
							ooff = i + 1;
							continue;
						}
					}
					break;
				case '\t':
					// タブ文字
					if (!this.collapseSpaces) {
						quad = new Tab(flm, charOffset + i);
					}
					break;
				}
				if (quad != null) {
					// 1文字削除
					if (i > ooff) {
						this._characters(charOffset + ooff, ch, off + ooff, i - ooff);
					}
					ooff = i + 1;
					this.requireTextShaper();
					this.textShaper.control(quad);
					this.followingChar = c;
					continue;
				}
				// 空白に変換
				c = '\u0020';
			}
			if (c == '\u0020') {
				// 1文字削除
				if (i > ooff) {
					this._characters(charOffset + ooff, ch, off + ooff, i - ooff);
				}
				ooff = i + 1;
				if (this.followingChar != '\u0020' || !this.collapseSpaces) {
					// スペースの出力
					WhiteSpace ws = new WhiteSpace(flm, charOffset + i);
					ws.setWordSpacing(this.wordSpacing);
					this.requireTextShaper();
					this.textShaper.control(ws);
				}
				this.followingChar = c;
				continue;
			}
			if (c == '\u00AD') {
				// ソフトハイフンは字形化せず分割機会のマーカーに変換する
				// 1文字削除
				if (i > ooff) {
					this._characters(charOffset + ooff, ch, off + ooff, i - ooff);
				}
				ooff = i + 1;
				if (params.hyphens != AbstractTextParams.HYPHENS_NONE) {
					this.requireTextShaper();
					this.textShaper.control(new WordHyphenator.Marker(charOffset + i));
				}
				this.followingChar = c;
				continue;
			}
			this.followingChar = c;
			ch[i + off] = c;
		}
		if (len > ooff) {
			this._characters(charOffset + ooff, ch, off + ooff, len - ooff);
		}
	}

	private void resolvePendingRubyEnd(final boolean safeNeighbor) {
		if (this.pendingRubyEnd == null) {
			return;
		}
		if (!safeNeighbor) {
			this.pendingRubyEnd.reserveEndOverhang();
			// BuilderGlyphHandlerはcontrol受理時にadvanceを写す。後から箱の
			// 幅を戻した場合も、同じquadの送りを同期しないと描画だけ広がる。
			this.pendingRubyQuad.advance = this.pendingRubyEnd
					.getLineExtent(this.pendingRubyEnd.getBlockParams().flow);
		}
		this.emitPendingRubyControls();
		this.pendingRubyEnd = null;
		this.pendingRubyQuad = null;
	}

	/**
	 * JLREQの張り出し対象を安全側に限定する。仮名・漢字は字面が親文字側に
	 * あり注釈行と衝突しないが、欧文・約物・別のルビ箱は予約する。
	 */
	private static boolean isSafeRubyOverhangNeighbor(final int codePoint) {
		final Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
		return script == Character.UnicodeScript.HAN || script == Character.UnicodeScript.HIRAGANA
				|| script == Character.UnicodeScript.KATAKANA;
	}

	private void _characters(int charOffset, char[] ch, int off, int len) {
		TextTransforms.apply(this.getTextParams().textTransform, ch, off, len);
		this.requireTextShaper();
		this.textUnitizer.characters(this.textShaper, charOffset, ch, off, len);
	}

	/**
	 * 縦中横の文字数に対応するOpenType幅字形を優先して字形化します。
	 * フォントがfeatureを持たない場合は送りが変わらないため、後段の
	 * {@code compressTextCombine}が従来どおり1emへ圧縮します。
	 */
	private void emitTextCombineChars() {
		if (this.textCombineChars == null) {
			return;
		}
		final List<TextCombineChars> chars = this.textCombineChars;
		this.textCombineChars = null;
		if (chars.isEmpty()) {
			return;
		}
		this.requireTextShaper();
		this.textShaper.fontStyle(textCombineFontStyle(this.getTextParams().fontStyle, this.textCombineCharCount));
		for (final TextCombineChars text : chars) {
			this.characters(text.charOffset, text.chars, 0, text.chars.length, text.lineFeed);
		}
	}

	/** 複雑な子要素を含む縦中横は従来のアフィン圧縮へ戻します。 */
	private void disableTextCombineWidthVariant() {
		if (this.textCombineChars == null) {
			return;
		}
		final List<TextCombineChars> chars = this.textCombineChars;
		this.textCombineChars = null;
		for (final TextCombineChars text : chars) {
			this.characters(text.charOffset, text.chars, 0, text.chars.length, text.lineFeed);
		}
	}

	/** 文字数に対応する幅字形を追加した縦中横用スタイルを構築します。 */
	static FontStyle textCombineFontStyle(final FontStyle base, final int charCount) {
		final String tag = switch (charCount) {
		case 2 -> "hwid";
		case 3 -> "twid";
		case 4 -> "qwid";
		default -> null;
		};
		if (tag == null) {
			return base;
		}
		final FontFeatureSet width = FontFeatureSet.of(new int[] { FontFeatureSet.packTag(tag) }, new int[] { 1 });
		return LayoutFontStyle.withFeatures(base, base.getFeatures().override(width));
	}

}

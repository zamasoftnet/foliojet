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
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.util.TextUtils;
import net.zamasoft.pdfg2d.gc.font.FontListMetrics;
import net.zamasoft.pdfg2d.gc.text.FilterGlyphHandler;
import net.zamasoft.foliojet.layout.text.Quad;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.TextShaper;
import net.zamasoft.pdfg2d.gc.text.layout.control.LineBreak;
import net.zamasoft.pdfg2d.gc.text.layout.control.Tab;
import net.zamasoft.pdfg2d.gc.text.layout.control.WhiteSpace;

// TODO ブロックの末尾のスペースをつぶす
public class StyledTextUnitizer {
	private static final boolean DEBUG = false;

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

	/**
	 * 注釈付きテキスト方式のルビ単位バッファです(2026-07-25、F-1試作)。
	 * ルビコンテナ({@code rubyRole == RUBY_CONTAINER}のINLINE)の開始で
	 * 生成され、コンテナ終了で単位を配達して破棄されます。非null中は
	 * ルビ範囲内のインライン・文字イベントを横取りします。既定
	 * ({@code text.ruby=box})ではrubyRoleが立たないため常にnullで
	 * 挙動不変です。
	 */
	private RubyUnitCollector rubyCollector = null;

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
		final FilterGlyphHandler textUnitizer = new CSSJTextUnitizer(inlineContext);
		final FilterGlyphHandler wordHyphenator = new WordHyphenator(inlineContext);
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
		if (DEBUG) {
			System.out.println(this.textParamsStack.size() + "/startContainer|" + params.element);
		}
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
	}

	/**
	 * 配達済みソース文字の終端オフセットを返します(M6b v3)。
	 */
	public int getDeliveredCharEnd() {
		return this.gh == null ? 0 : this.gh.getDeliveredCharEnd();
	}

	public void flushText() {
		if (this.textShaper != null) {
			this.textShaper.flush();
		}
	}

	public void endContainer() {
		if (this.rubyCollector != null) {
			// 防御: コンテナが閉じる前にルビが閉じていない(malformed)場合は
			// たまった単位をその場で配達して混線を避ける(F-1)
			final RubyUnitCollector collector = this.rubyCollector;
			this.rubyCollector = null;
			this.emitRubyUnits(collector);
		}
		final AbstractTextParams params = (AbstractTextParams) this.textParamsStack
				.remove(this.textParamsStack.size() - 1);
		if (this.textShaper != null) {
			this.textShaper.close();
			this.textShaper = null;
			this.gh.builder.endTextBlock();
		}
		if (DEBUG) {
			System.out.println(this.textParamsStack.size() + "/endContainer|" + params.element);
		}
		if (this.textParamsStack.size() >= 1) {
			this.gh.endTextBox();
		}
	}

	public void startInline(InlineBox inlineBox) {
		final AbstractTextParams inlineParams = inlineBox.getInlineParams();
		if (this.rubyCollector != null) {
			// ルビ範囲内のマークアップは無視して深さだけ数える(F-1)
			this.rubyCollector.startInline(inlineParams.rubyRole);
			return;
		}
		if (inlineParams.rubyRole == AbstractTextParams.RUBY_CONTAINER) {
			// ルビコンテナの開始: 以降の文字を単位バッファへためる。
			// コンテナ自身のインラインボックスは行へ流さない(単位が
			// 内容を丸ごと置き換える)
			this.rubyCollector = new RubyUnitCollector((net.zamasoft.foliojet.layout.box.params.InlineParams) inlineParams);
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
		this.textShaper.control(start);
		this.changeTextState(params);
	}

	public void endInline() {
		if (this.rubyCollector != null) {
			if (this.rubyCollector.endInline()) {
				// ルビコンテナの終了: 確定した単位を配達する
				final RubyUnitCollector collector = this.rubyCollector;
				this.rubyCollector = null;
				this.emitRubyUnits(collector);
			}
			return;
		}
		// ブロックでインラインが寸断されて復帰した直後にインラインが終わるときに、ここを実行する
		this.requireTextShaper();

		Quad end = (InlineEndQuad) this.inlineQuadStack.remove(this.inlineQuadStack.size() - 1);
		this.textShaper.control(end);
		this.textParamsStack.remove(this.textParamsStack.size() - 1);
		AbstractTextParams params = this.getTextParams();
		this.textShaper.fontStyle(params.fontStyle);
		this.changeTextState(params);
	}

	public void addInlineReplaced(AbstractReplacedBox inlineReplacedBox) {
		if (this.rubyCollector != null) {
			// ルビ単位内は文字のみ(仕様)——置換要素は捨てる(F-1)
			return;
		}
		this.requireTextShaper();
		Quad quad = InlineQuad.createReplacedBoxQuad(inlineReplacedBox);
		this.textShaper.control(quad);
		this.followingChar = 'x';
	}

	public void addInlineBlock(InlineBlockBox inlineBlockBox) {
		if (this.rubyCollector != null) {
			// ルビ単位内は文字のみ(仕様)——インラインブロックは捨てる(F-1)
			return;
		}
		this.requireTextShaper();
		final Quad quad = InlineQuad.createInlineBlockBoxQuad(inlineBlockBox);
		this.textShaper.control(quad);
		this.followingChar = 'x';
	}

	public void addInlineAbsolute(final IAbsoluteBox absoluteBox) {
		if (this.rubyCollector != null) {
			// ルビ単位内は文字のみ(仕様)——絶対配置は捨てる(F-1)
			return;
		}
		this.requireTextShaper();
		final Quad quad = InlineQuad.createInlineAbsoluteBoxQuad(absoluteBox);
		this.textShaper.control(quad);
	}

	/**
	 * ルビコンテナ1個分の確定済み単位列を、atomic inline
	 * ({@code RubyUnitBox}をインラインブロック扱いのquadに載せる)として
	 * 下流へ配達します(2026-07-25、F-1)。
	 */
	private void emitRubyUnits(final RubyUnitCollector collector) {
		for (final RubyUnitCollector.PendingUnit unit : collector.units()) {
			final net.zamasoft.foliojet.layout.box.impl.RubyUnitBox box = net.zamasoft.foliojet.layout.box.impl.RubyUnitBox
					.create(collector.containerParams(), unit.baseText(), unit.baseOffset(), unit.rubyText(),
							unit.rubyOffset());
			if (box == null) {
				continue;
			}
			this.requireTextShaper();
			this.textShaper.control(InlineQuad.createInlineBlockBoxQuad(box));
			this.followingChar = 'x';
		}
	}

	public void characters(int charOffset, char[] ch, final int off, final int len, boolean lineFeed) {
		assert len > 0;
		if (this.rubyCollector != null) {
			// ルビ範囲内の文字は単位バッファへためる(F-1)
			this.rubyCollector.characters(charOffset, ch, off, len);
			return;
		}
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

	private void _characters(int charOffset, char[] ch, int off, int len) {
		switch (this.getTextParams().textTransform) {
		case AbstractTextParams.TEXT_TRANSFORM_LOWERCASE:
			for (int i = 0; i < len; ++i) {
				char c = ch[i + off];
				ch[i + off] = Character.toLowerCase(c);
			}
			break;
		case AbstractTextParams.TEXT_TRANSFORM_UPPERCASE:
			for (int i = 0; i < len; ++i) {
				char c = ch[i + off];
				ch[i + off] = Character.toUpperCase(c);
			}
			break;
		case AbstractTextParams.TEXT_TRANSFORM_CAPITALIZE:
			boolean spaceBefore = true;
			for (int i = 0; i < len; ++i) {
				char c = ch[i + off];
				if (Character.isLetter(c)) {
					if (spaceBefore) {
						ch[i + off] = Character.toUpperCase(c);
					}
					spaceBefore = false;
				} else {
					spaceBefore = true;
				}
			}
			break;
		case AbstractTextParams.TEXT_TRANSFORM_NONE:
			break;
		default:
			throw new IllegalStateException();
		}
		this.requireTextShaper();
		this.textShaper.characters(charOffset, ch, off, len);
	}

}

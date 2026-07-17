package net.zamasoft.foliojet.layout.builder.impl;

import net.zamasoft.foliojet.layout.box.params.WritingMode;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.impl.InlineBox;
import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.builder.Builder;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.util.LayoutUtils;
import net.zamasoft.pdfg2d.gc.font.FontMetrics;
import net.zamasoft.pdfg2d.gc.font.FontStyle;
import net.zamasoft.pdfg2d.gc.text.GlyphHandler;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.pdfg2d.gc.text.layout.control.Control;
import net.zamasoft.pdfg2d.gc.text.layout.control.SoftHyphen;

/**
 * 改行を処理し、インラインボックスの幅を確定します。
 * 
 * @author MIYABE Tatsuhiko
 * @version $Id: BuilderGlyphHandler.java 1593 2019-12-03 07:02:17Z miyabe $
 */
public class BuilderGlyphHandler implements GlyphHandler {
	private static final boolean DEBUG = false;

	final Builder builder;

	private List<AbstractTextParams> textParamsStack = null;

	/**
	 * 次のインラインまたはテキストの追加で改行することを示すフラグ。
	 */
	private boolean toLineFeed = false, wrap;
	private WritingMode progression;

	public BuilderGlyphHandler(Builder builder) {
		this.builder = builder;
		this.changeTextState(this.builder.getFlowBox().getBlockParams());
	}

	public void startTextBox(AbstractTextParams params) {
		if (this.textParamsStack == null) {
			this.textParamsStack = new ArrayList<AbstractTextParams>();
		}
		this.textParamsStack.add(params);
		this.changeTextState(params);
		if (DEBUG) {
			System.out.println(this.textParamsStack.size() + "/start|" + params.element);
		}
	}

	public void endTextBox() {
		AbstractTextParams params = (AbstractTextParams) this.textParamsStack.remove(this.textParamsStack.size() - 1);
		if (DEBUG) {
			System.out.println(this.textParamsStack.size() + "/end|" + params.element);
		}
		if (this.textParamsStack.isEmpty()) {
			params = this.builder.getFlowBox().getBlockParams();
		} else {
			params = (AbstractTextParams) this.textParamsStack.get(this.textParamsStack.size() - 1);
		}
		this.changeTextState(params);
	}

	public void updateText() {
		AbstractTextParams params = this.builder.getFlowBox().getBlockParams();
		this.changeTextState(params);
	}

	private void changeTextState(AbstractTextParams params) {
		switch (params.whiteSpace) {
		case AbstractTextParams.WHITE_SPACE_PRE:
			this.wrap = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NOWRAP:
			this.wrap = false;
			break;

		case AbstractTextParams.WHITE_SPACE_NORMAL:
			this.wrap = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_LINE:
			this.wrap = true;
			break;

		case AbstractTextParams.WHITE_SPACE_PRE_WRAP:
			this.wrap = true;
			break;
		default:
			throw new IllegalStateException();
		}
		this.progression = params.flow;
	}

	public void startTextRun(final int charOffset, final FontStyle fontStyle, final FontMetrics fontMetrics) {
		this.journal.run(charOffset);
		this.builder.startTextRun(charOffset, fontStyle, fontMetrics);
	}

	/**
	 * これまでに配達された最後のソース文字の終端オフセットです(M6b v3)。
	 * shaper 内に保留中(未配達)の文字はこれ以降にあり、切断段落の
	 * 尾部再生はここで打ち切ることで live パイプラインとの二重供給を防ぐ。
	 *
	 * <p>
	 * 注意: この値は {@link #glyph} でしか進まない — 最後のグリフの後に
	 * 配達された control(空白・改行・SoftHyphen)や inline quad は
	 * 反映されない。「正規化イベントの配達境界」としては不完全であり、
	 * open 段落の接合キーには使えない(C3 完遂形は正規化イベント列の
	 * 値渡し — ARCHITECTURE §5.9、codex 相談 2026-07-17)。
	 * </p>
	 */
	private int deliveredCharEnd = 0;

	/**
	 * 境界イベントの shadow journal です(M3b Phase 0。挙動不変の観測)。
	 */
	private final TextEventJournal journal = new TextEventJournal();

	/**
	 * 境界イベントの journal を返します(M3b Phase 0)。
	 */
	public TextEventJournal getJournal() {
		return this.journal;
	}

	/**
	 * 配達済みソース文字の終端オフセットを返します(M6b v3)。
	 */
	public int getDeliveredCharEnd() {
		return this.deliveredCharEnd;
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
		this.journal.glyph(charOffset, charOffset + clen);
		if (charOffset >= 0) {
			this.deliveredCharEnd = Math.max(this.deliveredCharEnd, charOffset + clen);
		}
		// System.out.print(new String(ch, coff, clen));
		this.builder.glyph(charOffset, ch, coff, clen, gid);
	}

	public void endTextRun() {
		this.builder.endTextRun();
	}

	public void control(final TextControl quad) {
		// System.out.println(quad);
		if (quad instanceof InlineQuad) {
			// インラインボックス
			this.journal.inline();
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				// インライン開始
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				final InlineBox inlineBox = inlineStartQuad.box;

				inlineStartQuad.advance = inlineStartQuad.box.getFrame().getFrameLineStart(this.progression);

				AbstractTextParams params = inlineBox.getTextParams();
				this.startTextBox(params);
			}
				break;

			case InlineQuad.INLINE_END:
				// インライン終了
				InlineEndQuad inlineEndQuad = (InlineEndQuad) inlineQuad;
				this.endTextBox();

				inlineEndQuad.advance = inlineEndQuad.box.getFrame().getFrameLineEnd(this.progression);
				break;

			case InlineQuad.INLINE_REPLACED: {
				// 置換可能なインライン
				InlineReplacedQuad inlineReplacedQuad = (InlineReplacedQuad) inlineQuad;
				LayoutUtils.calculateReplacedSize(this.builder, inlineReplacedQuad.box);
				inlineReplacedQuad.advance = inlineReplacedQuad.box.getLineExtent(this.progression);
			}
				break;

			case InlineQuad.INLINE_BLOCK:
				// インラインブロック
				inlineQuad.advance = inlineQuad.getBox().getLineExtent(this.progression);
				break;

			case InlineQuad.INLINE_ABSOLUTE:
				// 絶対配置
				break;
			default:
				throw new IllegalStateException();
			}
		} else {
			// 制御コード
			Control control = (Control) quad;
			this.journal.control(control.getCharOffset());
			switch (control.getControlChar()) {
			case '\n':
				this.toLineFeed = true;
				break;

			case '\t':
			case '\u0020':
			case SoftHyphen.CHAR:
				break;

			default:
				throw new IllegalStateException();
			}
		}
		this.builder.control(quad);
	}

	public void flush() {
		//System.err.println("BGH FLUSH: "+this.wrap);
		if (!this.wrap && !this.toLineFeed) {
			return;
		}
		this.toLineFeed = false;
		this.journal.flush();
		this.builder.flush();
	}
	
	public void close() {
		this.builder.flush();
	}
}

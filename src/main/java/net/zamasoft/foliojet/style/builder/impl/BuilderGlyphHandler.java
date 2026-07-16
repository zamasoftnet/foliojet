package net.zamasoft.foliojet.style.builder.impl;

import net.zamasoft.foliojet.style.box.params.WritingMode;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.style.box.impl.InlineBox;
import net.zamasoft.foliojet.style.box.params.AbstractTextParams;
import net.zamasoft.foliojet.style.builder.Builder;
import net.zamasoft.foliojet.style.builder.InlineQuad;
import net.zamasoft.foliojet.style.builder.InlineQuad.InlineEndQuad;
import net.zamasoft.foliojet.style.builder.InlineQuad.InlineReplacedQuad;
import net.zamasoft.foliojet.style.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.style.util.StyleUtils;
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
		this.builder.startTextRun(charOffset, fontStyle, fontMetrics);
	}

	public void glyph(int charOffset, char[] ch, int coff, byte clen, int gid) {
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
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				// インライン開始
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				final InlineBox inlineBox = inlineStartQuad.box;

				final double startAdvance;
				switch (this.progression) {
				case WritingMode.TB:
					// 横書き
					startAdvance = inlineStartQuad.box.getFrame().getFrameLeft();
					break;
				case WritingMode.LR:
				case WritingMode.RL:
					// 縦書き
					startAdvance = inlineStartQuad.box.getFrame().getFrameTop();
					break;
				default:
					throw new IllegalStateException();
				}
				inlineStartQuad.advance = startAdvance;

				AbstractTextParams params = inlineBox.getTextParams();
				this.startTextBox(params);
			}
				break;

			case InlineQuad.INLINE_END:
				// インライン終了
				InlineEndQuad inlineEndQuad = (InlineEndQuad) inlineQuad;
				this.endTextBox();

				double endAdvance;
				switch (this.progression) {
				case WritingMode.TB:
					// 横書き
					endAdvance = inlineEndQuad.box.getFrame().getFrameRight();
					break;
				case WritingMode.LR:
				case WritingMode.RL:
					// 縦書き
					endAdvance = inlineEndQuad.box.getFrame().getFrameBottom();
					break;
				default:
					throw new IllegalStateException();
				}
				inlineEndQuad.advance = endAdvance;
				break;

			case InlineQuad.INLINE_REPLACED: {
				// 置換可能なインライン
				InlineReplacedQuad inlineReplacedQuad = (InlineReplacedQuad) inlineQuad;
				StyleUtils.calclateReplacedSize(this.builder, inlineReplacedQuad.box);
				double advance;
				switch (this.progression) {
				case WritingMode.TB:
					// 横書き
					advance = inlineReplacedQuad.box.getWidth();
					break;
				case WritingMode.LR:
				case WritingMode.RL:
					// 縦書き
					advance = inlineReplacedQuad.box.getHeight();
					break;
				default:
					throw new IllegalStateException();
				}
				inlineReplacedQuad.advance = advance;
			}
				break;

			case InlineQuad.INLINE_BLOCK:
				// インラインブロック
				double advance;
				switch (this.progression) {
				case WritingMode.TB:
					// 横書き
					advance = inlineQuad.getBox().getWidth();
					break;
				case WritingMode.LR:
				case WritingMode.RL:
					// 縦書き
					advance = inlineQuad.getBox().getHeight();
					break;
				default:
					throw new IllegalStateException();
				}
				inlineQuad.advance = advance;
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
		this.builder.flush();
	}
	
	public void close() {
		this.builder.flush();
	}
}

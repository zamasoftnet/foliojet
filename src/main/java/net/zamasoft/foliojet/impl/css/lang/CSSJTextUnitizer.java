package net.zamasoft.foliojet.impl.css.lang;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.foliojet.layout.text.InlineParamsStack;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.foliojet.layout.text.breaking.TextUnitizer;

/**
 * インライン境界(InlineQuad)を追跡して行分割規則を切り替える unitizer です。
 * パイプラインの先頭ステージとして InlineParamsStack を駆動し、
 * 下流ステージ(WordHyphenator 等)は同じスタックを共有して読み取ります。
 */
public class CSSJTextUnitizer extends TextUnitizer {

	private final InlineParamsStack inlineContext;

	public CSSJTextUnitizer(AbstractTextParams params) {
		this(new InlineParamsStack(params));
	}

	public CSSJTextUnitizer(InlineParamsStack inlineContext) {
		super(inlineContext.current().lineBreakRules);
		this.inlineContext = inlineContext;
	}

	public void control(TextControl quad) {
		if (quad instanceof InlineQuad) {
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				this.inlineContext.push(inlineStartQuad.box.getTextParams());
				this.setLineBreakRules(this.inlineContext.current().lineBreakRules);
			}
				break;

			case InlineQuad.INLINE_END: {
				this.inlineContext.pop();
				this.setLineBreakRules(this.inlineContext.current().lineBreakRules);
			}
				break;

			case InlineQuad.INLINE_REPLACED:
			case InlineQuad.INLINE_BLOCK:
			case InlineQuad.INLINE_ABSOLUTE:
				break;

			default:
				throw new IllegalStateException();
			}
		}
		super.control(quad);
	}

}

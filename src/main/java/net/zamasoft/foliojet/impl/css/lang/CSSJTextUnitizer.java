package net.zamasoft.foliojet.impl.css.lang;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;
import net.zamasoft.foliojet.layout.builder.InlineQuad;
import net.zamasoft.foliojet.layout.builder.InlineQuad.InlineStartQuad;
import net.zamasoft.pdfg2d.gc.text.TextControl;
import net.zamasoft.foliojet.layout.text.breaking.LineBreakRules;
import net.zamasoft.foliojet.layout.text.breaking.TextUnitizer;

public class CSSJTextUnitizer extends TextUnitizer {

	private List<LineBreakRules> breakRulesStack = new ArrayList<LineBreakRules>();;

	public CSSJTextUnitizer(LineBreakRules breakRules) {
		super(breakRules);
		this.breakRulesStack.add(breakRules);
	}

	public void control(TextControl quad) {
		if (quad instanceof InlineQuad) {
			final InlineQuad inlineQuad = (InlineQuad) quad;
			switch (inlineQuad.getType()) {
			case InlineQuad.INLINE_START: {
				final InlineStartQuad inlineStartQuad = (InlineStartQuad) inlineQuad;
				AbstractTextParams params = inlineStartQuad.box.getTextParams();
				this.breakRulesStack.add(params.lineBreakRules);
				this.setLineBreakRules(params.lineBreakRules);
			}
				break;

			case InlineQuad.INLINE_END: {
				this.breakRulesStack.remove(this.breakRulesStack.size() - 1);
				final LineBreakRules breakRules = this.breakRulesStack.get(this.breakRulesStack.size() - 1);
				this.setLineBreakRules(breakRules);
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

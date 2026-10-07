package net.zamasoft.foliojet.layout.text;

import java.util.ArrayList;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.AbstractTextParams;

/**
 * Inline context flowing through the glyph pipeline (nested AbstractTextParams).
 * The first stage in the chain (CSSJTextUnitizer) pushes/pops as InlineQuad passes through;
 * downstream stages (WordHyphenator, etc.) share the reference and only read current().
 *
 * <p>
 * Note: this context has "pipeline lifetime." The quad producer (StyledTextUnitizer),
 * BuilderGlyphHandler (which survives across text blocks), and TextBuilder (which runs during line
 * reconstruction) have different lifetimes, so they keep their own stacks (ARCHITECTURE.md §5.5).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class InlineParamsStack {
	private final List<AbstractTextParams> stack = new ArrayList<AbstractTextParams>();

	public InlineParamsStack(AbstractTextParams root) {
		this.stack.add(root);
	}

	public void push(AbstractTextParams params) {
		this.stack.add(params);
	}

	public void pop() {
		this.stack.remove(this.stack.size() - 1);
	}

	/**
	 * Returns the parameters of the current inline context.
	 *
	 * @return Parameters at the top of the stack
	 */
	public AbstractTextParams current() {
		return this.stack.get(this.stack.size() - 1);
	}
}

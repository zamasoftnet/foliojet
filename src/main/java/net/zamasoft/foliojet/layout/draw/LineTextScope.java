package net.zamasoft.foliojet.layout.draw;

import net.zamasoft.foliojet.layout.text.bidi.LogicalLineEmission;
import net.zamasoft.foliojet.layout.util.DelegatingGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;

/** Drawer-owned latch for one logical line's main-text replacement scope. */
public final class LineTextScope {
	private final LogicalLineEmission emission;
	private final int drawableCount;
	private final boolean suppressed;
	private int completed;
	private int paintSequence;
	private Object output;
	private GC.State state;

	LineTextScope(final LogicalLineEmission emission, final int drawableCount, final boolean suppressed) {
		this.emission = emission;
		this.drawableCount = drawableCount;
		this.suppressed = suppressed;
	}

	/** Opens the replacement immediately before the first main-text pass. */
	public void beforeMainText(final GC gc) throws GraphicsException {
		if (this.suppressed || this.state != null) {
			return;
		}
		final Object output = DelegatingGC.unwrap(gc);
		if (this.completed != 0 || this.output != null && this.output != output) {
			return;
		}
		this.output = output;
		this.state = gc.beginTextReplacement(this.emission.logicalText());
	}

	/** Closes the replacement immediately after the last main-text pass. */
	public void afterMainText() throws GraphicsException {
		++this.completed;
		if (this.completed == this.drawableCount) {
			this.close();
		}
	}

	public int nextPaintSequence() {
		return this.paintSequence++;
	}

	void close() throws GraphicsException {
		if (this.state != null) {
			try {
				this.state.close();
			} finally {
				this.state = null;
			}
		}
	}
}

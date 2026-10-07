package net.zamasoft.foliojet.css.style;

import net.zamasoft.foliojet.css.CSSStyle;

/**
 * Event count, depth, and generation of the main-flow style window.
 *
 * <p>
 * Relayout uses frozen {@code LayoutSource/BoxRecipe} data. No consumer reads styles
 * or characters of closed elements from this legacy M6a window. Retaining Start/End
 * until a page boundary would retain every td/tr's {@code CSSStyle.values/computedValues}
 * until Pass B starts for auto tables.
 * </p>
 *
 * <p>
 * <b>Retention invariant</b>: holds no style or character references; updates numbers only.
 * Does not change CSSStyle's own computed values, so the caller's remaining work,
 * such as ::after evaluation and anonymous-box closing, is unaffected.
 * Restyling rescans input; running/page-content use independent StyleSnapshot instances,
 * so this window need not retain closed styles.
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public class Segment {
	private int eventCount = 0;

	protected int depth = 0;

	/**
	 * Window generation, advanced at every page boundary. LayoutSource owns replay anchors.
	 */
	protected int epoch = 0;

	public int getDepth() {
		return this.depth;
	}

	/**
	 * Returns the current window's event count. Does not retain event objects.
	 */
	public int size() {
		return this.eventCount;
	}

	public void startStyle(CSSStyle style) {
		++this.eventCount;
		++this.depth;
	}

	public void characters(int offset, char[] ch, int off, int len) {
		// LayoutSource, recorded by RecordingLayoutSink, is the sole source for character replay.
		++this.eventCount;
	}

	public void endStyle(CSSStyle style) {
		++this.eventCount;
		--this.depth;
	}

	/**
	 * At a page boundary, resets the window to the Start count for open elements and advances
	 * the generation. Does not change depth.
	 */
	public void trimToOpenElements() {
		this.eventCount = this.depth;
		++this.epoch;
	}
}

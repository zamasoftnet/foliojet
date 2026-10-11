package net.zamasoft.foliojet.layout.box;

import net.zamasoft.foliojet.layout.box.content.BreakMode;
import net.zamasoft.foliojet.layout.fragment.SplitResult;

/**
 * A box that can be split along the page axis.
 *
 * <p>
 * <b>Splitting contract</b>: {@link #splitPageAxis(double, BreakMode, byte)} is a protocol
 * that mutates an already built box in place and returns the remainder to send to the next page.
 * The return value has three meanings (see below). The caller checks identity (this) and null
 * to continue processing. {@link net.zamasoft.foliojet.layout.box.content.BreakToken}
 * carries text continuation information (planned to be integrated with split results in pillar 2).
 * </p>
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: IPageBreakableBox.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public interface IPageBreakableBox extends IBox {
	/**
	 * Splits internally or keeps the box on the preceding page.
	 */
	public static final byte FLAGS_FIRST = 1;

	/**
	 * Splits internally or sends the box to the next page.
	 */
	public static final byte FLAGS_LAST = 2;

	/**
	 * Always splits internally.
	 */
	public static final byte FLAGS_SPLIT = 4;

	/**
	 * Splits internally or keeps the box on the preceding page (table rows).
	 */
	public static final byte FLAGS_FIRST_ROW = 8;

	/**
	 * A cuttable float of this container or of an enclosing container crosses the cut line (2026-10-07). Its head
	 * stays in the current fragmentainer, so moving content that was pushed below the cut by floats still makes
	 * progress. Set by {@code FlowContainer} and passed down unchanged.
	 */
	public static final byte FLAGS_FLOAT_CROSSES = 16;

	/**
	 * The cut line is the probe an avoid pushback of the parent puts just before the end of this box (2026-10-09). The
	 * box fits; the parent only asks for a break inside it that keeps the avoids, and relaxes its avoid when there is
	 * none. Such a break need not make progress, so at the start of the page the box stays whole rather than keep
	 * its first line alone. Set by {@code FlowContainer} and passed down unchanged.
	 */
	public static final byte FLAGS_AVOID_PROBE = 32;

	/**
	 * Splits the box along the page axis (M4-A3: native SplitResult).
	 *
	 * @param pageLimit the distance from the box's outer edge (page-axis start) to the split position.
	 * @param mode      the split mode: AutoBreakMode for automatic page breaks, ForceBreakMode
	 *                  for forced page breaks (not passed to TextBlockBox).
	 * @param flags     a bitwise combination of FLAGS_*.
	 *                  FLAGS_FIRST=this box is at the page start (may split internally or
	 *                  keep the whole box on the preceding page).
	 *                  FLAGS_LAST=at the page end (may split internally or send the whole box
	 *                  to the next page; prohibited for table rows and row groups).
	 *                  FLAGS_SPLIT=always split internally.
	 *                  FLAGS_FIRST_ROW=the table-row variant of FLAGS_FIRST (first row on the page).
	 *                  Column breaks (multi-column layout) use BreakMode.ColumnBreakMode.
	 * @return the split result. KEEP=keep on the preceding page without splitting.
	 *         MOVE=move the entire box to the next page. Split(remainder)=split internally
	 *         (this box has already been mutated to retain only the preceding page's portion;
	 *         send remainder to the next page).
	 */
	public SplitResult split(double pageLimit, BreakMode mode, byte flags);
}

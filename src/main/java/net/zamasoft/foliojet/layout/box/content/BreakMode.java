package net.zamasoft.foliojet.layout.box.content;

import net.zamasoft.foliojet.layout.box.params.PageBreakMode;

import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.AbstractInnerTableBox;
import net.zamasoft.foliojet.layout.box.IBox;

/**
 * The block splitting mode.
 *
 * @author MIYABE Tatsuhiko
 * @version $Id: BreakMode.java 1552 2018-04-26 01:43:24Z miyabe $
 */
public abstract class BreakMode {
	/**
	 * An automatic page break at the specified line.
	 */
	public static class AutoBreakMode extends BreakMode {
		public final IBox box;

		/**
		 * The fragmentainer's (page/column) inner page-axis size (2026-08-20; -1 if unknown).
		 * When a box with page-break-inside:avoid <b>cannot fit even in a whole fragmentainer</b>,
		 * css-break allows ignoring avoid: moving it would still require an internal split,
		 * merely leaving a large blank area on the source page
		 * (measured on large bilingual figures in w3c-jlreq: moving them produced 479 half-empty
		 * pages out of 2,053). Compare against the root type area's inner size
		 * (without subtracting ancestor frames, a conservative approximation: actual capacity
		 * is no larger, so a box larger than the type area definitely cannot fit).
		 */
		public final double fragmentCapacity;

		public AutoBreakMode(IBox box) {
			this(box, -1);
		}

		public AutoBreakMode(IBox box, final double fragmentCapacity) {
			assert box != null;
			this.box = box;
			this.fragmentCapacity = fragmentCapacity;
		}

		private AutoBreakMode() {
			this.box = null;
			this.fragmentCapacity = -1;
		}

		private AutoBreakMode(final double fragmentCapacity) {
			this.box = null;
			this.fragmentCapacity = fragmentCapacity;
		}

		/** Anonymous mode with capacity (for autoBreak when flowStack is shallow). */
		public static AutoBreakMode withCapacity(final double fragmentCapacity) {
			return new AutoBreakMode(fragmentCapacity);
		}

		public String toString() {
			if (this.box == null) {
				return "AUTO_BREAK_MODE";
			}
			return "AUTO_BREAK_MODE/" + this.box.getParams().element;
		}
	};

	public static AutoBreakMode DEFAULT_BREAK_MODE = new AutoBreakMode();

	/**
	 * An automatic column break in multi-column layout (the typed form of the former FLAGS_COLUMN).
	 * Absorb the column break when it reaches the multi-column box; inside it, behave
	 * as a normal automatic page break.
	 */
	public static final class ColumnBreakMode extends AutoBreakMode {
		private ColumnBreakMode(IBox box, final double fragmentCapacity) {
			super(box, fragmentCapacity);
		}

		private ColumnBreakMode() {
			super();
		}

		public String toString() {
			return "COLUMN_" + super.toString();
		}
	}

	/**
	 * Marks an automatic page break as a column break (leave forced breaks unchanged,
	 * since their breakType identifies the column break).
	 */
	public static BreakMode column(final BreakMode mode) {
		if (mode instanceof ColumnBreakMode) {
			return mode;
		}
		if (mode instanceof AutoBreakMode auto) {
			return auto.box == null ? new ColumnBreakMode() : new ColumnBreakMode(auto.box, auto.fragmentCapacity);
		}
		return mode;
	}

	/**
	 * Absorbs a column break that has reached the multi-column box itself,
	 * reverting to a normal page break inside it.
	 */
	public static BreakMode absorbColumn(final BreakMode mode, final int columnCount) {
		if (columnCount > 1 && mode instanceof ColumnBreakMode column) {
			return column.box == null ? DEFAULT_BREAK_MODE : new AutoBreakMode(column.box, column.fragmentCapacity);
		}
		return mode;
	}

	/**
	 * A forced page break at a specific location.
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: BreakMode.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class ForceBreakMode extends BreakMode {
		public final IBox box;

		public final PageBreakMode breakType;

		/**
		 * Whether this break comes from a page-name transition (named pages N2b). If the closing
		 * page is blank, omit it from output (unlike an explicit author-requested break,
		 * this is not a reason to preserve a blank page).
		 */
		public final boolean namedTransition;

		public ForceBreakMode(IBox box, PageBreakMode breakType) {
			this(box, breakType, false);
		}

		public ForceBreakMode(IBox box, PageBreakMode breakType, boolean namedTransition) {
			assert breakType == PageBreakMode.PAGE || breakType == PageBreakMode.COLUMN
					|| breakType == PageBreakMode.VERSO || breakType == PageBreakMode.RECTO;
			this.box = box;
			this.breakType = breakType;
			this.namedTransition = namedTransition;
		}

		public String toString() {
			switch (this.breakType) {
			case PageBreakMode.PAGE:
				return "FORCE_BREAK_MODE ALWAYS";
			case PageBreakMode.COLUMN:
				return "FORCE_BREAK_MODE COLUMN";
			case PageBreakMode.VERSO:
				return "FORCE_BREAK_MODE LEFT";
			case PageBreakMode.RECTO:
				return "FORCE_BREAK_MODE RIGHT";
			default:
				throw new IllegalStateException();
			}
		}
	}

	/**
	 * A forced page break within a table.
	 *
	 * @author MIYABE Tatsuhiko
	 * @version $Id: BreakMode.java 1552 2018-04-26 01:43:24Z miyabe $
	 */
	public static class TableForceBreakMode extends ForceBreakMode {
		public final int rowGroup, row;

		public TableForceBreakMode(AbstractInnerTableBox box, PageBreakMode breakType, int rowGroup, int row) {
			super(box, breakType);
			assert row == -1 || box.getType() == BoxType.TABLE_ROW;
			assert row != -1 || box.getType() == BoxType.TABLE_ROW_GROUP;
			this.rowGroup = rowGroup;
			this.row = row;
		}
	}
}

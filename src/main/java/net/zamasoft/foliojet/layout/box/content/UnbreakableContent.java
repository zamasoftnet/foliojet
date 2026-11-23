package net.zamasoft.foliojet.layout.box.content;

import java.util.ArrayDeque;
import java.util.Deque;

import net.zamasoft.foliojet.layout.box.AbstractContainerBox;
import net.zamasoft.foliojet.layout.box.BoxType;
import net.zamasoft.foliojet.layout.box.IBox;
import net.zamasoft.foliojet.layout.box.impl.TableBox;
import net.zamasoft.foliojet.layout.box.impl.TableCellBox;
import net.zamasoft.foliojet.layout.box.impl.TextBlockBox;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.layout.box.params.PageBreakMode;
import net.zamasoft.foliojet.layout.box.params.WritingMode;
import net.zamasoft.foliojet.layout.fragment.PaginationContract;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

/**
 * The longest thing in the contents of a multicol that no column break cuts (2026-11-23, triage §267): an image, a block,
 * table row or row group with {@code break-inside: avoid}, a line, a float that cannot be cut, and a box of the other axis
 * or progression. Balanced columns are no shorter than it.
 *
 * <p>
 * The search of the column height ({@link FlowContainer#getBalanceCutPointBelow}) takes what it does not look into (a float,
 * a table, a flex or grid item, an inner multicol) as cut at the proposed position. One image in three columns came out as
 * three slices, and a card with {@code break-inside: avoid} was cut over the columns.
 * </p>
 *
 * <p>
 * Where a break can come before the content, the column has to hold the content alone; the search sees the frame of a block
 * that starts a column. In a float or a table cell it has to hold the frames around the content, too. Deep nesting is
 * searched without recursion.
 * </p>
 */
public final class UnbreakableContent {
	private final WritingMode flow;
	private final double wholeLimit;
	private final Deque<FlowContainer> containers = new ArrayDeque<FlowContainer>();
	private final Deque<Double> frames = new ArrayDeque<Double>();
	private double extent = 0;

	private UnbreakableContent(final WritingMode flow, final double wholeLimit) {
		this.flow = flow;
		this.wholeLimit = wholeLimit;
	}

	/**
	 * @param wholeLimit the extent up to which an image, or a box with {@code break-inside: avoid}, is kept whole
	 * @return the page extent, 0 if there is no such content
	 */
	static double extent(final FlowContainer contents, final WritingMode flow, final double wholeLimit) {
		final UnbreakableContent search = new UnbreakableContent(flow, wholeLimit);
		search.push(contents, Double.NaN);
		while (!search.containers.isEmpty()) {
			final FlowContainer container = search.containers.pop();
			final double frame = search.frames.pop();
			container.eachFloatingBox(box -> search.addFloat(box, frame));
			container.eachFlowBox(box -> search.addFlow(box, frame));
		}
		return search.extent;
	}

	/**
	 * Whether column balancing keeps a box whole: a block of the other axis or progression, and no longer than
	 * {@code wholeLimit} an image or a block with {@code break-inside: avoid}. The actual cut cuts inside such a block only
	 * where a column starts with it, and slices an image only there.
	 */
	static boolean isKeptWhole(final IBox box, final WritingMode flow, final double wholeLimit) {
		if (box.getType() == BoxType.BLOCK) {
			final BlockParams params = ((AbstractContainerBox) box).getBlockParams();
			if (PaginationContract.isChainAtomicBoundary(flow, params.flow)) {
				return true;
			}
			if (params.pageBreakInside != PageBreakMode.AVOID) {
				return false;
			}
		} else if (box.getType() != BoxType.REPLACED) {
			return false;
		}
		return LayoutUtils.compare(box.getPageExtent(flow), wholeLimit) <= 0;
	}

	/**
	 * Takes a row or row group with {@code break-inside: avoid}, unless it is too long to keep whole.
	 *
	 * @param table what the table needs in the column with it (its frame, header and footer)
	 */
	public boolean addAvoid(final PageBreakMode breakInside, final double size, final double table) {
		if (breakInside != PageBreakMode.AVOID || LayoutUtils.compare(size + table, this.wholeLimit) > 0) {
			return false;
		}
		this.extent = Math.max(this.extent, size + table);
		return true;
	}

	/** Searches the contents of a table cell that a column break cuts. */
	public void addCell(final TableCellBox cell, final double table) {
		if (cell.getBlockParams().flow == this.flow) {
			this.push(cell.getContainer(), table + this.frame(cell));
		}
	}

	/** @param frame the frames around the box that a column has to hold with it, NaN if a break can come before the box */
	private void addFlow(final IBox box, final double frame) {
		if (isKeptWhole(box, this.flow, this.wholeLimit)) {
			this.take(box.getPageExtent(this.flow), frame);
		} else if (box.getType() == BoxType.TEXT_BLOCK) {
			this.take(((TextBlockBox) box).getLongestLinePageExtent(), frame);
		} else if (box.getType() == BoxType.TABLE) {
			((TableBox) box).eachUnbreakable(this.flow, this);
		} else if (box.getType() == BoxType.BLOCK) {
			final AbstractContainerBox block = (AbstractContainerBox) box;
			this.push(block.getContainer(), frame + this.frame(block));
		}
	}

	private void addFloat(final IBox box, final double frame) {
		if (box.getType() == BoxType.REPLACED || box.getType() == BoxType.RESCUE
				|| isKeptWhole(box, this.flow, this.wholeLimit)) {
			this.take(box.getPageExtent(this.flow), frame);
		} else if (box.getType() == BoxType.BLOCK) {
			final AbstractContainerBox block = (AbstractContainerBox) box;
			this.push(block.getContainer(), (Double.isNaN(frame) ? 0 : frame) + this.frame(block));
		}
	}

	private double frame(final AbstractContainerBox block) {
		return block.getFrame().getFramePageStart(this.flow) + block.getFrame().getFramePageEnd(this.flow);
	}

	private void take(final double size, final double frame) {
		this.extent = Math.max(this.extent, Double.isNaN(frame) ? size : size + frame);
	}

	private void push(final Container contents, final double frame) {
		if (contents instanceof FlowContainer flows) {
			this.containers.push(flows);
			this.frames.push(frame);
		} else if (contents instanceof ColumnsContainer columns) {
			columns.eachColumn(column -> this.push(column, frame));
		}
	}
}

package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.TableParams;
import net.zamasoft.foliojet.layout.box.params.WritingMode;

/**
 * A template that freezes the contents of {@link TableParams} (directly extends {@code BlockParams};
 * used by {@link BoxKind#TABLE}) and materializes a fresh, independent {@code TableParams} on each call
 * (introduced on 2026-07-22, M6d-A3b; removed on 2026-07-25 after the G-1 investigation, then restored
 * on 2026-07-30 after the user approved implementing the table set, updating the G-1 decision).
 *
 * <p>
 * Recording requires "the inner blockBox is a plain {@code FlowBlockBox} with a plain {@code FlowPos},
 * and params aliasing holds" (the TableBox branch of {@code RecordingLayoutSink.boxKind}).
 * The recorder must pass <b>the inner blockBox's pos</b>; the outer {@code TableBox.getPos()}
 * always returns {@code TablePos}, which has no placement kind.
 * </p>
 *
 * <p>
 * {@link BlockParamsFields} (shared with `BlockParamsTemplate`) handles the ancestor fields
 * (`Params`/`AbstractTextParams`/`AbstractLineParams`/`BlockParams`).
 * {@code borderSpacingH}/{@code borderSpacingV} (double) and {@code borderCollapse}/{@code layout}
 * (byte) are all primitives and are held directly
 * (replaced with an immutable record in Stage2 on 2026-07-22).
 * </p>
 */
public record TableParamsTemplate(BlockParamsFields common, double borderSpacingH, double borderSpacingV,
		byte borderCollapse, byte layout) {
	public static TableParamsTemplate freeze(final TableParams source) {
		return new TableParamsTemplate(BlockParamsFields.freeze(source), source.borderSpacingH,
				source.borderSpacingV, source.borderCollapse, source.layout);
	}

	/**
	 * Returns the frozen writing direction (E-6 increment 3b-4:
	 * {@code LayoutSource.containsMixedFlow} reads it from a frozen Start).
	 */
	public WritingMode flow() {
		return this.common.common().text().flow();
	}

	/** Returns a fresh {@code TableParams} on each call (multiple calls do not affect one another). */
	public TableParams materialize() {
		final TableParams p = new TableParams();
		this.common.materializeInto(p);
		p.borderSpacingH = this.borderSpacingH;
		p.borderSpacingV = this.borderSpacingV;
		p.borderCollapse = this.borderCollapse;
		p.layout = this.layout;
		return p;
	}
}

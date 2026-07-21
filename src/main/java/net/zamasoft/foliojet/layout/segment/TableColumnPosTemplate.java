package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.TableColumnPos;

/**
 * {@link TableColumnPos}({@code Pos}を直接実装、共有できる祖先を持たない、
 * {@link BoxKind#TABLE_COLUMN_GROUP}/{@link BoxKind#TABLE_COLUMN}が使う)
 * の内容をfreezeし、呼び出しごとに独立した新品の{@code TableColumnPos}を
 * materializeするテンプレートです(2026-07-22新設、M6d-A3b)。
 *
 * <p>
 * {@code span}(プリミティブ)のみを持つため、他のPosテンプレートのような
 * 合成は不要。
 * </p>
 */
public final class TableColumnPosTemplate {
	private final int span;

	private TableColumnPosTemplate(final int span) {
		this.span = span;
	}

	public static TableColumnPosTemplate freeze(final TableColumnPos source) {
		return new TableColumnPosTemplate(source.span);
	}

	/** 呼び出しごとに新品の{@code TableColumnPos}を返す(複数回呼んでも互いに影響しない)。 */
	public TableColumnPos materialize() {
		final TableColumnPos pos = new TableColumnPos();
		pos.span = this.span;
		return pos;
	}
}

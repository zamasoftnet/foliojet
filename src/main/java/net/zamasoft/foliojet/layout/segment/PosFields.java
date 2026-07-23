package net.zamasoft.foliojet.layout.segment;

import net.zamasoft.foliojet.layout.box.params.AbstractStaticPos;
import net.zamasoft.foliojet.layout.box.params.Offset;

/**
 * {@code AbstractStaticPos}が持つ唯一のフィールド({@code offset})の
 * freeze/materialize処理です(2026-07-22新設、M6d-A3b、
 * package-private——{@link BlockLevelPosFields}が合成する)。
 *
 * <p>
 * {@code offset}(既存{@code Offset}実装はfinalフィールドのみで実質
 * 不変)はコピー不要、そのまま保持する。
 * </p>
 */
record PosFields(Offset offset) {
	static PosFields freeze(final AbstractStaticPos source) {
		return new PosFields(source.offset);
	}

	void materializeInto(final AbstractStaticPos target) {
		target.offset = this.offset;
	}
}

package net.zamasoft.foliojet.layout.constraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * ある文脈(1つのformatting context)で現在有効な浮動体排除帯の集合です
 * (2026-07-23新設、排除域のConstraintSpace入力化のP0第一段——
 * `docs/consultations/consult-exclusion-zone-codex.txt`の設計に基づく)。
 *
 * <p>
 * 不変値型。{@code net.zamasoft.foliojet.layout.builder.impl
 * .BlockBuilder.floatings}(可変{@code List})が現在担っている
 * 「pageEnd昇順・同値は追加順」という並び契約
 * ({@code BlockBuilder.FLOAT_COMP}、安定ソート)を、この段階では
 * まだどの消費者にも配線せず値型だけで再現する。実際の消費者
 * (multicol回避・clear・addBound・TextBuilder・float配置)を
 * このqueryへ切り替える作業は後続の増分で行う——本クラス自体は
 * 挙動を一切変更しない。
 * </p>
 */
public final class ExclusionSpace {
	public static final ExclusionSpace EMPTY = new ExclusionSpace(List.of());

	/** {@link FloatExclusion#pageSpan}の{@code end}昇順(同値は{@link FloatExclusion#order}昇順)。 */
	private final List<FloatExclusion> ascendingByPageEnd;

	private ExclusionSpace(List<FloatExclusion> ascendingByPageEnd) {
		this.ascendingByPageEnd = ascendingByPageEnd;
	}

	/**
	 * {@code exclusion}を追加した新しい{@code ExclusionSpace}を返します。
	 * このインスタンス自体は変更しません。挿入位置は既存の
	 * {@code pageSpan.end}昇順を保ったまま、同値の要素より後ろに
	 * なります({@code BlockBuilder}が「末尾へadd後に安定ソート」する
	 * のと同じ結果)。
	 */
	public ExclusionSpace plus(FloatExclusion exclusion) {
		if (exclusion == null) {
			throw new IllegalArgumentException("exclusion must not be null");
		}
		final List<FloatExclusion> next = new ArrayList<>(this.ascendingByPageEnd.size() + 1);
		final double newEnd = exclusion.pageSpan().end();
		boolean inserted = false;
		for (final FloatExclusion existing : this.ascendingByPageEnd) {
			if (!inserted && existing.pageSpan().end() > newEnd) {
				next.add(exclusion);
				inserted = true;
			}
			next.add(existing);
		}
		if (!inserted) {
			next.add(exclusion);
		}
		return new ExclusionSpace(Collections.unmodifiableList(next));
	}

	/** 空かどうかです。 */
	public boolean isEmpty() {
		return this.ascendingByPageEnd.isEmpty();
	}

	/** 保持している排除帯の件数です。 */
	public int size() {
		return this.ascendingByPageEnd.size();
	}

	/**
	 * {@code pageSpan.end}昇順(同値は追加順)のビューです。
	 * {@code TextBuilder.locateLine}等、昇順走査する消費者向け。
	 */
	public List<FloatExclusion> ascendingByPageEnd() {
		return this.ascendingByPageEnd;
	}

	/**
	 * {@code pageSpan.end}降順(同値は最後に追加されたものが先)の
	 * ビューです。{@code BlockBuilder.startFlowBlock}のmulticol回避・
	 * {@code addBound}等、末尾から逆順走査する消費者向け
	 * (現行コードの{@code for (i = floatings.size() - 1; i >= 0; --i)}
	 * と同じ順序)。
	 */
	public List<FloatExclusion> descendingByPageEnd() {
		final List<FloatExclusion> reversed = new ArrayList<>(this.ascendingByPageEnd);
		Collections.reverse(reversed);
		return Collections.unmodifiableList(reversed);
	}
}

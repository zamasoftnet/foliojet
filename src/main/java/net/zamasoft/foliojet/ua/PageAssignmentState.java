package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 名前ごとの頁内代入を文書順で解決します。値は entry/first/last の三候補だけ保持します
 * (保持量は名前ごと O(1))。
 * <p>
 * 同じ (name, order) の再登録は<b>後の呼び出しが勝つ</b>。疑似要素は文書順キーを共有し
 * (`elementKey` が -1)、EPUB は章ごとに採番が戻り、build 時に即時登録した代入を
 * 配置確定時にもう一度登録するため、二重登録は正常な経路で起こる(codex レビュー
 * 2026-09-05 R1a #1/#2/#3)。
 * </p>
 */
public final class PageAssignmentState<T> {

	public enum Mode { FIRST, START, LAST, FIRST_EXCEPT }

	public enum Presence { ABSENT, VALUE, TOMBSTONE, SUPPRESSED }

	/** order は安定な文書順、beginsPage は配置確定時の事実です。 */
	public record Assignment<T>(long order, T value, boolean beginsPage, boolean tombstone) {
		public Assignment {
			if (tombstone && value != null) {
				throw new IllegalArgumentException("tombstone cannot carry a value");
			}
		}
	}

	/** 未登録・値・削除・抑止を区別した解決結果です。 */
	public record Resolution<T>(Presence presence, T value) {
	}

	/** 呼び出し時点の三候補です。値自身の複製は行いません。 */
	public record Snapshot<T>(Assignment<T> entry, Assignment<T> first, Assignment<T> last) {
	}

	private static final class Candidates<T> {
		Assignment<T> entry, first, last;
	}

	private final Map<String, Candidates<T>> names = new HashMap<String, Candidates<T>>();

	/** 解決済みの値を登録します。 */
	public void assign(final String name, final T value, final long order, final boolean beginsPage) {
		this.register(name, new Assignment<T>(order, Objects.requireNonNull(value), beginsPage, false));
	}

	/** 名前の削除を文書順つきの tombstone として登録します。 */
	public void clear(final String name, final long order, final boolean beginsPage) {
		this.register(name, new Assignment<T>(order, null, beginsPage, true));
	}

	private void register(final String name, final Assignment<T> assignment) {
		final Candidates<T> candidates = this.names.computeIfAbsent(Objects.requireNonNull(name),
				key -> new Candidates<T>());
		// 同じ order は後の呼び出しで置き換える(候補でない中間 order は捨てる)
		if (candidates.first == null || assignment.order() <= candidates.first.order()) {
			candidates.first = assignment;
		}
		if (candidates.last == null || assignment.order() >= candidates.last.order()) {
			candidates.last = assignment;
		}
	}

	/**
	 * 登録済みの代入に「頁先頭の要素が代入元である」事実を後から付けます
	 * (build 時に登録した代入へ、配置確定時に page builder が渡す。R1b)。
	 * 該当する頁内候補(first/last)が無ければ何もしません。
	 *
	 * @param name  名前
	 * @param order 代入元の文書順
	 */
	public void markBeginsPage(final String name, final long order) {
		final Candidates<T> candidates = this.names.get(name);
		if (candidates == null) {
			return;
		}
		if (candidates.first != null && candidates.first.order() == order && !candidates.first.beginsPage()) {
			candidates.first = new Assignment<T>(order, candidates.first.value(), true, candidates.first.tombstone());
		}
		if (candidates.last != null && candidates.last.order() == order && !candidates.last.beginsPage()) {
			candidates.last = new Assignment<T>(order, candidates.last.value(), true, candidates.last.tombstone());
		}
	}

	/** 指定した方針で現在の頁の値を解決します。 */
	public Resolution<T> resolve(final String name, final Mode mode) {
		Objects.requireNonNull(mode);
		final Snapshot<T> snapshot = this.snapshot(name);
		if (mode == Mode.FIRST_EXCEPT && snapshot.first() != null) {
			return new Resolution<T>(Presence.SUPPRESSED, null);
		}
		final Assignment<T> assignment = switch (mode) {
		case FIRST -> snapshot.first() != null ? snapshot.first() : snapshot.entry();
		case START -> snapshot.first() != null && snapshot.first().beginsPage()
				? snapshot.first() : snapshot.entry();
		case LAST -> snapshot.last() != null ? snapshot.last() : snapshot.entry();
		case FIRST_EXCEPT -> snapshot.entry();
		};
		if (assignment == null) {
			return new Resolution<T>(Presence.ABSENT, null);
		}
		return new Resolution<T>(assignment.tombstone() ? Presence.TOMBSTONE : Presence.VALUE,
				assignment.value());
	}

	/** 診断や頁スナップショットの作成用に三候補を返します。 */
	public Snapshot<T> snapshot(final String name) {
		final Candidates<T> candidates = this.names.get(name);
		return candidates == null ? new Snapshot<T>(null, null, null)
				: new Snapshot<T>(candidates.entry, candidates.first, candidates.last);
	}

	/** 頁の読み取り専用スナップショット作成用に、登録名のコピーを返します。 */
	public java.util.Set<String> names() {
		return java.util.Set.copyOf(this.names.keySet());
	}

	/** 頁内の最後の代入を次頁へ継承し、頁内候補を解放します。 */
	public void endPage() {
		for (final Candidates<T> candidates : this.names.values()) {
			if (candidates.last != null) {
				candidates.entry = candidates.last;
			}
			candidates.first = candidates.last = null;
		}
	}

	/** 全ての名前と頁状態を初期化します。 */
	public void reset() {
		this.names.clear();
	}

	/** 全状態の初期化です。 */
	public void clear() {
		this.reset();
	}
}

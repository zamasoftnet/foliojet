package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import net.zamasoft.foliojet.css.selector.Condition;

/**
 * 要素の終了時点(:has()は該当部分木の終了時点)まで真偽が確定しない
 * セレクタの判定結果です。{@code CSSElement.elementKey}(文書順の通し
 * 番号、パスをまたいで安定)をキーとする。
 * <p>
 * `:last-child`系は実レイアウトを組まない軽量な事前走査
 * {@code STRUCTURE_SCAN}(独立したパスフェーズ)が一度に確定させ、以降の
 * 全LAYOUTパスからは読み取り専用として参照される。`:has()`は
 * {@code STRUCTURE_SCAN}を使わず、通常のLAYOUTパス(`StyleContext.merge`)が
 * 要素ごとの祖先チェーンを見ながら段階的に確定させる(`PageRef`と同じ
 * 「複数パスにまたがって値を積み上げる」設計。理由はdocs/PLAN.md
 * 「2パス制御モード」参照——`:has()`の相対セレクタ評価には実際の
 * `CSSElement`(class/id/属性込み)と`StyleContext.matchesFromPath`が
 * 要り、`STRUCTURE_SCAN`専用の軽量walkerでは賄えないため)。
 * </p>
 * <p>
 * {@link PageRef}とは別クラス(意図的): {@code PageRef}はURI/id起点の
 * ページ参照・TOC専用ストアで、id無し要素の安定キーを持たない。
 * ライフサイクル(このクラスは`STRUCTURE_SCAN`開始時に1回だけリセットし、
 * 以降の全パスで蓄積し続ける)は`PageRef`の`reset()`と紛らわしいため
 * 相乗りはしない(docs/PLAN.md参照)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class SelectorFacts {
	// ---- E-6増分1(2026-07-24): 保持量のhigh-waterカウンタ群 ----
	// spillableテープ基盤のspill閾値・対象選定の実測基盤(codex設計§1.3:
	// SelectorFactsはE-6でtape化しない——まず実コーパスでエントリ数を測る)。
	// 各Map/Setのエントリ数のmax更新のみで挙動には影響しない。

	/** {@code lastChild}のエントリ数のhigh-water(E-6増分1、挙動不変)。 */
	public static final AtomicLong LAST_CHILD_HIGH_WATER = new AtomicLong();

	/** {@code lastOfType}のエントリ数のhigh-water(E-6増分1、挙動不変)。 */
	public static final AtomicLong LAST_OF_TYPE_HIGH_WATER = new AtomicLong();

	/** {@code empty}のエントリ数のhigh-water(E-6増分1、挙動不変)。 */
	public static final AtomicLong EMPTY_HIGH_WATER = new AtomicLong();

	/** {@code positionFromEnd}のエントリ数のhigh-water(E-6増分1、挙動不変)。 */
	public static final AtomicLong POSITION_FROM_END_HIGH_WATER = new AtomicLong();

	/** {@code typePositionFromEnd}のエントリ数のhigh-water(E-6増分1、挙動不変)。 */
	public static final AtomicLong TYPE_POSITION_FROM_END_HIGH_WATER = new AtomicLong();

	/** {@code hasMatches}のキー(要素)数のhigh-water(E-6増分1、挙動不変)。 */
	public static final AtomicLong HAS_MATCH_ELEMENT_HIGH_WATER = new AtomicLong();

	/**
	 * {@code hasMatches}の(要素, 条件)ペア総数のhigh-water(E-6増分1、
	 * 挙動不変)。最悪O(N×H)の実測。
	 */
	public static final AtomicLong HAS_MATCH_PAIR_HIGH_WATER = new AtomicLong();

	/** 現在の(要素, 条件)ペア総数(観測用の付随カウンタ、判定には使わない)。 */
	private long hasPairCount;

	private Set<Long> lastChild;
	private Set<Long> lastOfType;
	private Set<Long> empty;

	/** :has()。elementKey → その要素で真になったHAS_CONDITION(SelectorListCondition)集合。 */
	private Map<Long, Set<Condition>> hasMatches;

	/**
	 * 同じ親の子のうち、末尾から数えた通し番号(1始まり)。
	 * {@code :nth-last-child(An+B)}は{@code NthCondition.matches(int)}に
	 * そのまま渡せる(:nth-child()と同じ判定ロジックを再利用できる)。
	 */
	private Map<Long, Integer> positionFromEnd;

	/** 同じ親の同じ要素名の子のうち、末尾から数えた通し番号(1始まり)。 */
	private Map<Long, Integer> typePositionFromEnd;

	/**
	 * このパスで新たに事実を記録する前に呼びます。前回の走査結果
	 * (別文書、またはやり直しの走査)を引きずらないよう、保持している
	 * 事実をすべて破棄します。
	 */
	public void reset() {
		this.lastChild = null;
		this.lastOfType = null;
		this.empty = null;
		this.positionFromEnd = null;
		this.typePositionFromEnd = null;
		this.hasMatches = null;
		this.hasPairCount = 0;
	}

	/**
	 * elementKeyの要素についてhasConditionが真になったことを記録します。
	 * 一度真になったら以降は変わらない(:has()は「存在するか」の判定のため)。
	 */
	public void setHasMatch(long elementKey, Condition hasCondition) {
		if (this.hasMatches == null) {
			this.hasMatches = new HashMap<Long, Set<Condition>>();
		}
		Set<Condition> set = this.hasMatches.get(elementKey);
		if (set == null) {
			set = new HashSet<Condition>();
			this.hasMatches.put(elementKey, set);
		}
		if (set.add(hasCondition)) {
			++this.hasPairCount;
		}
		HAS_MATCH_ELEMENT_HIGH_WATER.accumulateAndGet(this.hasMatches.size(), Math::max);
		HAS_MATCH_PAIR_HIGH_WATER.accumulateAndGet(this.hasPairCount, Math::max);
	}

	public boolean isHasMatch(long elementKey, Condition hasCondition) {
		if (this.hasMatches == null) {
			return false;
		}
		Set<Condition> set = this.hasMatches.get(elementKey);
		return set != null && set.contains(hasCondition);
	}

	public void setLastChild(long elementKey) {
		if (this.lastChild == null) {
			this.lastChild = new HashSet<Long>();
		}
		this.lastChild.add(elementKey);
		LAST_CHILD_HIGH_WATER.accumulateAndGet(this.lastChild.size(), Math::max);
	}

	public boolean isLastChild(long elementKey) {
		return this.lastChild != null && this.lastChild.contains(elementKey);
	}

	public void setLastOfType(long elementKey) {
		if (this.lastOfType == null) {
			this.lastOfType = new HashSet<Long>();
		}
		this.lastOfType.add(elementKey);
		LAST_OF_TYPE_HIGH_WATER.accumulateAndGet(this.lastOfType.size(), Math::max);
	}

	public boolean isLastOfType(long elementKey) {
		return this.lastOfType != null && this.lastOfType.contains(elementKey);
	}

	public void setEmpty(long elementKey) {
		if (this.empty == null) {
			this.empty = new HashSet<Long>();
		}
		this.empty.add(elementKey);
		EMPTY_HIGH_WATER.accumulateAndGet(this.empty.size(), Math::max);
	}

	public boolean isEmpty(long elementKey) {
		return this.empty != null && this.empty.contains(elementKey);
	}

	public void setPositionFromEnd(long elementKey, int position) {
		if (this.positionFromEnd == null) {
			this.positionFromEnd = new HashMap<Long, Integer>();
		}
		this.positionFromEnd.put(elementKey, position);
		POSITION_FROM_END_HIGH_WATER.accumulateAndGet(this.positionFromEnd.size(), Math::max);
	}

	/** 末尾からの通し番号。走査結果が無ければ-1(未対応セレクタと同じ警告+不一致経路へ)。 */
	public int getPositionFromEnd(long elementKey) {
		if (this.positionFromEnd == null) {
			return -1;
		}
		Integer position = this.positionFromEnd.get(elementKey);
		return position != null ? position.intValue() : -1;
	}

	public void setTypePositionFromEnd(long elementKey, int position) {
		if (this.typePositionFromEnd == null) {
			this.typePositionFromEnd = new HashMap<Long, Integer>();
		}
		this.typePositionFromEnd.put(elementKey, position);
		TYPE_POSITION_FROM_END_HIGH_WATER.accumulateAndGet(this.typePositionFromEnd.size(), Math::max);
	}

	public int getTypePositionFromEnd(long elementKey) {
		if (this.typePositionFromEnd == null) {
			return -1;
		}
		Integer position = this.typePositionFromEnd.get(elementKey);
		return position != null ? position.intValue() : -1;
	}
}

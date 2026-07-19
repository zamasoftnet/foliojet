package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * {@code STRUCTURE_SCAN}パス(実レイアウトを組まない軽量な事前走査)が
 * 収集する、要素の終了時点まで真偽が確定しないセレクタの判定結果です。
 * {@code CSSElement.elementKey}(文書順の通し番号、パスをまたいで安定)を
 * キーとする。
 * <p>
 * {@link PageRef}とは別クラス(意図的): {@code PageRef}はURI/id起点の
 * ページ参照・TOC専用ストアで、{@code reset()}が蓄積済みのフラグメントを
 * 消さずカーソルだけ戻す(複数回のLAYOUTパスにまたがって値を段階的に
 * 確定させていく設計)。一方このクラスが保持する事実は、
 * {@code STRUCTURE_SCAN}という単一の専用パスで一度に確定し、以降の
 * 全LAYOUTパスからは読み取り専用として参照される(段階的な確定は無い)。
 * ライフサイクルが異なるため、{@code PageRef}への相乗りはしない
 * (docs/PLAN.md「2パス制御モード」の設計確定 v3参照)。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public final class SelectorFacts {
	private Set<Long> lastChild;
	private Set<Long> lastOfType;
	private Set<Long> empty;

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
	}

	public void setLastChild(long elementKey) {
		if (this.lastChild == null) {
			this.lastChild = new HashSet<Long>();
		}
		this.lastChild.add(elementKey);
	}

	public boolean isLastChild(long elementKey) {
		return this.lastChild != null && this.lastChild.contains(elementKey);
	}

	public void setLastOfType(long elementKey) {
		if (this.lastOfType == null) {
			this.lastOfType = new HashSet<Long>();
		}
		this.lastOfType.add(elementKey);
	}

	public boolean isLastOfType(long elementKey) {
		return this.lastOfType != null && this.lastOfType.contains(elementKey);
	}

	public void setEmpty(long elementKey) {
		if (this.empty == null) {
			this.empty = new HashSet<Long>();
		}
		this.empty.add(elementKey);
	}

	public boolean isEmpty(long elementKey) {
		return this.empty != null && this.empty.contains(elementKey);
	}

	public void setPositionFromEnd(long elementKey, int position) {
		if (this.positionFromEnd == null) {
			this.positionFromEnd = new HashMap<Long, Integer>();
		}
		this.positionFromEnd.put(elementKey, position);
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
	}

	public int getTypePositionFromEnd(long elementKey) {
		if (this.typePositionFromEnd == null) {
			return -1;
		}
		Integer position = this.typePositionFromEnd.get(elementKey);
		return position != null ? position.intValue() : -1;
	}
}

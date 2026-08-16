package net.zamasoft.foliojet.ua;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code @container}クエリのための、要素の事実です(2026-08-15段4——
 * docs/history/2026-08-15-container-queries-design.md §2)。
 * {@code CSSElement.elementKey}をキーとする点は{@link SelectorFacts}と同じ。
 *
 * <p>
 * 2種類の事実を持つ。区別する理由は寿命と書き込み時点が違うため:
 * </p>
 * <ul>
 * <li><b>コンテナ種別・名前</b>: その要素自身の{@code container-type}/
 * {@code container-name}(値の型は{@code container-type: inline-size}か
 * どうかの1bitと名前の並びだけ)。**スタイル解決の時点**(要素の宣言が
 * 確定した直後、レイアウトより前)で書き込める。値は要素の指定値そのもの
 * なので、パスをまたいで変わらない——上書きしても実害はないが、
 * {@link #reset()}のタイミングはSelectorFactsと合わせる。</li>
 * <li><b>実測inline-size</b>: レイアウト確定後(設計§2「finishLayoutで
 * 寸法を確定した時点」)にしか分からない。**このパスの値で毎回上書き**
 * する(:has()の「一度真になったら不変」とは逆——寸法は前のパスより
 * 縮むことも伸びることもある)。次のパス(N+1)の{@code StyleContext.merge}
 * が読む。パス1には値が無い(={@link #getInlineSize}が{@code NaN})ため、
 * 全クエリが偽になり現状のフォールバックと一致する(設計§2)。</li>
 * </ul>
 *
 * @author MIYABE Tatsuhiko
 */
public final class ContainerFacts {
	/** 不動点判定の許容差(pt)。{@code LayoutUtils.THRESHOLD}と同じ値(設計§3)。 */
	private static final double CONVERGENCE_THRESHOLD = 0.5;

	private Map<Long, String[]> containerNames;

	private Map<Long, Double> inlineSize;

	/**
	 * 直前のパス開始時点の{@link #inlineSize}の写し(段5——設計§3/§4の
	 * 不動点判定用)。{@link #beginPass()}が毎パス開始時に更新する。
	 */
	private Map<Long, Double> previousInlineSize;

	/** 2つ前のパス開始時点の写し(段7——振動検出用)。 */
	private Map<Long, Double> beforePreviousInlineSize;

	/**
	 * 振動を検出して値を固定したコンテナ(段7、設計§4「振動は狭いほうへ寄せる」)。
	 * 一度固定したら以降のパスでは書き換えない。
	 */
	private Map<Long, Double> pinnedInlineSize;

	/**
	 * このパスで新たに事実を記録する前に呼びます。{@link SelectorFacts#reset()}
	 * と同じくSTRUCTURE_SCAN開始時に1回だけ呼べば足りる——以降の全パスで
	 * 蓄積・上書きし続ける({@code container-type}/{@code container-name}が
	 * 消えることは無いため)。
	 */
	public void reset() {
		this.containerNames = null;
		this.inlineSize = null;
		this.previousInlineSize = null;
		this.beforePreviousInlineSize = null;
		this.pinnedInlineSize = null;
	}

	/**
	 * 実レイアウトを伴う各パス(MIDDLE_PASS/LAST_PASS)の開始時に呼びます
	 * (段5、設計§3「パスN-1の寸法でクエリ評価→レイアウト→寸法を記録」の
	 * 「パスN-1の寸法」を固定するためのスナップショット)。STRUCTURE_SCAN/
	 * DOCUMENT(1パス変換)では呼ばない——寸法事実そのものが無い。
	 */
	public void beginPass() {
		this.beforePreviousInlineSize = this.previousInlineSize;
		this.previousInlineSize = this.inlineSize == null ? null : new HashMap<Long, Double>(this.inlineSize);
	}

	/**
	 * 直近の{@link #beginPass()}以降に書き込まれた実測inline-sizeが、
	 * その直前(スナップショット時点)から不動点に達しているか(設計§3/§4)。
	 * 判定は0.5pt許容差。片方にしか無いキー(新たにコンテナと判明した、
	 * または前パスでは無かった)も不一致として扱う。
	 *
	 * <p>
	 * 振動(周期2)は{@link #setInlineSize}が検出して狭いほうへ固定するので、
	 * 固定後は不動点として扱われる(=ここでは収束と判定される)。固定が
	 * 起きたかどうかは{@link #hasOscillation()}で分かる。
	 * </p>
	 */
	public boolean isConverged() {
		final Map<Long, Double> before = this.previousInlineSize;
		final Map<Long, Double> after = this.inlineSize;
		final int beforeSize = before == null ? 0 : before.size();
		final int afterSize = after == null ? 0 : after.size();
		if (beforeSize != afterSize) {
			return false;
		}
		if (after == null) {
			return true;
		}
		for (final Map.Entry<Long, Double> entry : after.entrySet()) {
			final Double beforeValue = before.get(entry.getKey());
			if (beforeValue == null || Math.abs(beforeValue.doubleValue() - entry.getValue().doubleValue()) >= CONVERGENCE_THRESHOLD) {
				return false;
			}
		}
		return true;
	}

	/**
	 * elementKeyの要素が{@code container-type: inline-size}のクエリコンテナ
	 * であることを記録します。{@code normal}(非コンテナ)は記録しない
	 * (=容量節約。{@link #isInlineSizeContainer}がfalseを返す既定と一致)。
	 * {@code container-type: size}は第1段階の対象外のためここでは記録しない
	 * (設計§4「container-type: sizeは初回に入れない」)。
	 */
	public void setInlineSizeContainer(long elementKey, String[] names) {
		if (this.containerNames == null) {
			this.containerNames = new HashMap<Long, String[]>();
		}
		this.containerNames.put(elementKey, names);
	}

	public boolean isInlineSizeContainer(long elementKey) {
		return this.containerNames != null && this.containerNames.containsKey(elementKey);
	}

	/** そのコンテナの{@code container-name}(無名なら空配列)。 */
	public String[] getContainerNames(long elementKey) {
		if (this.containerNames == null) {
			return EMPTY_NAMES;
		}
		String[] names = this.containerNames.get(elementKey);
		return names != null ? names : EMPTY_NAMES;
	}

	private static final String[] EMPTY_NAMES = new String[0];

	/**
	 * レイアウト確定後の、そのコンテナのused inline-size(pt)を記録します。
	 *
	 * <p>
	 * 段7(設計§4後半): 周期2の<b>振動</b>——「合致すると縮み、外れると伸びる」
	 * クエリは値がA→B→A→B…と往復して不動点に達しない——を検出したら、
	 * <b>狭いほうへ寄せて固定</b>する。狭いほうはフォールバック側であり、
	 * 現在の挙動に近く内容が欠けにくいため。一度固定したコンテナは以降の
	 * パスで書き換えない(固定が次のパスで再び揺れては意味がない)。
	 * </p>
	 */
	public void setInlineSize(long elementKey, double lengthPt) {
		if (this.inlineSize == null) {
			this.inlineSize = new HashMap<Long, Double>();
		}
		final Long key = elementKey;
		if (this.pinnedInlineSize != null) {
			final Double pinned = this.pinnedInlineSize.get(key);
			if (pinned != null) {
				this.inlineSize.put(key, pinned);
				return;
			}
		}
		// 今回値がN-2の値と一致し、かつN-1の値とは違う ＝ 周期2の往復
		final Double twoAgo = this.beforePreviousInlineSize == null ? null
				: this.beforePreviousInlineSize.get(key);
		final Double oneAgo = this.previousInlineSize == null ? null : this.previousInlineSize.get(key);
		if (twoAgo != null && oneAgo != null
				&& Math.abs(twoAgo.doubleValue() - lengthPt) < CONVERGENCE_THRESHOLD
				&& Math.abs(oneAgo.doubleValue() - lengthPt) >= CONVERGENCE_THRESHOLD) {
			final double narrower = Math.min(lengthPt, oneAgo.doubleValue());
			if (this.pinnedInlineSize == null) {
				this.pinnedInlineSize = new HashMap<Long, Double>();
			}
			this.pinnedInlineSize.put(key, narrower);
			this.inlineSize.put(key, narrower);
			return;
		}
		this.inlineSize.put(key, lengthPt);
	}

	/** 振動を検出して値を固定したコンテナがあるか(診断用)。 */
	public boolean hasOscillation() {
		return this.pinnedInlineSize != null && !this.pinnedInlineSize.isEmpty();
	}

	/** 前パスまでの実測inline-size(pt)。未確定なら{@code Double.NaN}。 */
	public double getInlineSize(long elementKey) {
		if (this.inlineSize == null) {
			return Double.NaN;
		}
		final Double value = this.inlineSize.get(elementKey);
		return value != null ? value.doubleValue() : Double.NaN;
	}
}

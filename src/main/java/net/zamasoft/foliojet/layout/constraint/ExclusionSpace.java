package net.zamasoft.foliojet.layout.constraint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.zamasoft.foliojet.layout.box.params.ClearMode;
import net.zamasoft.foliojet.layout.box.params.FloatSide;
import net.zamasoft.foliojet.layout.util.LayoutUtils;

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
	 * 既に{@code pageSpan.end}昇順(同値は追加順)に並んだリストから
	 * 一括構築します(2026-07-23、codexレビュー指摘のO(N²)解消——
	 * {@code BlockBuilder.floatings}は{@code FLOAT_COMP}安定ソート済み
	 * のため、要素ごとの{@link #plus}挿入は不要でO(N)コピーで足りる)。
	 * 並び順の契約は呼び出し元の責任(assertで検査)。
	 */
	public static ExclusionSpace copyOfSorted(final List<FloatExclusion> ascendingByPageEnd) {
		if (ascendingByPageEnd.isEmpty()) {
			return EMPTY;
		}
		assert isAscendingByPageEnd(ascendingByPageEnd) : "list must be sorted by pageSpan.end ascending";
		return new ExclusionSpace(List.copyOf(ascendingByPageEnd));
	}

	private static boolean isAscendingByPageEnd(final List<FloatExclusion> list) {
		for (int i = 1; i < list.size(); ++i) {
			if (list.get(i - 1).pageSpan().end() > list.get(i).pageSpan().end()) {
				return false;
			}
		}
		return true;
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

	/**
	 * 2つの不変スナップショットを{@code pageSpan.end, order}順にマージします。
	 * 両方の入力が既に整列済みなので、要素ごとの挿入を行わずO(N+M)で
	 * 新しいスナップショットを作る。
	 */
	public ExclusionSpace mergedWith(final ExclusionSpace other) {
		if (other == null) {
			throw new IllegalArgumentException("other must not be null");
		}
		if (this.isEmpty()) {
			return other;
		}
		if (other.isEmpty()) {
			return this;
		}
		final List<FloatExclusion> merged = new ArrayList<>(this.size() + other.size());
		int i = 0, j = 0;
		while (i < this.ascendingByPageEnd.size() && j < other.ascendingByPageEnd.size()) {
			final FloatExclusion a = this.ascendingByPageEnd.get(i);
			final FloatExclusion b = other.ascendingByPageEnd.get(j);
			final int endOrder = Double.compare(a.pageSpan().end(), b.pageSpan().end());
			if (endOrder < 0 || endOrder == 0 && a.order() <= b.order()) {
				merged.add(a);
				++i;
			} else {
				merged.add(b);
				++j;
			}
		}
		merged.addAll(this.ascendingByPageEnd.subList(i, this.ascendingByPageEnd.size()));
		merged.addAll(other.ascendingByPageEnd.subList(j, other.ascendingByPageEnd.size()));
		return copyOfSorted(merged);
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

	/**
	 * {@code BlockBuilder.startFlowBlock}のmulticol回避と同じ規則で、
	 * {@code lineBand}を浮動体が占める帯だけ狭めます(2026-07-23新設、
	 * P0 Step3のshadow比較用——`BlockBuilder`の既存ループを1対1で
	 * 移した実装。挙動を変えないことが目的なので、比較演算子・走査順は
	 * 既存ループと完全に同じにする)。
	 *
	 * <p>
	 * {@code pageAxis}以下(浮動体の下端がpage軸開始位置以前)まで
	 * 遡ったら打ち切る——既存ループの{@code break}と同じ。
	 * </p>
	 */
	public AxisSpan narrowLineBandForMulticol(final double pageAxis, final AxisSpan lineBand) {
		double lineStart = lineBand.start();
		double lineEnd = lineBand.end();
		for (int i = this.ascendingByPageEnd.size() - 1; i >= 0; --i) {
			final FloatExclusion exclusion = this.ascendingByPageEnd.get(i);
			if (exclusion.pageSpan().end() <= pageAxis) {
				break;
			}
			switch (exclusion.side()) {
			case START:
				lineStart = Math.max(lineStart, exclusion.lineSpan().end());
				break;
			case END:
				lineEnd = Math.min(lineEnd, exclusion.lineSpan().start());
				break;
			}
		}
		return new AxisSpan(lineStart, lineEnd);
	}

	/**
	 * {@code BlockBuilder.startFlowBlock}のclear処理と同じ規則で、
	 * clearの対象になる浮動体を探します(2026-07-23新設、P0 Step3の
	 * shadow比較用——既存ループを1対1で移した実装)。
	 *
	 * <p>
	 * {@code pageEnd}は既存コードと同じく{@code marginStart}を引いた
	 * 相対値で比較する(算術的には{@code pageStart}側へ足し戻すのと
	 * 同値だが、浮動小数点の丸めまで完全に再現するため既存コードと
	 * 同じ引き算の順序・箇所を保つ)。descending順で最初に見つかった
	 * 対象だけを返す——{@code pageEnd <= pageStart}になった時点で
	 * (現ページより手前の浮動体に達したら)探索を打ち切り、見つから
	 * なかったものとして{@code null}を返す。
	 * </p>
	 */
	public FloatExclusion findClearBoundary(final double pageStart, final double marginStart, final ClearMode clear) {
		for (int i = this.ascendingByPageEnd.size() - 1; i >= 0; --i) {
			final FloatExclusion exclusion = this.ascendingByPageEnd.get(i);
			final double pageEnd = exclusion.pageSpan().end() - marginStart;
			if (pageEnd <= pageStart) {
				return null;
			}
			switch (clear) {
			case START:
				if (exclusion.side() == FloatSide.START) {
					return exclusion;
				}
				break;
			case END:
				if (exclusion.side() == FloatSide.END) {
					return exclusion;
				}
				break;
			case BOTH:
				return exclusion;
			default:
				throw new IllegalStateException();
			}
		}
		return null;
	}

	/**
	 * {@code BlockBuilder.addBound}(置換要素・表がフロー中で浮動体を
	 * 避ける処理)と同じ規則を再現します(2026-07-23新設、P0 Step3の
	 * shadow比較用——既存ループを1対1で移した実装)。
	 *
	 * <p>
	 * このループは2つの独立した規則を1回の走査で行う既存構造を
	 * そのまま保つ: (1) {@code clear}指定に応じた境界浮動体の探索
	 * (見つかったら即座に走査終了)、(2) 見つからなかった場合の
	 * START/END浮動体によるline帯の狭窄(START側は既存コードの
	 * 挙動どおり{@code xMarginStart}を0にリセットして走査を打ち切る
	 * ——既存コードのコメントにある非対称な既存挙動をそのまま再現)。
	 * </p>
	 */
	public BoundAvoidance findBoundAvoidance(final double pageStart, final double lineSize, final double lineStop,
			final double marginAdjust, final ClearMode clear) {
		double xMarginStart = 0, lineEnd = lineStop;
		for (int i = this.ascendingByPageEnd.size() - 1; i >= 0; --i) {
			final FloatExclusion exclusion = this.ascendingByPageEnd.get(i);
			final double pageEnd = exclusion.pageSpan().end() - marginAdjust;
			if (pageStart >= pageEnd) {
				return new BoundAvoidance(null, 0, xMarginStart, lineEnd);
			}
			switch (clear) {
			case NONE:
				break;
			case START:
				if (exclusion.side() == FloatSide.START) {
					return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
				}
				break;
			case END:
				if (exclusion.side() == FloatSide.END) {
					return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
				}
				break;
			case BOTH:
				return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
			default:
				throw new IllegalStateException();
			}
			switch (exclusion.side()) {
			case START:
				// END側と対称に「横に入るか」を検査する(2026-08-10)。従来は
				// 開始側floatだと無条件にclearing扱い=常に下ろしていたため、
				// 幅が十分でも表がfloat下端まで落ちた(cocoon.apache.orgの
				// 左ナビ約630pt+width:100%表で、本文が丸ごとページ2へ)。
				// Chromeはfloat右端(178px)と表左端(193px)が非干渉なら
				// 最上部へ並べる(実測)。入らない場合は従来どおり下ろす
				if (LayoutUtils.compare(lineEnd - exclusion.lineSpan().end(), lineSize) < 0) {
					return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
				}
				xMarginStart = Math.max(xMarginStart, exclusion.lineSpan().end());
				break;
			case END:
				if (LayoutUtils.compare(exclusion.lineSpan().start() - xMarginStart, lineSize) < 0) {
					lineEnd = lineStop;
					return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
				}
				lineEnd = Math.min(lineEnd, exclusion.lineSpan().start());
				break;
			default:
				throw new IllegalStateException();
			}
		}
		return new BoundAvoidance(null, 0, xMarginStart, lineEnd);
	}

	/**
	 * {@link #findBoundAvoidance}の結果です(2026-07-23新設)。
	 * {@code clearingExclusion}が非nullなら、それがclearの境界になった
	 * 浮動体で{@code clearPageEnd}がその際の(margin調整済み)pageEnd。
	 * {@code clearingExclusion}がnullの場合、clearによる境界移動はなく
	 * {@code xMarginStart}/{@code lineEnd}がそのまま採用される狭窄結果。
	 */
	public record BoundAvoidance(FloatExclusion clearingExclusion, double clearPageEnd, double xMarginStart,
			double lineEnd) {
	}

	/**
	 * {@code TextBuilder.locateLine}の1回分の行帯走査と同じ規則を
	 * 再現します(2026-07-23新設、P0 Step3のshadow比較用——既存ループを
	 * 1対1で移した実装)。他3消費者とは異なり{@code ascendingByPageEnd}
	 * (昇順)で走査する——既存コードの{@code for (i = 0; i <
	 * floatings.size(); ++i)}と同じ順序。
	 *
	 * <p>
	 * 4つの照会のうちこれだけが{@code shape-outside}の形状
	 * ({@link FloatExclusion#lineSpanAt})を見る(2026-08-29)。
	 * css-shapes-1 §4.1は形状の影響をインライン内容の折返しに限定し、
	 * 浮動体の配置・BFCを作るブロックの回避はマージンボックスのまま
	 * (Chromeも同じ)なので、他3照会は矩形{@code lineSpan}を読み続ける。
	 * </p>
	 */
	public LineScan scanLineBand(final double pageStart, final double lineHeight, final double lineStart0,
			final double lineEnd0) {
		FloatExclusion startExclusion = null, endExclusion = null;
		double lineStart = lineStart0;
		double lineEnd = lineEnd0;
		boolean maxPageSizeSet = false;
		double maxPageSize = 0;
		for (final FloatExclusion exclusion : this.ascendingByPageEnd) {
			if (LayoutUtils.compare(pageStart, exclusion.pageSpan().end()) >= 0) {
				continue;
			}
			if (LayoutUtils.compare(exclusion.pageSpan().start(), pageStart + lineHeight) >= 0) {
				maxPageSizeSet = true;
				maxPageSize = exclusion.pageSpan().start() - pageStart;
				break;
			}
			// shape-outside(2026-08-29): 行の高さ全体の帯で形状が占める範囲。
			// 形状なしは従来どおりマージンボックス。帯と形状が交わらない
			// 浮動体はこの行を狭めない(円の上下の空白へ行が入り込める)
			final AxisSpan band = exclusion.lineSpanAt(pageStart, pageStart + lineHeight);
			if (band == null) {
				continue;
			}
			switch (exclusion.side()) {
			case START:
				final double tempStart = band.end();
				if (LayoutUtils.compare(tempStart, lineStart) >= 0) {
					startExclusion = exclusion;
					lineStart = tempStart;
				}
				continue;
			case END:
				final double tempEnd = band.start();
				if (LayoutUtils.compare(tempEnd, lineEnd) <= 0) {
					endExclusion = exclusion;
					lineEnd = tempEnd;
				}
				continue;
			default:
				throw new IllegalStateException();
			}
		}
		return new LineScan(startExclusion, endExclusion, lineStart, lineEnd, maxPageSizeSet, maxPageSize);
	}

	/**
	 * ページフロート用に、未来から始まる排除域で打ち切らず全件を走査します。
	 *
	 * <p>
	 * 通常フロートは「登録済みの排除域は現在位置以前から始まる」という
	 * 不変条件を持つため、{@link #scanLineBand}は最初の未来開始で走査を
	 * 打ち切る。ページ末へ置くbottomフロートは登録時点では未来から
	 * 始まるので、その集合だけをこの走査へ分離する。通常フロートの
	 * 走査順・比較・早期終了は変更しない。
	 * </p>
	 */
	public LineScan scanLineBandFully(final double pageStart, final double lineHeight, final double lineStart0,
			final double lineEnd0) {
		FloatExclusion startExclusion = null, endExclusion = null;
		double lineStart = lineStart0;
		double lineEnd = lineEnd0;
		boolean maxPageSizeSet = false;
		double maxPageSize = 0;
		for (final FloatExclusion exclusion : this.ascendingByPageEnd) {
			if (LayoutUtils.compare(pageStart, exclusion.pageSpan().end()) >= 0) {
				continue;
			}
			if (LayoutUtils.compare(exclusion.pageSpan().start(), pageStart + lineHeight) >= 0) {
				final double candidate = exclusion.pageSpan().start() - pageStart;
				if (!maxPageSizeSet || LayoutUtils.compare(candidate, maxPageSize) < 0) {
					maxPageSizeSet = true;
					maxPageSize = candidate;
				}
				continue;
			}
			final AxisSpan band = exclusion.lineSpanAt(pageStart, pageStart + lineHeight);
			if (band == null) {
				continue;
			}
			switch (exclusion.side()) {
			case START:
				final double tempStart = band.end();
				if (LayoutUtils.compare(tempStart, lineStart) >= 0) {
					startExclusion = exclusion;
					lineStart = tempStart;
				}
				continue;
			case END:
				final double tempEnd = band.start();
				if (LayoutUtils.compare(tempEnd, lineEnd) <= 0) {
					endExclusion = exclusion;
					lineEnd = tempEnd;
				}
				continue;
			default:
				throw new IllegalStateException();
			}
		}
		return new LineScan(startExclusion, endExclusion, lineStart, lineEnd, maxPageSizeSet, maxPageSize);
	}

	/**
	 * {@link #scanLineBand}の結果です(2026-07-23新設)。
	 * {@code maxPageSizeSet}がfalseの場合、呼び出し元は
	 * {@code TextBuilder.maxPageSize}相当の値を更新してはならない——
	 * 既存コードはこの走査でその分岐に到達したときだけ更新し、到達
	 * しなければ外側の再探索ループの前回反復の値をそのまま持ち越す。
	 */
	public record LineScan(FloatExclusion startExclusion, FloatExclusion endExclusion, double lineStart,
			double lineEnd, boolean maxPageSizeSet, double maxPageSize) {
	}

	/**
	 * {@code BlockBuilder.addStartFloat}/{@code addEndFloat}(新規floatの
	 * 配置先探索)と同じ規則を再現します(2026-07-23新設、P0 Step3最後の
	 * 消費者——既存ループを1対1で移した実装。両メソッドは完全に同一の
	 * アルゴリズムを重複して持つため、このqueryも共有できる)。
	 *
	 * <p>
	 * clear境界に遭遇した場合、それまでの{@code startExclusion}/
	 * {@code endExclusion}/{@code lineStart}/{@code lineEnd}を保持した
	 * まま{@code pageStart}だけ更新して即座に返す。
	 * </p>
	 */
	public FloatPlacementScan scanFloatPlacementBand(final double pageStartIn, final double lineStart0,
			final double lineEnd0, final ClearMode clear) {
		double pageStart = pageStartIn;
		FloatExclusion startExclusion = null, endExclusion = null;
		double lineStart = lineStart0, lineEnd = lineEnd0;
		for (int i = this.ascendingByPageEnd.size() - 1; i >= 0; --i) {
			final FloatExclusion exclusion = this.ascendingByPageEnd.get(i);
			final double pageEnd = exclusion.pageSpan().end();
			if (LayoutUtils.compare(pageStart, pageEnd) >= 0) {
				break;
			}
			switch (clear) {
			case NONE:
				break;
			case START:
				if (exclusion.side() == FloatSide.START) {
					pageStart = pageEnd;
					return new FloatPlacementScan(startExclusion, endExclusion, lineStart, lineEnd, pageStart);
				}
				break;
			case END:
				if (exclusion.side() == FloatSide.END) {
					pageStart = pageEnd;
					return new FloatPlacementScan(startExclusion, endExclusion, lineStart, lineEnd, pageStart);
				}
				break;
			case BOTH:
				pageStart = pageEnd;
				return new FloatPlacementScan(startExclusion, endExclusion, lineStart, lineEnd, pageStart);
			default:
				throw new IllegalStateException();
			}
			switch (exclusion.side()) {
			case START:
				final double tempStart = exclusion.lineSpan().end();
				if (LayoutUtils.compare(tempStart, lineStart) >= 0) {
					startExclusion = exclusion;
					lineStart = tempStart;
				}
				continue;
			case END:
				final double tempEnd = exclusion.lineSpan().start();
				if (LayoutUtils.compare(tempEnd, lineEnd) <= 0) {
					endExclusion = exclusion;
					lineEnd = tempEnd;
				}
				continue;
			default:
				throw new IllegalStateException();
			}
		}
		return new FloatPlacementScan(startExclusion, endExclusion, lineStart, lineEnd, pageStart);
	}

	/**
	 * {@link #scanFloatPlacementBand}の結果です(2026-07-23新設)。
	 * {@code pageStart}はclearの条件一致で更新された値——呼び出し元は
	 * 常にこの値を採用する。
	 */
	public record FloatPlacementScan(FloatExclusion startExclusion, FloatExclusion endExclusion, double lineStart,
			double lineEnd, double pageStart) {
	}
}

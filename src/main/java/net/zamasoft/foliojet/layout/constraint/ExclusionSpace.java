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
		for (final FloatExclusion exclusion : this.descendingByPageEnd()) {
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
		for (final FloatExclusion exclusion : this.descendingByPageEnd()) {
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
		for (final FloatExclusion exclusion : this.descendingByPageEnd()) {
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
				xMarginStart = 0;
				// BlockBuilder.computeBoundAvoidanceの対応するjavadoc参照:
				// 既存コードはこの分岐でもループ後のclearance適用と同じ
				// 経路を通るため、ここもclearing扱いにする(2026-07-23
				// 発見の実挙動)。
				return new BoundAvoidance(exclusion, pageEnd, xMarginStart, lineEnd);
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
}

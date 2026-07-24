package net.zamasoft.foliojet.layout.box.content;

/**
 * {@link Floatings}のページ分割の型付き結果です(2026-07-24新設、
 * 排除域P2のP2-3。{@code docs/consultations/consult-exclusion-p2-design-codex.txt}
 * §2.2の型)。旧sentinel(null=KeepAll / this=MoveAll / 新=Partition)を
 * 置き換えます。
 *
 * <p>
 * <b>語彙対応(E-4)</b>: {@code All}接尾辞は「台帳({@link Floatings})
 * 全体」の粒度を表す——box 1個粒度の
 * {@code SplitResult.Keep}/{@code Move}、float 1個粒度の
 * {@link FloatSplitPlan.FloatItemPlan}とは意図的に区別している。
 * {@code Partition}は台帳を残す側と送る側({@code remainder})へ二分する
 * ことを表し、box 1個の{@code SplitResult.Split}とは別概念。全体表は
 * {@code SplitResult}のjavadoc参照。
 * </p>
 *
 * @author MIYABE Tatsuhiko
 */
public sealed interface FloatSplitResult {
	/** 全floatを元のフラグメントに残します(旧 null)。元リストは無傷。 */
	FloatSplitResult KEEP_ALL = new KeepAll();

	/**
	 * 全floatを丸ごと次のフラグメントへ送ります(旧 this)。遅延表現——
	 * floatは元の{@link Floatings}に残したままで、台帳ごとの付け替えは
	 * 呼び出し側(owner)が行う。
	 */
	FloatSplitResult MOVE_ALL = new MoveAll();

	record KeepAll() implements FloatSplitResult {
	}

	record MoveAll() implements FloatSplitResult {
	}

	/**
	 * 一部を次のフラグメントへ送ります(旧 新Floatings)。元の
	 * {@link Floatings}にはKEEPとSPLIT元が元順序で残り、{@code remainder}
	 * にはMOVEされた元のFloatingとSPLITの残余(座標(0,0)・serial引き継ぎ)
	 * が元順序で入る。{@code remainder}は空にならない。
	 *
	 * @param remainder 次のフラグメントへ送る台帳(非空)
	 */
	record Partition(Floatings remainder) implements FloatSplitResult {
	}
}

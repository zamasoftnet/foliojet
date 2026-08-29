package net.zamasoft.foliojet.layout.util;

import net.zamasoft.pdfg2d.gc.GC;

/**
 * 別の{@link GC}へ委譲する包み紙です(2026-08-29)。
 *
 * <p>
 * {@link FilterGC}(filterの色変換)と{@link ApproximationGC}(近似の報告)は
 * ページのGCを包んで描画要素へ渡す。PDF固有の処理(構造タグ・注釈)は
 * 描画先が{@code PDFGC}かどうかを{@code instanceof}で見分けるので、
 * 包み紙越しでも中身に辿り着けるよう{@link #unwrap}を用意する。
 * </p>
 */
public interface DelegatingGC {
	/** 包んでいるGC。 */
	public GC delegate();

	/** 包み紙を全て剥がした中身のGCを返します(包まれていなければそのまま)。 */
	public static GC unwrap(GC gc) {
		while (gc instanceof DelegatingGC d) {
			gc = d.delegate();
		}
		return gc;
	}
}

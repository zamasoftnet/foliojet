package net.zamasoft.foliojet.layout.text;

/**
 * 1グリフ分のCSS組版幅式です(2026-08-01、85点計画増分3——計測単一
 * コアの第一歩)。
 *
 * <p>
 * フォント基礎advance(kerning込み、pdfg2dのTextImplが唯一の所有者)の
 * 上に載るCSS層の幅会計——letter-spacing・autospaceギャップ・約物詰め
 * ——は、従来TextBuilder.glyph()/TotalFitSession.recordGlyph()/
 * intrinsic計測に同じ式が複製されていた(「幅会計3系統」)。この
 * recordがその式の唯一の定義であり、<b>3系統すべて</b>がこれを通じて
 * 同じ値を計算する(通常組版・K-P鏡像=2026-08-01増分3、
 * intrinsic計測=2026-08-06増分5で接続完了)。
 * </p>
 *
 * <p>
 * 適用の作法: 行会計への加算は{@link #baseAndSpacing()}と
 * {@link #adjustment()}を従来どおりの順で別々に足してよい(浮動小数点の
 * 加算順を保存するため)。一括での見積り(折返し予測・K-P候補幅)は
 * {@link #totalAdvance()}を使う。intrinsic計測は方針が2点違う
 * (gapはmax-contentのみ・加算順が(base−trim)+spacing)ため成分別に足す
 * ——理由はIntrinsicMeasurer.glyph()のjavadocを見よ。
 * </p>
 *
 * @param baseAdvance     フォント基礎advance(kerning込み。
 *                        TextImpl.appendGlyphの返り値またはglyphAdvance)
 * @param letterSpacing   letter-spacing
 * @param autospaceGap    text-autospaceのアキ(正)
 * @param punctuationTrim 約物詰め(正の値で詰める)
 * @author MIYABE Tatsuhiko
 */
public record GlyphMeasureStep(double baseAdvance, double letterSpacing, double autospaceGap,
		double punctuationTrim) {

	/** クラスタ境界の調整量(gap−trim)。xadvanceへ焼き込まれる値。 */
	public double adjustment() {
		return this.autospaceGap - this.punctuationTrim;
	}

	/** 基礎advance+letter-spacing(調整前の前進量)。 */
	public double baseAndSpacing() {
		return this.baseAdvance + this.letterSpacing;
	}

	/** このグリフの総幅(見積り・候補幅用)。 */
	public double totalAdvance() {
		return this.baseAndSpacing() + this.adjustment();
	}
}

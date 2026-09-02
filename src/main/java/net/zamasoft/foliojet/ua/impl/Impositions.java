package net.zamasoft.foliojet.ua.impl;

import net.zamasoft.foliojet.css.util.ValueUtils;
import net.zamasoft.foliojet.css.value.AbsoluteLengthValue;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;

/**
 * 出力設定から面付け({@link Imposition})を作る側の窓口です(2026-09-02)。
 *
 * <p>
 * 以前は{@code layout.util.LayoutUtils}にあり、layout が ua.impl の具象
 * ({@code SinglePageImposition}等)を知る逆依存になっていた(設計レビュー
 * §1-1)。面付けの選択は出力(UA)側の関心なのでここへ移した。
 * </p>
 */
public final class Impositions {
	private Impositions() {
	}

	/**
	 * 現在の処理段階と出力設定に対応する面付けを作ります。
	 * 中間パスと構造走査ではページの論理的な進行だけを保ち、GCや
	 * serializerを一切起動しません。
	 */
	public static Imposition createImposition(final UserAgent ua) {
		if (ua.isMeasurePass() || ua.isStructureScanPass()) {
			return new NopImposition(ua);
		}
		final int nUp = UAProps.OUTPUT_N_UP.getInteger(ua);
		if (nUp > 1) {
			return new NUpImposition(ua, nUp, UAProps.OUTPUT_N_UP_ORDER.get(ua));
		}
		if (nUp < 1) {
			ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_N_UP.name, String.valueOf(nUp));
		}
		return new SinglePageImposition(ua);
	}

	public static void setupImposition(final UserAgent ua, final Imposition imposition) {
		imposition.setAutoRotate(UAProps.OUTPUT_AUTO_ROTATE.get(ua));
		imposition.setAlign(UAProps.OUTPUT_FIT_TO_PAPER.get(ua));

		// 左右断ちしろ
		{
			String s = UAProps.OUTPUT_HTRIM.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
			if (length != null) {
				double l = length.getLength();
				imposition.setTrims(imposition.getTrimTop(), l, imposition.getTrimBottom(), l);
			} else {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_HTRIM.name, s);
			}
		}
		// 上下断ちしろ
		{
			String s = UAProps.OUTPUT_VTRIM.getString(ua);
			AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
			if (length != null) {
				double l = length.getLength();
				imposition.setTrims(l, imposition.getTrimRight(), l, imposition.getTrimLeft());
			} else {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_VTRIM.name, s);
			}
		}
		{
			double[] trims;
			String s = UAProps.OUTPUT_TRIMS.getString(ua);
			if (s != null) {
				String[] values = s.split("[\\s]+");
				if (values.length <= 0 || values.length > 4) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_TRIMS.name, s);
					trims = null;
				} else {
					trims = new double[values.length];
					for (int i = 0; i < values.length; ++i) {
						AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, values[i]);
						if (length != null) {
							trims[i] = length.getLength();
						} else {
							ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_TRIMS.name, s);
							trims = null;
							break;
						}
					}
				}
				switch (trims.length) {
				case 1:
					imposition.setTrims(trims[0], trims[0], trims[0], trims[0]);
					break;
				case 2:
					imposition.setTrims(trims[0], trims[1], trims[0], trims[1]);
					break;
				case 3:
					imposition.setTrims(trims[0], trims[1], trims[2], trims[1]);
					break;
				case 4:
					imposition.setTrims(trims[0], trims[1], trims[2], trims[3]);
					break;
				}
			}
		}

		// トンボ
		switch (UAProps.OUTPUT_MARKS.get(ua)) {
		case NONE:
			imposition.setTrims(0, 0, 0, 0);
			imposition.setCuttingMargin(0);
			imposition.setNote(null);
			break;
		case CROP:
			imposition.setCrop(true);
			imposition.setNote("page {0}");
			break;
		case CROSS:
			imposition.setCross(true);
			imposition.setNote("page {0}");
			break;
		case BOTH:
			imposition.setCrop(true);
			imposition.setCross(true);
			imposition.setNote("page {0}");
			break;
		case HIDDEN:
			imposition.setNote("page {0}");
			break;
		default:
			throw new IllegalStateException();
		}
		// 塗り足し込みデータの仕上り位置(2026-08-29、利用者報告B-3)。
		// output.marksがnoneのときにドブを0にする分岐より**後**に置く
		// ——外周の帯はそのままドブ(塗り足し)なので、ここで入れ直す
		{
			String s = UAProps.OUTPUT_TRIM_INSET.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null && length.getLength() >= 0) {
					final double l = length.getLength();
					imposition.setTrimInset(l);
					imposition.setCuttingMargin(l);
				} else {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_TRIM_INSET.name, s);
				}
			}
		}

		imposition.setClip(UAProps.OUTPUT_CLIP.getBoolean(ua));

		// 背表紙
		{
			String s = UAProps.OUTPUT_MARKS_SPINE_WIDTH.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setSpineWidth(l);
					if (imposition.getNote() != null) {
						imposition.setNote(imposition.getNote() + " / spine " + s);
					}
				} else {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_MARKS_SPINE_WIDTH.name, s);
				}
			}
		}

		{
			// 用紙幅
			String s = UAProps.OUTPUT_PAPER_WIDTH.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setPaperWidth(l);
				} else {
					imposition.fitPaperWidth();
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAPER_WIDTH.name, s);
				}
			} else {
				imposition.fitPaperWidth();
			}
		}

		{
			// 用紙高さ
			String s = UAProps.OUTPUT_PAPER_HEIGHT.getString(ua);
			if (s != null) {
				AbsoluteLengthValue length = ValueUtils.toAbsoluteLength(ua, false, s);
				if (length != null) {
					double l = length.getLength();
					imposition.setPaperHeight(l);
				} else {
					imposition.fitPaperHeight();
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PAPER_HEIGHT.name, s);
				}
			} else {
				imposition.fitPaperHeight();
			}
		}
	}
}

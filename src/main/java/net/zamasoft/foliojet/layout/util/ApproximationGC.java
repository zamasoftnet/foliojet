package net.zamasoft.foliojet.layout.util;

import java.util.Set;

import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.GroupImageGC;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * 「近似で描いた」ことを利用者へ知らせる経路を、ページのGCに載せる
 * 包み紙です(2026-08-29)。
 *
 * <p>
 * ぼかし・円錐グラデーション・繰り返しグラデーション・filter・
 * mix-blend-modeは、出力先が厳密に描けるか({@link GC#supports})を描画時に
 * 問い合わせ、できなければ近似する。近似したことは
 * {@link MessageCodes#WARN_APPROXIMATED_RENDERING}(2822)で知らせるが、
 * 描画要素は描画先のGCしか受け取らず、UAへの経路が無い。そこで
 * {@code PageSequence.drawPage}がページのGCをこれで包み、描画要素は
 * {@link #report}で(包み紙を辿って)報告する。同じ文書で同じ近似を
 * 何度も報告しないよう、報告済みの鍵は{@link net.zamasoft.foliojet.ua.UAContext}
 * (=変換1回の寿命)の集合に控える。
 * </p>
 *
 * <p>
 * {@link FilterGC}のような他の包み紙の内側からでも辿れるよう、
 * {@link DelegatingGC#delegate()}を順に剥がして探す。グループ画像の
 * GCも同じ報告先を持つ包み紙で返す。
 * </p>
 */
public final class ApproximationGC extends AbstractDelegatingGC {
	private final UserAgent ua;
	private final String outputType;
	private final Set<String> reported;

	private ApproximationGC(final GC gc, final UserAgent ua, final String outputType, final Set<String> reported) {
		super(gc);
		this.ua = ua;
		this.outputType = outputType;
		this.reported = reported;
	}

	/** ページのGCを包みます。 */
	public static GC wrap(final GC gc, final UserAgent ua) {
		if (gc == null || gc instanceof ApproximationGC) {
			return gc;
		}
		return new ApproximationGC(gc, ua, UAProps.OUTPUT_TYPE.getString(ua),
				ua.getUAContext().getReportedApproximations());
	}

	/**
	 * 近似で描いたことを報告します。{@code gc}が報告経路を持たなければ
	 * (単体テストの素のGCなど)何もしない。
	 *
	 * @param gc       描画先(包み紙でもよい)
	 * @param property CSSのプロパティ名({@code box-shadow}など、字面のまま)
	 * @param detail   近似の内容(利用者向けの短い説明)
	 */
	public static void report(GC gc, final String property, final String detail) {
		while (gc != null) {
			if (gc instanceof ApproximationGC a) {
				a.approximated(property, detail);
				return;
			}
			gc = gc instanceof DelegatingGC d ? d.delegate() : null;
		}
	}

	private void approximated(final String property, final String detail) {
		if (this.reported.add(property + ' ' + detail)) {
			this.ua.message(MessageCodes.WARN_APPROXIMATED_RENDERING, property, this.outputType, detail);
		}
	}

	@Override
	public GroupImageGC createGroupImage(final double width, final double height) throws GraphicsException {
		return new Group(this.gc.createGroupImage(width, height), this);
	}

	/** グループ画像のGCにも同じ報告経路を載せる包み紙。 */
	private static final class Group extends AbstractDelegatingGC implements GroupImageGC {
		private final GroupImageGC group;

		Group(final GroupImageGC group, final ApproximationGC outer) {
			super(new ApproximationGC(group, outer.ua, outer.outputType, outer.reported));
			this.group = group;
		}

		@Override
		public Image finish() throws GraphicsException {
			return this.group.finish();
		}
	}
}

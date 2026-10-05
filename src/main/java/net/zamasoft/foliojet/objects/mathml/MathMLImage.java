package net.zamasoft.foliojet.objects.mathml;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

import net.zamasoft.foliojet.layout.box.content.BaselineImage;
import net.zamasoft.pdfg2d.g2d.gc.BridgeGraphics2D;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.sourceforge.jeuclid.layout.JEuclidView;

/**
 * 組んだ数式です。基準線の下へ下がる深さ({@link #getDescent()})を持ち、
 * 行の中では下端でなく基準線を本文の基準線に合わせて置かれる
 * (2026-10-04。以前は下端を置いたので、添字・括弧・y のある式が浮いた)。
 *
 * <p>
 * 縦組みの行では欧文と同じく<b>横倒し</b>(90° 右回り)にする({@code sideways}、2026-10-05)。幅と高さを入れ替えるので、
 * 行の進む向きに式の幅だけ進み、行の幅は式の高さになる。以前は正立の横組みの箱のまま置いたので、行の向きには
 * 1 字ほどしか進まず、式の幅が隣の行へはみ出した。
 * </p>
 */
public class MathMLImage implements Image, BaselineImage {
	protected final JEuclidView view;

	/** 拡大率(文字の拡大。CSS の大きさを受け取ったときは 1)。 */
	protected final double scale;

	/** 縦組みの行で横倒しにするか。 */
	protected final boolean sideways;

	public MathMLImage(JEuclidView view) {
		this(view, 1.0, false);
	}

	public MathMLImage(JEuclidView view, double scale, boolean sideways) {
		this.view = view;
		this.scale = scale;
		this.sideways = sideways;
	}

	/** 組んだ式の幅(横倒しにする前)。 */
	private double mathWidth() {
		return this.view.getWidth() * this.scale;
	}

	/** 組んだ式の高さ(横倒しにする前)。 */
	private double mathHeight() {
		return (this.view.getAscentHeight() + this.view.getDescentHeight()) * this.scale;
	}

	public double getWidth() {
		return this.sideways ? this.mathHeight() : this.mathWidth();
	}

	public double getHeight() {
		return this.sideways ? this.mathWidth() : this.mathHeight();
	}

	public double getDescent() {
		return this.view.getDescentHeight() * this.scale;
	}

	public void drawTo(GC gc) throws GraphicsException {
		try (final var gcState = gc.begin()) {
			if (this.sideways) {
				// 90° 右回り: 式の上が右(行の上の側)を向き、読む向きが下になる
				gc.transform(new AffineTransform(0, 1, -1, 0, this.mathHeight(), 0));
			}
			if (this.scale != 1.0) {
				gc.transform(AffineTransform.getScaleInstance(this.scale, this.scale));
			}
			// BridgeGraphics2D の setTransform は GC を直近の begin() へ戻してから掛け直すので、回転と拡大の後に
			// 保存点を置く。置かないと JEuclid が変換を設定し直したところで回転と拡大が捨てられた
			try (final var transformed = gc.begin()) {
				Graphics2D g2d = new BridgeGraphics2D(gc);
				this.view.draw(g2d, 0, this.view.getAscentHeight());
				g2d.dispose();
			}
		}
	}

	public String getAltString() {
		return null;
	}
}

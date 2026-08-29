package net.zamasoft.foliojet.ua.impl.pagedsvg;

import java.util.List;

import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.image.Image;

/**
 * SVGの断片として保持した層(グループ画像)です(2026-08-29)。
 *
 * <p>
 * 不透明度・filter・mix-blend-modeの層は、PDFでは透明化グループ、
 * Java2Dではラスタになるが、ブラウザが描くSVGでは{@code <g>}で包むだけで
 * よく、中身はベクタのまま残せる。{@code DirectPagedSVGGC.createGroupImage}
 * は層の中身を別のバッファへ書き、{@code finish()}でこの絵にする。
 * 描くときは{@code <g transform opacity filter style>}で包んで流し込む。
 * </p>
 *
 * <p>
 * 中身の文字(ページJSONの文字位置)は層の座標系で記録されているので、
 * 描く位置が決まったときに変換を掛けてページへ移す({@link #textRuns})。
 * </p>
 */
final class SVGFragmentImage implements Image {
	private final String svg;
	private final double width, height;
	private final List<PagedSVGResources.TextRun> textRuns;

	SVGFragmentImage(final String svg, final double width, final double height,
			final List<PagedSVGResources.TextRun> textRuns) {
		this.svg = svg;
		this.width = width;
		this.height = height;
		this.textRuns = textRuns;
	}

	String svg() {
		return this.svg;
	}

	/** 層の座標系で記録された文字列。 */
	List<PagedSVGResources.TextRun> textRuns() {
		return this.textRuns;
	}

	@Override
	public double getWidth() {
		return this.width;
	}

	@Override
	public double getHeight() {
		return this.height;
	}

	/** SVGの断片はSVGにしか置けない。 */
	@Override
	public void drawTo(final GC gc) throws GraphicsException {
		throw new UnsupportedOperationException("SVG fragment can only be drawn to the SVG writer");
	}

	@Override
	public String getAltString() {
		return null;
	}
}

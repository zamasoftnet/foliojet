package net.zamasoft.foliojet.ua.impl.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileCacheImageOutputStream;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.css.value.ext.CSSJFontPolicyValue;
import net.zamasoft.foliojet.layout.box.impl.TargetCounterSlotImage;
import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.zstream.io.SequentialOutput;
import net.zamasoft.zstream.io.util.FragmentOutputAdapter;
import net.zamasoft.zstream.io.util.SequentialOutputAdapter;
import net.zamasoft.pdfg2d.g2d.gc.G2DGC;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.RecorderGC;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.pdf.font.FontManagerImpl;
import net.zamasoft.foliojet.ua.PrepareMode;

public class ImageUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	/**
	 * ラスタ画像出力の既定のフォント方針は<b>埋め込み</b>です(2026-09-03、
	 * ユーザー判断。単一SVG・ページ分割SVGと同じ)。
	 *
	 * <p>
	 * 共通の既定{@code cid-keyed}はPDFの外部参照CIDフォントを前提にした方針で、
	 * 画像にはその仕組みが無い。字形データを持たないCID-keyedフォントは
	 * AWTのシステムフォント({@code SystemCIDFont})の代替に落ち、別の面の
	 * ヒント済み輪郭で描かれていた(単一SVGで実測: 「日」が本物より6%広く
	 * 縦画が太い)。埋め込み方針ならpdfg2d自身の輪郭で、PDFと同じ字形になる。
	 * 利用者が{@code output.pdf.fonts.policy}を明示した場合はそちらに従う。
	 * </p>
	 */
	@Override
	public CSSJFontPolicyValue getDefaultFontPolicy() {
		if (this.getProperty(UAProps.OUTPUT_PDF_FONTS_POLICY.name) != null) {
			return super.getDefaultFontPolicy();
		}
		return CSSJFontPolicyValue.CORE_EMBEDDED_VALUE;
	}

	private Results results, xresults;
	private boolean middleStateSaved = false;

	protected FontManagerImpl fontManager;

	protected BufferedImage image;

	protected int page = 0;

	// ---- 1パスの target-counter() の頁番号(2026-10-04、docs/design/one-pass-target-counter-design.md §8)

	/**
	 * 今の頁の描画を記録している記録器。欄({@code TargetCounterSlotImage})のある文書では
	 * 頁をまず記録し、欄の値が揃ってから画像に描いて出す。
	 */
	private RecorderGC recorder;

	/** 記録した頁と、その頁の大きさ(pt)。 */
	private record HeldPage(RecorderGC.Page recording, List<TargetCounterSlotImage> slots, double width,
			double height) {
		boolean resolved() {
			for (final var slot : this.slots) {
				if (!slot.isResolved()) {
					return false;
				}
			}
			return true;
		}
	}

	/**
	 * 出していない頁。結果の番号({@code #1}、{@code #2}…)は出した順なので、未解決の頁より
	 * 後ろの頁も、解決していても待たせて頁の順に出す(ページ分割SVGは頁番号のファイル名で
	 * 出すので順不同でよいが、画像は受け手が順番を頁番号とみなす)。
	 */
	private final ArrayDeque<HeldPage> heldPages = new ArrayDeque<>();

	/**
	 * 待たせておく頁の上限(ページ分割SVGと同じ)。記録はメモリに持つので、遠くの頁を参照する
	 * 頁があっても際限なく溜めない。超えたら古い頁から、分かっている番号で出す(未解決は空)。
	 */
	private static final int MAX_HELD_PAGES = 64;

	/**
	 * 未解決の欄がある頁は、値が揃うまで出力を待たせる(2026-10-04、§8)。
	 */
	@Override
	public boolean paintsPageNumbersLater() {
		return true;
	}

	public void setResults(Results results) {
		this.results = results;
	}

	public void prepare(PrepareMode mode) {
		super.prepare(mode);
		switch (mode) {
		case MIDDLE_PASS:
			if (!this.middleStateSaved) {
				this.xresults = this.results;
				this.middleStateSaved = true;
			}
			this.results = NopResults.SHARED_INSTANCE;
			this.reset();
			break;
		case LAST_PASS:
			if (this.middleStateSaved) {
				this.results = this.xresults;
				this.xresults = null;
				this.middleStateSaved = false;
			}
			this.reset();
			break;
		}
	}

	private void reset() {
		this.image = null;
		this.fontManager = null;
		this.page = 0;
		this.recorder = null;
		this.heldPages.clear();
	}

	public FontManager getFontManager() {
		if (this.fontManager == null) {
			this.fontManager = new FontManagerImpl(this.getUAContext().getFontSourceManager());
			// 字形を持たない中核書体は Java2D の代用で崩れるので最後の頼みにする(2026-10-04)
			this.fontManager.setCoreFontsLast(true);
		}
		return this.fontManager;
	}

	public void meta(String name, String content) {
		// ignore
	}

	public GC nextPage() {
		this.checkAbort(CTISession.ABORT_FORCE);
		if (this.isMeasurePass() || this.isStructureScanPass()) {
			this.noteProgress();
			return null;
		}
		if (!this.heldPages.isEmpty()
				|| this.getUAContext().hasTargetCounterSlots() && TargetCounterSlotImage.available(this)) {
			// 欄のある文書: 頁をまず記録する。画素数の上限は記録の前に確かめる。記録器の
			// supports() は Java2D と同じ答え(すべて描ける)を返す。違うと記録のときに近似の
			// 描き方へ入り、描き直しても戻らない
			this.pixelSize(this.pageWidth, this.pageHeight);
			this.recorder = new RecorderGC(this.getFontManager(), capability -> capability != null);
			return this.recorder;
		}
		return this.openImage(this.pageWidth, this.pageHeight);
	}

	/** 頁の画素数。版面の画素数の上限を超えるなら変換をやめる。 */
	private int[] pixelSize(final double pageWidth, final double pageHeight) {
		final Point2D size = new Point2D.Double(pageWidth, pageHeight);
		final double ppi = UAProps.OUTPUT_IMAGE_RESOLUTION.getDouble(this);
		final double pxPerPt = ppi / 72;
		final AffineTransform at = AffineTransform.getScaleInstance(pxPerPt, pxPerPt);
		at.transform(size, size);
		// 四捨五入(2026-10-04)。切り捨てでは 50mm×350dpi=688.98 が 688 画素になり、印刷所が寸法を読み違えた
		final int w = (int) Math.round(size.getX());
		final int h = (int) Math.round(size.getY());
		// 版面の画素数の上限(2026-10-03)。頁の大きさ×解像度はいくらでも
		// 大きくできるので、確保する前に断る
		final long outputPixelLimit = UAProps.OUTPUT_IMAGE_PIXEL_LIMIT.getLong(this);
		if (outputPixelLimit >= 0 && (long) w * h > outputPixelLimit) {
			this.message(MessageCodes.ERROR_OUTPUT_IMAGE_TOO_LARGE, String.valueOf(w), String.valueOf(h),
					String.valueOf(outputPixelLimit));
			throw new AbortException(CTISession.ABORT_FORCE);
		}
		return new int[] { w, h };
	}

	/** 頁の画像を作り、描く GC を返します。 */
	private G2DGC openImage(final double pageWidth, final double pageHeight) {
		final int[] size = this.pixelSize(pageWidth, pageHeight);
		final int w = size[0];
		final int h = size[1];
		final double pxPerPt = UAProps.OUTPUT_IMAGE_RESOLUTION.getDouble(this) / 72;
		final AffineTransform at = AffineTransform.getScaleInstance(pxPerPt, pxPerPt);
		final boolean transparent = this.transparentBackground();
		this.image = new BufferedImage(w, h,
				transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
		final Graphics2D g2d = (Graphics2D) this.image.getGraphics();

		if (!transparent) {
			// 背景クリア。透明のときは**塗らない**ので、何も描かれなかった
			// ところはアルファ0のまま残る
			g2d.setColor(Color.WHITE);
			g2d.fillRect(0, 0, w, h);
		}
		g2d.setColor(Color.BLACK);
		g2d.setTransform(at);

		// オブジェクトとテキストのアンチエイリアス
		if (UAProps.OUTPUT_IMAGE_ANTIALIAS.getBoolean(this)) {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		} else {
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
			g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		}
		return new G2DGC(g2d, this.getFontManager());
	}

	/** 透明にすると決めたかどうか。1文書につき1回だけ判定し、警告も1回だけ出す。 */
	private Boolean transparent = null;

	/**
	 * 背景を透明にするかどうかです。
	 *
	 * <p>
	 * 求められていても、<b>書き出す形式がアルファを持てなければ白のまま</b>にし、
	 * {@code 2824}で知らせます。持てるかどうかは形式表の決め打ちではなく、
	 * {@link ImageWriter}に問い合わせます——利用できるライタは実行環境で
	 * 変わるためです(Java Image I/Oのライタを足せば形式は増える)。
	 * </p>
	 */
	private boolean transparentBackground() {
		if (this.transparent != null) {
			return this.transparent.booleanValue();
		}
		boolean value = false;
		if (UAProps.OUTPUT_IMAGE_TRANSPARENT.getBoolean(this)) {
			final String mimeType = UAProps.OUTPUT_TYPE.getString(this);
			if (canStoreAlpha(mimeType)) {
				value = true;
			} else {
				this.message(MessageCodes.WARN_NO_ALPHA_IN_IMAGE_FORMAT, mimeType);
			}
		}
		this.transparent = Boolean.valueOf(value);
		return value;
	}

	/** その形式がアルファを保てるかを、書き出す側に問い合わせます。 */
	private static boolean canStoreAlpha(final String mimeType) {
		final Iterator<ImageWriter> i = ImageIO.getImageWritersByMIMEType(mimeType);
		if (!i.hasNext()) {
			return false;
		}
		final ImageWriter writer = i.next();
		try {
			return writer.getOriginatingProvider().canEncodeImage(
					ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_ARGB));
		} catch (final RuntimeException e) {
			return false;
		} finally {
			writer.dispose();
		}
	}

	public void closePage(GC gc) throws IOException {
		super.closePage(gc);
		if (gc == null) {
			return;
		}
		if (this.recorder != null) {
			final RecorderGC.Page recording = this.recorder.getPage();
			this.recorder = null;
			this.heldPages.add(
					new HeldPage(recording, TargetCounterSlotImage.slots(recording), this.pageWidth, this.pageHeight));
			if (this.heldPages.size() > MAX_HELD_PAGES) {
				final var context = this.getUAContext();
				if (context.getReportedApproximations().add("target-counter() 2822.target-counter-held")) {
					this.message(MessageCodes.WARN_APPROXIMATED_RENDERING, "target-counter()",
							UAProps.OUTPUT_TYPE.getString(this),
							net.zamasoft.foliojet.message.MessageCodeUtils.detail("2822.target-counter-held"));
				}
				this.writeHeldPage(true);
			}
			while (!this.heldPages.isEmpty() && this.heldPages.peekFirst().resolved()) {
				this.writeHeldPage(false);
			}
		} else {
			this.writeImage();
		}
		this.checkAbort(CTISession.ABORT_NORMAL);
	}

	/**
	 * 先頭の待たせた頁を画像に描いて出します。{@code force}なら未解決の欄を空のまま黙って描く
	 * (上限を超えたとき・文書の終わり)。受け手がもう結果を取らないなら変換をやめる。
	 */
	private void writeHeldPage(final boolean force) throws IOException {
		final HeldPage held = this.heldPages.removeFirst();
		final var context = this.getUAContext();
		context.setDrawingHeldPages(force);
		try {
			held.recording().drawTo(this.openImage(held.width(), held.height()));
		} finally {
			context.setDrawingHeldPages(false);
		}
		this.writeImage();
	}

	/** 描いた頁の画像を次の結果へ書き出します。受け手がもう結果を取らないなら変換をやめる。 */
	private void writeImage() throws IOException {
		String mimeType = UAProps.OUTPUT_TYPE.getString(this);
		SourceMetadata metaSource = new SimpleSourceMetadata(URI.create("#" + (++this.page)), mimeType, null, -1);
		FragmentedOutput builder = this.results.nextBuilder(metaSource);
		try {
			OutputStream out;
			if (builder instanceof SequentialOutput) {
				out = new SequentialOutputAdapter((SequentialOutput) builder);
			} else {
				builder.addFragment();
				out = new FragmentOutputAdapter(builder, 0);
			}
			try (FileCacheImageOutputStream iout = new FileCacheImageOutputStream(out, null)) {
				Iterator<ImageWriter> i = ImageIO.getImageWritersByMIMEType(mimeType);
				ImageWriter writer = (ImageWriter) i.next();
				try {
					writer.setOutput(iout);
					writer.write(null, new javax.imageio.IIOImage(this.image, null, resolutionMetadata(writer,
							this.image, UAProps.OUTPUT_IMAGE_RESOLUTION.getDouble(this))), null);
				} finally {
					writer.dispose();
				}
			}
		} catch (IOException e) {
			throw new GraphicsException(e);
		} finally {
			builder.close();
			this.image = null;
		}
		if (!this.results.hasNext()) {
			throw new AbortException(CTISession.ABORT_NORMAL);
		}
	}

	/**
	 * 解像度(dpi)を書き込んだ画像のメタデータです(2026-10-04)。PNG は pHYs、JPEG は JFIF(単位 1=dpi)、
	 * ほかは標準形式の画素の大きさ。書けない形式なら null(書き出しは従来どおり)。
	 *
	 * <p>
	 * 解像度が無いと、受け手(印刷所の入稿など)は画素数から寸法を読めない。製本直送の表紙作成コースは
	 * 300〜350dpi の画像で入稿するので、読み器と出版の道具で書き足していた。
	 * </p>
	 */
	private static javax.imageio.metadata.IIOMetadata resolutionMetadata(final ImageWriter writer,
			final BufferedImage image, final double dpi) {
		try {
			final javax.imageio.metadata.IIOMetadata metadata = writer.getDefaultImageMetadata(
					ImageTypeSpecifier.createFromRenderedImage(image), writer.getDefaultWriteParam());
			if (metadata == null || metadata.isReadOnly() || !(dpi > 0)) {
				return null;
			}
			final String nativeFormat = metadata.getNativeMetadataFormatName();
			if ("javax_imageio_png_1.0".equals(nativeFormat)) {
				final javax.imageio.metadata.IIOMetadataNode root = new javax.imageio.metadata.IIOMetadataNode(
						nativeFormat);
				final javax.imageio.metadata.IIOMetadataNode phys = new javax.imageio.metadata.IIOMetadataNode("pHYs");
				final String perMeter = Long.toString(Math.round(dpi / 0.0254));
				phys.setAttribute("pixelsPerUnitXAxis", perMeter);
				phys.setAttribute("pixelsPerUnitYAxis", perMeter);
				phys.setAttribute("unitSpecifier", "meter");
				root.appendChild(phys);
				metadata.mergeTree(nativeFormat, root);
				return metadata;
			}
			if ("javax_imageio_jpeg_image_1.0".equals(nativeFormat)) {
				final org.w3c.dom.Node tree = metadata.getAsTree(nativeFormat);
				final org.w3c.dom.NodeList jfif = ((org.w3c.dom.Element) tree).getElementsByTagName("app0JFIF");
				if (jfif.getLength() == 0) {
					return null;
				}
				final org.w3c.dom.Element app0 = (org.w3c.dom.Element) jfif.item(0);
				final String density = Long.toString(Math.min(65535, Math.round(dpi)));
				app0.setAttribute("resUnits", "1");
				app0.setAttribute("Xdensity", density);
				app0.setAttribute("Ydensity", density);
				metadata.setFromTree(nativeFormat, tree);
				return metadata;
			}
			if (metadata.isStandardMetadataFormatSupported()) {
				final javax.imageio.metadata.IIOMetadataNode root = new javax.imageio.metadata.IIOMetadataNode(
						javax.imageio.metadata.IIOMetadataFormatImpl.standardMetadataFormatName);
				final javax.imageio.metadata.IIOMetadataNode dimension = new javax.imageio.metadata.IIOMetadataNode(
						"Dimension");
				final String mmPerPixel = Double.toString(25.4 / dpi);
				for (final String name : new String[] { "HorizontalPixelSize", "VerticalPixelSize" }) {
					final javax.imageio.metadata.IIOMetadataNode node = new javax.imageio.metadata.IIOMetadataNode(name);
					node.setAttribute("value", mmPerPixel);
					dimension.appendChild(node);
				}
				root.appendChild(dimension);
				metadata.mergeTree(javax.imageio.metadata.IIOMetadataFormatImpl.standardMetadataFormatName, root);
				return metadata;
			}
		} catch (final javax.imageio.metadata.IIOInvalidTreeException | RuntimeException e) {
			// 書けない形式は解像度なしで書く
		}
		return null;
	}

	public void finish() throws BrokenResultException, IOException {
		super.finish();
		// 待たせた頁を出す。最後まで参照先の無かった欄は空(受け手がもう取らなければやめる)
		try {
			while (!this.heldPages.isEmpty() && this.results.hasNext()) {
				this.writeHeldPage(true);
			}
		} catch (final AbortException e) {
			// 受け手がもう結果を取らない
		}
		this.heldPages.clear();
		this.results.end();
	}

	public Visitor getVisitor(GC gc) {
		return new NopVisitor(this);
	}
}

package net.zamasoft.foliojet.ua.impl.pdf;

import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.FolioJetVersion;
import net.zamasoft.foliojet.ua.impl.AbstractUserAgent;
import net.zamasoft.foliojet.ua.impl.NopVisitor;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.pdfg2d.util.IntList;
import net.zamasoft.foliojet.layout.visitor.Visitor;
import net.zamasoft.foliojet.ua.BrokenResultException;
import net.zamasoft.foliojet.ua.RandomResultUserAgent;
import net.zamasoft.foliojet.ua.props.OutputColor;
import net.zamasoft.foliojet.ua.props.OutputPdfCompression;
import net.zamasoft.foliojet.ua.props.OutputPdfEncryption;
import net.zamasoft.foliojet.ua.props.OutputPdfEncryptionV4CFM;
import net.zamasoft.foliojet.ua.props.OutputPdfImageCompression;
import net.zamasoft.foliojet.ua.props.OutputPdfJpegImage;
import net.zamasoft.foliojet.ua.props.OutputPdfVersion;
import net.zamasoft.foliojet.ua.props.OutputPdfWatermarkMode;
import net.zamasoft.foliojet.ua.props.OutputPdfViewerPreferencesDuplex;
import net.zamasoft.foliojet.ua.props.OutputPdfViewerPreferencesNonFullScreenPageMode;
import net.zamasoft.foliojet.ua.props.OutputPdfViewerPreferencesPrintScaling;
import net.zamasoft.foliojet.ua.props.UAProps;
import net.zamasoft.zstream.resolver.SourceMetadata;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.zstream.resolver.util.SimpleSourceMetadata;
import net.zamasoft.zstream.resolver.util.URIHelper;
import net.zamasoft.zstream.io.FragmentedOutput;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.GraphicsException;
import net.zamasoft.pdfg2d.gc.font.FontManager;
import net.zamasoft.pdfg2d.gc.image.Image;
import net.zamasoft.pdfg2d.gc.image.util.TransformedImage;
import net.zamasoft.pdfg2d.gc.paint.Pattern;
import net.zamasoft.pdfg2d.pdf.Attachment;
import net.zamasoft.pdfg2d.pdf.PDFGraphicsOutput;
import net.zamasoft.pdfg2d.pdf.PDFMetaInfo;
import net.zamasoft.pdfg2d.pdf.PDFOutput;
import net.zamasoft.pdfg2d.pdf.PDFPageOutput;
import net.zamasoft.pdfg2d.pdf.PDFWriter;
import net.zamasoft.pdfg2d.pdf.action.JavaScriptAction;
import net.zamasoft.pdfg2d.pdf.annot.SquareAnnot;
import net.zamasoft.pdfg2d.pdf.gc.PDFGC;
import net.zamasoft.pdfg2d.pdf.gc.PDFGroupImage;
import net.zamasoft.pdfg2d.pdf.impl.PDFWriterImpl;
import net.zamasoft.pdfg2d.pdf.params.EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.PDFParams;
import net.zamasoft.pdfg2d.pdf.params.R2Permissions;
import net.zamasoft.pdfg2d.pdf.params.R3Permissions;
import net.zamasoft.pdfg2d.pdf.params.V1EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.V2EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.V4EncryptionParams;
import net.zamasoft.pdfg2d.pdf.params.ViewerPreferences;
import net.zamasoft.foliojet.ua.BoundSide;
import net.zamasoft.foliojet.ua.PrepareMode;

public class PDFUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	private static final Logger LOG = Logger.getLogger(PDFUserAgent.class.getName());

	private Results results, xresults;
	private FragmentedOutput builder, xbuilder;
	private PDFWriter pdfWriter = null, xpdfWriter = null;
	private boolean middleStateSaved = false;
	private net.zamasoft.pdfg2d.pdf.font.FontManagerImpl nonOutputFontManager = null;
	private final Map<URI, Image> nonOutputImages = new HashMap<>();
	private final PDFMetaInfo metaInfo;
	private Pattern watermark = null;
	/**
	 * 背面透かしのグループ画像です。ページ寸法ごとにキャッシュする——
	 * {@code @page size}(名前付きページN3/N4)でページ毎に寸法が変わり得る
	 * ため、最初のページの寸法で作った1個を使い回すと覆う範囲が不正になる
	 * (N5、consult-codex-2026-07-31-named-pages.txt)。
	 */
	private final Map<String, PDFGroupImage> watermarkGroups = new HashMap<>();

	protected PDFVisitor visitor = null;

	private boolean pageGenerated = false;

	protected PDFUserAgent() {
		this.metaInfo = new PDFMetaInfo();
		this.metaInfo.setProducer(FolioJetVersion.INSTANCE.longVersion);
	}

	public void setResults(Results results) {
		this.results = results;
	}

	public void prepare(PrepareMode mode) {
		super.prepare(mode);
		switch (mode) {
		case DOCUMENT:
			break;
		case STRUCTURE_SCAN:
			// ボックス構築・レイアウトを一切行わない軽量な事前走査。
			// TranscoderHandlerがCSSProcessor(PDF生成に関わる状態を
			// 使う側)自体を経由させないため、PDF固有の状態(results/
			// pdfWriter/builder)には一切触れない。
			this.resetNonOutputResources();
			break;
		case MIDDLE_PASS:
			if (!this.middleStateSaved) {
				this.xresults = this.results;
				this.xpdfWriter = this.pdfWriter;
				this.xbuilder = this.builder;
				this.middleStateSaved = true;
				// 継続変換中の実出力を閉じずに一時退避する。
				this.pdfWriter = null;
				this.builder = null;
			}
			this.results = NopResults.SHARED_INSTANCE;
			this.reset();
			break;
		case LAST_PASS:
			this.reset();
			if (this.middleStateSaved) {
				this.results = this.xresults;
				this.xresults = null;
				this.pdfWriter = this.xpdfWriter;
				this.xpdfWriter = null;
				this.builder = this.xbuilder;
				this.xbuilder = null;
				this.middleStateSaved = false;
			}
			break;
		default:
			throw new IllegalArgumentException();
		}
	}

	private void reset() {
		this.resetNonOutputResources();
		if (this.builder != null) {
			try {
				this.builder.close();
			} catch (IOException e) {
				// ignore
			}
			this.builder = null;
		}
		this.visitor = null;
		this.pageGenerated = false;
		this.pdfWriter = null;
	}

	private void resetNonOutputResources() {
		if (this.nonOutputFontManager != null) {
			this.nonOutputFontManager.close();
			this.nonOutputFontManager = null;
		}
		this.nonOutputImages.clear();
	}

	private boolean isNonOutputPass() {
		return this.isMeasurePass() || this.isStructureScanPass();
	}

	public void setBoundSide(BoundSide boundSide) {
		super.setBoundSide(boundSide);

		// 綴じ方向
		if (this.getBoundSide() != BoundSide.SINGLE && this.pdfWriter != null) {
			ViewerPreferences vp = this.pdfWriter.getParams().viewerPreferences();
			switch (this.getBoundSide()) {
			case LEFT:
				vp.setDirection(ViewerPreferences.Direction.L2R);
				break;
			case RIGHT:
				vp.setDirection(ViewerPreferences.Direction.R2L);
				break;
			default:
				throw new IllegalStateException();
			}
		}
	}

	private void preparePDFWriter() throws IOException {
		if (this.pdfWriter != null) {
			return;
		}
		// PDFセットアップ
		// 入出力プロパティ→PDFParamsの解決(警告発行のみの副作用)は
		// PDFParamsResolverへ分離(85点計画・増分15)。ここは出力先と
		// writerの生成だけを行う
		PDFParams params = PDFParamsResolver.resolve(this, this.metaInfo);
		SourceMetadata metaSource = new SimpleSourceMetadata(URIHelper.CURRENT_URI, "application/pdf", null, -1);
		this.builder = this.results.nextBuilder(metaSource);
		params = params.withMetaInfo(this.metaInfo);
		this.pdfWriter = new PDFWriterImpl(this.builder, params);
		this.setBoundSide(this.getBoundSide());
	}


	public FontManager getFontManager() {
		if (this.isNonOutputPass()) {
			if (this.nonOutputFontManager == null) {
				this.nonOutputFontManager = new net.zamasoft.pdfg2d.pdf.font.FontManagerImpl(
						this.getUAContext().getFontSourceManager());
			}
			return this.nonOutputFontManager;
		}
		try {
			this.preparePDFWriter();
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
		return this.pdfWriter.getFontManager();
	}

	public void meta(String name, String content) {
		if (name.equalsIgnoreCase("author")) {
			this.metaInfo.setAuthor(content);
		} else if (name.equalsIgnoreCase("creator") || name.equalsIgnoreCase("generator")) {
			this.metaInfo.setCreator(content);
		} else if (name.equalsIgnoreCase("keywords")) {
			this.metaInfo.setKeywords(content);
		} else if (name.equalsIgnoreCase("producer")) {
			this.metaInfo.setProducer(content);
		} else if (name.equalsIgnoreCase("subject") || name.equalsIgnoreCase("description")) {
			this.metaInfo.setSubject(content);
		} else if (name.equalsIgnoreCase("title")) {
			this.message(MessageCodes.INFO_TITLE, content);
			this.metaInfo.setTitle(content);
		}
	}

	public Image getImage(Source source) throws IOException {
		if (this.isNonOutputPass()) {
			final URI uri = source.getURI();
			Image image = this.nonOutputImages.get(uri);
			if (image == null) {
				final Image loaded = super.getImage(source);
				final double width = loaded.getWidth();
				final double height = loaded.getHeight();
				final String alt = loaded.getAltString();
				image = new Image() {
					public double getWidth() {
						return width;
					}

					public double getHeight() {
						return height;
					}

					public void drawTo(GC gc) {
						// 中間パスでは描画されない寸法専用画像。
					}

					public String getAltString() {
						return alt;
					}
				};
				this.nonOutputImages.put(uri, image);
			}
			return image;
		}
		this.preparePDFWriter();
		Image image;
		try {
			image = this.pdfWriter.loadImage(source);
			// filterの画素変換に備えて、復号は遅延させたまま画素への道を
			// 添える(PixelBackedImage参照、2026-08-29)
			final URI uri = source.getURI();
			if (uri != null) {
				image = new PixelBackedImage(image, () -> {
					try {
						final Source s = this.resolve(uri);
						try {
							return this.loadImage(s);
						} finally {
							this.release(s);
						}
					} catch (IOException e) {
						return null;
					}
				});
			}
		} catch (IOException e) {
			image = this.loadImage(source);
		}
		AffineTransform pixelToUnit = this.getPixelToUnit();
		if (!pixelToUnit.isIdentity()) {
			image = new TransformedImage(image, pixelToUnit);
		}
		return image;
	}

	public GC nextPage() {
		this.checkAbort(CTISession.ABORT_FORCE);
		this.noteProgress();
		if (this.isNonOutputPass()) {
			return null;
		}
		try {
			this.preparePDFWriter();
			double w = this.pageWidth;
			double h = this.pageHeight;
			if (w < PDFWriter.MIN_PAGE_WIDTH) {
				this.message(MessageCodes.ERROR_BAD_PAGE_SIZE, PDFWriter.MIN_PAGE_WIDTH + "(width)>",
						String.valueOf(w));
				w = PDFWriter.MIN_PAGE_WIDTH;
			}
			if (h < PDFWriter.MIN_PAGE_HEIGHT) {
				this.message(MessageCodes.ERROR_BAD_PAGE_SIZE, PDFWriter.MIN_PAGE_HEIGHT + "(height)>",
						String.valueOf(h));
				h = PDFWriter.MIN_PAGE_HEIGHT;
			}
			if (w > PDFWriter.MAX_PAGE_WIDTH) {
				this.message(MessageCodes.ERROR_BAD_PAGE_SIZE, PDFWriter.MAX_PAGE_WIDTH + "(width)>",
						String.valueOf(w));
				w = PDFWriter.MAX_PAGE_WIDTH;
			}
			if (h > PDFWriter.MAX_PAGE_HEIGHT) {
				this.message(MessageCodes.ERROR_BAD_PAGE_SIZE, PDFWriter.MAX_PAGE_HEIGHT + "(height)>",
						String.valueOf(h));
				h = PDFWriter.MAX_PAGE_HEIGHT;
			}

			// すかし
			if (this.watermark == null) {
				String uri = UAProps.OUTPUT_PDF_WATERMARK_URI.getString(this);
				if (uri != null) {
					if (this.pdfWriter.getParams().version().v >= PDFParams.Version.V_1_4.v) {
						try {
							Source source = this.resolve(URIHelper.create("UTF-8", uri));
							try {
								Image image = this.getImage(source);
								this.watermark = new Pattern(image, null);
							} finally {
								this.release(source);
							}
						} catch (Exception e) {
							LOG.log(Level.FINE, "Missing image", e);
							this.message(MessageCodes.WARN_MISSING_IMAGE, uri);
						}
					} else {
						this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
								UAProps.OUTPUT_PDF_WATERMARK_URI.name, uri, "1.3");
					}
				}
			}
			PDFGraphicsOutput page = this.pdfWriter.nextPage(w, h);
			PDFGC gc = new PDFGC(page);
			this.pageGenerated = true;
			if (this.watermark != null) {
				// 背面
				OutputPdfWatermarkMode mode = UAProps.OUTPUT_PDF_WATERMARK_MODE.get(this);
				if (mode == OutputPdfWatermarkMode.BACK) {
					final String dims = w + "x" + h;
					PDFGroupImage watermarkGroup = this.watermarkGroups.get(dims);
					if (watermarkGroup == null) {
						PDFPageOutput out = (PDFPageOutput) gc.getPDFGraphicsOutput();
						watermarkGroup = out.getPdfWriter().createGroupImage(w, h);
						int flags = 0;
						if (!UAProps.OUTPUT_PDF_WATERMARK_VIEW.getBoolean(this)) {
							if (this.pdfWriter.getParams().version().v >= PDFParams.Version.V_1_5.v) {
								flags |= PDFGroupImage.VIEW_OFF;
							} else {
								this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
										UAProps.OUTPUT_PDF_WATERMARK_VIEW.name, "false", "1.4");
							}
						}
						if (!UAProps.OUTPUT_PDF_WATERMARK_PRINT.getBoolean(this)) {
							if (this.pdfWriter.getParams().version().v >= PDFParams.Version.V_1_5.v) {
								flags |= PDFGroupImage.PRINT_OFF;
							} else {
								this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
										UAProps.OUTPUT_PDF_WATERMARK_PRINT.name, "false", "1.4");
							}
						}
						if (flags != 0) {
							watermarkGroup.setOCG(flags);
						}
						this.paintWatermark(watermarkGroup, null, w, h);
						this.watermarkGroups.put(dims, watermarkGroup);
					}
					gc.drawImage(watermarkGroup);
				}
			}
			return gc;
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	/**
	 * 透かしパターンを塗って、グループ画像を閉じます(2026-08-06、85点計画
	 * ua残増分)。
	 *
	 * <p>
	 * 背面(BACK=ページ内容の下に直接描く)と前面(FRONT=注釈の
	 * appearanceにする)は仕込み先が違うだけで、「パターンをopacityつきで
	 * 矩形に塗る」部分と<b>opacityの規格警告(PDF/A-1・PDF/X-1aは透明を
	 * 使えない)</b>は同一だった——ほぼ逐語の複製が2箇所にあり、警告を
	 * 直すとき片方を忘れる形をしていた。ここが唯一の定義。
	 * 仕込み先ごとの表示制御(BACKのOCGフラグ・FRONTの注釈Fフラグ)は
	 * 機構が違うので呼び出し側に残る。
	 * </p>
	 *
	 * @param group      この上へ塗り、このメソッドが閉じる
	 * @param scale      塗りに先立って適用する拡大(FRONTの注釈座標系。
	 *                   BACKはnull)
	 * @param maskWidth  塗る矩形の幅
	 * @param maskHeight 塗る矩形の高さ
	 */
	private void paintWatermark(final PDFGroupImage group, final AffineTransform scale, final double maskWidth,
			final double maskHeight) throws IOException {
		final PDFGC gc = new PDFGC(group);
		if (scale != null) {
			gc.transform(scale);
		}
		gc.setFillPaint(this.watermark);
		final double opacity = UAProps.OUTPUT_PDF_WATERMARK_OPACITY.getDouble(this);
		if (opacity != 1) {
			final PDFParams.Version version = group.getPdfWriter().getParams().version();
			if (version == PDFParams.Version.V_PDFA1B) {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_WATERMARK_OPACITY.name,
						String.valueOf(opacity), "PDF/A-1");
			} else if (version == PDFParams.Version.V_PDFX1A) {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_WATERMARK_OPACITY.name,
						String.valueOf(opacity), "PDF/X-1a");
			} else {
				gc.setFillAlpha((float) opacity);
			}
		}
		gc.fill(new Rectangle2D.Double(0, 0, maskWidth, maskHeight));
		group.close();
	}

	public void closePage(final GC gc) throws IOException {
		super.closePage(gc);
		if (gc == null) {
			return;
		}
		final PDFGC pdfGc = (PDFGC) gc;
		try (final PDFPageOutput out = (PDFPageOutput) pdfGc.getPDFGraphicsOutput()) {
			if (this.watermark != null) {
				OutputPdfWatermarkMode mode = UAProps.OUTPUT_PDF_WATERMARK_MODE.get(this);
				if (mode == OutputPdfWatermarkMode.FRONT) {
					// 前面
					Rectangle2D rect = new Rectangle2D.Double(0, 0, this.pageWidth, this.pageHeight);
					final AffineTransform at = gc.getTransform();
					if (at != null) {
						rect = at.createTransformedShape(rect).getBounds2D();
					}
					final SquareAnnot annot = new SquareAnnot() {
						public void writeTo(PDFOutput out, PDFPageOutput pageOut) throws IOException {
							super.writeTo(out, pageOut);

							Rectangle2D rect = this.getShape().getBounds2D();
							final PDFGroupImage group = pageOut.getPdfWriter().createGroupImage(rect.getWidth(),
									rect.getHeight());
							AffineTransform scale = null;
							if (at != null) {
								scale = new AffineTransform();
								scale.scale(at.getScaleX(), at.getScaleY());
							}
							paintWatermark(group, scale, PDFUserAgent.this.pageWidth, PDFUserAgent.this.pageHeight);

							// 印刷時だけ表示するフラグ
							out.writeName("F");
							int flags = 0;
							if (!UAProps.OUTPUT_PDF_WATERMARK_VIEW.getBoolean(PDFUserAgent.this)) {
								flags |= 0x20;
							}
							if (UAProps.OUTPUT_PDF_WATERMARK_PRINT.getBoolean(PDFUserAgent.this)) {
								flags |= 0x4;
							}
							out.writeInt(flags);
							out.breakBefore();

							out.writeName("AP");
							out.startHash();
							out.writeName("N");
							out.writeObjectRef(group.getObjectRef());
							out.endHash();
							out.breakBefore();
						}
					};
					annot.setShape(rect);
					try {
						out.addAnnotation(annot);
					} catch (IOException e) {
						throw new GraphicsException(e);
					}
				}
			}
		}

		// 中断チェック
		this.checkAbort(CTISession.ABORT_NORMAL);
	}

	public Visitor getVisitor(GC gc) {
		if (gc == null) {
			return new NopVisitor(this);
		}
		if (this.visitor == null) {
			this.visitor = new PDFVisitor(this);
		}
		this.visitor.nextPage((PDFGC) gc);
		return this.visitor;
	}

	public void finish() throws BrokenResultException, IOException {
		super.finish();
		if (!this.pageGenerated) {
			final short code = MessageCodes.ERROR_NO_CONTENT;
			String mes = MessageCodeUtils.toString(code, null);
			this.message(code, mes);
			throw new TranscoderException(TranscoderException.STATE_BROKEN, code, null, mes);
		}
		try {
			// PDF後処理
			// ファイルの添付
			byte[] buff = new byte[8192];
			for (int i = 0;; ++i) {
				String prefix = UAProps.OUTPUT_PDF_ATTACHMENTS + i + ".";
				String uriStr = this.getProperty(prefix + "uri");
				if (uriStr == null) {
					break;
				}
				if (this.pdfWriter.getParams().version().v < PDFParams.Version.V_1_4.v) {
					this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, prefix + "uri", uriStr, "1.3");
					break;
				}
				if (this.pdfWriter.getParams().version() == PDFParams.Version.V_PDFA1B) {
					this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, prefix + "uri", uriStr, "PDF/A-1");
					break;
				}
				if (this.pdfWriter.getParams().version() == PDFParams.Version.V_PDFX1A) {
					this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, prefix + "uri", uriStr, "PDF/X-1a");
					break;
				}
				URI uri;
				try {
					uri = URIHelper.create(this.getDocumentContext().getEncoding(), uriStr);
				} catch (URISyntaxException e1) {
					this.message(MessageCodes.WARN_MISSING_ATTACHMENT, uriStr);
					continue;
				}
				String name = this.getProperty(prefix + "name");
				String description = this.getProperty(prefix + "description");
				String mimeType = this.getProperty(prefix + "mime-type");
				if (name == null) {
					uriStr = uri.getPath();
					int slash = uriStr.lastIndexOf('/');
					if (slash == -1) {
						name = uriStr;
					} else {
						name = uriStr.substring(slash + 1);
					}
				}
				Source attachmetSource = null;
				try {
					attachmetSource = this.resolve(uri);
				} catch (Exception e) {
					this.message(MessageCodes.WARN_MISSING_ATTACHMENT, uri.toString());
					continue;
				}
				String relationship = this.getProperty(prefix + "relationship");
				if (relationship != null) {
					// PDF/A-3のAFRelationship名へ正規化(電子インボイスは
					// alternative——2026-08-02)
					switch (relationship.toLowerCase()) {
					case "alternative" -> relationship = "Alternative";
					case "data" -> relationship = "Data";
					case "source" -> relationship = "Source";
					case "supplement" -> relationship = "Supplement";
					case "unspecified" -> relationship = "Unspecified";
					default -> {
						this.message(MessageCodes.WARN_BAD_IO_PROPERTY, prefix + "relationship", relationship);
						relationship = null;
					}
					}
				}
				try {
					if (mimeType == null) {
						mimeType = attachmetSource.getMimeType();
					}
					Attachment att = new Attachment(description, mimeType, relationship);
					try (OutputStream out = this.pdfWriter.addAttachment(name, att);
							InputStream in = attachmetSource.getInputStream()) {
						for (int len = in.read(buff); len != -1; len = in.read(buff)) {
							out.write(buff, 0, len);
						}
					}
				} finally {
					this.release(attachmetSource);
				}
			}
			this.pdfWriter.close();
			this.builder.close();
		} finally {
			this.builder = null;
			this.pdfWriter = null;
			this.watermark = null;
			this.watermarkGroups.clear();
		}
		this.results.end();
	}

	public void dispose() {
		super.dispose();
		this.resetNonOutputResources();
		if (this.builder != null) {
			try {
				this.builder.close();
			} catch (IOException e) {
				// ignore
			}
			this.builder = null;
		}
		if (this.xbuilder != null) {
			try {
				this.xbuilder.close();
			} catch (IOException e) {
				// ignore
			}
			this.xbuilder = null;
		}
		this.pdfWriter = null;
		this.xpdfWriter = null;
		this.xresults = null;
		this.middleStateSaved = false;
		this.watermark = null;
		this.watermarkGroups.clear();
	}
}

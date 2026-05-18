package net.zamasoft.foliojet.impl.ua.pdf;

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
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import jp.cssj.cti2.CTISession;
import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.results.NopResults;
import jp.cssj.cti2.results.Results;
import net.zamasoft.foliojet.FolioJetVersion;
import net.zamasoft.foliojet.impl.ua.AbstractUserAgent;
import net.zamasoft.foliojet.impl.ua.NopVisitor;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.foliojet.style.visitor.Visitor;
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
import net.zamasoft.foliojet.ua.props.OutputPdfViewerPreferencesNoneFullScreenPageMode;
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

import org.apache.commons.collections.primitives.ArrayIntList;
import org.apache.commons.collections.primitives.IntList;

public class PDFUserAgent extends AbstractUserAgent implements RandomResultUserAgent {
	private static final Logger LOG = Logger.getLogger(PDFUserAgent.class.getName());

	private Results results, xresults;
	private FragmentedOutput builder, xbuilder;
	private PDFWriter pdfWriter = null, xpdfWriter = null;
	private final PDFMetaInfo metaInfo;
	private Pattern watermark = null;
	private PDFGroupImage watermarkGroup = null;

	protected PDFVisitor visitor = null;

	private boolean pageGenerated = false;

	protected PDFUserAgent() {
		this.metaInfo = new PDFMetaInfo();
		this.metaInfo.setProducer(FolioJetVersion.INSTANCE.longVersion);
	}

	public void setResults(Results results) {
		this.results = results;
	}

	public void prepare(byte mode) {
		super.prepare(mode);
		switch (mode) {
		case PREPARE_DOCUMENT:
			break;
		case PREPARE_MIDDLE_PASS:
			if (this.results != NopResults.SHARED_INSTANCE) {
				this.xresults = this.results;
				this.results = NopResults.SHARED_INSTANCE;
				this.xpdfWriter = this.pdfWriter;
				this.xbuilder = this.builder;
			}
			this.reset();
			break;
		case PREPARE_LAST_PASS:
			this.results = this.xresults;
			this.xresults = null;
			if (this.xpdfWriter != null) {
				this.builder = null;
			}
			this.reset();
			if (this.xpdfWriter != null) {
				this.pdfWriter = this.xpdfWriter;
				this.xpdfWriter = null;
				this.builder = this.xbuilder;
			}
			break;
		default:
			throw new IllegalArgumentException();
		}
	}

	private void reset() {
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

	public void setBoundSide(byte boundSide) {
		super.setBoundSide(boundSide);

		// 綴じ方向
		if (this.getBoundSide() != BOUND_SIDE_SINGLE && this.pdfWriter != null) {
			ViewerPreferences vp = this.pdfWriter.getParams().viewerPreferences();
			switch (this.getBoundSide()) {
			case BOUND_SIDE_LEFT:
				vp.setDirection(ViewerPreferences.Direction.L2R);
				break;
			case BOUND_SIDE_RIGHT:
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
		PDFParams params = PDFParams.createDefault();
		params = params.withFontSourceManager(this.getUAContext().getFontSourceManager());

		// バージョン
		switch (UAProps.OUTPUT_PDF_VERSION.getCode(this)) {
		case OutputPdfVersion.V1_2:
			params = params.withVersion(PDFParams.Version.V_1_2);
			break;
		case OutputPdfVersion.V1_3:
			params = params.withVersion(PDFParams.Version.V_1_3);
			break;
		case OutputPdfVersion.V1_4:
			params = params.withVersion(PDFParams.Version.V_1_4);
			break;
		case OutputPdfVersion.V1_4A1:
			params = params.withVersion(PDFParams.Version.V_PDFA1B);
			break;
		case OutputPdfVersion.V1_4X1:
			params = params.withVersion(PDFParams.Version.V_PDFX1A);
			break;
		case OutputPdfVersion.V1_5:
			params = params.withVersion(PDFParams.Version.V_1_5);
			break;
		case OutputPdfVersion.V1_6:
			params = params.withVersion(PDFParams.Version.V_1_6);
			break;
		case OutputPdfVersion.V1_7:
			params = params.withVersion(PDFParams.Version.V_1_7);
			break;
		default:
			throw new IllegalStateException();
		}

		// ファイルID
		String fileId = UAProps.OUTPUT_PDF_FILE_ID.getString(this);
		if (fileId != null) {
			if (fileId.length() == 32) {
				byte[] id = new byte[16];
				try {
					for (int i = 0; i < fileId.length(); i += 2) {
						String hex = fileId.substring(i, i + 2);
						id[i / 2] = (byte) (Integer.parseInt(hex, 16) & 0xFF);
					}
					params = params.withFileId(id);
				} catch (NumberFormatException e) {
					this.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							new String[] { UAProps.OUTPUT_PDF_FILE_ID.name, fileId });
				}
			} else {
				this.message(MessageCodes.WARN_BAD_IO_PROPERTY,
						new String[] { UAProps.OUTPUT_PDF_FILE_ID.name, fileId });
			}
		}

		// 日付
		String creationDate = UAProps.OUTPUT_PDF_META_CREATION_DATE.getString(this);
		String modDate = UAProps.OUTPUT_PDF_META_MOD_DATE.getString(this);
		if (creationDate != null || modDate != null) {
			DateFormat format1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z");
			DateFormat format2 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
			format1.setLenient(true);
			format2.setLenient(true);
			if (creationDate != null) {
				try {
					long time;
					try {
						time = format1.parse(creationDate).getTime();
					} catch (ParseException e) {
						try {
							int colon = creationDate.lastIndexOf(':');
							String s = creationDate.substring(0, colon) + creationDate.substring(colon + 1);
							time = format1.parse(s).getTime();
						} catch (Exception e2) {
							time = format2.parse(creationDate).getTime();
						}
					}
					this.metaInfo.setCreationDate(time);
				} catch (ParseException e) {
					this.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							new String[] { UAProps.OUTPUT_PDF_META_CREATION_DATE.name, creationDate });
				}
			}
			if (modDate != null) {
				try {
					long time;
					try {
						time = format1.parse(modDate).getTime();
					} catch (ParseException e) {
						try {
							int colon = modDate.lastIndexOf(':');
							String s = modDate.substring(0, colon) + modDate.substring(colon + 1);
							time = format1.parse(s).getTime();
						} catch (Exception e2) {
							time = format2.parse(modDate).getTime();
						}
					}
					this.metaInfo.setModDate(time);
				} catch (ParseException e) {
					this.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PDF_META_MOD_DATE.name, modDate);
				}
			}
		}

		// カラー
		int color = UAProps.OUTPUT_COLOR.getCode(this);
		if (params.version() == PDFParams.Version.V_PDFX1A && color == OutputColor.RGB) {
			this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_COLOR.name, "rgb", "PDF/X-1a");
			color = OutputColor.CMYK;
		}
		switch (color) {
		case OutputColor.RGB:
			params = params.withColorMode(PDFParams.ColorMode.PRESERVE);
			break;
		case OutputColor.GRAY:
			params = params.withColorMode(PDFParams.ColorMode.GRAY);
			break;
		case OutputColor.CMYK:
			params = params.withColorMode(PDFParams.ColorMode.CMYK);
			break;
		default:
			throw new IllegalStateException();
		}

		// 圧縮
		switch (UAProps.OUTPUT_PDF_COMPRESSION.getCode(this)) {
		case OutputPdfCompression.NONE:
			params = params.withCompression(PDFParams.Compression.NONE);
			break;
		case OutputPdfCompression.ASCII:
			params = params.withCompression(PDFParams.Compression.ASCII);
			break;
		case OutputPdfCompression.BINARY:
			params = params.withCompression(PDFParams.Compression.BINARY);
			break;
		default:
			throw new IllegalStateException();
		}

		// ブックマーク
		if (UAProps.OUTPUT_PDF_BOOKMARKS.getBoolean(this)) {
			params = params.withBookmarks(true);
		}

		// JPEG画像
		switch (UAProps.OUTPUT_PDF_JPEG_IMAGE.getCode(this)) {
		case OutputPdfJpegImage.RAW:
			params = params.withJPEGImage(PDFParams.JPEGImage.RAW);
			break;
		case OutputPdfJpegImage.TO_FLATE:
		case OutputPdfJpegImage.TO_RECOMPRESS:
			params = params.withJPEGImage(PDFParams.JPEGImage.RECOMPRESS);
			break;
		default:
			throw new IllegalStateException();
		}

		// JPEG圧縮
		switch (UAProps.OUTPUT_PDF_IMAGE_COMPRESSION.getCode(this)) {
		case OutputPdfImageCompression.FLATE:
			params = params.withImageCompression(PDFParams.ImageCompression.FLATE);
			break;
		case OutputPdfImageCompression.JPEG:
			params = params.withImageCompression(PDFParams.ImageCompression.JPEG);
			break;
		case OutputPdfImageCompression.JPEG2000:
			params = params.withImageCompression(PDFParams.ImageCompression.JPEG2000);
			break;
		default:
			throw new IllegalStateException();
		}

		// ロスレス圧縮
		params = params.withImageCompressionLossless(UAProps.OUTPUT_PDF_IMAGE_COMPRESSION_LOSSLESS.getInteger(this));

		// 最大画像サイズ
		params = params.withMaxImageWidth(UAProps.OUTPUT_PDF_IMAGE_MAX_WIDTH.getInteger(this));
		params = params.withMaxImageHeight(UAProps.OUTPUT_PDF_IMAGE_MAX_HEIGHT.getInteger(this));

		// プラットフォームエンコーディング
		params = params.withPlatformEncoding(UAProps.OUTPUT_PDF_PLATFORM_ENCODING.getString(this));

		// 暗号化
		switch (UAProps.OUTPUT_PDF_ENCRYPTION.getCode(this)) {
		case OutputPdfEncryption.NONE:
			break;

		case OutputPdfEncryption.V1:
			// v1暗号化
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v1",
						"PDF/A-1");
			} else if (params.version() == PDFParams.Version.V_PDFX1A) {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v1",
						"PDF/X-1a");
			} else {
				V1EncryptionParams v1Params = new V1EncryptionParams();
				this.applyEncryptionParams(v1Params);
				R2Permissions r2p = v1Params.getPermissions();
				this.applyR2Permissions(r2p);
				params = params.withEncryption(v1Params);
			}
			break;

		case OutputPdfEncryption.V2:
			// v2暗号化
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v2",
						"PDF/A-1");
			} else if (params.version() == PDFParams.Version.V_PDFX1A) {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v2",
						"PDF/X-1a");
			} else if (params.version().v >= PDFParams.Version.V_1_3.v) {
				V2EncryptionParams v2Params = new V2EncryptionParams();
				this.applyEncryptionParams(v2Params);
				int length = UAProps.OUTPUT_PDF_ENCRYPTION_LENGTH.getInteger(this);
				try {
					v2Params.setLength(length);
				} catch (IllegalArgumentException e) {
					this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
							UAProps.OUTPUT_PDF_ENCRYPTION_LENGTH.name, String.valueOf(length), "V2 Encryption");
				}
				R3Permissions r3p = v2Params.getPermissions();
				this.applyR2Permissions(r3p);
				this.applyR3Permissions(r3p);
				params = params.withEncryption(v2Params);
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v2",
						"1.2");
			}
			break;

		case OutputPdfEncryption.V4:
			// v4暗号化
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v4",
						"PDF/A-1");
			} else if (params.version() == PDFParams.Version.V_PDFX1A) {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v4",
						"PDF/X-1a");
			} else if (params.version().v >= PDFParams.Version.V_1_5.v) {
				V4EncryptionParams v4Params = new V4EncryptionParams();
				this.applyEncryptionParams(v4Params);
				switch (UAProps.OUTPUT_PDF_ENCRYPTION_V4_CFM.getCode(this)) {
				case OutputPdfEncryptionV4CFM.V2:
					v4Params.setCFM(V4EncryptionParams.CFM.V2);
					break;
				case OutputPdfEncryptionV4CFM.AESV2:
					if (params.version().v >= PDFParams.Version.V_1_6.v) {
						v4Params.setCFM(V4EncryptionParams.CFM.AESV2);
					} else {
						this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
								UAProps.OUTPUT_PDF_ENCRYPTION_V4_CFM.name, "AESV2", "1.5");
					}
					break;
				default:
					throw new IllegalStateException();
				}
				int length = UAProps.OUTPUT_PDF_ENCRYPTION_LENGTH.getInteger(this);
				try {
					v4Params.setLength(length);
				} catch (IllegalArgumentException e) {
					this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
							UAProps.OUTPUT_PDF_ENCRYPTION_LENGTH.name, String.valueOf(length), "V4 Encryption");
				}
				R3Permissions r3p = v4Params.getPermissions();
				this.applyR2Permissions(r3p);
				this.applyR3Permissions(r3p);
				params = params.withEncryption(v4Params);
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v4",
						"1.4");
			}
			break;

		default:
			throw new IllegalStateException();
		}

		ViewerPreferences vp = params.viewerPreferences();
		vp.setHideToolbar(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_TOOLBAR.getBoolean(this));
		vp.setHideMenubar(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_MENUBAR.getBoolean(this));
		vp.setHideWindowUI(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_WINDOWUI.getBoolean(this));
		vp.setFitWindow(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_FIT_WINDOW.getBoolean(this));
		vp.setCenterWindow(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_CENTER_WINDOW.getBoolean(this));
		if (UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DISPLAY_DOC_TITLE.getBoolean(this)) {
			if (params.version().v >= PDFParams.Version.V_1_4.v) {
				vp.setDisplayDocTitle(true);
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DISPLAY_DOC_TITLE.name, "true", "1.3");
			}
		}

		switch (UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_NON_FULL_SCREEN_PAGE_MODE.getCode(this)) {
		case OutputPdfViewerPreferencesNoneFullScreenPageMode.USE_NONE:
			vp.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.NONE);
			break;
		case OutputPdfViewerPreferencesNoneFullScreenPageMode.USE_OUTLINES:
			vp.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.OUTLINES);
			break;
		case OutputPdfViewerPreferencesNoneFullScreenPageMode.USE_THUMBS:
			vp.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.THUMBS);
			break;
		case OutputPdfViewerPreferencesNoneFullScreenPageMode.USE_OC:
			vp.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.OC);
			break;
		default:
			throw new IllegalStateException();
		}

		short printScaling = (short) UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING.getCode(this);
		if (printScaling != OutputPdfViewerPreferencesPrintScaling.APP_DEFAULT) {
			if (params.version().v >= PDFParams.Version.V_1_6.v) {
				vp.setPrintScaling(ViewerPreferences.PrintScaling.NONE);
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING.name,
						this.getProperty(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING.name), "1.5");
			}
		}

		short duplex = (short) UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX.getCode(this);
		if (duplex != OutputPdfViewerPreferencesDuplex.NONE) {
			if (params.version().v >= PDFParams.Version.V_1_7.v) {
				switch (duplex) {
				case OutputPdfViewerPreferencesDuplex.SIMPLEX:
					vp.setDuplex(ViewerPreferences.Duplex.SIMPLEX);
					break;
				case OutputPdfViewerPreferencesDuplex.FLIP_SHORT_EDGE:
					vp.setDuplex(ViewerPreferences.Duplex.FLIP_SHORT_EDGE);
					break;
				case OutputPdfViewerPreferencesDuplex.FLIP_LONG_EDGE:
					vp.setDuplex(ViewerPreferences.Duplex.FLIP_LONG_EDGE);
					break;
				default:
					throw new IllegalStateException();
				}
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX.name,
						this.getProperty(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX.name), "1.6");
			}
		}

		if (UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PICK_TRAY_BY_PDF_SIZE.getBoolean(this)) {
			if (params.version().v >= PDFParams.Version.V_1_7.v) {
				vp.setPickTrayByPDFSize(true);
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PICK_TRAY_BY_PDF_SIZE.name, "true", "1.6");
			}
		}

		String pageRange = UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE.getString(this);
		if (pageRange != null) {
			if (params.version().v >= PDFParams.Version.V_1_7.v) {
				IntList ranges = new ArrayIntList();
				try {
					for (StringTokenizer st = new StringTokenizer(pageRange, ", "); st.hasMoreTokens();) {
						String token = st.nextToken();
						int hyphen = token.indexOf('-');
						if (hyphen == -1) {
							int page = Integer.parseInt(token);
							ranges.add(page);
							ranges.add(page);
						} else {
							int a = Integer.parseInt(token.substring(0, hyphen));
							int b = Integer.parseInt(token.substring(hyphen + 1));
							ranges.add(a);
							ranges.add(b);
						}
					}
					vp.setPrintPageRange(ranges.toArray());
				} catch (NumberFormatException e) {
					this.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE.name, pageRange);
				}
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE.name, pageRange, "1.6");
			}
		}

		int numCopies = UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES.getInteger(this);
		if (numCopies != 0) {
			if (params.version().v >= PDFParams.Version.V_1_7.v) {
				try {
					vp.setNumCopies(numCopies);
				} catch (IllegalArgumentException e) {
					this.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES.name, String.valueOf(numCopies));
				}
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES.name, String.valueOf(numCopies), "1.6");
			}
		}

		String javaScript = UAProps.OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT.getString(this);
		if (javaScript != null) {
			if (params.version().v >= PDFParams.Version.V_1_3.v) {
				params = params.withOpenAction(new JavaScriptAction(javaScript));
			} else {
				this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT.name, String.valueOf(numCopies), "1.2");
			}
		}

		SourceMetadata metaSource = new SimpleSourceMetadata(URIHelper.CURRENT_URI, "application/pdf", null, -1);
		this.builder = this.results.nextBuilder(metaSource);
		params = params.withMetaInfo(this.metaInfo);
		this.pdfWriter = new PDFWriterImpl(this.builder, params);
		this.setBoundSide(this.getBoundSide());
	}

	private void applyEncryptionParams(EncryptionParams params) {
		params.setUserPassword(UAProps.OUTPUT_PDF_ENCRYPTION_USER_PASSWORD.getString(this));
		params.setOwnerPassword(UAProps.OUTPUT_PDF_ENCRYPTION_OWNER_PASSWORD.getString(this));
	}

	private void applyR2Permissions(R2Permissions r2p) {
		r2p.setPrint(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT.getBoolean(this));
		r2p.setModify(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_MODIFY.getBoolean(this));
		r2p.setCopy(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_COPY.getBoolean(this));
		r2p.setAdd(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ADD.getBoolean(this));
	}

	private void applyR3Permissions(R3Permissions r3p) {
		r3p.setFill(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_FILL.getBoolean(this));
		r3p.setExtract(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_EXTRACT.getBoolean(this));
		r3p.setAssemble(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ASSEMBLE.getBoolean(this));
		r3p.setPrintHigh(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT_HIGH.getBoolean(this));
	}

	public FontManager getFontManager() {
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
			this.getPassContext().getSectionState().title = content;
		}
	}

	public Image getImage(Source source) throws IOException {
		this.preparePDFWriter();
		Image image;
		try {
			image = this.pdfWriter.loadImage(source);
		} catch (IOException e) {
			image = this.loadImage(source);
		}
		AffineTransform pixelToUnit = this.getPixelToUnit();
		if (!pixelToUnit.isIdentity()) {
			image = new TransformedImage(image, pixelToUnit);
		}
		return image;
	}

	public boolean isMeasurePass() {
		return this.results == NopResults.SHARED_INSTANCE;
	}

	public GC nextPage() {
		this.checkAbort(CTISession.ABORT_FORCE);
		try {
			this.preparePDFWriter();
			if (this.isMeasurePass()) {
				this.pageGenerated = true;
				return null;
			}
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
				short mode = UAProps.OUTPUT_PDF_WATERMARK_MODE.getCode(this);
				if (mode == OutputPdfWatermarkMode.BACK) {
					if (this.watermarkGroup == null) {
						PDFPageOutput out = (PDFPageOutput) gc.getPDFGraphicsOutput();
						this.watermarkGroup = out.getPdfWriter().createGroupImage(this.pageWidth, this.pageHeight);
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
							this.watermarkGroup.setOCG(flags);
						}
						PDFGC ggc = new PDFGC(this.watermarkGroup);
						ggc.setFillPaint(this.watermark);
						double opacity = UAProps.OUTPUT_PDF_WATERMARK_OPACITY.getDouble(PDFUserAgent.this);
						if (opacity != 1) {
							if (this.pdfWriter.getParams().version() == PDFParams.Version.V_PDFA1B) {
								this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
										UAProps.OUTPUT_PDF_WATERMARK_OPACITY.name, String.valueOf(opacity), "PDF/A-1");
							} else if (this.pdfWriter.getParams().version() == PDFParams.Version.V_PDFX1A) {
								this.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
										UAProps.OUTPUT_PDF_WATERMARK_OPACITY.name, String.valueOf(opacity), "PDF/X-1a");
							} else {
								ggc.setFillAlpha((float) opacity);
							}
						}
						Rectangle2D mask = new Rectangle2D.Double(0, 0, w, h);
						ggc.fill(mask);
						this.watermarkGroup.close();
					}
					gc.drawImage(this.watermarkGroup);
				}
			}
			return gc;
		} catch (IOException e) {
			throw new GraphicsException(e);
		}
	}

	public void closePage(final GC gc) throws IOException {
		super.closePage(gc);
		if (gc == null) {
			return;
		}
		final PDFGC pdfGc = (PDFGC) gc;
		try (final PDFPageOutput out = (PDFPageOutput) pdfGc.getPDFGraphicsOutput()) {
			if (this.watermark != null) {
				final short mode = UAProps.OUTPUT_PDF_WATERMARK_MODE.getCode(this);
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
							final PDFGC gc = new PDFGC(group);
							if (at != null) {
								AffineTransform atd = new AffineTransform();
								atd.scale(at.getScaleX(), at.getScaleY());
								gc.transform(atd);
							}

							gc.setFillPaint(PDFUserAgent.this.watermark);
							final double opacity = UAProps.OUTPUT_PDF_WATERMARK_OPACITY.getDouble(PDFUserAgent.this);
							if (opacity != 1) {
								PDFParams params = pageOut.getPdfWriter().getParams();
								if (params.version() == PDFParams.Version.V_PDFA1B) {
									message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
											UAProps.OUTPUT_PDF_WATERMARK_OPACITY.name, String.valueOf(opacity),
											"PDF/A-1");
								} else if (params.version() == PDFParams.Version.V_PDFX1A) {
									message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
											UAProps.OUTPUT_PDF_WATERMARK_OPACITY.name, String.valueOf(opacity),
											"PDF/X-1a");
								} else {
									gc.setFillAlpha((float) opacity);
								}
							}
							final Rectangle2D mask = new Rectangle2D.Double(0, 0, PDFUserAgent.this.pageWidth,
									PDFUserAgent.this.pageHeight);
							gc.fill(mask);
							group.close();

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
				try {
					if (mimeType == null) {
						mimeType = attachmetSource.getMimeType();
					}
					Attachment att = new Attachment(description, mimeType);
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
			this.watermarkGroup = null;
		}
		this.results.end();
	}

	public void dispose() {
		super.dispose();
		if (this.builder != null) {
			try {
				this.builder.close();
			} catch (IOException e) {
				// ignore
			}
			this.builder = null;
		}
		this.pdfWriter = null;
		this.watermark = null;
		this.watermarkGroup = null;
	}
}

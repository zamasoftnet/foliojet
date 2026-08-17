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
import net.zamasoft.pdfg2d.pdf.FacturX;
import net.zamasoft.pdfg2d.pdf.params.OutputIntent;
import net.zamasoft.pdfg2d.pdf.params.RenderingIntent;
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

/**
 * 入出力プロパティからPDFParamsを解決します(2026-08-01、85点計画
 * 増分15——PDFUserAgent.preparePDFWriter()約480行が設定解決とwriter
 * 生成を混在させていたのを分離した)。副作用は{@code ua.message()}への
 * 警告発行と{@code metaInfo}への日付設定のみで、writer・出力先の
 * 生成には一切触れない。プロパティ組合せのテストがwriter生成なしで
 * 可能になる。
 *
 * @author MIYABE Tatsuhiko
 */
final class PDFParamsResolver {

	private PDFParamsResolver() {
		// static use only
	}

	/**
	 * 入出力プロパティを解決したPDFParamsを返します。
	 *
	 * @param ua       プロパティ源+警告先
	 * @param metaInfo 文書メタ情報(日付プロパティをここへ設定する)
	 * @return 解決済みパラメータ
	 * @throws IOException 添付ファイル等の読み込みに失敗した場合
	 */
	static PDFParams resolve(final PDFUserAgent ua, final PDFMetaInfo metaInfo) throws IOException {
		PDFParams params = PDFParams.createDefault();
		params = params.withFontSourceManager(ua.getUAContext().getFontSourceManager());

		// バージョン
		switch (UAProps.OUTPUT_PDF_VERSION.get(ua)) {
		case V1_2:
			params = params.withVersion(PDFParams.Version.V_1_2);
			break;
		case V1_3:
			params = params.withVersion(PDFParams.Version.V_1_3);
			break;
		case V1_4:
			params = params.withVersion(PDFParams.Version.V_1_4);
			break;
		case V1_4A1:
			params = params.withVersion(PDFParams.Version.V_PDFA1B);
			break;
		case V1_4X1:
			params = params.withVersion(PDFParams.Version.V_PDFX1A);
			break;
		case V1_5:
			params = params.withVersion(PDFParams.Version.V_1_5);
			break;
		case V1_6:
			params = params.withVersion(PDFParams.Version.V_1_6);
			break;
		case V1_7:
			params = params.withVersion(PDFParams.Version.V_1_7);
			break;
		case V1_7A2:
			params = params.withVersion(PDFParams.Version.V_PDFA2B);
			break;
		case V1_7A2U:
			params = params.withVersion(PDFParams.Version.V_PDFA2U);
			break;
		case V1_7A2A:
			params = params.withVersion(PDFParams.Version.V_PDFA2A);
			break;
		case V1_7A3:
			params = params.withVersion(PDFParams.Version.V_PDFA3B);
			break;
		case V1_7A3A:
			params = params.withVersion(PDFParams.Version.V_PDFA3A);
			break;
		case V2_0A4:
			params = params.withVersion(PDFParams.Version.V_PDFA4);
			break;
		case V1_6X4:
			params = params.withVersion(PDFParams.Version.V_PDFX4);
			break;
		case V2_0X6:
			params = params.withVersion(PDFParams.Version.V_PDFX6);
			break;
		case V1_7UA1:
			params = params.withVersion(PDFParams.Version.V_1_7);
			break;
		case V2_0UA2:
			// PDF/UA-2(ISO 14289-2:2024)はPDF 2.0基底
			params = params.withVersion(PDFParams.Version.V_2_0);
			break;
		case V2_0:
			params = params.withVersion(PDFParams.Version.V_2_0);
			break;
		default:
			throw new IllegalStateException();
		}

		// タグ付き PDF / PDF/UA。level A の PDF/A（A-2a/A-3a）と PDF/UA-1 は
		// 論理構造が必須なので自動で有効化し、それ以外は output.pdf.tagged で選ぶ。
		{
			OutputPdfVersion versionCode = UAProps.OUTPUT_PDF_VERSION.get(ua);
			if (net.zamasoft.foliojet.ua.props.TaggedPdf.isActive(ua)) {
				String lang = UAProps.OUTPUT_PDF_TAGGED_LANG.getString(ua);
				params = params.withTagged(switch (versionCode) {
				case V1_7UA1 -> net.zamasoft.pdfg2d.pdf.params.TaggedParams.pdfua(lang);
				case V2_0UA2 -> net.zamasoft.pdfg2d.pdf.params.TaggedParams.pdfua2(lang);
				default -> new net.zamasoft.pdfg2d.pdf.params.TaggedParams(lang, false);
				});
			}
		}

		// ファイルID
		String fileId = UAProps.OUTPUT_PDF_FILE_ID.getString(ua);
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
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							new String[] { UAProps.OUTPUT_PDF_FILE_ID.name, fileId });
				}
			} else {
				ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
						new String[] { UAProps.OUTPUT_PDF_FILE_ID.name, fileId });
			}
		}

		// 日付
		String creationDate = UAProps.OUTPUT_PDF_META_CREATION_DATE.getString(ua);
		String modDate = UAProps.OUTPUT_PDF_META_MOD_DATE.getString(ua);
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
					metaInfo.setCreationDate(time);
				} catch (ParseException e) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
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
					metaInfo.setModDate(time);
				} catch (ParseException e) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PDF_META_MOD_DATE.name, modDate);
				}
			}
		}

		// 電子インボイス(Factur-X/ZUGFeRD——2026-08-02、PLAN §2の時限1位)。
		// conformance-levelの設定で有効化し、XMPのfx:拡張スキーマを出す。
		// 請求書XML自体はoutput.pdf.attachments.*(relationship=alternative)
		// で添付する——検証器はXMPと添付の両方を見る
		final String facturXLevel = UAProps.OUTPUT_PDF_FACTURX_CONFORMANCE_LEVEL.getString(ua);
		if (facturXLevel != null) {
			metaInfo.setFacturX(new FacturX(UAProps.OUTPUT_PDF_FACTURX_DOCUMENT_TYPE.getString(ua),
					UAProps.OUTPUT_PDF_FACTURX_DOCUMENT_FILE_NAME.getString(ua),
					UAProps.OUTPUT_PDF_FACTURX_VERSION.getString(ua), facturXLevel));
		}

		// 出力インテント(PDF/X適合の実質要件——PLAN §2の2位、2026-08-02。
		// WeasyPrint v67のPDF/X+ICC出荷で無償エンジンに並ばれた項目)
		final String oiIdentifier = UAProps.OUTPUT_PDF_OUTPUT_INTENT_IDENTIFIER.getString(ua);
		if (oiIdentifier != null) {
			byte[] icc = null;
			int components = 4;
			final String iccUri = UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.getString(ua);
			if (iccUri != null) {
				try {
					final net.zamasoft.zstream.resolver.Source source = ua
							.resolve(URIHelper.create("UTF-8", iccUri));
					try (java.io.InputStream in = source.getInputStream();
							java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
						in.transferTo(buffer);
						icc = buffer.toByteArray();
					} finally {
						ua.release(source);
					}
					components = iccColorComponents(icc);
					if (components == -1) {
						ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
								UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name, iccUri);
						icc = null;
						components = 4;
					}
				} catch (final Exception e) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name, iccUri);
					icc = null;
				}
			}
			params = params.withOutputIntent(new OutputIntent(oiIdentifier,
					UAProps.OUTPUT_PDF_OUTPUT_INTENT_CONDITION.getString(ua),
					UAProps.OUTPUT_PDF_OUTPUT_INTENT_REGISTRY.getString(ua),
					UAProps.OUTPUT_PDF_OUTPUT_INTENT_INFO.getString(ua), icc, components));
		}

		// レンダリングインテント(コンテンツストリーム既定のri演算子)
		final String renderingIntent = UAProps.OUTPUT_PDF_RENDERING_INTENT.getString(ua);
		if (renderingIntent != null) {
			switch (renderingIntent.toLowerCase()) {
			case "perceptual" -> params = params.withRenderingIntent(RenderingIntent.PERCEPTUAL);
			case "relative-colorimetric" ->
				params = params.withRenderingIntent(RenderingIntent.RELATIVE_COLORIMETRIC);
			case "saturation" -> params = params.withRenderingIntent(RenderingIntent.SATURATION);
			case "absolute-colorimetric" ->
				params = params.withRenderingIntent(RenderingIntent.ABSOLUTE_COLORIMETRIC);
			default -> ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PDF_RENDERING_INTENT.name,
					renderingIntent);
			}
		}

		// カラー
		OutputColor color = UAProps.OUTPUT_COLOR.get(ua);
		if (params.version() == PDFParams.Version.V_PDFX1A && color == OutputColor.RGB) {
			ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_COLOR.name, "rgb", "PDF/X-1a");
			color = OutputColor.CMYK;
		}
		switch (color) {
		case RGB:
			params = params.withColorMode(PDFParams.ColorMode.PRESERVE);
			break;
		case GRAY:
			params = params.withColorMode(PDFParams.ColorMode.GRAY);
			break;
		case CMYK:
			params = params.withColorMode(PDFParams.ColorMode.CMYK);
			break;
		default:
			throw new IllegalStateException();
		}

		// deflateの水準。範囲外は既定へ落として変換を止めない
		final int deflateLevel = UAProps.OUTPUT_PDF_DEFLATE_LEVEL.getInteger(ua);
		params = params.withDeflateLevel(deflateLevel < -1 || deflateLevel > 9 ? -1 : deflateLevel);

		// 圧縮
		switch (UAProps.OUTPUT_PDF_COMPRESSION.get(ua)) {
		case NONE:
			params = params.withCompression(PDFParams.Compression.NONE);
			break;
		case ASCII:
			params = params.withCompression(PDFParams.Compression.ASCII);
			break;
		case BINARY:
			params = params.withCompression(PDFParams.Compression.BINARY);
			break;
		default:
			throw new IllegalStateException();
		}

		// ブックマーク
		if (UAProps.OUTPUT_PDF_BOOKMARKS.getBoolean(ua)) {
			params = params.withBookmarks(true);
		}

		// JPEG画像
		switch (UAProps.OUTPUT_PDF_JPEG_IMAGE.get(ua)) {
		case RAW:
			params = params.withJPEGImage(PDFParams.JPEGImage.RAW);
			break;
		case TO_FLATE:
		case RECOMPRESS:
			params = params.withJPEGImage(PDFParams.JPEGImage.RECOMPRESS);
			break;
		default:
			throw new IllegalStateException();
		}

		// JPEG圧縮
		switch (UAProps.OUTPUT_PDF_IMAGE_COMPRESSION.get(ua)) {
		case FLATE:
			params = params.withImageCompression(PDFParams.ImageCompression.FLATE);
			break;
		case JPEG:
			params = params.withImageCompression(PDFParams.ImageCompression.JPEG);
			break;
		case JPEG2000:
			params = params.withImageCompression(PDFParams.ImageCompression.JPEG2000);
			break;
		default:
			throw new IllegalStateException();
		}

		// ロスレス圧縮
		params = params.withImageCompressionLossless(UAProps.OUTPUT_PDF_IMAGE_COMPRESSION_LOSSLESS.getInteger(ua));

		// 最大画像サイズ
		params = params.withMaxImageWidth(UAProps.OUTPUT_PDF_IMAGE_MAX_WIDTH.getInteger(ua));
		params = params.withMaxImageHeight(UAProps.OUTPUT_PDF_IMAGE_MAX_HEIGHT.getInteger(ua));

		// プラットフォームエンコーディング
		params = params.withPlatformEncoding(UAProps.OUTPUT_PDF_PLATFORM_ENCODING.getString(ua));

		// 暗号化
		switch (UAProps.OUTPUT_PDF_ENCRYPTION.get(ua)) {
		case NONE:
			break;

		case V1:
			// v1暗号化
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v1",
						"PDF/A-1");
			} else if (params.version() == PDFParams.Version.V_PDFX1A) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v1",
						"PDF/X-1a");
			} else {
				V1EncryptionParams v1Params = new V1EncryptionParams();
				applyEncryptionParams(ua, v1Params);
				R2Permissions r2p = v1Params.getPermissions();
				applyR2Permissions(ua, r2p);
				params = params.withEncryption(v1Params);
			}
			break;

		case V2:
			// v2暗号化
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v2",
						"PDF/A-1");
			} else if (params.version() == PDFParams.Version.V_PDFX1A) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v2",
						"PDF/X-1a");
			} else if (params.version().v >= PDFParams.Version.V_1_3.v) {
				V2EncryptionParams v2Params = new V2EncryptionParams();
				applyEncryptionParams(ua, v2Params);
				int length = UAProps.OUTPUT_PDF_ENCRYPTION_LENGTH.getInteger(ua);
				try {
					v2Params.setLength(length);
				} catch (IllegalArgumentException e) {
					ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
							UAProps.OUTPUT_PDF_ENCRYPTION_LENGTH.name, String.valueOf(length), "V2 Encryption");
				}
				R3Permissions r3p = v2Params.getPermissions();
				applyR2Permissions(ua, r3p);
				applyR3Permissions(ua, r3p);
				params = params.withEncryption(v2Params);
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v2",
						"1.2");
			}
			break;

		case V4:
			// v4暗号化
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v4",
						"PDF/A-1");
			} else if (params.version() == PDFParams.Version.V_PDFX1A) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v4",
						"PDF/X-1a");
			} else if (params.version().v >= PDFParams.Version.V_1_5.v) {
				V4EncryptionParams v4Params = new V4EncryptionParams();
				applyEncryptionParams(ua, v4Params);
				switch (UAProps.OUTPUT_PDF_ENCRYPTION_V4_CFM.get(ua)) {
				case V2:
					v4Params.setCFM(V4EncryptionParams.CFM.V2);
					break;
				case AESV2:
					if (params.version().v >= PDFParams.Version.V_1_6.v) {
						v4Params.setCFM(V4EncryptionParams.CFM.AESV2);
					} else {
						ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
								UAProps.OUTPUT_PDF_ENCRYPTION_V4_CFM.name, "AESV2", "1.5");
					}
					break;
				default:
					throw new IllegalStateException();
				}
				int length = UAProps.OUTPUT_PDF_ENCRYPTION_LENGTH.getInteger(ua);
				try {
					v4Params.setLength(length);
				} catch (IllegalArgumentException e) {
					ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
							UAProps.OUTPUT_PDF_ENCRYPTION_LENGTH.name, String.valueOf(length), "V4 Encryption");
				}
				R3Permissions r3p = v4Params.getPermissions();
				applyR2Permissions(ua, r3p);
				applyR3Permissions(ua, r3p);
				params = params.withEncryption(v4Params);
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v4",
						"1.4");
			}
			break;

		case V5:
			// AES-256 (V5/R6)。PDF 1.7 以上が必要。PDF/A・PDF/X では暗号化不可。
			if (params.version().isPdfA() || params.version().isPdfX()) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v5",
						params.version().isPdfA() ? "PDF/A" : "PDF/X");
			} else if (params.version().v >= PDFParams.Version.V_1_7.v) {
				net.zamasoft.pdfg2d.pdf.params.V5EncryptionParams v5Params =
						new net.zamasoft.pdfg2d.pdf.params.V5EncryptionParams();
				applyEncryptionParams(ua, v5Params);
				R3Permissions r3p = v5Params.getPermissions();
				applyR2Permissions(ua, r3p);
				applyR3Permissions(ua, r3p);
				params = params.withEncryption(v5Params);
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v5",
						"1.6");
			}
			break;

		default:
			throw new IllegalStateException();
		}

		ViewerPreferences vp = params.viewerPreferences();
		vp.setHideToolbar(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_TOOLBAR.getBoolean(ua));
		vp.setHideMenubar(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_MENUBAR.getBoolean(ua));
		vp.setHideWindowUI(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_HIDE_WINDOWUI.getBoolean(ua));
		vp.setFitWindow(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_FIT_WINDOW.getBoolean(ua));
		vp.setCenterWindow(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_CENTER_WINDOW.getBoolean(ua));
		if (UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DISPLAY_DOC_TITLE.getBoolean(ua)) {
			if (params.version().v >= PDFParams.Version.V_1_4.v) {
				vp.setDisplayDocTitle(true);
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DISPLAY_DOC_TITLE.name, "true", "1.3");
			}
		}

		switch (UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_NON_FULL_SCREEN_PAGE_MODE.get(ua)) {
		case USE_NONE:
			vp.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.NONE);
			break;
		case USE_OUTLINES:
			vp.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.OUTLINES);
			break;
		case USE_THUMBS:
			vp.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.THUMBS);
			break;
		case USE_OC:
			vp.setNonFullScreenPageMode(ViewerPreferences.NonFullScreenPageMode.OC);
			break;
		default:
			throw new IllegalStateException();
		}

		OutputPdfViewerPreferencesPrintScaling printScaling = UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING.get(ua);
		if (printScaling != OutputPdfViewerPreferencesPrintScaling.APP_DEFAULT) {
			if (params.version().v >= PDFParams.Version.V_1_6.v) {
				vp.setPrintScaling(ViewerPreferences.PrintScaling.NONE);
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING.name,
						ua.getProperty(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_SCALING.name), "1.5");
			}
		}

		OutputPdfViewerPreferencesDuplex duplex = UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX.get(ua);
		if (duplex != OutputPdfViewerPreferencesDuplex.NONE) {
			if (params.version().v >= PDFParams.Version.V_1_7.v) {
				switch (duplex) {
				case SIMPLEX:
					vp.setDuplex(ViewerPreferences.Duplex.SIMPLEX);
					break;
				case FLIP_SHORT_EDGE:
					vp.setDuplex(ViewerPreferences.Duplex.FLIP_SHORT_EDGE);
					break;
				case FLIP_LONG_EDGE:
					vp.setDuplex(ViewerPreferences.Duplex.FLIP_LONG_EDGE);
					break;
				default:
					throw new IllegalStateException();
				}
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX.name,
						ua.getProperty(UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_DUPLEX.name), "1.6");
			}
		}

		if (UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PICK_TRAY_BY_PDF_SIZE.getBoolean(ua)) {
			if (params.version().v >= PDFParams.Version.V_1_7.v) {
				vp.setPickTrayByPDFSize(true);
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PICK_TRAY_BY_PDF_SIZE.name, "true", "1.6");
			}
		}

		String pageRange = UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE.getString(ua);
		if (pageRange != null) {
			if (params.version().v >= PDFParams.Version.V_1_7.v) {
				IntList ranges = new IntList();
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
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE.name, pageRange);
				}
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_PRINT_PAGE_RANGE.name, pageRange, "1.6");
			}
		}

		int numCopies = UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES.getInteger(ua);
		if (numCopies != 0) {
			if (params.version().v >= PDFParams.Version.V_1_7.v) {
				try {
					vp.setNumCopies(numCopies);
				} catch (IllegalArgumentException e) {
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES.name, String.valueOf(numCopies));
				}
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_VIEWER_PREFERENCES_NUM_COPIES.name, String.valueOf(numCopies), "1.6");
			}
		}

		String javaScript = UAProps.OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT.getString(ua);
		if (javaScript != null) {
			if (params.version().v >= PDFParams.Version.V_1_3.v) {
				params = params.withOpenAction(new JavaScriptAction(javaScript));
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT.name, String.valueOf(numCopies), "1.2");
			}
		}

		return params;
	}

	private static void applyEncryptionParams(final PDFUserAgent ua, final EncryptionParams params) {
		params.setUserPassword(UAProps.OUTPUT_PDF_ENCRYPTION_USER_PASSWORD.getString(ua));
		params.setOwnerPassword(UAProps.OUTPUT_PDF_ENCRYPTION_OWNER_PASSWORD.getString(ua));
	}

	private static void applyR2Permissions(final PDFUserAgent ua, final R2Permissions r2p) {
		r2p.setPrint(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT.getBoolean(ua));
		r2p.setModify(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_MODIFY.getBoolean(ua));
		r2p.setCopy(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_COPY.getBoolean(ua));
		r2p.setAdd(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ADD.getBoolean(ua));
	}

	private static void applyR3Permissions(final PDFUserAgent ua, final R3Permissions r3p) {
		r3p.setFill(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_FILL.getBoolean(ua));
		r3p.setExtract(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_EXTRACT.getBoolean(ua));
		r3p.setAssemble(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_ASSEMBLE.getBoolean(ua));
		r3p.setPrintHigh(UAProps.OUTPUT_PDF_ENCRYPTION_PERMISSIONS_PRINT_HIGH.getBoolean(ua));
	}

	/**
	 * ICCプロファイルヘッダの色空間シグネチャ(オフセット16..19)から
	 * 色成分数を判別します(CMYK=4、RGB=3、GRAY=1。判別不能は-1)。
	 */
	private static int iccColorComponents(final byte[] icc) {
		if (icc == null || icc.length < 20) {
			return -1;
		}
		final String sig = new String(icc, 16, 4, java.nio.charset.StandardCharsets.US_ASCII);
		return switch (sig) {
		case "CMYK" -> 4;
		case "RGB " -> 3;
		case "GRAY" -> 1;
		default -> -1;
		};
	}
}

package net.zamasoft.foliojet.ua.impl.pdf;

import java.awt.color.ColorSpace;
import java.awt.color.ICC_Profile;
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
 * Resolves PDFParams from I/O properties (2026-08-01, increment 15 of the 85-point plan:
 * separated configuration resolution from writer creation, previously mixed in about
 * 480 lines of PDFUserAgent.preparePDFWriter()). The only side effects are warnings via
 * {@code ua.message()} and setting dates on {@code metaInfo}; this does not create writers
 * or output destinations. Property combinations can thus be tested without creating a writer.
 *
 * @author MIYABE Tatsuhiko
 */
final class PDFParamsResolver {

	private PDFParamsResolver() {
		// static use only
	}

	/**
	 * Returns PDFParams resolved from I/O properties.
	 *
	 * @param ua       property source and warning destination
	 * @param metaInfo document metadata (date properties are set here)
	 * @return resolved parameters
	 * @throws IOException if reading attachments or similar resources fails
	 */
	static PDFParams resolve(final PDFUserAgent ua, final PDFMetaInfo metaInfo) throws IOException {
		PDFParams params = PDFParams.createDefault();
		params = params.withFontSourceManager(ua.getUAContext().getFontSourceManager());

		// Version
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
		case V1_4X3:
			params = params.withVersion(PDFParams.Version.V_PDFX3);
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
			// PDF/UA-2 (ISO 14289-2:2024) is based on PDF 2.0.
			params = params.withVersion(PDFParams.Version.V_2_0);
			break;
		case V2_0:
			params = params.withVersion(PDFParams.Version.V_2_0);
			break;
		default:
			throw new IllegalStateException();
		}

		// Tagged PDF / PDF/UA. Level A PDF/A (A-2a/A-3a) and PDF/UA-1 require
		// logical structure, so enable it automatically; otherwise use output.pdf.tagged.
		{
			OutputPdfVersion versionCode = UAProps.OUTPUT_PDF_VERSION.get(ua);
			if (net.zamasoft.foliojet.ua.props.TaggedPdf.isActive(ua)) {
				String lang = UAProps.OUTPUT_PDF_TAGGED_LANG.getString(ua);
				if ((versionCode == OutputPdfVersion.V1_7UA1 || versionCode == OutputPdfVersion.V2_0UA2)
						&& (lang == null || lang.isBlank())) {
					// PDF/UA requires a document language. Until 2026-10-05, this failed with an unexpected exception.
					final short code = MessageCodes.ERROR_PDFUA_LANG;
					final String[] args = { versionCode.ident() };
					ua.message(code, args);
					throw new jp.cssj.cti2.TranscoderException(code, args, MessageCodeUtils.toString(code, args));
				}
				params = params.withTagged(switch (versionCode) {
				case V1_7UA1 -> net.zamasoft.pdfg2d.pdf.params.TaggedParams.pdfua(lang);
				case V2_0UA2 -> net.zamasoft.pdfg2d.pdf.params.TaggedParams.pdfua2(lang);
				default -> new net.zamasoft.pdfg2d.pdf.params.TaggedParams(lang, 0);
				});
			}
		}

		// File ID
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

		// Dates
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

		// Electronic invoices (Factur-X/ZUGFeRD; 2026-08-02, top time-sensitive priority in PLAN §2).
		// Enable via conformance-level and emit the fx: XMP extension schema.
		// Attach the invoice XML itself with output.pdf.attachments.* (relationship=alternative).
		// Validators inspect both XMP and the attachment.
		final String facturXLevel = UAProps.OUTPUT_PDF_FACTURX_CONFORMANCE_LEVEL.getString(ua);
		if (facturXLevel != null) {
			metaInfo.setFacturX(new FacturX(UAProps.OUTPUT_PDF_FACTURX_DOCUMENT_TYPE.getString(ua),
					UAProps.OUTPUT_PDF_FACTURX_DOCUMENT_FILE_NAME.getString(ua),
					UAProps.OUTPUT_PDF_FACTURX_VERSION.getString(ua), facturXLevel));
		}

		// Output intent (a substantive PDF/X conformance requirement; second priority in PLAN §2, 2026-08-02.
		// WeasyPrint v67's release of PDF/X + ICC brought a free engine to parity on this feature.)
		final String oiIdentifier = UAProps.OUTPUT_PDF_OUTPUT_INTENT_IDENTIFIER.getString(ua);
		final String iccUri = UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.getString(ua);
		final boolean pdfX = params.version().isPdfX();
		if (oiIdentifier == null && iccUri != null && pdfX) {
			// Keep the existing behavior of not creating OutputIntent without an identifier,
			// but warn that specifying only an ICC profile for PDF/X leaves that profile unused.
			ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
					UAProps.OUTPUT_PDF_OUTPUT_INTENT_IDENTIFIER.name, "");
		}
		if (oiIdentifier != null) {
			if (pdfX && oiIdentifier.isBlank()) {
				throw pdfXOutputIntentError(ua, UAProps.OUTPUT_PDF_OUTPUT_INTENT_IDENTIFIER.name,
						oiIdentifier, "380E.identifier");
			}
			// Identifiers and registry names describe printing conditions (all ICC registered names are ASCII). PDF/X
			// allows printable ASCII only (2026-10-07; previously, truncating to the low 8 bits silently corrupted them).
			final String oiRegistry = UAProps.OUTPUT_PDF_OUTPUT_INTENT_REGISTRY.getString(ua);
			if (pdfX && !printableAscii(oiIdentifier)) {
				throw pdfXOutputIntentError(ua, UAProps.OUTPUT_PDF_OUTPUT_INTENT_IDENTIFIER.name,
						oiIdentifier, "380E.identifier-ascii");
			}
			if (pdfX && oiRegistry != null && !printableAscii(oiRegistry)) {
				throw pdfXOutputIntentError(ua, UAProps.OUTPUT_PDF_OUTPUT_INTENT_REGISTRY.name,
						oiRegistry, "380E.identifier-ascii");
			}
			// PDF/X output intents require DestOutputProfile (previously, a raw pdfg2d exception failed conversion).
			if (pdfX && iccUri == null) {
				throw pdfXOutputIntentError(ua, UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name, "",
						"380E.missing-profile");
			}
			byte[] icc = null;
			int components = 4;
			if (iccUri != null) {
				byte[] candidate = null;
				try {
					final net.zamasoft.zstream.resolver.Source source = ua
							.resolve(URIHelper.create("UTF-8", iccUri));
					try (java.io.InputStream in = source.getInputStream();
							java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream()) {
						in.transferTo(buffer);
						candidate = buffer.toByteArray();
					} finally {
						ua.release(source);
					}
				} catch (final Exception e) {
					if (pdfX) {
						throw pdfXOutputIntentError(ua, UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name,
								iccUri, "380E.unreadable");
					}
					ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
							UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name, iccUri);
				}
				if (candidate != null) {
					ICC_Profile profile = null;
					int profileClass = 0;
					int colorSpaceType = 0;
					int profileComponents = 0;
					try {
						profile = ICC_Profile.getInstance(candidate);
						profileClass = profile.getProfileClass();
						colorSpaceType = profile.getColorSpaceType();
						profileComponents = profile.getNumComponents();
					} catch (final RuntimeException e) {
						profile = null;
						if (pdfX) {
							throw pdfXOutputIntentError(ua,
									UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name, iccUri,
									"380E.unreadable");
						}
						ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
								UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name, iccUri);
					}
					if (profile != null) {
						String errorDetail = null;
						if (profileClass != ICC_Profile.CLASS_OUTPUT) {
							errorDetail = "380E.profile-class";
						} else if (pdfX && colorSpaceType != ColorSpace.TYPE_CMYK) {
							errorDetail = "380E.color-space";
						} else if ((profileComponents != 1 && profileComponents != 3 && profileComponents != 4)
								|| (pdfX && profileComponents != 4)) {
							errorDetail = "380E.component-count";
						} else if (params.version().isPdfXOnPdf14() && profile.getMajorVersion() >= 4) {
							// ICC v4 requires PDF 1.5 or later. X-1a and X-3 are based on PDF 1.4.
							errorDetail = "380E.icc-version";
						}
						if (errorDetail != null) {
							if (pdfX) {
								throw pdfXOutputIntentError(ua,
										UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name, iccUri, errorDetail);
							}
							ua.message(MessageCodes.WARN_BAD_IO_PROPERTY,
									UAProps.OUTPUT_PDF_OUTPUT_INTENT_ICC_PROFILE.name, iccUri);
						} else {
							icc = candidate;
							components = profileComponents;
						}
					}
				}
			}
			params = params.withOutputIntent(new OutputIntent(oiIdentifier,
					UAProps.OUTPUT_PDF_OUTPUT_INTENT_CONDITION.getString(ua),
					UAProps.OUTPUT_PDF_OUTPUT_INTENT_REGISTRY.getString(ua),
					UAProps.OUTPUT_PDF_OUTPUT_INTENT_INFO.getString(ua), icc, components));
		}

		// Rendering intent (the default ri operator in the content stream)
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

		// Color
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

		// Compression
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

		// Bookmarks (PDF outline)
		if (UAProps.navigation(UAProps.OUTPUT_PDF_BOOKMARKS, ua)) {
			params = params.withBookmarks(true);
		}
		if (UAProps.OUTPUT_PDF_BIDI_ACTUAL_TEXT.getBoolean(ua)) {
			params = params.withActualTextReplacement(true);
		}

		// JPEG images
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

		// JPEG compression
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

		// Lossless compression
		params = params.withImageCompressionLossless(UAProps.OUTPUT_PDF_IMAGE_COMPRESSION_LOSSLESS.getInteger(ua));

		// Maximum image size
		params = params.withMaxImageWidth(UAProps.OUTPUT_PDF_IMAGE_MAX_WIDTH.getInteger(ua));
		params = params.withMaxImageHeight(UAProps.OUTPUT_PDF_IMAGE_MAX_HEIGHT.getInteger(ua));
		// Pixel limit (reject by header dimensions before decoding; 2026-10-03)
		params = params.withImagePixelLimit(UAProps.INPUT_IMAGE_PIXEL_LIMIT.getLong(ua));

		// Resolution for rasterizing only box-shadow/text-shadow shadows
		params = params.withBlurRasterDpi(UAProps.OUTPUT_PDF_BLUR_RESOLUTION.getInteger(ua));
		// Resolution for rasterizing only elements with filters
		params = params.withFilterRasterDpi(UAProps.OUTPUT_PDF_FILTER_RESOLUTION.getInteger(ua));

		// Platform encoding
		params = params.withPlatformEncoding(platformEncoding(ua));

		// Encryption
		// Names of PDF/X variants based on PDF 1.4 (X-1a, X-3). Warn and omit encryption.
		final String pdf14PdfX = params.version().isPdfXOnPdf14() ? pdfxName(params.version()) : null;
		switch (UAProps.OUTPUT_PDF_ENCRYPTION.get(ua)) {
		case NONE:
			break;

		case V1:
			// v1 encryption
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v1",
						"PDF/A-1");
			} else if (pdf14PdfX != null) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v1",
						pdf14PdfX);
			} else {
				V1EncryptionParams v1Params = new V1EncryptionParams();
				applyEncryptionParams(ua, v1Params);
				R2Permissions r2p = v1Params.getPermissions();
				applyR2Permissions(ua, r2p);
				params = params.withEncryption(v1Params);
			}
			break;

		case V2:
			// v2 encryption
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v2",
						"PDF/A-1");
			} else if (pdf14PdfX != null) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v2",
						pdf14PdfX);
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
			// v4 encryption
			if (params.version() == PDFParams.Version.V_PDFA1B) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v4",
						"PDF/A-1");
			} else if (pdf14PdfX != null) {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY, UAProps.OUTPUT_PDF_ENCRYPTION.name, "v4",
						pdf14PdfX);
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
			// AES-256 (V5/R6). Requires PDF 1.7 or later. PDF/A and PDF/X prohibit encryption.
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
			if (params.version().isPdfA() || params.version().isPdfX()) {
				// PDF/A and PDF/X prohibit actions (JavaScript). pdfg2d throws an exception, so warn and omit them here.
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT.name, javaScript,
						params.version().isPdfA() ? "PDF/A" : "PDF/X");
			} else if (params.version().v >= PDFParams.Version.V_1_3.v) {
				params = params.withOpenAction(new JavaScriptAction(javaScript));
			} else {
				ua.message(MessageCodes.WARN_UNSUPPORTED_PDF_CAPABILITY,
						UAProps.OUTPUT_PDF_OPEN_ACTION_JAVA_SCRIPT.name, javaScript, "1.2");
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
	 * PDF/X name used in warnings (the version name for X-1a and X-3; otherwise "PDF/X").
	 * Read it from the version being written: during continuous conversion, UA properties may already
	 * have changed to the next document's values (codex review, 2026-09-30).
	 */
	static String pdfxName(final PDFParams.Version version) {
		return switch (version) {
		case V_PDFX1A -> "PDF/X-1a";
		case V_PDFX3 -> "PDF/X-3";
		default -> "PDF/X";
		};
	}

	/**
	 * Character encoding for names inside PDF. PDF syntax uses ASCII, so accept only encodings
	 * that represent ASCII with the same bytes; otherwise warn and restore the default
	 * (2026-10-05; previously, unknown names failed the entire conversion and UTF-16 produced
	 * PDF with broken syntax).
	 */
	private static String platformEncoding(final PDFUserAgent ua) {
		final String name = UAProps.OUTPUT_PDF_PLATFORM_ENCODING.getString(ua);
		try {
			final String probe = "AZaz09 /()<>[]{}%";
			if (java.nio.charset.Charset.isSupported(name) && java.util.Arrays.equals(probe.getBytes(name),
					probe.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
				return name;
			}
		} catch (final IllegalArgumentException | java.io.UnsupportedEncodingException e) {
			// Warn below (IllegalCharsetNameException is an IllegalArgumentException).
		}
		ua.message(MessageCodes.WARN_BAD_IO_PROPERTY, UAProps.OUTPUT_PDF_PLATFORM_ENCODING.name, name);
		return UAProps.OUTPUT_PDF_PLATFORM_ENCODING.getDefaultString();
	}

	private static boolean printableAscii(final String s) {
		return s.chars().allMatch(c -> c >= 0x20 && c <= 0x7E);
	}

	/** Returns to the caller with a code (until 2026-10-05, a raw IOException was returned as unexpected exception 4001). */
	private static IOException pdfXOutputIntentError(final PDFUserAgent ua, final String property,
			final String value, final String detailKey) {
		final short code = MessageCodes.ERROR_PDFX_OUTPUT_INTENT;
		final String[] args = new String[] { property, value, MessageCodeUtils.detail(detailKey) };
		ua.message(code, args);
		return new jp.cssj.cti2.TranscoderException(code, args, MessageCodeUtils.toString(code, args));
	}
}

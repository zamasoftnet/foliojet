package net.zamasoft.foliojet.formatter.impl.image;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import jp.cssj.cti2.TranscoderException;
import jp.cssj.cti2.helpers.CTIMessageCodes;
import net.zamasoft.foliojet.formatter.Formatter;
import net.zamasoft.foliojet.ua.impl.image.RasterImageLoader;
import net.zamasoft.foliojet.message.MessageCodeUtils;
import net.zamasoft.foliojet.layout.imposition.Imposition;
import net.zamasoft.foliojet.ua.impl.Impositions;
import net.zamasoft.foliojet.ua.AbortException;
import net.zamasoft.foliojet.ua.ImageLoader;
import net.zamasoft.foliojet.ua.UserAgent;
import net.zamasoft.foliojet.plugin.PluginRegistry;
import net.zamasoft.zstream.resolver.Source;
import net.zamasoft.pdfg2d.gc.GC;
import net.zamasoft.pdfg2d.gc.image.Image;

public class ImageFormatter implements Formatter {
	private static final Logger LOG = Logger.getLogger(ImageFormatter.class.getName());

	public boolean match(Source source) {
		try {
			final String mimeType = source.getMimeType();
			if (mimeType != null && !mimeType.startsWith("image/")) {
				return false;
			}
			final ImageLoader loader = PluginRegistry.getInstance().search(ImageLoader.class, source);
			if (loader instanceof RasterImageLoader) {
				return ((RasterImageLoader) loader).available(source);
			}
			return loader != null;
		} catch (IOException e) {
			LOG.log(Level.WARNING, "変換元文書のMIME型を取得できませんでした", e);
		}
		return false;
	}

	public void format(Source source, UserAgent ua) throws AbortException, TranscoderException {
		try {
			final Image image = ua.getImage(source);
			double iw = image.getWidth();
			double ih = image.getHeight();

			Imposition imposition = Impositions.createImposition(ua);
			imposition.setPageWidth(iw);
			imposition.setPageHeight(ih);
			Impositions.setupImposition(ua, imposition);

			final GC gc = imposition.nextPage();
			if (gc != null) {
				gc.drawImage(image);
				imposition.closePage();
			}
			imposition.finish();
		} catch (IOException e) {
			short code = CTIMessageCodes.ERROR_IO;
			String[] args = new String[] { e.getMessage() };
			String mes = MessageCodeUtils.toString(code, args);
			ua.message(code, args);
			LOG.log(Level.WARNING, mes, e);
			throw new TranscoderException(code, args, mes);
		}
	}
}

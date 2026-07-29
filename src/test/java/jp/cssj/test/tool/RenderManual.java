package jp.cssj.test.tool;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.DirectoryResults;
import jp.cssj.cti2.results.SingleResult;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** 説明書をPDFへ変換する検証用ツールです(製品には含めません)。 */
public final class RenderManual {
	public static void main(final String[] args) throws Exception {
		final File in = new File(args[0]);
		final File out = new File(args[1]);
		final String passes = args.length > 2 ? args[2] : "3";
		final boolean image = out.isDirectory();
		try (OutputStream os = image ? OutputStream.nullOutputStream() : new FileOutputStream(out)) {
			final DirectSession session = (DirectSession) new DirectDriver()
					.getSession(URI.create("copper:direct:"), null);
			try {
				if (image) {
					session.setResults(new DirectoryResults(out, "page", ".png"));
					session.property("output.type", "image/png");
					session.property("output.resolution", "110");
				} else {
					session.setResults(new SingleResult(new StreamFragmentedOutput(os)));
				}
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				session.property("processing.pass-count", passes);
				session.property("processing.page-references", "true");
				for (int i = 3; i + 1 < args.length; i += 2) {
					session.property(args[i], args[i + 1]);
				}
				CTISessionHelper.transcodeFile(session, in, "application/xml", null);
			} finally {
				session.close();
			}
		}
		System.out.println("done: " + out + " " + out.length() + " bytes");
	}
}

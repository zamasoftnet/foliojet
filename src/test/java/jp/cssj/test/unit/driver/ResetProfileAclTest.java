package jp.cssj.test.unit.driver;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.foliojet.message.MessageCodes;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/**
 * The profile's {@code input.exclude} keeps its place ahead of the client's patterns after a session reset.
 *
 * <p>
 * The first matching pattern wins, so what the operator excludes in the profile must come before whatever a client
 * sends. {@code DirectSession.reset()} read the profile and then cleared the resolver, which wiped the profile's
 * patterns; they came back only at the next conversion, behind the client's {@code input.include=**}, and the
 * excluded resource was read.
 * </p>
 */
public class ResetProfileAclTest extends TestCase {
	private File profile;

	private File document;

	@Override
	protected void setUp() throws Exception {
		final File dir = new File("local/unittest/reset-acl");
		dir.mkdirs();
		Files.writeString(new File(dir, "secret.css").toPath(), "p { color: #123456 }\n", StandardCharsets.UTF_8);
		this.document = new File(dir, "doc.html");
		Files.writeString(this.document.toPath(), "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">"
				+ "<link rel=\"stylesheet\" type=\"text/css\" href=\"secret.css\">"
				+ "</head><body><p>probe</p></body></html>\n", StandardCharsets.UTF_8);
		// Beside the test profile, so that the fonts it names are found
		final File baseProfile = new File(System.getProperty("jp.cssj.driver.default"));
		this.profile = new File(baseProfile.getParentFile(), this.getClass().getSimpleName() + ".properties");
		Files.writeString(this.profile.toPath(), "system.fonts=fonts/fonts.xml\ninput.exclude=**/secret.css\n",
				StandardCharsets.ISO_8859_1);
	}

	@Override
	protected void tearDown() throws Exception {
		if (this.profile != null) {
			Files.deleteIfExists(this.profile.toPath());
		}
	}

	/** The control: in a fresh session the profile's exclude wins over the client's {@code input.include=**}. */
	public void testProfileExcludeWinsInFreshSession() throws Exception {
		final List<String> blocked = this.convert(false);
		assertTrue("the stylesheet excluded by the profile must be refused: " + blocked, refusedSecret(blocked));
	}

	/** It still wins after {@code reset()} (CTIP reset, REST /reset). */
	public void testProfileExcludeWinsAfterReset() throws Exception {
		final List<String> blocked = this.convert(true);
		assertTrue("the stylesheet excluded by the profile must be refused after a reset too: " + blocked,
				refusedSecret(blocked));
	}

	private static boolean refusedSecret(final List<String> blocked) {
		for (final String uri : blocked) {
			if (uri.endsWith("secret.css")) {
				return true;
			}
		}
		return false;
	}

	/** Converts the document with {@code input.include=**} from the client; returns the resources reported as refused. */
	private List<String> convert(final boolean reset) throws Exception {
		final List<String> blocked = new ArrayList<String>();
		// copper:direct:<profile> names the profile for the session, as a server's connection URI does
		final URI uri = new URI("copper", "direct:" + this.profile.getAbsolutePath().replace('\\', '/'), null);
		final DirectSession session = (DirectSession) new DirectDriver().getSession(uri, null);
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			session.setMessageHandler((code, args, mes) -> {
				if (code == MessageCodes.WARN_BLOCKED_RESOURCE && args != null && args.length > 0) {
					blocked.add(args[0]);
				}
			});
			if (reset) {
				session.reset();
			}
			session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
			// The resolver of the embedding application, as the command line and the servers install it;
			// after the reset, which removes it
			session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
			session.property("input.include", "**");
			CTISessionHelper.transcodeFile(session, this.document, "text/html", null);
		} finally {
			session.close();
		}
		return blocked;
	}
}

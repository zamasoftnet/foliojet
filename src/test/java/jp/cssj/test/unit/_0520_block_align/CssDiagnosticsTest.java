package jp.cssj.test.unit._0520_block_align;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.test.unit.AbstractTestCase;
import net.zamasoft.foliojet.message.MessageCodes;

/** 未対応プロパティと不正値を、変換ログへ必ず通知する。 */
public class CssDiagnosticsTest extends AbstractTestCase {
	private final List<Short> codes = new ArrayList<>();
	private final List<String> names = new ArrayList<>();

	public CssDiagnosticsTest(String name) {
		super(name);
	}

	@Override
	public void message(short code, String[] args, String mes) {
		super.message(code, args, mes);
		this.codes.add(code);
		this.names.add(args != null && args.length != 0 ? args[0] : null);
	}

	@Override
	protected void transcode() throws Exception {
		CTISessionHelper.transcodeFile(this.session,
				new File("files/unittest/0520-block-align/css-diagnostics.html"), "text/html", null);
		assertDiagnostic(MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY, "copper-unknown-property");
		assertDiagnostic(MessageCodes.WARN_BAD_CSS_ARGMENTS, "color");
		assertNoUnsupportedDiagnostic("text-emphasis");
	}

	private void assertDiagnostic(short expectedCode, String expectedName) {
		for (int i = 0; i < this.codes.size(); ++i) {
			if (this.codes.get(i).shortValue() == expectedCode && expectedName.equals(this.names.get(i))) {
				return;
			}
		}
		fail("Missing CSS diagnostic " + Integer.toHexString(expectedCode & 0xFFFF) + " for " + expectedName);
	}

	private void assertNoUnsupportedDiagnostic(String propertyName) {
		for (int i = 0; i < this.codes.size(); ++i) {
			if (this.codes.get(i).shortValue() == MessageCodes.WARN_UNSUPPORTED_CSS_PROPERTY
					&& propertyName.equals(this.names.get(i))) {
				fail("Supported CSS property was reported as unsupported: " + propertyName);
			}
		}
	}
}

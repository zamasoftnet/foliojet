package jp.cssj.test.unit._3090_bidi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNull;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDNameTreeNode;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionURI;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import jp.cssj.cti2.helpers.CTIMessageHelper;
import jp.cssj.cti2.helpers.CTISessionHelper;
import jp.cssj.cti2.results.SingleResult;
import junit.framework.TestCase;
import net.zamasoft.foliojet.driver.DirectDriver;
import net.zamasoft.foliojet.driver.DirectSession;
import net.zamasoft.zstream.io.impl.StreamFragmentedOutput;
import net.zamasoft.zstream.resolver.composite.CompositeSourceResolver;

/** visual tree の自己検査に依存しない、出力 PDF 上の bidi 受入検査。 */
public class ParagraphBidiPdfTest extends TestCase {
	private static final URI COPPER_URI = URI.create("copper:direct:");
	private static final String LINK_URI = "https://example.test/bidi-semantic";
	private static final String SEMANTIC_TEXT = "אבג-ABC-דהו-DEF-וזח-GHI-טיך-JKL";
	private static final String MIRROR_CHARS = "()[]{}«»";

	static {
		System.setProperty("jp.cssj.copper.config", System.getProperty("jp.cssj.copper.config", "build/conf"));
		System.setProperty("jp.cssj.driver.default",
				System.getProperty("jp.cssj.driver.default", "build/conf/profiles/default.properties"));
	}

	public void testPdfGlyphCoordinatesAndInlineSemantics() throws Exception {
		final File output = new File("local/unittest/pdf/" + this.getClass().getName() + ".pdf");
		writePdf(new File("files/unittest/3090-bidi/stage1.html"), output, true);

		try (PDDocument pdf = Loader.loadPDF(output)) {
			final List<PdfLine> lines = collectLines(pdf);
			assertRtl(lines);
			assertEmbed(lines);
			assertOverride(lines);
			assertMirror(lines);
			assertInlineSemantics(pdf);
		}
	}

	public void testPdfFloatIsOnPhysicalRight() throws Exception {
		final File output = new File("local/unittest/pdf/" + this.getClass().getName() + "-float.pdf");
		writePdf(new File("files/unittest/3090-bidi/float-barrier.html"), output, false);
		try (PDDocument pdf = Loader.loadPDF(output)) {
			final TextPosition marker = collectLines(pdf).stream().flatMap(line -> line.text().stream())
					.filter(value -> "X".equals(value.getUnicode())).findFirst()
					.orElseThrow(() -> new AssertionError("float marker was not found in the PDF"));
			assertTrue("rtl inline-start float must be on the physical right: x=" + marker.getXDirAdj(),
					marker.getXDirAdj() > 245);
		}
	}

	public void testLogicalActualTextAndShadowPasses() throws Exception {
		final File output = new File("local/unittest/pdf/" + this.getClass().getName() + "-logical.pdf");
		writePdf(new File("files/unittest/3090-bidi/logical-output.html"), output, false, false, true);
		try (PDDocument pdf = Loader.loadPDF(output)) {
			final List<ActualTextScan> scopes = actualTextScopes(pdf.getPage(0));
			assertActualText(scopes, "אבג ABC");
			assertActualText(scopes, "אבג SHADOW");
			assertActualText(scopes, "אבג BLUR");
			assertEquals("rasterized filter text must not open ActualText", 0,
					scopes.stream().filter(value -> "אבג FILTER".equals(value.value)).count());

			// PDFBox 3.0.3 currently ignores ActualText. Keep both heuristic results as an
			// observation only; the decoded dictionary value above is the assertion oracle.
			final String unsorted = extractedText(pdf, false);
			final String sorted = extractedText(pdf, true);
			System.err.println("PDFBox 3.0.3 #rtl observation sortByPosition=false: "
					+ oneLine(firstExtractedLine(unsorted)));
			System.err.println("PDFBox 3.0.3 #rtl observation sortByPosition=true: "
					+ oneLine(firstExtractedLine(sorted)));
			assertNotNull(unsorted);
			assertNotNull(sorted);
		}
	}

	public void testTaggedParagraphKidsUseLogicalOrderAndParentTreeUsesPaintMcids() throws Exception {
		final File output = new File("local/unittest/pdf/" + this.getClass().getName() + "-tagged.pdf");
		writePdf(new File("files/unittest/3090-bidi/ua-logical-output.html"), output, false, true, true);
		try (PDDocument pdf = Loader.loadPDF(output)) {
			final ActualTextScan rtl = onlyScope(actualTextScopes(pdf.getPage(0)), "אבג ABC");
			final List<Integer> paint = rtl.mcids;
			assertTaggedLogicalOrder(pdf, paint);
		}
	}

	public void testDefaultOmitsActualTextAndKeepsLogicalOutput() throws Exception {
		final File output = new File("local/unittest/pdf/" + this.getClass().getName() + "-default.pdf");
		writePdf(new File("files/unittest/3090-bidi/stage1.html"), output, false, true);
		try (PDDocument pdf = Loader.loadPDF(output)) {
			for (final var page : pdf.getPages()) {
				assertTrue("ActualText must be absent by default", actualTextScopes(page).isEmpty());
			}
			final List<PdfLine> lines = collectLines(pdf);
			assertRtl(lines);
			assertOverride(lines);

			final List<Integer> allPaint = markedContentMcids(pdf.getPage(0));
			assertTrue("the first paragraph must have six visual glyph leaves", allPaint.size() >= 6);
			assertTaggedLogicalOrder(pdf, new ArrayList<>(allPaint.subList(0, 6)));
		}
		// 鏡像の ToUnicode は埋め込み subset で検査する。stage1 の core フォントでは ActualText 無しだと
		// PDFBox が約物の Unicode を得られない
		final File mirrorOutput = new File("local/unittest/pdf/" + this.getClass().getName() + "-default-mirror.pdf");
		writePdf(new File("files/unittest/3090-bidi/mirror-embedded.html"), mirrorOutput, false, false);
		try (PDDocument pdf = Loader.loadPDF(mirrorOutput)) {
			for (final var page : pdf.getPages()) {
				assertTrue("ActualText must be absent by default", actualTextScopes(page).isEmpty());
			}
			final List<PdfLine> lines = collectLines(pdf);
			assertMirror(lines);
			assertMirrorDisplayCharacters(lines);
		}
	}

	public void testActualTextOptInKeepsMirrorCidAliases() throws Exception {
		final File output = new File("local/unittest/pdf/" + this.getClass().getName() + "-alias-mirror.pdf");
		writePdf(new File("files/unittest/3090-bidi/mirror-embedded.html"), output, false, false, true);
		try (PDDocument pdf = Loader.loadPDF(output)) {
			final List<PdfLine> lines = collectLines(pdf);
			assertMirror(lines);
			assertMirrorCidAliases(lines);
		}
	}

	private static void writePdf(final File source, final File output, final boolean pageReferences) throws Exception {
		writePdf(source, output, pageReferences, false, false);
	}

	private static void writePdf(final File source, final File output, final boolean pageReferences,
			final boolean tagged) throws Exception {
		writePdf(source, output, pageReferences, tagged, false);
	}

	private static void writePdf(final File source, final File output, final boolean pageReferences,
			final boolean tagged, final boolean actualText) throws Exception {
		output.getParentFile().mkdirs();
		try (OutputStream out = new FileOutputStream(output)) {
			final DirectSession session = (DirectSession) new DirectDriver().getSession(COPPER_URI, null);
			try {
				session.setResults(new SingleResult(new StreamFragmentedOutput(out)));
				session.setMessageHandler(CTIMessageHelper.createStreamMessageHandler(System.err));
				session.setSourceResolver(CompositeSourceResolver.createGenericCompositeSourceResolver());
				session.property("input.include", "**");
				session.property("input.property-pi", "true");
				session.property("layout.bidi.paragraph", "true");
				session.property("output.pdf.hyperlinks", "true");
				// 既定の fonts.policy(cid-keyed)は埋め込みを含まず @font-face が無視される。鏡像の CID alias は
				// 埋め込み subset でだけ成立するので、埋め込みを許す
				session.property("output.pdf.fonts.policy", "core embedded");
				if (actualText) {
					session.property("output.pdf.bidi.actual-text", "true");
				}
				if (tagged) {
					session.property("output.pdf.version", "1.7UA-1");
					session.property("output.pdf.tagged.lang", "he");
				}
				if (pageReferences) {
					session.property("processing.page-references", "true");
				}
				session.property("processing.pass-count", pageReferences ? "3" : "1");
				CTISessionHelper.transcodeFile(session, source, "text/html", null);
			} finally {
				session.close();
			}
		}
	}

	private static final class ActualTextScan {
		final COSName tag;
		final String value;
		final List<Integer> mcids = new ArrayList<>();
		int textOperators;

		ActualTextScan(final COSName tag, final String value) {
			this.tag = tag;
			this.value = value;
		}
	}

	private static List<ActualTextScan> actualTextScopes(final org.apache.pdfbox.pdmodel.PDPage page)
			throws Exception {
		final List<ActualTextScan> scopes = new ArrayList<>();
		final Deque<ActualTextScan> active = new ArrayDeque<>();
		final Deque<Boolean> markedContent = new ArrayDeque<>();
		final PDFStreamParser parser = new PDFStreamParser(page);
		final List<Object> operands = new ArrayList<>();
		Object token;
		while ((token = parser.parseNextToken()) != null) {
			if (!(token instanceof Operator operator)) {
				operands.add(token);
				continue;
			}
			if ("BDC".equals(operator.getName())) {
				final COSName tag = operands.size() >= 2
						&& operands.get(operands.size() - 2) instanceof COSBase rawTag
						&& dereference(rawTag) instanceof COSName value
								? value : null;
				final COSDictionary properties = operands.size() >= 2
						&& operands.get(operands.size() - 1) instanceof COSBase rawProperties
						&& dereference(rawProperties) instanceof COSDictionary value
								? value : null;
				final String actual = properties == null ? null : properties.getString(COSName.ACTUAL_TEXT);
				if (actual != null) {
					final ActualTextScan scan = new ActualTextScan(tag, actual);
					scopes.add(scan);
					active.push(scan);
					markedContent.push(Boolean.TRUE);
				} else {
					markedContent.push(Boolean.FALSE);
				}
				if (!active.isEmpty() && properties != null && properties.containsKey(COSName.MCID)) {
					active.peek().mcids.add(properties.getInt(COSName.MCID));
				}
			} else if ("EMC".equals(operator.getName())) {
				if (!markedContent.isEmpty() && markedContent.pop().booleanValue() && !active.isEmpty()) {
					active.pop();
				}
			} else if (!active.isEmpty() && ("Tj".equals(operator.getName()) || "TJ".equals(operator.getName())
					|| "'".equals(operator.getName()) || "\"".equals(operator.getName()))) {
				++active.peek().textOperators;
			}
			operands.clear();
		}
		assertTrue("an ActualText marked-content sequence was left open", active.isEmpty());
		return scopes;
	}

	private static List<Integer> markedContentMcids(final org.apache.pdfbox.pdmodel.PDPage page)
			throws Exception {
		final List<Integer> mcids = new ArrayList<>();
		final PDFStreamParser parser = new PDFStreamParser(page);
		final List<Object> operands = new ArrayList<>();
		Object token;
		while ((token = parser.parseNextToken()) != null) {
			if (!(token instanceof Operator operator)) {
				operands.add(token);
				continue;
			}
			if ("BDC".equals(operator.getName()) && operands.size() >= 2
					&& operands.get(operands.size() - 1) instanceof COSBase rawProperties
					&& dereference(rawProperties) instanceof COSDictionary properties
					&& properties.containsKey(COSName.MCID)) {
				mcids.add(properties.getInt(COSName.MCID));
			}
			operands.clear();
		}
		return mcids;
	}

	private static void assertActualText(final List<ActualTextScan> scopes, final String logical) {
		final ActualTextScan scope = onlyScope(scopes, logical);
		assertEquals("ActualText must use a /Span marked-content tag: " + logical,
				COSName.getPDFName("Span"), scope.tag);
		assertTrue("ActualText does not enclose any text operators: " + logical, scope.textOperators > 0);
	}

	private static ActualTextScan onlyScope(final List<ActualTextScan> scopes, final String logical) {
		final List<ActualTextScan> matching = scopes.stream().filter(value -> logical.equals(value.value)).toList();
		assertEquals("ActualText must occur exactly once: " + logical, 1, matching.size());
		return matching.get(0);
	}

	private static void assertTaggedLogicalOrder(final PDDocument pdf, final List<Integer> paint) {
		assertEquals("the fixture has six visual glyph leaves", 6, paint.size());
		for (int i = 1; i < paint.size(); ++i) {
			assertTrue("paint-time MCIDs must stay ascending", paint.get(i - 1) < paint.get(i));
		}
		final List<Integer> expectedLogical = Arrays.asList(paint.get(5), paint.get(4), paint.get(3),
				paint.get(0), paint.get(1), paint.get(2));
		final COSDictionary root = pdf.getDocumentCatalog().getStructureTreeRoot().getCOSObject();
		final List<Integer> kids = findParagraphKids(root.getDictionaryObject(COSName.K), new HashSet<>(paint));
		assertNotNull("the #rtl paragraph structure element was not found", kids);
		assertEquals("paragraph /K must follow BidiSlice.syntheticStart", expectedLogical, kids);

		final COSArray owners = parentTreeOwners(root, pdf.getPage(0).getStructParents());
		assertNotNull("ParentTree entry for the page was not found", owners);
		final List<Integer> parentTreeMcids = new ArrayList<>();
		for (final int mcid : paint) {
			assertTrue("ParentTree has no slot for MCID " + mcid, mcid < owners.size());
			assertNotNull("ParentTree owner is null for MCID " + mcid, dereference(owners.get(mcid)));
		}
		for (int mcid = 0; mcid < owners.size(); ++mcid) {
			if (paint.contains(mcid) && dereference(owners.get(mcid)) != COSNull.NULL) {
				parentTreeMcids.add(mcid);
			}
		}
		assertEquals("ParentTree slots must remain in ascending paint order", paint, parentTreeMcids);
	}

	private static String extractedText(final PDDocument pdf, final boolean sortByPosition) throws Exception {
		final PDFTextStripper stripper = new PDFTextStripper();
		stripper.setSortByPosition(sortByPosition);
		stripper.setStartPage(1);
		stripper.setEndPage(1);
		return stripper.getText(pdf);
	}

	private static String oneLine(final String value) {
		return value.replace("\r", "\\r").replace("\n", "\\n");
	}

	private static String firstExtractedLine(final String value) {
		return value.lines().filter(line -> !line.isBlank()).findFirst().orElse("");
	}

	private static COSBase dereference(final COSBase value) {
		return value instanceof COSObject object ? object.getObject() : value;
	}

	private static List<Integer> findParagraphKids(final COSBase value, final Set<Integer> target) {
		final COSBase base = dereference(value);
		if (base instanceof COSArray array) {
			for (final COSBase item : array) {
				final List<Integer> found = findParagraphKids(item, target);
				if (found != null) {
					return found;
				}
			}
		} else if (base instanceof COSDictionary dictionary) {
			if (COSName.P.equals(dictionary.getCOSName(COSName.S))) {
				final List<Integer> kids = directMcids(dictionary.getDictionaryObject(COSName.K));
				if (new HashSet<>(kids).equals(target)) {
					return kids;
				}
			}
			return findParagraphKids(dictionary.getDictionaryObject(COSName.K), target);
		}
		return null;
	}

	private static List<Integer> directMcids(final COSBase value) {
		final List<Integer> mcids = new ArrayList<>();
		final COSBase base = dereference(value);
		if (base instanceof COSNumber number) {
			mcids.add(number.intValue());
		} else if (base instanceof COSArray array) {
			for (final COSBase item : array) {
				mcids.addAll(directMcids(item));
			}
		} else if (base instanceof COSDictionary dictionary && dictionary.containsKey(COSName.MCID)) {
			mcids.add(dictionary.getInt(COSName.MCID));
		}
		return mcids;
	}

	private static COSArray parentTreeOwners(final COSDictionary structureRoot, final int structParents) {
		final COSBase parentTree = structureRoot.getDictionaryObject(COSName.getPDFName("ParentTree"));
		return findNumberTreeValue(parentTree, structParents);
	}

	private static COSArray findNumberTreeValue(final COSBase value, final int key) {
		final COSBase base = dereference(value);
		if (!(base instanceof COSDictionary dictionary)) {
			return null;
		}
		final COSArray nums = dictionary.getCOSArray(COSName.getPDFName("Nums"));
		if (nums != null) {
			for (int i = 0; i + 1 < nums.size(); i += 2) {
				if (dereference(nums.get(i)) instanceof COSNumber number && number.intValue() == key
						&& dereference(nums.get(i + 1)) instanceof COSArray owners) {
					return owners;
				}
			}
		}
		final COSArray kids = dictionary.getCOSArray(COSName.KIDS);
		if (kids != null) {
			for (final COSBase child : kids) {
				final COSArray found = findNumberTreeValue(child, key);
				if (found != null) {
					return found;
				}
			}
		}
		return null;
	}

	private static void assertRtl(final List<PdfLine> lines) {
		final PdfLine line = firstLine(lines, value -> "ABC".equals(ascii(value)));
		final List<TextPosition> latin = containingAny(line, "ABC");
		final List<TextPosition> hebrew = hebrew(line);
		assertFalse("#rtl Latin run was not found", latin.isEmpty());
		if (!hebrew.isEmpty()) {
			assertTrue("#rtl must place ABC to the left of Hebrew",
					maxRight(latin) < minX(hebrew));
		}
		assertEquals("#rtl must be aligned to the paragraph's 320pt right edge", 320, maxRight(line.text()), 6);
	}

	private static void assertEmbed(final List<PdfLine> lines) {
		final PdfLine line = firstLine(lines, value -> "abcDEFxyz".equals(ascii(value)));
		final List<TextPosition> abc = containingAny(line, "abc");
		final List<TextPosition> def = containingAny(line, "DEF");
		final List<TextPosition> xyz = containingAny(line, "xyz");
		assertTrue("#embed abc must precede DEF", maxRight(abc) < minX(def));
		final List<TextPosition> hebrew = hebrew(line);
		if (hebrew.isEmpty()) {
			assertTrue("#embed DEF must precede xyz when Hebrew is not mappable", maxRight(def) < minX(xyz));
		} else {
			assertTrue("#embed DEF must precede Hebrew", maxRight(def) < minX(hebrew));
			assertTrue("#embed Hebrew must precede xyz", maxRight(hebrew) < minX(xyz));
		}
	}

	private static void assertOverride(final List<PdfLine> lines) {
		final PdfLine line = firstLine(lines, value -> {
			final String text = physicalText(value);
			return text.length() == 6 && text.chars().allMatch(c -> "ABC-12".indexOf(c) >= 0);
		});
		assertEquals("#override visual glyph order", "21-CBA", physicalText(line));
	}

	private static void assertMirror(final List<PdfLine> lines) {
		final PdfLine mirror = firstLine(lines,
				value -> !ascii(value).contains("MIRRORREF")
						&& containingAny(value, MIRROR_CHARS).size() == MIRROR_CHARS.length());
		final PdfLine reference = firstLine(lines, value -> ascii(value).contains("MIRRORREF"));
		final List<TextPosition> punctuation = containingAny(mirror, MIRROR_CHARS);
		punctuation.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
		assertEquals(MIRROR_CHARS.length(), punctuation.size());
		assertMirroredCode(reference, '«', punctuation.get(0), "guillemets");
		assertMirroredCode(reference, '{', punctuation.get(2), "braces");
		assertMirroredCode(reference, '[', punctuation.get(4), "square brackets");
		assertMirroredCode(reference, '(', punctuation.get(6), "parentheses");
	}

	private static void assertMirrorCidAliases(final List<PdfLine> lines) {
		final PdfLine mirror = firstLine(lines,
				value -> !ascii(value).contains("MIRRORREF")
						&& containingAny(value, MIRROR_CHARS).size() == MIRROR_CHARS.length());
		final List<TextPosition> punctuation = containingAny(mirror, MIRROR_CHARS);
		punctuation.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
		assertEquals("mirrored CID aliases must retain logical characters in physical order",
				"»«}{][)(", punctuation.stream().map(TextPosition::getUnicode).reduce("", String::concat));
	}

	private static void assertMirrorDisplayCharacters(final List<PdfLine> lines) {
		final PdfLine mirror = firstLine(lines,
				value -> !ascii(value).contains("MIRRORREF")
						&& containingAny(value, MIRROR_CHARS).size() == MIRROR_CHARS.length());
		final List<TextPosition> punctuation = containingAny(mirror, MIRROR_CHARS);
		punctuation.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
		assertEquals("mirrored glyphs must map to display characters in physical order",
				"«»{}[]()", punctuation.stream().map(TextPosition::getUnicode).reduce("", String::concat));
	}

	private static void assertMirroredCode(final PdfLine reference, final char open,
			final TextPosition leftmost, final String label) {
		final TextPosition expected = sorted(reference).stream()
				.filter(value -> String.valueOf(open).equals(value.getUnicode())).findFirst()
				.orElseThrow(() -> new AssertionError("mirror reference open character was not found: " + open));
		// CID alias(埋め込み subset)では鏡像 glyph は論理文字ごとに別 CID を持つので、CID ではなく GID(輪郭)で比べる
		assertEquals("#mirror leftmost " + label + " character must use the mirrored open-character glyph",
				outline(expected), outline(leftmost));
	}

	private static void assertInlineSemantics(final PDDocument pdf) throws Exception {
		int links = 0;
		for (int page = 0; page < pdf.getNumberOfPages(); ++page) {
			for (final PDAnnotation annotation : pdf.getPage(page).getAnnotations()) {
				if (annotation instanceof PDAnnotationLink link && link.getAction() instanceof PDActionURI action
						&& LINK_URI.equals(action.getURI())) {
					++links;
					assertEquals("link /Contents must use the logical source inline text", SEMANTIC_TEXT,
							link.getContents());
				}
			}
		}
		assertTrue("the wrapped anchor must retain one link rectangle per visual fragment", links >= 2);

		final var names = pdf.getDocumentCatalog().getNames();
		assertNotNull("PDF has no name dictionary", names);
		assertNotNull("PDF has no named destinations", names.getDests());
		assertEquals("anchor destination semantics must be emitted once", 1,
				countName(names.getDests(), "semantic_anchor"));
	}

	private static int countName(final PDNameTreeNode<?> node, final String name) throws Exception {
		int count = node.getNames() != null && node.getNames().containsKey(name) ? 1 : 0;
		if (node.getKids() != null) {
			for (final PDNameTreeNode<?> child : node.getKids()) {
				count += countName(child, name);
			}
		}
		return count;
	}

	private static List<PdfLine> collectLines(final PDDocument pdf) throws Exception {
		final List<PdfLine> lines = new ArrayList<>();
		for (int page = 1; page <= pdf.getNumberOfPages(); ++page) {
			final List<TextPosition> positions = new ArrayList<>();
			final PDFTextStripper stripper = new PDFTextStripper() {
				@Override
				protected void processTextPosition(final TextPosition text) {
					positions.add(text);
				}
			};
			stripper.setStartPage(page);
			stripper.setEndPage(page);
			stripper.setSuppressDuplicateOverlappingText(false);
			stripper.getText(pdf);
			final List<PdfLine> pageLines = new ArrayList<>();
			for (final TextPosition position : positions) {
				PdfLine target = null;
				for (final PdfLine candidate : pageLines) {
					if (Math.abs(candidate.y() - position.getYDirAdj()) <= .75) {
						target = candidate;
						break;
					}
				}
				if (target == null) {
					target = new PdfLine(page, position.getYDirAdj(), new ArrayList<>());
					pageLines.add(target);
				}
				target.text().add(position);
			}
			pageLines.sort(Comparator.comparingDouble(PdfLine::y));
			lines.addAll(pageLines);
		}
		return lines;
	}

	private static PdfLine firstLine(final List<PdfLine> lines,
			final java.util.function.Predicate<PdfLine> predicate) {
		return lines.stream().filter(predicate).findFirst()
				.orElseThrow(() -> new AssertionError("PDF line was not found"));
	}

	private static List<TextPosition> sorted(final PdfLine line) {
		final List<TextPosition> sorted = new ArrayList<>(line.text());
		sorted.sort(Comparator.comparingDouble(TextPosition::getXDirAdj));
		return sorted;
	}

	private static String physicalText(final PdfLine line) {
		final StringBuilder text = new StringBuilder();
		for (final TextPosition position : sorted(line)) {
			if (position.getUnicode() != null) {
				text.append(position.getUnicode());
			}
		}
		return text.toString();
	}

	private static String ascii(final PdfLine line) {
		final StringBuilder text = new StringBuilder();
		physicalText(line).codePoints().filter(cp -> cp >= 0x21 && cp <= 0x7E && cp != '(' && cp != ')')
				.forEach(text::appendCodePoint);
		return text.toString();
	}

	private static List<TextPosition> containingAny(final PdfLine line, final String chars) {
		final List<TextPosition> found = new ArrayList<>();
		for (final TextPosition position : line.text()) {
			final String unicode = position.getUnicode();
			if (unicode != null && unicode.codePoints().anyMatch(cp -> chars.indexOf(cp) >= 0)) {
				found.add(position);
			}
		}
		return found;
	}

	private static List<TextPosition> hebrew(final PdfLine line) {
		final List<TextPosition> found = new ArrayList<>();
		for (final TextPosition position : line.text()) {
			final String unicode = position.getUnicode();
			if (unicode != null && unicode.codePoints().anyMatch(cp -> cp >= 0x0590 && cp <= 0x05FF)) {
				found.add(position);
			}
		}
		return found;
	}

	private static double minX(final List<TextPosition> positions) {
		return positions.stream().mapToDouble(TextPosition::getXDirAdj).min().orElseThrow();
	}

	private static double maxRight(final List<TextPosition> positions) {
		return positions.stream().mapToDouble(value -> value.getXDirAdj() + value.getWidthDirAdj()).max()
				.orElseThrow();
	}

	/**
	 * 埋め込み Type0 なら glyph の輪郭(経路の列)。CID alias は論理文字ごとに別 CID・別 GID を持つが輪郭は同じ。
	 * それ以外は文字コードそのもの。
	 */
	private static String outline(final TextPosition position) {
		final int code = firstCode(position);
		if (position.getFont() instanceof org.apache.pdfbox.pdmodel.font.PDType0Font type0) {
			try {
				final java.awt.geom.PathIterator it = type0.getDescendantFont().getPath(code).getPathIterator(null);
				final double[] c = new double[6];
				final StringBuilder sb = new StringBuilder();
				while (!it.isDone()) {
					final int type = it.currentSegment(c);
					sb.append(type);
					for (int k = 0; k < 6; ++k) {
						sb.append(',').append(Math.round(c[k] * 100));
					}
					sb.append(';');
					it.next();
				}
				return sb.toString();
			} catch (final java.io.IOException e) {
				throw new java.io.UncheckedIOException(e);
			}
		}
		return String.valueOf(code);
	}

	private static int firstCode(final TextPosition position) {
		final int[] codes = position.getCharacterCodes();
		if (codes == null || codes.length == 0) {
			throw new AssertionError("PDFBox did not expose the glyph character code");
		}
		return codes[0];
	}

	private record PdfLine(int page, double y, List<TextPosition> text) {
	}
}

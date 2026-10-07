package net.zamasoft.foliojet.layout.segment;

import org.xml.sax.helpers.AttributesImpl;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.ua.props.TaggedPdf;

/**
 * Unit tests for {@link StructureToken} (E-6 increment 3b-4, added 2026-07-24).
 * Locks down recording-time freezing of {@code Params.element}: (1) map real elements to tokens
 * containing only the four fields needed by Tagged PDF and annotation consumers, (2) retain static
 * singletons (pseudo-elements and anonymous elements) as-is to preserve live identity, and
 * (3) intern within a replay session (same logical element = same instance).
 */
public class StructureTokenTest extends TestCase {

	private static CSSElement liElement(final long elementKey) {
		final AttributesImpl atts = new AttributesImpl();
		atts.addAttribute("", "scope", "scope", "CDATA", "row");
		return new CSSElement(null, "li", "item", null, null, null, null, atts, null, 10, elementKey);
	}

	/** Real elements (elementKey>=0) map to tokens retaining lName/id/atts/elementKey. */
	public void testFreezeCopiesReaderContract() {
		final CSSElement ce = liElement(42);
		final StructureElement frozen = StructureToken.freeze(ce);
		assertTrue(frozen instanceof StructureToken);
		assertEquals(42L, frozen.elementKey());
		assertEquals("li", frozen.lName());
		assertEquals("item", frozen.id());
		assertSame(ce.atts(), frozen.atts());
	}

	/** Null and already frozen tokens stay as-is (idempotent). */
	public void testFreezeIsIdempotent() {
		assertNull(StructureToken.freeze(null));
		final StructureElement frozen = StructureToken.freeze(liElement(1));
		assertSame(frozen, StructureToken.freeze(frozen));
	}

	/**
	 * Retain static singletons (pseudo-elements and anonymous elements, elementKey=-1) as-is.
	 * Singleton sharing is the live identity itself (e.g. sharing ANON_TABLE for anonymous tables
	 * prevents opening Tagged PDF tags twice), and -1 collides across pseudo-elements, so they cannot
	 * be tokenized or interned.
	 */
	public void testStaticSingletonsPassThrough() {
		assertSame(CSSElement.ANON_TABLE, StructureToken.freeze(CSSElement.ANON_TABLE));
		assertSame(CSSElement.BEFORE, StructureToken.freeze(CSSElement.BEFORE));
		assertSame(CSSElement.MARKER, StructureToken.freeze(CSSElement.MARKER));
	}

	/** Tagged PDF consumers (blockRole/headerScope) can read the same values from tokens. */
	public void testTaggedPdfReadersAcceptTokens() {
		final StructureElement frozen = StructureToken.freeze(liElement(7));
		assertEquals("LI", TaggedPdf.blockRole(frozen));
		assertEquals(TaggedPdf.blockRole(liElement(7)), TaggedPdf.blockRole(frozen));
		// Attributes equivalent to <th scope="row"> remain readable through a token.
		assertEquals("Row", TaggedPdf.headerScope(frozen));
		assertEquals("Column", TaggedPdf.headerScope(null));
	}

	/** Template freeze→materialize turns element into a token (without retaining CSSElement). */
	public void testTemplateFreezeDetachesElement() {
		final BlockParams params = new BlockParams();
		params.element = liElement(3);
		final BlockParamsTemplate template = BlockParamsTemplate.freeze(params);
		final BlockParams materialized = template.materialize();
		assertTrue(materialized.element instanceof StructureToken);
		assertEquals("li", materialized.element.lName());
		// The live instance is unchanged.
		assertTrue(params.element instanceof CSSElement);
	}

	/**
	 * Interning within a replay session: even when multiple recipes for the same logical element
	 * (elementKey), such as an li's principal and marker boxes, are materialized, they use the same
	 * token instance within a session (SegmentExecutor). This is the contract that prevents opening
	 * tags twice via the identity set in PageBox.beginStruct.
	 */
	public void testInternUnifiesTokensWithinSession() {
		final CSSElement ce = liElement(99);
		final BlockParams principal = new BlockParams();
		principal.element = StructureToken.freeze(ce);
		final BlockParams marker = new BlockParams();
		marker.element = StructureToken.freeze(ce);
		assertNotSame("freezeはrecipeごとに独立したtokenを作る", principal.element, marker.element);

		final SegmentExecutor session = new SegmentExecutor(null, 0);
		session.internStructureToken(principal);
		session.internStructureToken(marker);
		assertSame("同一セッション内では同じ論理要素=同じtokenインスタンス", principal.element, marker.element);

		// Static singletons (elementKey<0) are excluded from interning and stay unchanged.
		final BlockParams anon = new BlockParams();
		anon.element = CSSElement.ANON_TABLE;
		session.internStructureToken(anon);
		assertSame(CSSElement.ANON_TABLE, anon.element);
	}
}

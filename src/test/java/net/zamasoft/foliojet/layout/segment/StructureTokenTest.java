package net.zamasoft.foliojet.layout.segment;

import org.xml.sax.helpers.AttributesImpl;

import junit.framework.TestCase;
import net.zamasoft.foliojet.css.CSSElement;
import net.zamasoft.foliojet.css.StructureElement;
import net.zamasoft.foliojet.layout.box.params.BlockParams;
import net.zamasoft.foliojet.ua.props.TaggedPdf;

/**
 * {@link StructureToken}(E-6増分3b-4、2026-07-24新設)の単体テスト
 * です。{@code Params.element}の記録時freeze——(1) 実要素はTagged PDF・
 * 注釈系の読み手が必要とする4フィールドだけのtokenへ写す、(2) static
 * singleton(擬似要素・匿名要素)はそのまま保持してlive identityを
 * 保つ、(3) 再生セッション内のintern(同じ論理要素=同じインスタンス)
 * ——を固定する。
 */
public class StructureTokenTest extends TestCase {

	private static CSSElement liElement(final long elementKey) {
		final AttributesImpl atts = new AttributesImpl();
		atts.addAttribute("", "scope", "scope", "CDATA", "row");
		return new CSSElement(null, "li", "item", null, null, null, null, atts, null, 10, elementKey);
	}

	/** 実要素(elementKey>=0)はlName/id/atts/elementKeyを保持するtokenへ写る。 */
	public void testFreezeCopiesReaderContract() {
		final CSSElement ce = liElement(42);
		final StructureElement frozen = StructureToken.freeze(ce);
		assertTrue(frozen instanceof StructureToken);
		assertEquals(42L, frozen.elementKey());
		assertEquals("li", frozen.lName());
		assertEquals("item", frozen.id());
		assertSame(ce.atts(), frozen.atts());
	}

	/** null・freeze済みtokenはそのまま(冪等)。 */
	public void testFreezeIsIdempotent() {
		assertNull(StructureToken.freeze(null));
		final StructureElement frozen = StructureToken.freeze(liElement(1));
		assertSame(frozen, StructureToken.freeze(frozen));
	}

	/**
	 * static singleton(擬似要素・匿名要素、elementKey=-1)はそのまま
	 * 保持する——singleton共有こそがliveのidentity(例: 匿名表の
	 * ANON_TABLE共有によるTagged PDFタグ二重開き防止)であり、-1は
	 * 擬似要素間で衝突するためtoken化・internできない。
	 */
	public void testStaticSingletonsPassThrough() {
		assertSame(CSSElement.ANON_TABLE, StructureToken.freeze(CSSElement.ANON_TABLE));
		assertSame(CSSElement.BEFORE, StructureToken.freeze(CSSElement.BEFORE));
		assertSame(CSSElement.MARKER, StructureToken.freeze(CSSElement.MARKER));
	}

	/** Tagged PDFの読み手(blockRole/headerScope)はtokenからも同じ値を読める。 */
	public void testTaggedPdfReadersAcceptTokens() {
		final StructureElement frozen = StructureToken.freeze(liElement(7));
		assertEquals("LI", TaggedPdf.blockRole(frozen));
		assertEquals(TaggedPdf.blockRole(liElement(7)), TaggedPdf.blockRole(frozen));
		// <th scope="row">相当の属性はtoken経由でも読める
		assertEquals("Row", TaggedPdf.headerScope(frozen));
		assertEquals("Column", TaggedPdf.headerScope(null));
	}

	/** テンプレートfreeze→materializeでelementはtokenになる(CSSElementを引き留めない)。 */
	public void testTemplateFreezeDetachesElement() {
		final BlockParams params = new BlockParams();
		params.element = liElement(3);
		final BlockParamsTemplate template = BlockParamsTemplate.freeze(params);
		final BlockParams materialized = template.materialize();
		assertTrue(materialized.element instanceof StructureToken);
		assertEquals("li", materialized.element.lName());
		// live側は変わらない
		assertTrue(params.element instanceof CSSElement);
	}

	/**
	 * 再生セッション内のintern: 同じ論理要素(elementKey)の複数recipe
	 * (例: liのprincipal boxとmarker box)がmaterializeされても、同一
	 * セッション(SegmentExecutor)内では同じtokenインスタンスへ揃う
	 * ——PageBox.beginStructのidentity setによるタグ二重開き防止の契約。
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

		// static singleton(elementKey<0)はintern対象外・不変
		final BlockParams anon = new BlockParams();
		anon.element = CSSElement.ANON_TABLE;
		session.internStructureToken(anon);
		assertSame(CSSElement.ANON_TABLE, anon.element);
	}
}

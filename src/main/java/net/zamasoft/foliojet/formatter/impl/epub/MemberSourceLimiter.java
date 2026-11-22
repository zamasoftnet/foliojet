package net.zamasoft.foliojet.formatter.impl.epub;

import net.zamasoft.zstream.resolver.SourceResolver;

/**
 * A source resolver that can put the members of an archive under the limits it applies to the resources of a
 * conversion.
 *
 * <p>
 * The members of an EPUB are served by a resolver of their own, placed in front of the user agent's resolver, so
 * they never pass through it. A user agent's resolver that limits what one conversion may read
 * ({@code input.resource-size-limit}, {@code input.resource-count-limit}) implements this interface, and
 * {@link EPubFormatter} passes the member resolver through it: a member of the archive then counts like any other
 * resource of the document.
 * </p>
 */
public interface MemberSourceLimiter {
	/**
	 * Returns a resolver that serves the same members and charges them to the limits of the conversion.
	 *
	 * @param members the resolver that serves the members of the archive
	 * @return the limited resolver; sources obtained from it are released through it
	 */
	SourceResolver limitMembers(SourceResolver members);
}

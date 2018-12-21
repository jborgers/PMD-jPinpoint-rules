package nl.rabobank.perf.pinpointrules;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.portlet.context.PortletRequestAttributes;
import org.springframework.web.portlet.util.PortletUtils;

import javax.portlet.PortletSession;
import javax.portlet.ResourceRequest;

/**
 * If a .setAttribute occurs in a method in a certain class, a .removeAttribute must occur in a method of the same class 
 */
public class MinimizeAttributesInSessionTest {


	static class Test1_Flag {
		public void setAttribute(final String sessionAttributeName,
				final Object object) {
			getPortletSession().setAttribute(sessionAttributeName, object);
		}

		public Object getAttribute(final String sessionAttributeName) {
			return getPortletSession().getAttribute(sessionAttributeName);
		}

		public void removeAttribute(final String sessionAttributeName) {
			// getPortletSession().removeAttribute(sessionAttributeName);
		}
	}

	static class Test1_Ok {
		public void setAttribute(final String sessionAttributeName,
				final Object object) {
			getPortletSession().setAttribute(sessionAttributeName, object);
		}

		public void removeAttribute(final String sessionAttributeName) {
			getPortletSession().removeAttribute(sessionAttributeName);
		}
	}

	static class Test2_Flag {
		PortletSession ses;

		public void setAttribute(final String sessionAttributeName,
				final Object object) {
			ses.setAttribute(sessionAttributeName, object);
		}

		public Object getAttribute(final String sessionAttributeName) {
			return ses.getAttribute(sessionAttributeName);
		}

		public void removeAttribute(final String sessionAttributeName) {
			//ses.removeAttribute(sessionAttributeName);
		}
	}

	static class Test2_Ok {
		PortletSession ses;

		public void setAttribute(final String sessionAttributeName,
				final Object object) {
			ses.setAttribute(sessionAttributeName, object);
		}

		public void removeAttribute(final String sessionAttributeName) {
			ses.removeAttribute(sessionAttributeName);
		}
	}

	static class Test3_Flag {

		public void setAttribute(final String sessionAttributeName,
				final Object object) {
			getPortletSession().setAttribute(sessionAttributeName, object);
		}

		public void removeAttributes(final String... sessionAttributeNames) {
			final PortletSession portletSession = getPortletSession();
			for (final String sessionAttributeName : sessionAttributeNames) {
				//portletSession.removeAttribute(sessionAttributeName);
			}
		}
	}

	static class Test3_Ok {

		public void setAttribute(final String sessionAttributeName,
				final Object object) {
			getPortletSession().setAttribute(sessionAttributeName, object);
		}

		public void removeAttributes(final String... sessionAttributeNames) {
			final PortletSession portletSession = getPortletSession();
			for (final String sessionAttributeName : sessionAttributeNames) {
				portletSession.removeAttribute(sessionAttributeName);
			}
		}
	}
	
	static class Test4_Flag {

		private void setUploadStatus(final ResourceRequest request) {
			PortletUtils.setSessionAttribute(request, "status", "value");
		}

		public void removeUploadStatus(final ResourceRequest request) {
			PortletUtils.setSessionAttribute(request, "status", "");
		}
	}

	static class Test4_Ok {

		private void setUploadStatus(final ResourceRequest request) {
			PortletUtils.setSessionAttribute(request, "status", "value");
		}

		public void removeUploadStatus(final ResourceRequest request) {
			PortletUtils.setSessionAttribute(request, "status", null); // Spring will invoke removeAttribute 
		}
	}

	// TODO: case: /*(ActionRequest)*/ request.getPortletSession().setAttribute(SIGN_SPARKLE_SESSION_KEY, signSparkle);
	
	/**
	 * Get the {@link PortletSession} from the current request.
	 * 
	 * @return {@link PortletSession}
	 * @see org.springframework.web.portlet.context.PortletApplicationContextUtils
	 */
	public static PortletSession getPortletSession() {
		final RequestAttributes requestAttr = RequestContextHolder
				.currentRequestAttributes();
		if (!(requestAttr instanceof PortletRequestAttributes)) {
			throw new IllegalStateException(
					"Current request is not a portlet request");
		}
		return ((PortletRequestAttributes) requestAttr).getRequest()
				.getPortletSession();
	}

}

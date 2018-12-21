package nl.rabobank.perf.pinpointrules.spring;

import org.springframework.ui.ModelMap;
import org.springframework.validation.BindingResult;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.portlet.bind.annotation.ActionMapping;

import javax.portlet.ActionRequest;
import java.util.HashMap;
import java.util.Map;

public class MinimizeActionModelMapInSessionTest {
	public static final String SAVE_SPRINKLE_ACTION = "action=saveSprinkleDetails";
	public static final String MODAL_SUBMIT_RESET_OK = "modal_submit_resetok";

	private Validator validator;

	@ActionMapping(params = { SAVE_SPRINKLE_ACTION, MODAL_SUBMIT_RESET_OK })
	public void clearSprinkleDetails(final ActionRequest request,
			final ModelMap modelMap) {
		// LOG.trace("Clearing all sprinkle details");
		// session.clearAll();
		modelMap.clear(); // OK
	}

	@ActionMapping(params = { SAVE_SPRINKLE_ACTION, MODAL_SUBMIT_RESET_OK })
	public void clearSprinkleDetails2(final ActionRequest request,
			final ModelMap modelMap) {
		Map otherMap = new HashMap();
		// LOG.trace("Clearing all sprinkle details");
		otherMap.clear(); // current shortcoming
		modelMap.isEmpty(); // Method should flag FAIL
	}

	@ActionMapping(params = { SAVE_SPRINKLE_ACTION, MODAL_SUBMIT_RESET_OK })
	public void clearSprinkleDetails3(final ActionRequest request,
			final ModelMap modelMap) {
		Map otherMap = new HashMap();
		// LOG.trace("Clearing all sprinkle details");
		modelMap.isEmpty(); // Method should flag FAIL
	}

	// Map cleared in happy flow, only in session while validation error(s)
	// map.clear() present, should NOT flag
	@ActionMapping
	public void action(@ModelAttribute Object form, BindingResult errors,
			Object response, final ModelMap map) {
		// Validate the form, on error fill the BindingResult errors object and
		// return the name of the render
		// (In this case use the implicit model by Spring to render the original
		// screen including the error)
		validator.validate(form, errors);
		if (errors.hasErrors()) {
			// will show errors on same page
			return;
		}
		// happy flow
		// By clearing the map it won't be persistent in session by Spring
		map.clear();
	}

	// Map cleared in happy flow, only in session while validation error(s)
	// map.clear missing, so should FLAG
	@ActionMapping
	public void action2(@ModelAttribute Object form, BindingResult errors,
			Object response, final ModelMap map) {

		validator.validate(form, errors);
		if (errors.hasErrors()) {
			return;
		}
	}

	// Map cleared in happy flow, only in session while validation error(s)
	// map.clear missing, but non-public so should NOT FLAG
	@ActionMapping
	private void action3(@ModelAttribute Object form, BindingResult errors,
			Object response, final ModelMap map) {

		validator.validate(form, errors);
		if (errors.hasErrors()) {
			return;
		}
	}

}

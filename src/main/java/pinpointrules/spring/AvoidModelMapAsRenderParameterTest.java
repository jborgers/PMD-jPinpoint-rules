package nl.rabobank.perf.pinpointrules.spring;

import org.springframework.ui.ModelMap;
import org.springframework.validation.Validator;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.portlet.bind.annotation.RenderMapping;
import org.springframework.web.portlet.bind.annotation.ResourceMapping;
import org.springframework.web.servlet.ModelAndView;

import javax.portlet.PortletRequest;
import javax.portlet.RenderRequest;

public class AvoidModelMapAsRenderParameterTest {

	protected Object messageSource;
	private Validator validator;

	// ModelMap in session between action and render, and after that
	// render in method name, no RenderRequest param
	// must flag [--> NOT, see  JPCC-88?]
	@ResourceMapping("subview-init")
	public String initialOverviewRender0(final PortletRequest request,
			final ModelMap modelMap, @RequestParam("viewName") final String viewName) {
		modelMap.put("texts", messageSource);
		// modelmap used during render
		return "/" + viewName;
	}

	// ModelMap in session between action and render, and after that
	// render in method name, no RenderRequest param
	// but non-public, so should NOT flag
	@ResourceMapping("subview-init")
	private String initialOverviewRender01(final PortletRequest request,
			final ModelMap modelMap, @RequestParam("viewName") final String viewName) {
		modelMap.put("texts", messageSource);
		return "/" + viewName;
	}
	
	// ModelMap in session between action and render, and after that
	// must flag [--> NOT, see JPCC-88?]
	@ResourceMapping("subview-init")
	public String initialOverviewRender1(final RenderRequest request,
			final ModelMap modelMap, @RequestParam("viewName") final String viewName) {

		modelMap.put("texts", messageSource);
		// modelmap used during render

		return "/" + viewName;
	}
	
	// ModelMap in session between action and render, and after that
	// must flag
    @RenderMapping(params = "flow=view_unsigned")
    public ModelAndView viewUnsignedDoodle(final RenderRequest request, @RequestParam final String doodleId,
                                            @RequestParam final String modifiedTimeStamp, ModelMap modelMap,
                                            final Object flow)
    {
    	//Object doodleForm = new Object();
    	return new ModelAndView("FIRST_SCREEN_VIEW", "FIRST_SCREEN_FORM", modelMap);
    }
	
    // @ModelAttribute creates an implicit ModelMap in session
    // must flag
    @RenderMapping(params = "RENDER=SECOND_SCREEN_FIRST_TIME")
    public ModelAndView renderSecondScreenFirstTime(final RenderRequest request,
                                                    final @ModelAttribute Object firstScreenForm)
    {
        final Object secondScreenForm = new Object();
    	return new ModelAndView("SECOND_SCREEN_VIEW", "SECOND_SCREEN_FORM", new Object[]{firstScreenForm, secondScreenForm});
    }
    
	// must NOT flag
	public ModelAndView initialOverviewRender2(final RenderRequest request,
			final String viewName) {
		ModelMap modelMap = new ModelMap();
		modelMap.put("texts", messageSource);

		// modelmap used during render, in view template. Assumed not to be put
		// in session, because no need.
		return new ModelAndView("income/" + viewName, modelMap);
	}

}

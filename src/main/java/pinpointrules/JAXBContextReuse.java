package pinpointrules;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;

public class JAXBContextReuse {
	
	private static final JAXBContext sharedContext;

	static {
		try {
			sharedContext = JAXBContext.newInstance(JAXBContextReuse.class.getPackage().getName());
					//createContext();
		}
		catch(JAXBException e) {
			throw new RuntimeException();
		}
	}
	
	public static JAXBContext createContext() {
		JAXBContext context = null;
		try {
			context = JAXBContext.newInstance(JAXBContextReuse.class.getPackage().getName()); // violation
		} catch (JAXBException e) {
			e.printStackTrace();
		}
		return context;
	}
	
	public void myMethod() throws JAXBException {
        final JAXBContext context = JAXBContext.newInstance(JAXBContextReuse.class.getPackage().getName()); // violation
	}

}

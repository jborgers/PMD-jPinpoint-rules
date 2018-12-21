package nl.rabobank.perf.pinpointrules.concurrent;

import javax.validation.ConstraintViolation;
import javax.validation.metadata.BeanDescriptor;
import javax.xml.bind.Marshaller;
import java.util.Set;

public class AvoidThreadUnsafeMarshallerUsage {

	// these are thread-unsafe objects, do not use in fields,
	// rather recreated every time and referenced from local variables
	private javax.xml.bind.Unmarshaller unmarshaller; // should violate
	private Marshaller marshaller; // should violate
	private javax.xml.bind.Validator validator; // should violate
	private javax.xml.validation.Validator validator2; // should violate
	private javax.validation.Validator validator3; // should *not* violate

	// this one is OK to use
	private EmailValidator eValidator; // OK

	private class EmailValidator {
		javax.validation.Validator validator = new javax.validation.Validator() { // should *not* violate
			public <T> Set<ConstraintViolation<T>> validate(T t, Class<?>... classes) {
				return null;
			}

			public <T> Set<ConstraintViolation<T>> validateProperty(T t, String s, Class<?>... classes) {
				return null;
			}

			public <T> Set<ConstraintViolation<T>> validateValue(Class<T> aClass, String s, Object o, Class<?>... classes) {
				return null;
			}

			public BeanDescriptor getConstraintsForClass(Class<?> aClass) {
				return null;
			}

			public <T> T unwrap(Class<T> aClass) {
				return null;
			}
		};
	}

	//private javax.ejb.SessionContext mySessionCtx; // nothing wrong here
}

package nl.rabobank.perf.pinpointrules;

import java.io.IOException;

import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class ObjectMapperReuse {
	private static final ObjectMapper sharedMapper;

	static {
		sharedMapper = new ObjectMapper();
	}

	public static ObjectMapper createMapper() {
		return new ObjectMapper(); // violation
	}

	public void myMethod() throws IOException, JsonMappingException {
		final ObjectMapper mapper = new ObjectMapper(); // violation
		String json = new ObjectMapper().writeValueAsString("authorizations"); // violation

	}
	
	public void myMethodOk() throws IOException, JsonMappingException {
		final ObjectMapper mapper = sharedMapper; // OK
		String json = mapper.writeValueAsString("authorizations"); // OK

	}

}

@Configuration
class BankSpringRootConfig {

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper(); // OK because configuration is only executed once
	}
}

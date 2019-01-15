package com.jpinpoint.perf.pinpointrules;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;

public class AvoidXMLGregorianCalendar {

	private XMLGregorianCalendar sprinkleDatum;
	protected XMLGregorianCalendar sparkleDatum;

	public XMLGregorianCalendar getSparkleDatum2() {
		XMLGregorianCalendar cal = sprinkleDatum;
		try {
			cal = DatatypeFactory.newInstance().newXMLGregorianCalendar();
		} catch (DatatypeConfigurationException e) {
			// huh?
			assert false;
		}
		return cal;
	}
}

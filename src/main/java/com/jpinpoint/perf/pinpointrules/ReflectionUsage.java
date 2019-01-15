package com.jpinpoint.perf.pinpointrules;

import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.commons.lang.builder.HashCodeBuilder;

public class ReflectionUsage {
	
	private int state;

	@Override
	public boolean equals(Object o) {
		return new EqualsBuilder().reflectionEquals(o, this);
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().reflectionHashCode(this);
	}

	public boolean alternativeEquals(Object o) {
		return EqualsBuilder.reflectionEquals(o, this);
	}

	public int alternativeHashCode() {
		int code = HashCodeBuilder.reflectionHashCode(this);
		return code;
	}


	public boolean equalsOK(Object o) {
		if (o == null) return false;
		if (!(o instanceof ReflectionUsage)) return false;
		ReflectionUsage that = (ReflectionUsage) o;
		return new EqualsBuilder().append(that.state, state).isEquals();
	}


	public int hashCodeOK() {
		return new HashCodeBuilder().append(state).toHashCode();
	}

}

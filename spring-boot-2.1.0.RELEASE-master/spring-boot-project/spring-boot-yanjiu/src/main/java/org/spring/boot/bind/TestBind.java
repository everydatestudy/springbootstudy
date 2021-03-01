package org.spring.boot.bind;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;

public class TestBind {
	public static void main(String[] args) {
		Bindable<FooProperties> bind = Bindable.of(FooProperties.class);
		
	//	ConfigurationPropertyName.of(name)
	}
}

package org.spring.boot.type;

import java.lang.annotation.Annotation;
import java.lang.reflect.GenericDeclaration;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;

public class TestTypeVariable<T extends Runnable & Type> implements GenericDeclaration {
	private T t;
	public static void main(String[] args) {
		TestTypeVariable<TestTypeValue> ccc;
	}

	@Override
	public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Annotation[] getAnnotations() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Annotation[] getDeclaredAnnotations() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public TypeVariable<?>[] getTypeParameters() {
		// TODO Auto-generated method stub
		return null;
	}
}

class TestTypeValue implements Runnable, Type {

	private static final long serialVersionUID = -6079706664734259067L;

	@Override
	public void run() {
		// TODO Auto-generated method stub

	}

}
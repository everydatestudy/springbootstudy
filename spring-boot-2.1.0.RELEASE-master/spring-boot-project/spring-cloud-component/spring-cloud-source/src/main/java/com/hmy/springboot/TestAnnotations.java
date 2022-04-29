package com.hmy.springboot;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

//@Controller
public class TestAnnotations {
	private static Map<String, AnnotationAttributes> attributesMap = new HashMap<String, AnnotationAttributes>();

	public static void main(String[] args) {
		Set<Annotation> visited = new LinkedHashSet<>();
		Annotation[] metaAnnotations = TestSpringBoot.class.getAnnotations();
		for (Annotation metaAnnotation : metaAnnotations) {
			recursivelyCollectMetaAnnotations(visited, metaAnnotation);
		}
		System.out.println(attributesMap);
	}

	private static void recursivelyCollectMetaAnnotations(Set<Annotation> visited, Annotation annotation) {
		Class<? extends Annotation> annotationType = annotation.annotationType();
		String annotationName = annotationType.getName();
		if (!AnnotationUtils.isInJavaLangAnnotationPackage(annotationName) && visited.add(annotation)) {
			try {
				// Only do attribute scanning for public annotations; we'd run into
				// IllegalAccessExceptions otherwise, and we don't want to mess with
				// accessibility in a SecurityManager environment.
				if (Modifier.isPublic(annotationType.getModifiers())) {
					attributesMap.put(annotationName, AnnotationUtils.getAnnotationAttributes(annotation, false, true));
				}
				for (Annotation metaMetaAnnotation : annotationType.getAnnotations()) {
					recursivelyCollectMetaAnnotations(visited, metaMetaAnnotation);
				}
			} catch (Throwable ex) {

			}
		}
	}

}

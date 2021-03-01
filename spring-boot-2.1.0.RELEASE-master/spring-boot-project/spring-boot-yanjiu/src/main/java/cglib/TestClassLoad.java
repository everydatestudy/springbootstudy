package cglib;

import java.util.Map;
import java.util.WeakHashMap;

import org.springframework.cglib.core.CodeGenerationException;

public class TestClassLoad {

	class ClassLoaderData {

	}

	public static void main(String[] args) {

		try {
		 
			Map<ClassLoader, String> newCache = new WeakHashMap<>();
			newCache.put(CodeGenerationException.class.getClassLoader(), "2");
			newCache.put(TestClassLoad.class.getClassLoader(), "4");
			System.out.println(newCache.get(CodeGenerationException.class.getClassLoader()));
			
		} catch (RuntimeException e) {
			throw e;
		} catch (Error e) {
			throw e;
		} catch (Exception e) {
			throw new CodeGenerationException(e);
		}

	}
}

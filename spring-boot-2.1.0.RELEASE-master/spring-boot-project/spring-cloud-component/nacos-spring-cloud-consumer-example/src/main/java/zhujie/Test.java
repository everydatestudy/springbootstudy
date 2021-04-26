package zhujie;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class Test {
	public static void main(String[] args) {
		AnnotationConfigApplicationContext configApplicationContext = new AnnotationConfigApplicationContext();
		configApplicationContext.register(QualifierConfig.class);configApplicationContext.refresh();
		
		Programmer programmer = configApplicationContext.getBean(Programmer.class);
		
		System.out.println("fdsafdsa  "+programmer.getFriends());
	//	configApplicationContext.close();
	}

}

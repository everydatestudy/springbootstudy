package com.hmy;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import javax.sql.DataSource;

import org.springframework.cglib.core.DebuggingClassWriter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import com.hmy.recycle.aop.AService;

import event.TestEvent;

//@EnableAspectJAutoProxy
@ComponentScan({ "com.hmy.recycle", "event" })
//https://www.cnblogs.com/developer_chan/p/10740664.html 新的spring知识讲解了创建实例化
//https://blog.csdn.net/qq_30321211/article/details/108365617 BeanDefinitionValueResolver 类型转换

//上面的说明其实也就指出了aspectJ的几种标准的使用方法(参考文档)：
//https://blog.csdn.net/aizhupo1314/article/details/84989894
//编译时织入，利用ajc编译器替代javac编译器，直接将源文件(java或者aspect文件)编译成class文件并将切面织入进代码。
//编译后织入，利用ajc编译器向javac编译期编译后的class文件或jar文件织入切面代码。
//加载时织入，不使用ajc编译器，利用aspectjweaver.jar工具，使用java agent代理在类加载期将切面织入进代码
//https://blog.csdn.net/m0_46125280/article/details/103854904 spring aop

//https://blog.csdn.net/v123411739/category_8589693.html springaop的博客

//https://my.oschina.net/zhangxufeng?tab=newest&catalogId=3608352 这个讲解的非常详细
//@EnableAspectJAutoProxy
@EnableTransactionManagement
public class TestMain {

	public static void main(String[] args) {
		System.setProperty(DebuggingClassWriter.DEBUG_LOCATION_PROPERTY, "D:\\home\\cglib");
		// ClassPathXmlApplicationContext默认是加载src目录下的xml文件
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
// 		context.addBeanFactoryPostProcessor(new TestBeanDefinitionRegistryPostProcessors());
		context.register(TestMain.class);
		context.refresh();
		AService aservice = context.getBean(AService.class);
		TestEvent event = new TestEvent(new Object(), "事件1");
		context.publishEvent(event);
//		System.out.println("\n===========普通调用=============\n");
//
		System.out.println(aservice.show());
//
//		System.out.println("\n===========异常调用=============\n");
//
////		aservice.excuteException();
//
//		System.out.println("\n========================\n");
		((AbstractApplicationContext) context).close();
	}

	@Bean
	public DataSource dataSource() {
		DriverManagerDataSource dataSource = new DriverManagerDataSource();
		dataSource.setUsername("root");
		dataSource.setPassword("test");
		dataSource.setUrl("jdbc:h2:file:~/.h2/DBName;AUTO_SERVER=TRUE");
		dataSource.setDriverClassName("org.h2.Driver");
		
		
         try {
//        	 Connection connection = dataSource.getConnection();
//             Statement statement = connection.createStatement();
//			statement.execute("create table person (" +
//			         "id BIGINT(20) NOT NULL," +
//			         "name VARCHAR(30) NULL," +
//			         "age INT(11) NULL," +
//			         "PRIMARY KEY (id)" +
//			         ")");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return dataSource;
	}

	@Bean
	public JdbcTemplate jdbcTemplate(DataSource dataSource) {
		return new JdbcTemplate(dataSource);
	}

	@Bean
	public PlatformTransactionManager transactionManager(DataSource dataSource) {
		return new DataSourceTransactionManager(dataSource);
	}
}
package com.hmy.recycle.aop;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
//@Scope(value = "prototype") 
public class AServiceImpl implements AService {
	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Transactional
	public String show() {

		String sql = "insert into person(id,name,age) values(?,?,?)";
		jdbcTemplate.update(sql, 11, "zhangsan", 30);

		return "fsdafd";
	}
}

package com.hmy.circular.dependency;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class Aservice {
	@Autowired
	Bservice b;
}

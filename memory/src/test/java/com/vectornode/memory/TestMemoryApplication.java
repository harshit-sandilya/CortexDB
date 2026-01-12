package com.vectornode.memory;

import org.springframework.boot.SpringApplication;

public class TestMemoryApplication {

	public static void main(String[] args) {
		SpringApplication.from(MemoryApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

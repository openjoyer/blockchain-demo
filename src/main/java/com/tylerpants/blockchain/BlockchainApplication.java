package com.tylerpants.blockchain;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
public class BlockchainApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(BlockchainApplication.class, args);
	}

	@Override
	public void run(String[] args) {
		CmdManager.init();
	}
}
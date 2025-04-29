package com.tylerpants.blockchain;

import com.tylerpants.blockchain.ECDSA.Point;
import com.tylerpants.blockchain.ECDSA.SignatureManager;

import java.io.*;
import java.util.Random;
import java.util.Scanner;

public class AccountManager {
    private final Scanner scanner;

    private final SignatureManager signatureManager = new SignatureManager();

    public AccountManager() {
        this.scanner = new Scanner(System.in);
    }

    public String init() {
        System.out.println("Enter your username:");
        String name = scanner.nextLine();

        System.out.println("Enter your password:");
        String password = scanner.nextLine();

        System.out.println("Your seed words!");
        String[] seeds = seedWords();

        for (int i = 0; i < seeds.length; i++) {
            System.out.println((i+1) +". " + seeds[i]);
        }

        System.out.println("Note it in right order and hide from everyone! Please type 'ready' if you are ready to continue...");

        while (!scanner.nextLine().equals("ready")) {

        }
        String privateKey = signatureManager.generatePrivateKey(seeds);
        System.out.println("[TEST] Private key: " + privateKey);

        return privateKey;
    }

    public String[] seedWords() {
        int amount = 12;
        int total = 2048;
        String[] arr = new String[amount];
        Random random = new Random();

        String[] totalArr = new String[total];

        try (BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\PC\\OneDrive\\Рабочий стол\\proga\\blockchain\\src\\main\\resources\\seed.txt"))) {
            for (int i = 0; i < total; i++) {
                totalArr[i] = br.readLine();
            }

            for (int i = 0; i < amount; i++) {
                int r = random.nextInt(2048);
                arr[i] = totalArr[r];
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return arr;
    }
}

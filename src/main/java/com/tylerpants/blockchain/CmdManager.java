package com.tylerpants.blockchain;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tylerpants.blockchain.ECDSA.Message;
import com.tylerpants.blockchain.ECDSA.Point;
import com.tylerpants.blockchain.ECDSA.SignatureManager;
import com.tylerpants.blockchain.util.Pair;
import com.tylerpants.blockchain.util.Utils;


import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class CmdManager {
    private static final String BLOCKCHAIN_DATA_PATH = "C:\\Users\\PC\\OneDrive\\Рабочий стол\\proga\\blockchain\\src\\main\\resources\\blockchain-data.json";
    private static final String HELP_TEXT = """
            Commands:
                'c' - create operation
                'h' - help
                'b' - commit block
                'll' - print all blockchain list
                'q' - quit program""";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final SignatureManager signatureManager = new SignatureManager();
    private static String lastHash;
    private static Scanner scanner;
    private static Block currentBlock;
    private static List<Block> blockChain;

    public static void init() {
        scanner = new Scanner(System.in);

        String privateKey = new AccountManager().init();
        Point publicKeyPoint = signatureManager.generatePublicKey(privateKey);

        String publicKey = signatureManager.uncompressedPublicKey(publicKeyPoint);
        System.out.println("[TEST] Uncompressed Public Key / Address: " + publicKey);

        String compressedPublicKey = signatureManager.compressedPublicKey(publicKeyPoint);
        System.out.println("[TEST] Compressed Public Key " + compressedPublicKey);

        System.out.println("Welcome to Demo Blockchain (v. 1.0 SNAPSHOT)\n"+ HELP_TEXT);

        try {
            blockChain = objectMapper.readValue(new File(BLOCKCHAIN_DATA_PATH), new TypeReference<>() {
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        lastHash = blockChain.get(blockChain.size()-1).getHash();
        currentBlock = new Block(lastHash, new ArrayList<>(3));


        String scannedCommand;
        while (true) {
            scannedCommand = scanner.nextLine();

            switch (scannedCommand) {
                case "q" : {
                    return;
                }
                case "c" : {
                    if(currentBlock.getOperationList().size() == 3) {
                        System.out.println("A Maximum block capacity has been reached!\nPlease commit this block and create a new one.");
                    }
                    else {
                        System.out.println("Enter recipient address");
                        String recipientPK = scanner.nextLine();

                        System.out.println("Enter data to send");
                        String data = scanner.nextLine();
                        createOperation(publicKey, privateKey, recipientPK, data);

                        System.out.println("DONE!");
                    }
                    break;
                }
                case "b" : {
                    commitBlock();
                    System.out.println("Block committed!");
                    break;
                }
                case "ll" : {
                    print();
                    break;
                }
                case "h" : {
                    System.out.println(HELP_TEXT);
                    break;
                }
                default: {
                    System.out.println("Unknown command. Enter 'h' for help info");
                }
            }
        }

    }

    private static void createOperation(String publicKey, String privateKey, String recipientPK, String data) {
        String message_hash = Utils.sha256(data);
        Pair<BigInteger, BigInteger> signature = signatureManager.signMessage(new Message(message_hash), privateKey);
        Operation operation = new Operation(publicKey, recipientPK, data, signature);

        currentBlock.addOperation(operation);
    }

    private static void commitBlock() {
        System.out.println("Started mining block...");
        currentBlock.mineBlock();
        blockChain.add(currentBlock);

        Block newBlock = new Block(lastHash, new ArrayList<>(3));
        lastHash = newBlock.getHash();

        currentBlock = newBlock;

        try {
            objectMapper.writeValue(new File(BLOCKCHAIN_DATA_PATH), blockChain);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void print() {
        for (Block block : blockChain) {
            System.out.println("-------------------------------------------------------------------------");
            System.out.println("| Hash: " + block.getHash());
            System.out.println("| Previous hash: " + block.getPrevHash());
            System.out.println("| Timestamp: " + block.getTimestamp());
            System.out.println("| Operations: ");
            for (Operation o : block.getOperationList()) {
                System.out.println("|   " + o);
            }
            System.out.println("| Nonce: " + block.getNonce());
        }
    }
}
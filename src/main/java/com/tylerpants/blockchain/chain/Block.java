package com.tylerpants.blockchain.chain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tylerpants.blockchain.util.Utils;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;
import java.util.Random;

@Getter
@Setter
public class Block {
    private int difficulty = 4;

    private String hash;
    private String prevHash;
    private long timestamp;
    private List<Operation> operationList;
    private int nonce;
    private static Random random = new Random();

    @JsonCreator
    public Block(@JsonProperty("hash") String hash,
                 @JsonProperty("timestamp") long timestamp,
                 @JsonProperty("operationList") List<Operation> operationList,
                 @JsonProperty("prevHash") String prevHash,
                 @JsonProperty("nonce") int nonce) {
        super();
        this.hash = hash;
        this.timestamp = timestamp;
        this.operationList = operationList;
        this.prevHash = prevHash;
        this.nonce = nonce;
    }

    public void mineBlock() {
        String target = new String(new char[difficulty]).replace('\0', '0');
        while (!hash.substring(0, difficulty).equals(target)) {
            nonce = random.nextInt(Integer.MAX_VALUE);
            hash = calculateHash();
        }
        System.out.println("Block mined! Hash: " + hash);
        this.timestamp = new Date().getTime();
    }

    public Block(String prevHash, List<Operation> operationList) {
        this.prevHash = prevHash;
        this.operationList = operationList;
        this.hash = calculateHash();
    }

    private String calculateHash() {
        String s = prevHash + timestamp + operationList.toString() + nonce;
        return Utils.sha256(s);
    }


    public void addOperation(Operation o) {
        this.operationList.add(o);
    }

}

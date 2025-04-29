package com.tylerpants.blockchain.ECDSA;

import lombok.Getter;

import java.math.BigInteger;

public class Message {
    private BigInteger intValue;
    private String hashValue;

    public Message(BigInteger intValue) {
        this.intValue = intValue;
    }

    public Message(String hashValue) {
        this.hashValue = hashValue;
    }

    public BigInteger getIntValue() {
        return intValue;
    }

    public String getHashValue() {
        return hashValue;
    }
}

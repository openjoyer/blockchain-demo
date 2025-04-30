package com.tylerpants.blockchain.ECDSA;

import com.tylerpants.blockchain.util.Utils;
import lombok.Getter;

import java.math.BigInteger;

public class Message {
    private BigInteger intValue;
    private String hashValue;

    public Message(BigInteger intValue) {
        this.intValue = intValue;
        this.hashValue = Utils.sha256(String.valueOf(intValue));
    }

    public Message(String hashValue) {
        this.hashValue = hashValue;
        this.intValue = Utils.hexToDec(hashValue);
    }

    public BigInteger getIntValue() {
        return intValue;
    }

    public String getHashValue() {
        return hashValue;
    }
}

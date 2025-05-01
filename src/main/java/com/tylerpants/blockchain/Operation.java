package com.tylerpants.blockchain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tylerpants.blockchain.util.Pair;
import com.tylerpants.blockchain.util.Utils;
import lombok.Getter;

import java.math.BigInteger;
import java.util.Date;

@Getter
public class Operation {
    private final String hash;
    private final String sender;
    private final String recipient;
    private final long timestamp;
    private final String data;
    private final Pair<BigInteger, BigInteger> signature;

    @JsonCreator
    public Operation(
            @JsonProperty("hash") String hash,
            @JsonProperty("sender") String sender,
            @JsonProperty("recipient") String recipient,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("data") String data,
            @JsonProperty("signature")Pair<String, String> signature) {
        this.hash = hash;
        this.recipient = recipient;
        this.sender = sender;
        this.timestamp = timestamp;
        this.data = data;
        this.signature = new Pair<>(new BigInteger(signature.getA()), new BigInteger(signature.getB()));
    }

    public Operation(String sender, String recipient, String data, Pair<BigInteger, BigInteger> signature) {
        this.sender = sender;
        this.recipient = recipient;
        this.data = data;
        this.signature = signature;

        this.hash = calculateHash();
        this.timestamp = new Date().getTime();
    }

    private String calculateHash() {
        String s = sender + recipient + timestamp + data + signature.toString();
        return Utils.sha256(s);
    }

    @Override
    public String toString() {
        return hash;
    }

}

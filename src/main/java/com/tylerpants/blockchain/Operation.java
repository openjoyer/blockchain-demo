package com.tylerpants.blockchain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tylerpants.blockchain.util.Pair;
import lombok.Getter;

import java.math.BigInteger;
import java.util.List;

@Getter
public class Operation {
    private static int counter;
    private final int id;
    private final String sender;
    private final String recipient;

    private final String data;

    private Pair<BigInteger, BigInteger> signature;

    @JsonCreator
    public Operation(
            @JsonProperty("id") int id,
            @JsonProperty("sender") String sender,
            @JsonProperty("recipient") String recipient,
            @JsonProperty("data") String data,
            @JsonProperty("signature")List<String> signatureList) {
        this.id = id;
        this.recipient = recipient;
        this.sender = sender;
        this.data = data;
        this.signature = new Pair<>(new BigInteger(signatureList.get(0)), new BigInteger(signatureList.get(1)));
    }

    @Override
    public String toString() {
        return "#" + id + "  " + sender + " -> " + recipient + ", data: " + data +
                "\nSignature: " + signature;
    }

    public Operation(String sender, String recipient, String data) {
        this.id = ++counter;
        this.sender = sender;
        this.recipient = recipient;
        this.data = data;
    }

}

package com.tylerpants.blockchain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.tylerpants.blockchain.util.Utils;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class Block {

    private int number;
    private String hash;
    private String prevHash;
    private long timestamp;
    private List<Operation> operationList;

    @JsonCreator
    public Block(@JsonProperty("number") int number,
                 @JsonProperty("hash") String hash,
                 @JsonProperty("timestamp") long timestamp,
                 @JsonProperty("operationList") List<Operation> operationList) {
        super();
        this.number = number;
        this.hash = hash;
        this.timestamp = timestamp;
        this.operationList = operationList;
    }

    public Block(String prevHash, List<Operation> operationList) {
        this.prevHash = prevHash;
        this.operationList = operationList;
        this.timestamp = new Date().getTime();
        this.hash = Utils.sha256(number + prevHash + timestamp + operationList.toString());
    }


    public void addOperation(Operation o) {
        this.operationList.add(o);
    }

}

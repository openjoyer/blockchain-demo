package com.tylerpants.blockchain.chain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class Blockchain {

    private List<Block> blocks;

    @JsonCreator
    public Blockchain(@JsonProperty("blocks") List<Block> blocks) {
        this.blocks = blocks;
    }


    public void addBlock(Block b) {
        blocks.add(b);
    }

    public Block get(int index) {
        return blocks.get(index);
    }

    public List<Block> allBlocks() {
        return blocks;
    }

    public int size() {
        return blocks.size();
    }

    public boolean isEmpty() {
        return blocks.isEmpty();
    }
}

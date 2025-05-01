package com.tylerpants.blockchain;

import com.tylerpants.blockchain.util.Utils;
import lombok.Getter;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Getter
@Setter
public class MessageHeader {
    private final byte[] magic;
    private final String command;
    private final int payloadLength;
    private final byte[] checksum;

    public MessageHeader(byte[] magic, String command, int payloadLength, byte[] checksum) {
        this.magic = magic;
        this.command = command.trim(); // Удаляем нулевые байты в конце
        this.payloadLength = payloadLength;
        this.checksum = checksum;
    }

    @Override
    public String toString() {
        return "MessageHeader { " + Utils.bytesToHex(magic) + ", " + command + ", " + payloadLength + ", " + Utils.bytesToHex(checksum) + " }";
    }
}

package com.tylerpants.blockchain.network.file;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;

import java.net.InetAddress;
import java.net.UnknownHostException;

@AllArgsConstructor
@Getter
@Setter
public class PeerRecord {
    private byte[] ip;        // 4 байт (IPv4/IPv6)
    private int port;         // 0-65535
    private long timestamp;   // Unix time
    private int score;        // -1 = забанен, 0 = новый, >0 = надежный
    private String source;    // "dns", "file", "incoming", "hardcoded"

    public InetAddress getAddress() {
        try {
            return InetAddress.getByAddress(ip);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    @SneakyThrows
    @Override
    public String toString() {
        return "{" + InetAddress.getByAddress(ip).getHostAddress() +
                ":" + port +
                ", timestamp=" + timestamp +
                ", score=" + score +
                ", source='" + source + '\'' +
                "}";
    }
}
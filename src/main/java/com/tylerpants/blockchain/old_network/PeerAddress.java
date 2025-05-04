package com.tylerpants.blockchain.old_network;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class PeerAddress {
    private final String host; // IP или доменное имя
    private final int port;    // Порт

    @Override
    public String toString() {
        return host + ":" + port;
    }


    // Сохранение пиров на диск
    public String toSerializedString() {
        return host + "|" + port;
    }

    public static PeerAddress fromString(String s) {
        String[] parts = s.split("\\|");
        return new PeerAddress(parts[0], Integer.parseInt(parts[1]));
    }
}

package com.tylerpants.blockchain.network;

import lombok.Value;

@Value
public class PeerAddress {
    String host;
    int port;
}

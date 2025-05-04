package com.tylerpants.blockchain.network;

import lombok.Value;

@Value
public class NetworkMessage {
    PeerInfo peer;
    byte[] data;
}
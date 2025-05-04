package com.tylerpants.blockchain.network.dns;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DnsSeeder {
    private static final String[] MY_SEEDS = {
            "mybitcoinseed.example.com",
            "backup.mybitcoinseed.example.com"
    };

    public List<InetAddress> queryDnsSeeds() {
        List<InetAddress> nodes = new ArrayList<>();
        for (String host : MY_SEEDS) {
            try {
                nodes.addAll(Arrays.asList(InetAddress.getAllByName(host)));
            } catch (UnknownHostException e) {
                System.err.println("DNS seed failed: " + host);
            }
        }
        return nodes;
    }
}

package com.tylerpants.blockchain;

import com.tylerpants.blockchain.util.Utils;

public class Bench32Address {

    public static String generateAddress(String uncompressedPK) {
        String hash =  Utils.ripemd160(Utils.sha256(uncompressedPK));

        // Байт-идентификатор основной сети (0x00)
        hash = "00" + hash;

        String checkSum = Utils.sha256(Utils.sha256(hash)).substring(0, 8);
        hash = hash + checkSum;

        // Дальше конвертация в base58...


        return null;
    }


}

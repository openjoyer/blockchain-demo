package com.tylerpants.blockchain.ECDSA;


import com.tylerpants.blockchain.util.ConfigData;
import lombok.Getter;
import java.math.BigInteger;

@Getter
public class CurveConfig {
    private final BigInteger a;
    private final BigInteger b;
    private final BigInteger p;

    public CurveConfig() {
        ConfigData configData = new ConfigData();
        this.a = new BigInteger(configData.getA());
        this.b = new BigInteger(configData.getB());
        this.p = new BigInteger(configData.getP());
    }
    public CurveConfig(BigInteger a, BigInteger b, BigInteger p) {
        this.a = a;
        this.b = b;
        this.p = p;
    }

}

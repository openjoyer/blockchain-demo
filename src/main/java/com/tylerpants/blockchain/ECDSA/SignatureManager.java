package com.tylerpants.blockchain.ECDSA;

import com.fasterxml.jackson.databind.jsontype.impl.StdTypeResolverBuilder;
import com.tylerpants.blockchain.util.ConfigData;
import com.tylerpants.blockchain.util.Pair;
import com.tylerpants.blockchain.util.Utils;
import jdk.jshell.execution.Util;
import lombok.Getter;


import java.math.BigInteger;

@Getter
public class SignatureManager {
    private final Point gpPoint;

    private ConfigData configData;

    private BigInteger n;

    public SignatureManager() {
        configData = new ConfigData();
        this.n = new BigInteger(configData.getN());
        this.gpPoint = initPoint();
    }

    private Point initPoint() {
        try {
            return new Point(
                    new BigInteger(configData.getX()),
                    new BigInteger(configData.getY()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Pair<BigInteger, BigInteger> signMessage(Message message, String privateKeyStr) {
        BigInteger privateKey = Utils.hexToDec(privateKeyStr);
        BigInteger k = Utils.random(BigInteger.ONE, n);
        Point r_point = gpPoint.multiply(k);
        BigInteger r = r_point.getX().mod(n);

        if (r.equals(BigInteger.ZERO)) {
            return signMessage(message, privateKeyStr);
        }
        BigInteger k_inverse = Utils.findInverse(k, n);

        BigInteger inner = message.getIntValue().add(r.multiply(privateKey));
        BigInteger s = k_inverse.multiply(inner).mod(n);

        return new Pair<>(r, s);
    }

    public boolean verifyMessage(Pair<BigInteger, BigInteger> signature, Message message, Point publicKey) {
        BigInteger r = signature.getA();
        BigInteger s = signature.getB();

        BigInteger s_inverse = Utils.findInverse(s, n);

        BigInteger u = message.getIntValue().multiply(s_inverse).mod(n);
        BigInteger v = r.multiply(s_inverse).mod(n);

        Point c_point;
        try {
            c_point = gpPoint.multiply(u).add(publicKey.multiply(v));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return c_point.getX().equals(r);
    }

    public String generatePrivateKey(String[] seeds) {
        StringBuilder builder = new StringBuilder();
        for (String s : seeds) {
            builder.append(s);
        }

        String privateKey = Utils.sha256(builder.toString());
        return privateKey;
    }

    public Point generatePublicKey(String privateKey) {
        BigInteger privateKeyInt = Utils.hexToDec(privateKey);
        Point publicKey = gpPoint.multiply(privateKeyInt);

        return publicKey;
    }

    public String uncompressedPublicKey(Point publicKey) {
        String k = "04" + Utils.decToHex(publicKey.getX()) + Utils.decToHex(publicKey.getY());
        return k;
    }

    public Point uncompressedToPoint(String hex) {
        String x = hex.substring(2, 66);
        String y = hex.substring(66, 130);
        Point point;
        try {
            point =  new Point(new BigInteger(x, 16), new BigInteger(y, 16));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return point;
    }

    public String compressedPublicKey(Point publicKey) {
        String x = Utils.decToHex(publicKey.getX());
        String result;

        if (publicKey.getY().mod(BigInteger.TWO).equals(BigInteger.ZERO)) {
            result = "02" + x;
        } else {
            result = "03" + x;
        }
        return result;
    }
    public String compressedPublicKey(String uncompressedPK) {
        String x = uncompressedPK.substring(2, 66);
        String y = uncompressedPK.substring(66, 130);
        String result;

        if(Utils.hexToDec(y).mod(BigInteger.TWO).equals(BigInteger.ZERO)) {
            result = "02" + x;
        } else {
            result = "03" + x;
        }

        return result;
    }

    // ONLY IF Y IS EVEN
    public String xOnlyPublicKey(String uncompressedPK) {
        return uncompressedPK.substring(2,66);
    }
}

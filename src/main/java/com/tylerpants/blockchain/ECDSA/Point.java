package com.tylerpants.blockchain.ECDSA;

import com.tylerpants.blockchain.util.Pair;
import com.tylerpants.blockchain.util.Utils;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public class Point {

    private final BigInteger x;
    private final BigInteger y;
    private final CurveConfig curveConfig;

    public Point(BigInteger x, BigInteger y) throws Exception {
        curveConfig = new CurveConfig();

        BigInteger a = curveConfig.getA();
        BigInteger b = curveConfig.getB();
        BigInteger p = curveConfig.getP();

        // (y ** 2) % p != (x ** 3 + a * x + b) % p
        BigInteger leftSide = y.pow(2).mod(p);
        BigInteger rightSide = x.pow(3).add(a.multiply(x)).add(b).mod(p);
        if (!leftSide.equals(rightSide)) {
            throw new Exception("The point is not on the curve");
        }

        this.x = x;
        this.y = y;}

    public boolean isEqualTo(Point point) {
        return this.x.equals(point.getX()) && (this.y.equals(point.getY()));
    }

    public Point add(Point point) throws Exception {
        BigInteger p = curveConfig.getP();
        BigInteger slope;

        if (this.isEqualTo(point)) {
            // Calculate slope for the same point (point doubling)
            BigInteger numerator = BigInteger.valueOf(3).multiply(point.getX().pow(2));
            BigInteger denominator = Utils.findInverse(BigInteger.valueOf(2).multiply(this.y), p);
            slope = numerator.multiply(denominator).mod(p);
        } else {
            // Calculate slope for different points
            BigInteger numerator = point.y.subtract(this.y);
            BigInteger denominator = Utils.findInverse(point.x.subtract(this.x), p).mod(p);
            slope = numerator.multiply(denominator);
        }

        // Calculate new x and y coordinates
        BigInteger x = slope.pow(2).subtract(point.x).subtract(this.x).mod(p);
        BigInteger y = slope.multiply(this.x.subtract(x)).subtract(this.y).mod(p);

        // Return the new point
        return new Point(x, y);
    }
    public Point multiply(BigInteger times) {
        Point currentPoint = this;
        BigInteger currentCoef = new BigInteger("1");
        List<Pair<BigInteger, Point>> prevPoints = new ArrayList<>();

        while (currentCoef.compareTo(times) < 0) {
            prevPoints.add(new Pair<>(currentCoef, currentPoint));

            if (currentCoef.multiply(BigInteger.valueOf(2)).compareTo(times) <= 0) {
                try {
                    currentPoint = currentPoint.add(currentPoint);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                currentCoef = currentCoef.multiply(BigInteger.valueOf(2));

            }
            else {
                Point nextPoint = this;
                BigInteger nextCoef = BigInteger.valueOf(1);

                for (Pair<BigInteger, Point> pair : prevPoints) {
                    if (pair.getA().add(currentCoef).compareTo(times) <= 0) {
                        if (pair.getB().getX().compareTo(currentPoint.getX()) != 0) {
                            nextCoef = pair.getA();
                            nextPoint = pair.getB();
                        }
                    }
                }

                try {
                    currentPoint = currentPoint.add(nextPoint);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                currentCoef = currentCoef.add(nextCoef);
            }
        }
        return currentPoint;
    }

    @Override
    public String toString() {
        return "("+x+", "+y+")";
    }

    public BigInteger getX() {
        return x;
    }

    public BigInteger getY() {
        return y;
    }
}

package com.tylerpants.blockchain.util;

import lombok.Getter;

@Getter
//@Configuration
//@Data
//@PropertySource("src/main/resources/application.properties")
public class ConfigData {

//    @Value("${ecdsa.gp-point.x}")
    private String x = "55066263022277343669578718895168534326250603453777594175500187360389116729240";
//    @Value("${ecdsa.gp-point.y}")
    private String y = "32670510020758816978083085130507043184471273380659243275938904335757337482424";

//    @Value("${ecdsa.n}")
    private String n = "115792089237316195423570985008687907852837564279074904382605163141518161494337";

//    @Value("${ecdsa.curve.a}")
    private String a = "0";

//    @Value("${ecdsa.curve.b}")
    private String b = "7";

//    @Value("${ecdsa.curve.p}")
    private String p = "115792089237316195423570985008687907853269984665640564039457584007908834671663";
}

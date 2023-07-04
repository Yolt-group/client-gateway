package nl.ing.lovebird.clientproxy;

import java.security.PrivateKey;
import java.security.Security;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import nl.ing.lovebird.clientproxy.filter.filters.pre.AccessTokenValidationFilter;
import nl.ing.lovebird.clienttokens.ClientToken;
import nl.ing.lovebird.clienttokens.constants.ClientTokenConstants;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.NumericDate;
import org.jose4j.lang.JoseException;

import static nl.ing.lovebird.clienttokens.constants.ClientTokenConstants.CLAIM_CLIENT_USERS_KYC_PRIVATE_INDIVIDUALS;

public class TestTokenCreator {

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    public static final String ENCRYPTION_SECRET = "Fdh9u8rINxfivbrianbbVT1u232VQBZYKx1HGAGPt2IFdh9u8rINxfivbrianbbVT1u232VQBZYKx1HGAGPt2I";

    public static final String CLIENT_ID = "11112222-3333-4444-5555-666677778888";

    public static final String ORIGIN_IP = "127.0.0.1";
    private static final String WRONG_ENCRYPTION_SECRET = "Gdh9u8rINxfivbrianbbVT1u232VQBZYKx1HGAGPt2IFdh9u8rINxfivbrianbbVT1u232VQBZYKx1HGAGPt2I";

    private static RsaJsonWebKey signaturePrivateKey;

    static {
        try {
            // this key matches the public jwks in application-test
            String privateKeyJwk = "{\"kty\":\"RSA\",\"kid\":\"60f7808d-03dd-42a7-8b62-2f91d0b0cb0a\",\"n\":\"lnI3PMDybQtgSwjSV-PYkkd0GK54aIZ-VRdh-HI-npzRTYE77Es49v0_13-cbGWH7TfFs-HLz7jS-3BGuSTduRKQbvBxk6EWTg6SSJeMc2A_dLSfhGwhZ_NvJSpJ98YcQUtHcZ8K0W-sTyHezyEJ3z1hq8q6P8KATQL9bVy2NMjQ3IEtNRkyV4CncoAeuNGD5Z6xkzhnjnMEqbcoHMb_FMtJ41CFGsEDzPuGYqEz3NvxUZb9_rWZoxKfrcTEOJrwne5nFhdCUcvCe_PID5TrOyyUUXbLRb6jumiVDLN3g8P-WtQaip5UrYUaSUEjjojyMEjQjxSHvo15qkqMxS_yxw\",\"e\":\"AQAB\",\"d\":\"MuSsXvb-i3jfuEJhta20I7fcREUxIlrs_agNUliDanCuNUPUm5jOym7dW-8lYV3vX4YQcUufAMQLS1et9Q_Nmb_38C-SnFhQDVPMlJX_wz_592bq14ckvd-R58aogxMXl9b5cixVIoheh95zWypYBpbjJZRM8SjA8kxios5MLQqFJVLxCbwmD-qHi8XQyGEAwqvqtnlO4JRT8Vya8A4yGd_Da6TWdm487G86U41U6VHOxQXKwvOlOu0uAjuFDckcx_AvcjkHlywqksTWpUUIKVqmcloKcu-qp8rq41XeRlVY8CUQKmJawDrdUXPjrmspW_Lw82ArxbobcyKaO6o0AQ\",\"p\":\"xYIl70LAVCkXRFmiUR74MXpyglgykKdSBHbimcrzwga38LrSxltcm8GH98cUvGG1gl8JKozdZfbdaChvEpC-KO7V1rA-Y-XhhF_tlZNDD9ievb9vqMufUnhjQclnBomjKlh8tIEk-bVYjvuIulyF2SDrrBmLEDs-uJ1M17wHSwE\",\"q\":\"wwAaXbGH6cwF8wpNIlrN1LhkW1v0euWFgEKHL8mCedyET30PNvY1vYguiudNRoOAgAEqi9Iervdl6XImGtuvHbiZgaizFrkZT8n0NqoYqLYxL639SYqQai7PsUbYrJqpL2YnotPX2BB5PtW1ZUQJ-4hf1YTti05Ow3wBLbktpcc\",\"dp\":\"ZQl9SnaFWQhkRKzt4j3LjdQr_A4OX_2YcXw306EFLb6uHlIUPTDDoVJRsil_rBb3-aeQUtoY8G5nOT9mAsNU5C-56MfkQsp4oXVJXvkkl1ijbEIgZuMzr8ayUBctwyRp-eGmediPB8cDdLGsclmeh0LWDQZMI5OLNHoTs1EXEgE\",\"dq\":\"LFGmrGq_-CwtofpSY599rn4mGPmCTDhEKk10ijDjXaz3yVUkExrMRgJgiaNeVctndjBNqi-cV6nU2MTf0jThzQB6qxRbd6ukDBVbUt0_84BNF4gUzBUZE3kGLUVr03bnQuWV1pUNNocv9079BkH7ftaU6WNn1cR7dESHxAuVS1s\",\"qi\":\"qSgt0X7ZFYaN04bRG2UeCmXdBamXA7HhUBhFPz0CqDVhWqcnYJAbVRRODBkA5JJookAaHAvgp_qrtOkyCMDIErVxzSN2ohJiGNTXnXrFFQg_aFauRfG2IkcM0ZETmas7nGGpuWz7dKVc5l6_NGODpzFsw_3GInjUqMw51JStzyY\"}";
            signaturePrivateKey = (RsaJsonWebKey) RsaJsonWebKey.Factory.newPublicJwk(privateKeyJwk);
        } catch (JoseException e) {
            e.printStackTrace();
        }
    }

    public static String createValidAccessToken() throws JoseException {
        return createAccessToken(createJsonWebKey(), createJwtClaims());
    }

    public static ClientToken createClientToken() throws JoseException {
        return createClientToken(claims -> {});
    }

    public static ClientToken createClientToken(Consumer<JwtClaims> claimsCustomizer) throws JoseException {
        JwtClaims claims = createJwtClaims();
        claims.setClaim(ClientTokenConstants.EXTRA_CLAIM_ISSUED_FOR, "client-gateway");
        claims.setClaim(ClientTokenConstants.EXTRA_CLAIM_CLIENT_GROUP_ID, UUID.randomUUID().toString());
        claims.setClaim(ClientTokenConstants.CLAIM_PSD2_LICENSED, true);
        claims.setClaim(ClientTokenConstants.CLAIM_CLIENT_USERS_KYC_PRIVATE_INDIVIDUALS, false);
        claims.setClaim(ClientTokenConstants.CLAIM_CLIENT_USERS_KYC_ENTITIES, false);

        claimsCustomizer.accept(claims);

        final JsonWebSignature jws = createJsonWebSignature(
                claims, signaturePrivateKey.getRsaPrivateKey(), signaturePrivateKey.getKeyId()
        );
        return new ClientToken(jws.getCompactSerialization(), claims);
    }

    public static JsonWebKey createJsonWebKey() throws JoseException {
        Map<String, Object> header = new HashMap<>();
        header.put(AccessTokenValidationFilter.TOKEN_HEADER_ENCRYPTION_KEY_TYPE, AccessTokenValidationFilter.TOKEN_HEADER_ENCRYPTION_KEY_TYPE_OCTET_SEQUENCE);
        header.put(AccessTokenValidationFilter.TOKEN_HEADER_ENCRYPTION_KEY, ENCRYPTION_SECRET);
        return JsonWebKey.Factory.newJwk(header);
    }

    public static JsonWebKey createWrongJsonWebKey() throws JoseException {
        Map<String, Object> header = new HashMap<>();
        header.put(AccessTokenValidationFilter.TOKEN_HEADER_ENCRYPTION_KEY_TYPE, AccessTokenValidationFilter.TOKEN_HEADER_ENCRYPTION_KEY_TYPE_OCTET_SEQUENCE);
        header.put(AccessTokenValidationFilter.TOKEN_HEADER_ENCRYPTION_KEY, WRONG_ENCRYPTION_SECRET);
        return JsonWebKey.Factory.newJwk(header);
    }

    public static JwtClaims createJwtClaims() {
        JwtClaims claims = new JwtClaims();
        claims.setSubject(CLIENT_ID);
        claims.setIssuedAt(NumericDate.now());
        NumericDate expirationDate = NumericDate.now();
        expirationDate.addSeconds(120);
        claims.setExpirationTime(expirationDate);
        claims.setJwtId(UUID.randomUUID().toString());
        claims.setClaim(AccessTokenValidationFilter.TOKEN_EXTRA_CLAIM_SUBJECT_IP, ORIGIN_IP);
        return claims;
    }

    public static String createAccessToken(JsonWebKey encryptionJwk, JwtClaims claims) throws JoseException {
        return createAccessToken(encryptionJwk, claims.toJson());
    }

    public static String createAccessToken(JsonWebKey encryptionJwk, String payload) throws JoseException {
        JsonWebEncryption jwe = new JsonWebEncryption();
        jwe.setPlaintext(payload);
        jwe.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.DIRECT);
        jwe.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_CBC_HMAC_SHA_512);
        jwe.setKey(encryptionJwk.getKey());
        return jwe.getCompactSerialization();
    }

    public static JsonWebSignature createJsonWebSignature(
            JwtClaims claims, PrivateKey signaturePrivateKey, String kid
    ) {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_PSS_USING_SHA512);
        jws.setKey(signaturePrivateKey);
        jws.setKeyIdHeaderValue(kid);
        return jws;
    }
}

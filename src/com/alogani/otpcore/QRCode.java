package com.alogani.otpcore;

import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alogani.otpcore.OTPType.*;

/*
This class holds static methods to transform a QRCode image into Token and reverse
 */
public class QRCode {

    /*
    Following Exception is originally called NotFoundException by zxing package.
    But if this program is distributed as a jar, the NotFoundException couldn't be catched by user if he doesn't also install zxing package
    Having a custom exception here prevents it
     */
    static public class NoQRCodeFoundException extends Exception {}

    static public class InvalidQRCodeException extends Exception {}



    /*
     Regex utilities
     credit to : https://github.com/hectorm/otpauth/blob/0a13a76b3ad15ef4b4aa4f345d68b602e5ad5d1d/src/uri.js#L58 for avoiding boilerplate to write it from zero
     */
    private final static List<String> OTPURI_PARAMS = Arrays.asList("issuer", "secret", "period", "counter", "digits", "algorithm");
    private final static String OTPURI_REGEX = "^otpauth:\\/\\/([ht]otp)\\/(.+)\\?((?:&?(?:" + String.join("|", OTPURI_PARAMS) + ")=[^&]+)+)$";


    // QRCode (file) -> Token
    static public Token getTokenfromQRCodeFile(String path) throws IOException, NoQRCodeFoundException, InvalidQRCodeException {
        String decodedQRcode = decodeURIFromQRCode(path);
        Map<String, String> tokenMap = parseURI(decodedQRcode);
        return generateTokenFromMap(tokenMap);
    }

    // Token -> QRCode (buffered image)
	static public BufferedImage generateQRCode(Token token, int width, int height) throws WriterException {
        String uri = generateURIFromToken(token);
        return generateQRCodeFromURI(uri, width, height);
	}

    // There are a lot of instructions, it has been divided into multiple little methods to improve readability
    // ----------------- PRIVATE CODE FOR DECODING QRCODE
    static private String decodeURIFromQRCode(String path) throws IOException, NoQRCodeFoundException  {
        BufferedImage bufferedImage = ImageIO.read(new FileInputStream(path)); // IOException means file not found
        LuminanceSource source = new BufferedImageLuminanceSource(bufferedImage);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source)); // NotFoundException means QRCode not lisible

        Result result;
        try { result = new MultiFormatReader().decode(bitmap);
        } catch (NotFoundException e) { throw new NoQRCodeFoundException(); }

        return result.getText();
    }


    // get Key and value pair from URI
    static private Map<String, String> parseURI (String URI) throws InvalidQRCodeException {
        // IllegalStateException will be thrown when accessing matcher groups if URI format is wrong
        Pattern p = Pattern.compile(OTPURI_REGEX, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(URI);
        m.find();// apply REGEX

        Map<String, String> map = new HashMap<>();
        String totparguments;
        try {
            map.put("otptype", m.group(1)); // first match of URI is always otpType
            map.put("account", m.group(2)); // second match of URI is always account (also named Label)
            totparguments = m.group(3); // third match are other TOTP arguments
        } catch (Exception e) {
            throw new InvalidQRCodeException();
        }

        for (String s : totparguments.split("&")) {
            String[] keyAndValue = s.split("=");
            // keys in lower case, just in case it wasn't
            map.put(keyAndValue[0].toLowerCase(), keyAndValue[1]);
        }
        return map;
    }


    // get new token from key and value pair
    static public Token generateTokenFromMap (Map<String, String> map) throws InvalidQRCodeException {
        // These keys must exist at this point
        String account = map.get("account");
        OTPType otpType = (map.get("otptype").equals("totp")) ? TOTP : HOTP;

        // Mandatory key
        String secretKey;
        if (map.get("secret") != null) {
            secretKey = (map.get("secret"));
        } else { throw new InvalidQRCodeException();}

        // These keys are not mandatory
        String issuer = (map.get("issuer") != null) ? map.get("issuer") : ""; // default = ""
        int digits = (map.get("digits") != null) ? Integer.parseInt(map.get("digits")) : 6; // default = 6 digits

        HashFunction hashFunction = HashFunction.SHA1; // default
        String algorithm = map.get("algorithm");
        for (HashFunction hash : HashFunction.values()) {
            if (algorithm == null)
                break; // if algorithm not in URI, resolve to default
            if (algorithm.toUpperCase().contains(hash.toString())) {
                hashFunction = hash;
                break;
            }
        }

        long intervalTOTP_OR_counterHOTP = 30; // default for TOTP = 30
        if (otpType == TOTP)
            try { intervalTOTP_OR_counterHOTP = Long.parseLong(map.get("period")); } catch (NumberFormatException ignored) {	}
        else // HOTP
            intervalTOTP_OR_counterHOTP = (map.get("counter") != null) ? Long.parseLong(map.get("counter")) : 0; // default counter = 0


        return new Token(issuer, account, secretKey, otpType, intervalTOTP_OR_counterHOTP, digits, hashFunction);
    }

    // ----------------- PRIVATE CODE FOR CREATING QRCODE
    static private String generateURIFromToken(Token token) {
        String uri = "otpauth://" + token.otpType.toString().toLowerCase() + "/" +
                token.account + "?" +
                "secret=" + token.secretKey + "&" +
                "issuer=" + token.issuer + "&" +
                "algorithm=" + token.hashFunction.toString() + "&" +
                "digits=" + token.digits + "&" +
                (token.otpType == TOTP ? "period=" : "counter=") + token.intervalTOTP_OR_counterHOTP;
        return uri;
    }

    static private BufferedImage generateQRCodeFromURI(String uri, int width, int height) throws WriterException {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(uri, BarcodeFormat.QR_CODE, width, height);
        return MatrixToImageWriter.toBufferedImage(bitMatrix);

    }

}
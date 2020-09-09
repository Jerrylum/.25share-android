package com.github.jerrylum.quartershare;

public class Util {

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public static byte[] joinByteArray(byte[] one, byte[] two) {
        byte[] combined = new byte[one.length + two.length];

        System.arraycopy(one,0, combined,0, one.length);
        System.arraycopy(two,0, combined, one.length, two.length);

        return  combined;
    }

    public static String trimMessage(String raw){
//        String rtn = "";
//
//        char lastChar = ' ';
//        for (int i = 0; i < raw.length(); i++) {
//            char nowChar = raw.charAt(i);
//
//            switch (lastChar) { // Important: be careful
//                case '，':
//                case '。':
//                case '、':
//                case '：':
//                case '；':
//                case '？':
//                case '！':
//                    if (nowChar == ' ')
//                        break;
//                default:
//                    rtn += nowChar;
//                    break;
//            }
//
//            lastChar = nowChar;
//        }
//
//        return rtn;
        return raw.replaceAll("\\s", "");
    }

}

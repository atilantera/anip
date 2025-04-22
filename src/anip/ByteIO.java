package anip;

/**
 * Conversions between integer - byte array and signed - unsigned
 * byte values.
 * 
 * @author Artturi Tilanterä
 *
 */

public class ByteIO {
    
    /**
     * Converts four bytes into an integer value.
     * Returns zero if the value represented by bytes is signed.
     * 
     * @param bytes array containing the bytes.
     * @param offset beginning index of the four byte sequence.
     * 
     * @return bytes converted to single integer value.
     */
    public static int bytesToInt(byte[] bytes, int offset) {
        int value = unsign(bytes[offset]) +
                    (unsign(bytes[offset + 1]) << 8) +
                    (unsign(bytes[offset + 2]) << 16) +
                    ((bytes[offset + 3] & 127) << 24);
        if (bytes[offset + 3] > 127) {
            value = 0;
        }
        return value;
    }
    
    /**
     * Converts an integer value into sequence of four bytes.
     * Returns zero if the value represented by bytes is signed.
     * 
     * @param value integer value.
     * @param bytes array containing the bytes.
     * @param offset beginning index of the four byte sequence.
     */
    public static void intToBytes(int value, byte[] bytes,
            int offset) {
        if (value < 0) {
            bytes[offset] = 0;
            bytes[offset + 1] = 0;
            bytes[offset + 2] = 0;
            bytes[offset + 3] = 0;
        } else {
            bytes[offset] = sign((value & 0xFF));
            bytes[offset + 1] = sign((value & 0xFF00) >> 8);
            bytes[offset + 2] = sign((value & 0xFF0000) >> 16);
            bytes[offset + 3] = sign((value & 0xFF000000) >> 24);
        }
    }    
    
    /**
     * Converts a byte with value -128..+127 to unsigned value
     * 0..255.
     * 
     * @param b the byte to be converted.
     * @return unsigned value.
     */
    
    public static short unsign(byte b) {
        if (b < 0) {
            return (short)(b + 256);
        } else {
            return b;
        }
    }
    
    /**
     * Converts a short with value 0..255 to byte with value
     * -128..127.
     * 
     * @param s the short value to be converted.
     * @return signed byte value.
     */
    
    public static byte sign(int s) {
        if (s > 127) {
            return (byte)(s - 256);
        } else {
            return (byte)s;
        }
    }  
}

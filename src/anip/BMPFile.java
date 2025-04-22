package anip;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;


/**
 * BMP file input and output handling.
 * 
 * @author Artturi Tilanterä
 *
 */

public class BMPFile {

    private final static int BMP_HEADER_SIZE = 54;
    private final static int DIB_HEADER_SIZE = 40;
    
    /**
     * Reads a 24-bit BMP file into memory.
     * 
     * @param image buffer to store the image data.
     * @param fileName name of the file to be read.
     * @throws IOException if the file is not a 24-bit BMP file or
     * cannot be read to the end. Further description is in
     * .getMessage() of the exception.
     */
    
    public static void read(Bitmap image, String fileName) 
        throws IOException {
        
        // A buffered stream is used to read the data, because
        // pixels are read line by line (to preserve memory
        // if the image is of very high resolution), and one line
        // can be even less than one kilobyte.
        FileInputStream fileStream = new FileInputStream(fileName);
        BufferedInputStream stream = new
            BufferedInputStream(fileStream);
        
        int bytesRead = 0;
        
        // Read and check BMP header
        byte[] header = new byte[BMP_HEADER_SIZE];        
        bytesRead = stream.read(header, 0, BMP_HEADER_SIZE);
        if (bytesRead != BMP_HEADER_SIZE) {
            throw new IOException("File is corrupt or cannot " +
                    "be read");
        }
        
        // A BMP file begins with magic number 4D 42.
        if (!(header[0] == 0x42 && header[1] == 0x4D)) {
            throw new IOException("File is not a BMP file");
        }        
        
        // Although image width and height are stored as 4-byte
        // integers, our version can handle only positive values
        // that can be fit into 2-byte short type variable.
        int width = ByteIO.bytesToInt(header, 18);
        int height = ByteIO.bytesToInt(header, 22);
        if (width < 1 || width > 32767 || height < 1 ||
                height > 32767) {
            throw new IOException("Image width and height must be" +
                    " in the range 1..32767, was " + width + " x " +
                    height);
        }
        
        // Check for most significantly that the bit depth is 24.
        // Number of color planes and compression type should
        // be according to the bit depth, but if they're not,
        // the file might be corrupt.
        if (!(header[26] == 1 && header[27] == 0)) {
            throw new IOException("Number of color planes must be" +
                    " 1 - file is corrupt or wrong type");
        }
        if (!(header[28] == 24 && header[29] == 0)) {
            throw new IOException("Bit depth must be 24");
        }
        if (ByteIO.bytesToInt(header, 30) != 0) {
            throw new IOException("Compression must be \"no " +
                    "compression\" - file is corrupt or " +
                    "wrong type");
        }
        
        // Reserve memory for pixel data
        image.resize((short)width, (short)height, (byte)3);
        
        // Read pixel data. Pixels in 24-bit BMP file are
        // listed from top to bottom and from left to right,
        // in order BGR BGR BGR. Every vertical pixel line
        // is stored in a sequence called scanline whose length
        // must be multiple of four.
        int scanlineLength = width * 3;
        if (scanlineLength % 4 != 0) {
            scanlineLength += 4 - scanlineLength % 4;
        }
        byte[] scanline = new byte[scanlineLength];
        
        int yOffset = (height - 1) * width * 3;
        int xOffset;
        int x;
        for (int y = 0; y < height; y++, yOffset -= width * 3) {
            bytesRead = stream.read(scanline, 0, scanlineLength);
            if (bytesRead != scanlineLength) {
                throw new IOException("File is corrupt");
            }
            
            for (x = 0, xOffset = 0; x < width; x++, xOffset += 3) {
                image.pixels[yOffset + xOffset] =
                    ByteIO.unsign(scanline[xOffset + 2]);
                
                image.pixels[yOffset + xOffset + 1] =
                    ByteIO.unsign(scanline[xOffset + 1]);
                
                image.pixels[yOffset + xOffset + 2] =
                    ByteIO.unsign(scanline[xOffset]);
            }            
        }
       
        fileStream.close();
    }

    /**
     * Writes 24-bit BMP file onto disk.
     * 
     * @param image buffer to store the image data.
     * @param fileName name of the file to be written.
     * @throws IOException if the file cannot be written.
     * Further description is in .getMessage() of the exception.
     */
    
    public static void write(Bitmap image, String fileName)
        throws IOException {
        
        if (image.width < 1 || image.height < 1) {
            throw new IOException("Image height and/or width " +
                    "is negative! (" + image.width + " x " +
                    image.height + ")");
        }
        if (image.depth != 3) {
            throw new IOException("Image depth must be 3 bytes = 24 bits");
        }
        
        // A buffered stream is used to write the data for the
        // same reason as in method read().
        FileOutputStream fileStream = new FileOutputStream(fileName);
        BufferedOutputStream stream = new
            BufferedOutputStream(fileStream);
        
        // Write BMP header
        byte[] header = new byte[BMP_HEADER_SIZE];
        
        // Magic number
        header[0] = 0x42;
        header[1] = 0x4D;
        
        // BMP file size 
        int scanlineLength = image.width * 3;
        if (scanlineLength % 4 != 0) {
            scanlineLength += 4 - scanlineLength % 4;
        }
        int bmpFileSize = BMP_HEADER_SIZE +
            scanlineLength * image.height; 
        ByteIO.intToBytes(bmpFileSize, header, 2);
        
        // Reserved
        header[6] = 0;
        header[7] = 0;
        header[8] = 0;
        header[9] = 0;
        
        // Address of bitmap data
        ByteIO.intToBytes(BMP_HEADER_SIZE, header, 10);
        
        // Size of information header (DIB header)
        ByteIO.intToBytes(DIB_HEADER_SIZE, header, 14);
        
        // Width and height of the image
        ByteIO.intToBytes(image.width, header, 18);
        ByteIO.intToBytes(image.height, header, 22);
        
        // Number of color planes = 1
        header[26] = 1;
        header[27] = 0;
        
        // Bit depth
        header[28] = 24;
        header[29] = 0;
        
        // Compression method. 0 = no compression
        header[30] = 0;
        header[31] = 0;
        header[32] = 0;
        header[33] = 0;
        
        // Size of image data
        ByteIO.intToBytes(scanlineLength * image.height, header, 34);
        
        // Vertical and horizontal resolution.
        // 2835 pixels per meter = 72 dots per inch
        ByteIO.intToBytes(2835, header, 38);
        ByteIO.intToBytes(2835, header, 42);
        
        // Number of colors in the palette
        ByteIO.intToBytes(0, header, 46);
        
        // Number of important colors
        ByteIO.intToBytes(0, header, 50);
        
        stream.write(header);
        
        // Pixel data
        int height = image.height;
        int width = image.width;
        byte[] scanline = new byte[scanlineLength];        
        int yOffset = (height - 1) * width * 3;
        int xOffset;
        int sourceIndex;
        int x;
        int y;
        
        int bytesWritten = BMP_HEADER_SIZE;
        
        for (y = 0; y < height; y++) {
            
            xOffset = 0;
            sourceIndex = yOffset;
            for (x = 0; x < width; x++) {
                
                scanline[xOffset + 2] =
                    ByteIO.sign(image.pixels[sourceIndex]);
                sourceIndex++;
                
                scanline[xOffset + 1] =
                    ByteIO.sign(image.pixels[sourceIndex]);
                sourceIndex++;
                
                scanline[xOffset] =
                    ByteIO.sign(image.pixels[sourceIndex]);
                sourceIndex++;
                
                xOffset += 3;
            }            
            stream.write(scanline);
            bytesWritten += scanline.length;
            yOffset -= width * 3;
        }
        stream.flush();
        fileStream.close();
        
    } 
}

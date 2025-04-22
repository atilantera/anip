package anip.tools;

import anip.BMPFile;
import anip.Bitmap;

/**
 * An extra tool to convert 24-bit fullcolor RGB image to 18-bit fullcolor
 * with 6 bits per component { r, g, b }.
 * @author alkim
 *
 */

public class MakeSixBit {

    public static void main(String[] args) throws Exception {
	String infile = "/home/alkim/koodit/eclipse-workspace/anip/testidata/haruhi0.bmp";
	String outfile = "/home/alkim/koodit/eclipse-workspace/anip/testidata/haruhi0_15bit.bmp";

	Bitmap image = new Bitmap();
        
        System.out.println("reading file");
	BMPFile.read(image, infile);
        System.out.println("posterizing");
        posterize(image);
        System.out.println("writing file");
	BMPFile.write(image, outfile);
        System.out.println("done");
    }
    
    private static void posterize(Bitmap image) {
        int i;
        int endI = image.width * image.height * image.depth;
        for (i = 0; i < endI; i++) {
            image.pixels[i] = (short)(image.pixels[i] & 0xF8);
        }
    }
}

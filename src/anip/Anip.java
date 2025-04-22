package anip;

import java.io.IOException;
import java.io.File;
import java.io.FileNotFoundException;
import anip.gui.PlayerWindow;

/**
 * Main class of the anip video codec-player program.
 * 
 * @author Artturi Tilanterä
 *
 */

public class Anip {

    private String bmpNamePrefix;
    private int bmpNameZeros;
    private String bmpNamePostfix;
    
    /**
     * Starts the program.
     * 
     * @param args command line arguments.
     */
    public static void main(String[] args) {
        Anip anip = new Anip();
        anip.parseArgs(args);
    }
       
    /**
     * Handles the command-line arguments.
     * 
     * @param args the same arguments that main() got.
     */
    
    private void parseArgs(String[] args) {
        
        boolean displayHelp = false;
        
        if (args.length > 0) {
            String command = args[0];
            if (command.compareTo("c") == 0 && args.length == 4) {
                
                // Create a new animation file
                String animationFileName = args[1];
                float frameRate = 0;
                boolean invalidFrameRate = false;
                try {
                    frameRate = Float.parseFloat(args[2]);
                } catch (NumberFormatException e) {
                    invalidFrameRate = true;
                    System.out.println("Invalid frame rate! " +
                            "Frame rate can be a whole number like 25 or " +
                            "a decimal number like 29.97.");                    
                }
                if (!invalidFrameRate) {
                    if (frameRate <= 0) {
                        System.out.println("Invalid frame rate! " +
                                "Frame rate must be positive.");
                        invalidFrameRate = true;
                    }                    
                }
                if (!invalidFrameRate) {
                    if (!this.parseFileNameTemplate(args[3])) {
                        displayHelp = true;
                    } else {
                        createAnimation(animationFileName, frameRate);
                    }                    
                }                
                
            } else if (command.compareTo("x") == 0 && args.length >= 3 &&
                    args.length <= 5) {
                
                // Extract images from an existing animation file
                String animationFileName = args[1];
                
                if (!this.parseFileNameTemplate(args[2])) {
                    displayHelp = true;
                } else {
                    
                    int firstFrame = 0;
                    int lastFrame = -1;
                    if (args.length >= 4) {
                        if (!args[3].matches("[0-9]*")) {
                            displayHelp = true;
                        } else {
                            firstFrame = Integer.parseInt(args[3]);                              
                        }
                    }
                    if (!displayHelp && args.length == 5) {
                        if (!args[4].matches("[0-9]+")) {
                            displayHelp = true;
                        } else {
                            lastFrame = Integer.parseInt(args[4]);
                            if (lastFrame < firstFrame) {
                                displayHelp = true;
                            }
                        }
                    } 
                    if (!displayHelp) {
                        try {
                            extractAnimation(animationFileName, firstFrame,
                                lastFrame);
                        } catch (Exception e) {
                            System.out.println(e);
                        }
                    }
                }  
                
            } else if (args.length == 1) {                
                // Play animation file on a window
                try {
                    playAnimation(command);
                } catch (Exception e) {
                    System.out.println(e);                    
                }
            } else {
                displayHelp = true;
            }
        } else {
            displayHelp = true;
        }
        
        if (displayHelp) {
            System.out.println("anip - the video codec-player.\n");
            System.out.println("Usage:");
            System.out.println("anip c animation.ap N.n image0000.bmp ");
            System.out.println("\tCreates a new animation file from a " +
                    "sequence of image files. N.n is the frame rate of " +
                    "the animation. 0000 indicates the number " +
                    "of leading zeros in the image file names.\n");
            
            System.out.println("anip x animation.ap image0000.bmp [A [B]]");
            System.out.println("\tExtracts a sequence of images from an " +
                        "existing animation file. 0000 indicates the " +
                    "number of leading zeros in the image file names. " + 
                    "If A or A and B are be specified, A is the number of " +
                    "the first frame to be extracted and B is the last " +
                    "one.\n");
            
            System.out.println("anip animation.ap");
            System.out.println("\tPlays an animation file on a window.");
        }
       
    }

    /**
     * Parses file name template got as command-line parameter
     * to rule to create new file names with generateFileName(..).
     * 
     * @param param template string. For example path/image000.bmp.
     * @return whether parameter string was valid.
     */
    private boolean parseFileNameTemplate(String param) {
        bmpNamePrefix = new String();
        bmpNameZeros = 0;
        bmpNamePostfix = new String();  
        if (!param.matches(".*[^0]+0+[^0]+")) {
            return false;
        }                   
        int pos = 0;
        // File name may contain path. Skip to after
        // last occurrence of '/' or '\\'.
        int lastIndex = param.lastIndexOf('/');
        if (lastIndex != -1) {
            bmpNamePrefix += param.substring(0, lastIndex + 1);
            pos += lastIndex + 1;
        }
        lastIndex = param.lastIndexOf('\\');
        if (lastIndex > pos) {
            bmpNamePrefix += param.substring(pos, lastIndex + 1);
            pos = lastIndex;
        }                        
        while (pos < param.length() &&
                param.charAt(pos) != '0') {
            bmpNamePrefix += param.charAt(pos);
            pos++;
        }
        while (pos < param.length() &&
                param.charAt(pos) == '0') {
            bmpNameZeros++;
            pos++;
        }
        while (pos < param.length() &&
                param.charAt(pos) != '0') {
            bmpNamePostfix += param.charAt(pos);
            pos++;
        }
        return true;
    }
    
    /**
     * Generates a file name string that contains a number and
     * leading zeros.
     * 
     * @param number an integer value to be in the file name.
     * @return the string generated, a file name.
     */
    
    private String generateFileName(int number) {

        String fileName = new String(bmpNamePrefix);
        String numbers = Integer.toString(number);
        int zeroCount = bmpNameZeros - numbers.length();
        if (zeroCount < 0) {
            zeroCount = 0;
        }
        for (int i = 0; i < zeroCount; i++) {
            fileName += "0";
        }
        fileName += numbers;
        fileName += bmpNamePostfix;
        return fileName;
    }
    
    /**
     * Creates new animation file (AP-file) from a series of BMP
     * images.
     * 
     * @param animationFileName name of the animation file to be created.
     * @param frameRate animation playing speed in frames per second.
     */
    private void createAnimation(String animationFileName, float frameRate) {

        APEncoder encoder = new APEncoder();        
        
        System.out.println("Creating new video file: " + animationFileName);
        
        try {
            encoder.setOptions(frameRate, 7);
            encoder.setFile(animationFileName);
        } catch (IOException e) {
            System.out.println("Error in creating video file: "
                    + e.getMessage());
            return;
        }

        Bitmap image = new Bitmap();
        String fileName;
        File testFile;
        
        // Read image files and encode them as frames into video file
        
        for (int i = 0; ; i++) {
            fileName = generateFileName(i);
            testFile = new File(fileName);
            if (!testFile.exists()) {
                System.out.println(fileName + " does not exist. End.");
                break;
            }
        
            try {
                BMPFile.read(image, fileName);
            } catch (Exception e) {
                System.out.println("Error in creating animation:");
                e.printStackTrace(System.out);
                break;
            }
            
            System.out.println(fileName);
            try {
                encoder.putImage(image);
            } catch (Exception e) {
                System.out.println("Error in creating animation:");
                e.printStackTrace(System.out);
                break;
            }
        }
                
        try {
            encoder.close();
        } catch (IOException e) {

        }        
    }
    
    /**
     * Extract a series of BMP images from an existing animation file
     * (AP-file).
     * 
     * @param animationFileName name of the animation file to be extracted
     * from.
     * @param firstFrame number of the first frame to extract. 0 is the first.
     * @param lastFrame number of the last frame to extract. Value -1 means
     * extract all frames to the end.
     */
    private void extractAnimation(String animationFileName, int firstFrame,
            int lastFrame) throws Exception {

        System.out.println("Extracting images from video file: " +
                animationFileName);
        
        APDecoder decoder = new APDecoder();

        try {
            decoder.openFile(animationFileName);
        } catch (IOException e) {
            System.out.println("Error in opening file: " + e.getMessage());
            throw e;
        }

        int length = decoder.getLength();
        
        if (lastFrame == -1) {
            lastFrame = length - 1;
        }
        if (firstFrame < 0 || firstFrame > length - 1 ||
                lastFrame < firstFrame || lastFrame > length - 1) {
            System.out.println("Error: number of the first frame must be " +
                        "less or equal to the number of last frame, and " +
                        "both of them must be in the range " +
                        "[0, length - 1]. Length of the video was " +
                        length + " frames.");
            throw new Exception();
        }

        Bitmap image = decoder.createFrameBufferBitmap();
        String fileName;
        
        // Add leading zeros to the image file name template if needed
        int log = Anip.log10(lastFrame);
        if (log > bmpNameZeros) {
            bmpNameZeros = log;
        }
        
        // Decode frames and write them as image files
	int i = 0;
	if (firstFrame > 0) {
	    System.out.println("Seeking to frame " + firstFrame + "...");
	}
	for (; i < firstFrame; i++) {
	    // TODO: this loop should be changed to a single
	    // call of decoder.seek(i) when APDecoder.seek()
	    // is implemented properly.
	    try {
		decoder.getImage(image);
	    } catch (Exception e) {
		System.out.println("Error in decoding video: ");
		e.printStackTrace(System.out);
		break;
	    }
	}
        for (i = firstFrame; i <= lastFrame; i++) {
            fileName = generateFileName(i);
            System.out.println(fileName);
            try {
                decoder.getImage(image);
            } catch (Exception e) {
                System.out.println("Error in extracting image files: ");
                e.printStackTrace(System.out);
                break;
            }
            BMPFile.write(image, fileName);
        }        
        
        try {
            decoder.closeFile();
        } catch (IOException e) {

        }
    }
    
    /**
     * Plays contents of an animation file.
     * 
     * @param animationFileName name of the animation file to be played.
     */
    private void playAnimation(String animationFileName) throws IOException {
        
        APDecoder decoder = new APDecoder();       
        
        try {
            decoder.openFile(animationFileName);
        } catch (IOException e) {
            System.out.println("Error in opening file: " + e.getMessage());
            throw e;
        }
        
        int[] frameBuffer = new int[decoder.getFrameBufferArraySize()];
        long interval = (long)(1000000000 / decoder.getSpeed());
        int frameNum = 0;
        int frameCount = decoder.getLength();
        Sleeper sleeper = new Sleeper(interval);
        
        PlayerWindow w = new PlayerWindow(decoder.getFrameWidth(),
                                          decoder.getFrameHeight(),
					  decoder.getBufferWidth(),
					  decoder.getBufferHeight());
        w.pack();                
        w.setVisible(true);
        int bufferWidth = decoder.getBufferWidth();
        int bufferHeight = decoder.getBufferHeight();
        
        while (true) {
            sleeper.measureWorkingStart();
            try {
                decoder.getImage(frameBuffer);                    
            } catch (Exception e) {
                System.out.println("Error in playing animation:");
                e.printStackTrace(System.out);
                break;
            }
            w.drawImage(frameBuffer);
            sleeper.sleep();       
            frameNum++;
            if (frameNum == frameCount) {
                frameNum = 0;
                decoder.seek(0);
            }
        }
          
        try {
            decoder.closeFile();
        } catch (IOException e) {
        }        
    }
    
    /**
     * Calculates the 10-based logarithm of an integer.
     *  
     * @param x value of which the logarithm is calculated.
     * @return 10-based logarithm rounded up.
     */
    private static int log10(int x) {
        int power = 0;
        while (x != 0) {
            x /= 10;
            power++;
        }
        return power;
    }
    
}

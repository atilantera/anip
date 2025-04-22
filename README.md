# anip

## Description

Java-based video codec for cel animation.

![Screenshot](media/anip.png)

`anip` (short for "animaation pakkaaja", i.e., "animation compressor" in
English) is a Java-based video codec for cel animation. I made it on 2008 as
an individual project work for an object-oriented programming course, when I
was an undergraduate student at Helsinki University of Technology (now
Aalto University).

## Demo

Download the source code and a Java runtime, e.g.
[Oracle Java](https://www.java.com/en/) or
[OpenJDK](https://openjdk.org/index.html).

After the setup above, try:
```
java -jar anip.jar media/skewb.ap
```

That should play an animation where a hand rotates a
[Skewb puzzle cube](https://en.wikipedia.org/wiki/Skewb).

## Usage

```
anip c animation.ap N.n image0000.bmp 
	Creates a new animation file from a sequence of image files. N.n is the frame rate of the animation. 0000 indicates the number of leading zeros in the image file names.

anip x animation.ap image0000.bmp [A [B]]
	Extracts a sequence of images from an existing animation file. 0000 indicates the number of leading zeros in the image file names. If A or A and B are be specified, A is the number of the first frame to be extracted and B is the last one.

anip animation.ap
	Plays an animation file on a window.

```



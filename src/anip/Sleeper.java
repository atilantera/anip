package anip;

/**
 * <p>
 * In-thread delayer that calls java.lang.Thread.sleep() manually after every
 * working perioid trying to keep durations of cycles consisting of a working
 * and a sleeping perioid equal. Accuracy in millisecods.
 * </p>
 *
 * <p>Usage: first set interval at the constructor or with .setInterval().
 * Then the Sleeper can be used in a working loop like this:
 * <pre>
 * Sleeper sleepy(50);
 * while (continueLoop) {
 *     sleepy.measureWorkingStart();
 *     // do the work
 *     sleepy.sleep();
 * }
 * </pre>
 */

public class Sleeper {

    /** Cycle length in nanosecods. */
    private long interval;

    /** Start time of the current working perioid. */
    private long workStartTime;

    /** Length of the current working perioid. */
    private long workDuration;

    /**
     * Constructs new Sleeper with given cycle length.
     *
     * @param interval cycle length in nanoseconds.
     */
    public Sleeper(long interval) {
	if (interval > 0) {
	    this.interval = interval;
	} else {
	    this.interval = 100000000;
	}
    }

    /**
     * Sets the cycle length.
     *
     * @param interval cycle length in milliseconds.
     */
    public void setInterval(long interval) {
	if (interval > 0) {
	    this.interval = interval;
	}
    }

    /**
     * Announces Sleeper to begin measuring the duration of the current
     * working perioid.
     */
    public void measureWorkingStart() {
	workStartTime = System.nanoTime();
    }

    /**
     * Causes current thread to sleep a time that is left of the current cycle
     * after the working perioid. If the working perioid had gone overtime,
     * no sleeping is caused.
     */
    public void sleep() {
	workDuration = System.nanoTime() - workStartTime;
	long sleepingTotal = interval - workDuration;
	if (sleepingTotal > 0) {
	    long sleepingMillisec = sleepingTotal / 1000000;
	    int sleepingNanosec = (int)(sleepingTotal -
                                         sleepingMillisec * 1000000);
            try {
                Thread.sleep(sleepingMillisec, sleepingNanosec);
            } catch (InterruptedException e) {
                
            }
	}
    }
}


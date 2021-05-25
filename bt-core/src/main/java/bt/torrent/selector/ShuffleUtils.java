package bt.torrent.selector;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * A class that has shuffle utility functions
 */
public class ShuffleUtils {

    public static void shuffle(int[] arr) {
        shuffle(arr, ThreadLocalRandom.current());
    }

    /**
     * Shuffle the passed in int array in-place with the specified Random
     *
     * @param arr the array to shuffle
     * @param rnd the instance of random to use
     */
    public static void shuffle(int[] arr, Random rnd) {
        for (int k = arr.length - 1; k > 0; k--) {
            int i = rnd.nextInt(k + 1);
            int a = arr[i];
            arr[i] = arr[k];
            arr[k] = a;
        }
    }
}

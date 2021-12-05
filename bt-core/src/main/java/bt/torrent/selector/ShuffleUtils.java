/*
 * Copyright (c) 2016—2021 Andrei Tomashpolskiy and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.kad.utils;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import lbms.plugins.mldht.kad.DHT;
import lbms.plugins.mldht.kad.DHT.LogLevel;
import lbms.plugins.mldht.kad.Key;
import lbms.plugins.mldht.kad.Prefix;
import lbms.plugins.mldht.utils.ExponentialWeightendMovingAverage;

/**
 * @author The_8472, Damokles
 *
 */
public class PopulationEstimator {

	static final int					KEYSPACE_BITS					= Key.KEY_BITS;
	static final double					KEYSPACE_SIZE					= Math.pow(2, KEYSPACE_BITS);
	
	
	static final double					DISTANCE_WEIGHT_INITIAL			= 0.3;
	static final double					DISTANCE_WEIGHT					= 0.003;
	private int							updateCount						= 0;
	static final int					INITIAL_UPDATE_COUNT			= 25;
	
	
	static final int					MAX_RAW_HISTORY					= 40;
	LinkedList<Double> 					rawDistances					= new LinkedList<>();

	
	private ExponentialWeightendMovingAverage						errorEstimate = new ExponentialWeightendMovingAverage().setWeight(0.03).setValue(0.5);
	private ExponentialWeightendMovingAverage						averageNodeDistanceExp2 = new ExponentialWeightendMovingAverage().setValue(1);
	private List<PopulationListener>	listeners						= new ArrayList<>(1);
	private static final int			MAX_RECENT_LOOKUP_CACHE_SIZE	= 40;
	private Deque<Prefix>				recentlySeenPrefixes			= new LinkedList<>();

	public long getEstimate () {
		return (long) (Math.pow(2, averageNodeDistanceExp2.getAverage()));
	}
	
	public double getStability() {
		return 1.0 - Math.abs(errorEstimate.getAverage());
	}
	
	public double getRawDistanceEstimate() {
		return averageNodeDistanceExp2.getAverage();
	}

	public void setInitialRawDistanceEstimate(double initialValue) {
		if(initialValue > KEYSPACE_BITS)
			averageNodeDistanceExp2.setValue(1);
		else
			averageNodeDistanceExp2.setValue(initialValue);
	}
	
	public static double distanceToDouble(Key a, Key b) {
		byte[] rawDistance = a.distance(b).getHash();
		double distance = 0;
		
		int nonZeroBytes = 0;
		for (int j = 0; j < Key.SHA1_HASH_LENGTH; j++) {
			if (rawDistance[j] == 0) {
				continue;
			}
			if (nonZeroBytes == 8) {
				break;
			}
			nonZeroBytes++;
			distance += (rawDistance[j] & 0xFF)
					* Math.pow(2, KEYSPACE_BITS - (j + 1) * 8);
		}
		
		return distance;
	}

	double toLog2(double value)
	{
		return 160 - Math.log(value)/Math.log(2);
	}
	
	long estimate(double avg) {
		return (long) (Math.pow(2, avg ));
	}
	
	
	private double median(List<Double> distances)
	{
		if(distances.size() == 1)
			return distances.get(0);
		double values[] = new double[distances.size()];
		int i=0;
		for(Double d : distances)
			values[i++] = d;
		Arrays.sort(values);
		// use a weighted 2-element median for max. accuracy
		double middle = (values.length - 1.0) / 2.0 ;
		int idx1 = (int) Math.floor(middle);
		int idx2 = (int) Math.ceil(middle);
		double middleWeight = middle - idx1;
		return values[idx1] * (1.0 - middleWeight) + values[idx2] * middleWeight;
	}
	
	private double mean(double[] values)
	{
		double result = 0.0;
		for(int i=0;i<values.length;i++)
			result += values[i];
		return result / values.length;
	}
	
	public void update (Set<Key> neighbors, Key target) {
		// need at least 2 elements to calculate distances
		if(neighbors.size() < 2)
			return;
		
		DHT.log("Estimator: new node group of "+neighbors.size(), LogLevel.Debug);
		Prefix prefix = Prefix.getCommonPrefix(neighbors);
		
		synchronized (recentlySeenPrefixes)
		{
			for(Prefix oldPrefix : recentlySeenPrefixes)
			{
				if(oldPrefix.isPrefixOf(prefix))
				{
					/*
					 * displace old entry, narrower entries will also replace
					 * wider ones, to clean out accidents like prefixes covering
					 * huge fractions of the keyspace
					 */
					recentlySeenPrefixes.remove(oldPrefix);
					recentlySeenPrefixes.addLast(prefix);
					return;
				}
				// new prefix is wider than the old one, return but do not displace
				if(prefix.isPrefixOf(oldPrefix))
					return;
			}

			// no match found => add
			recentlySeenPrefixes.addLast(prefix);
			if(recentlySeenPrefixes.size() > MAX_RECENT_LOOKUP_CACHE_SIZE)
				recentlySeenPrefixes.removeFirst();
		}
		
		
		ArrayList<Key> found = new ArrayList<>(neighbors);
		//found.add(target);
		Collections.sort(found,new Key.DistanceOrder(target));

		synchronized (PopulationEstimator.class)
		{
			
			List<Double> distances = new LinkedList<>();

			for(int i=1;i<found.size();i++)
			{
				distances.add(distanceToDouble(target, found.get(i)) - distanceToDouble(target, found.get(i-1)));
				//distances.add(distanceToDouble(found.get(i-1), found.get(i)));
				//distances.add(distanceToDouble(target, found.get(i))/(i+1.0));
				//distances.add(target.naturalDistance(found.get(i)));
			}
			
			//System.out.println(distances);

			// distances are exponentially distributed. since we're taking the median we need to compensate here
			double median = median(distances) / Math.log(2);

			// work in log2 space for better averaging
			median = toLog2(median);
			//143.39035952556318
			//143.255
			//0.135

			DHT.log("Estimator: distance value: " + median + " avg:" + averageNodeDistanceExp2, LogLevel.Debug);
			
			double absArror = Math.abs(errorEstimate.getAverage());
			double amplifiedError = Math.pow(absArror, 1.5);
			double clampedError = Math.max(0, Math.min(1, amplifiedError));


			double weight = 0.0001 + clampedError * 0.3 ;   //updateCount++ < INITIAL_UPDATE_COUNT ? DISTANCE_WEIGHT_INITIAL : DISTANCE_WEIGHT;
			//double weight = 0.001;
			
			double oldAverage = averageNodeDistanceExp2.getAverage();

			// exponential average of the mean value
			averageNodeDistanceExp2.setWeight(weight).updateAverage(median);
			
			double newAverage = averageNodeDistanceExp2.getAverage();
			
			//System.out.print("update: "+ Math.pow(2, KEYSPACE_BITS - median)+" ");
			
			errorEstimate.updateAverage((median - newAverage) / Math.min(median, newAverage));
			
			
			while(rawDistances.size() > MAX_RAW_HISTORY)
				rawDistances.remove();
		}
		DHT.log("Estimator: new estimate:"+getEstimate()+" raw:"+averageNodeDistanceExp2.getAverage()+" error:"+errorEstimate.getAverage(), LogLevel.Info);
		
		fireUpdateEvent();

	}

	

	public void addListener (PopulationListener l) {
		listeners.add(l);
	}

	public void removeListener (PopulationListener l) {
		listeners.remove(l);
	}

	private void fireUpdateEvent () {
		long estimated = getEstimate();
		for (int i = 0; i < listeners.size(); i++) {
			listeners.get(i).populationUpdated(estimated);
		}
	}
	

	
	public static void main(String[] args) throws Exception {
		NumberFormat formatter = NumberFormat.getNumberInstance(Locale.GERMANY);

		int keyspaceSize = 20000000;

		formatter.setMaximumFractionDigits(30);
		
		PopulationEstimator estimator = new PopulationEstimator();
		
		
		
		System.out.println(160-Math.log(keyspaceSize)/Math.log(2));
		
		Key[] keyspace = new Key[keyspaceSize];
		Runnable r = () -> {
			Arrays.parallelSetAll(keyspace, i -> Key.createRandomKey());
		};
		//Arrays.sort(keyspace);
		
		for(int i=0;i<100;i++)
		{
			if(i % 20 == 0)
				r.run();

			Key target = Key.createRandomKey();

			Arrays.parallelSort(keyspace, new Key.DistanceOrder(target));


			int sizeGoal = 8;

			TreeSet<Key> closestSet = new TreeSet<>();

			for(int j=0;j<sizeGoal;j++)
				closestSet.add(keyspace[j]);

			//estimator.update(closestSet);
			estimator.update(closestSet,target);
		}
		
	}
}

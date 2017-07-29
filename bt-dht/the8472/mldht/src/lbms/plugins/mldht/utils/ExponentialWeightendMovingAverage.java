/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package lbms.plugins.mldht.utils;

public class ExponentialWeightendMovingAverage {
	
	private double weight = 0.3;
	private double average = Double.NaN;
	
	public void updateAverage(double value) {
		if(Double.isNaN(average))
			average = value;
		else
			average = value * weight + average * (1.0 - weight);
	}
	
	public ExponentialWeightendMovingAverage setWeight(double weight) {
		this.weight = weight;
		return this;
	}
	
	public double getAverage() {
		return average;
	}
	
	public double getAverage(double defaultValue)
	{
		return Double.isNaN(average) ? defaultValue : average; 
	}
	
	public ExponentialWeightendMovingAverage setValue(double average) {
		this.average = average;
		return this;
	}
	
}

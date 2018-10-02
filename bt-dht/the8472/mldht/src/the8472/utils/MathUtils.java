package the8472.utils;

public class MathUtils {
	
	public static long roundToNearestMultiple(long num, long multiple) {
	    return multiple * ((num + multiple - 1) / multiple);
	}
	
	public static long ceilDiv(long num, long divisor){
	    return -Math.floorDiv(-num,divisor);
	}

}

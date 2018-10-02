package lbms.plugins.mldht.indexer.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import lbms.plugins.mldht.kad.utils.BitVector;

import java.nio.ByteBuffer;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.Test;

public class RotatingBloomFilterTest {
	
	@Test
	public void testAutorotation() {
		int num = 512*1024;
		
		RotatingBloomFilter filter = new RotatingBloomFilter(num , 0.001f);
		filter.setAutoRotate(true);
		byte[] buf = new byte[32];
		for(int i=0;i<num-1;i++) {
			ThreadLocalRandom.current().nextBytes(buf);
			filter.insert(ByteBuffer.wrap(buf));
		}
		int contains = 0;
		for(int i=0;i<10000;i++) {
			ThreadLocalRandom.current().nextBytes(buf);
			if(filter.contains(ByteBuffer.wrap(buf))) {
				contains += 1;
			}
		}
		
		assertTrue(contains < 20);
		assertEquals(0, filter.rotations);
		
		for(int i=0;i<2000;i++) {
			ThreadLocalRandom.current().nextBytes(buf);
			filter.insert(ByteBuffer.wrap(buf));
		}
		assertEquals(1, filter.rotations);
		
	}
	
	@Test
	public void testBitvector() {
		byte[] array = new byte[16];
		array[0] = 0b0000_1000;
		array[1] = 0b0100_0000;
		BitVector bv = new BitVector(128, array);
		assertEquals(0b1000_0100, bv.rangeToInt(4, 8));
		
		array[0] = (byte) 0b1111_1000;
		array[1] = 0b0100_1111;
		bv = new BitVector(128, array);
		assertEquals(0b1000_0100, bv.rangeToInt(4, 8));

		array[0] = (byte) 0b1111_1001;
		array[1] = 0b0100_1111;
		bv = new BitVector(128, array);
		assertEquals(0b1_0010_1001, bv.rangeToInt(4, 9));
		
		
		
	}

}

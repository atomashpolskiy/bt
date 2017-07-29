/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.bt;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import lbms.plugins.mldht.kad.utils.ThreadLocalUtils;

public class MetadataPool {
	

	public static enum Completion {
		PROGRESS,
		SUCCESS,
		FAILED;
	}
	
	Completion state = Completion.PROGRESS;
	
	int length;
	ByteBuffer[] buffers;
	PullMetaDataConnection[]	requestees;
	Completion status = Completion.PROGRESS;
	
	public MetadataPool(int length ) {
		this.length = length;
		int numChunks = (int) Math.ceil(length * 1.0 / (16*1024));
		requestees = new PullMetaDataConnection[numChunks];
		buffers = new ByteBuffer[numChunks];
	}
	
	int reservePiece(PullMetaDataConnection req) {
		for(int i=0;i<requestees.length;i++) {
			if(requestees[i] != null)
				continue;
			requestees[i] = req;
			return i;
		}

		return -1;
	}
	
	void releasePiece(int idx) {
		requestees[idx] = null;
	}
	
	void addBuffer(int idx, ByteBuffer buf) {
		buffers[idx] = buf;
	}
	
	void deRegister(PullMetaDataConnection req) {
		for(int i=0;i<requestees.length;i++) {
			if(requestees[i] == req && buffers[i] == null)
				requestees[i] = null;
		}
	}
	
	void checkComletion(byte[] hash) {
		if(status != Completion.PROGRESS)
			return;
		List<ByteBuffer> bufs = Arrays.asList(buffers);
		if(bufs.stream().anyMatch(Objects::isNull)) {
			return;
		}
			
		
		MessageDigest hasher = ThreadLocalUtils.getThreadLocalSHA1();
		
		hasher.reset();
		bufs.forEach(b -> {
			b.rewind();
			hasher.update(b);
		});
		
		if(Arrays.equals(hasher.digest(), hash)) {
			status = Completion.SUCCESS;
			return;
		}
			
		status = Completion.FAILED;
	}
	
	public Completion status() {
		return status;
	}
	
	public int bytes() {
		return length;
	}
	
	public ByteBuffer merge() {
		if(status != Completion.SUCCESS)
			throw new IllegalStateException("there is nothing to merge");
		
		ByteBuffer buf = ByteBuffer.allocate(length);
		
		Arrays.asList(buffers).forEach(b -> {
			b.rewind();
			buf.put(b);
		});
		
		buf.rewind();
		
		return buf;
	}

}

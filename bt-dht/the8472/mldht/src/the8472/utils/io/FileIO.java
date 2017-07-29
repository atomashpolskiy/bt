/*******************************************************************************
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package the8472.utils.io;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

public class FileIO {

	public static void writeAndAtomicMove(Path targetName, Consumer<PrintWriter> write) throws IOException {
		Path tempFile = Files.createTempFile(targetName.getParent(), targetName.getFileName().toString(), ".tmp");
		
		try (PrintWriter statusWriter = new PrintWriter(Files.newBufferedWriter(tempFile, StandardCharsets.UTF_8))) {
			
			write.accept(statusWriter);
	
			statusWriter.close();
			Files.move(tempFile, targetName, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		}
	}

}

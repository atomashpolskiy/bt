package the8472.mldht.cli;

import java.util.List;
import java.util.Optional;

public class ParseArgs {
	
	public static boolean extractBool(List<String> args, String param) {
		return args.removeIf(param::equals);
	}
	
	public static Optional<String> extractString(List<String> args, String param) {
		int idx = args.indexOf(param);
		
		if(idx >= 0) {
			String val = args.remove(idx + 1);
			args.remove(idx);
			return Optional.of(val);
		}
		
		
		return Optional.empty();
	}

}

package the8472.mldht;

import java.util.Collection;

import lbms.plugins.mldht.kad.DHT;
import the8472.utils.ConfigReader;

public interface Component {
	
	void start(Collection<DHT> dhts, ConfigReader config);
	
	void stop();

}

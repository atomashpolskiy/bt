package lbms.plugins.mldht.kad;

public enum RPCState {
	UNSENT,
	SENT,
	STALLED,
	ERROR,
	TIMEOUT,
	RESPONDED
}
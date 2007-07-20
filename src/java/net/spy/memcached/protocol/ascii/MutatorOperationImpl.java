// Copyright (c) 2006  Dustin Sallings <dustin@spy.net>

package net.spy.memcached.protocol.ascii;

import java.nio.ByteBuffer;

import net.spy.memcached.ops.MutatatorOperation;
import net.spy.memcached.ops.OperationCallback;

/**
 * Operation for mutating integers inside of memcached.
 */
public class MutatorOperationImpl extends OperationImpl
	implements MutatatorOperation {

	public static final int OVERHEAD=32;

	private final Mutator mutator;
	private final String key;
	private final int amount;

	public MutatorOperationImpl(Mutator m, String k, int amt,
			OperationCallback c) {
		super(c);
		mutator=m;
		key=k;
		amount=amt;
	}

	@Override
	public void handleLine(String line) {
		getLogger().debug("Result:  %s", line);
		String found=null;
		if(!line.equals("NOT_FOUND")) {
			found=line;
		}
		getCallback().receivedStatus(found);
		transitionState(State.COMPLETE);
	}

	@Override
	public void initialize() {
		int size=key.length() + OVERHEAD;
		ByteBuffer b=ByteBuffer.allocate(size);
		setArguments(b, mutator.name(), key, amount);
		b.flip();
		setBuffer(b);
	}

	@Override
	protected void wasCancelled() {
		// XXX:  Replace this comment with why the hell I did this.
		getCallback().receivedStatus("cancelled");
	}

}

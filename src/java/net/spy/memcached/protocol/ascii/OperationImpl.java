// Copyright (c) 2006  Dustin Sallings <dustin@spy.net>

package net.spy.memcached.protocol.ascii;

import java.io.IOException;
import java.nio.ByteBuffer;

import net.spy.SpyObject;
import net.spy.memcached.ops.Operation;
import net.spy.memcached.ops.OperationCallback;

/**
 * Operations on a memcached connection.
 */
abstract class OperationImpl extends SpyObject implements Operation {

	protected static final byte[] CRLF={'\r', '\n'};

	private final StringBuilder currentLine=new StringBuilder();
	private State state=State.WRITING;
	private ReadType readType=ReadType.LINE;
	private ByteBuffer cmd=null;
	private boolean cancelled=false;
	private boolean foundCr=false;
	private OperationException exception=null;
	private OperationCallback callback=null;

	protected OperationImpl() {
		super();
	}

	protected OperationImpl(OperationCallback cb) {
		super();
		callback=cb;
	}

	/**
	 * Get the operation callback associated with this operation.
	 */
	public OperationCallback getCallback() {
		return callback;
	}


	/**
	 * Set the callback for this instance.
	 */
	protected void setCallback(OperationCallback to) {
		callback=to;
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#isCancelled()
	 */
	public boolean isCancelled() {
		return cancelled;
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#hasErrored()
	 */
	public boolean hasErrored() {
		return exception != null;
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#getException()
	 */
	public OperationException getException() {
		return exception;
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#cancel()
	 */
	public void cancel() {
		cancelled=true;
		wasCancelled();
		callback.complete();
	}

	/**
	 * This is called on each subclass whenever an operation was cancelled.
	 */
	protected abstract void wasCancelled();

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#getState()
	 */
	public State getState() {
		return state;
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#getBuffer()
	 */
	public ByteBuffer getBuffer() {
		assert cmd != null : "No output buffer.";
		return cmd;
	}

	/**
	 * Set the write buffer for this operation.
	 */
	protected void setBuffer(ByteBuffer to) {
		assert to != null : "Trying to set buffer to null";
		cmd=to;
		cmd.mark();
	}

	/**
	 * Transition the state of this operation to the given state.
	 */
	protected void transitionState(State newState) {
		getLogger().debug("Transitioned state from %s to %s", state, newState);
		state=newState;
		// Discard our buffer when we no longer need it.
		if(state != State.WRITING) {
			cmd=null;
		}
		if(state == State.COMPLETE) {
			callback.complete();
		}
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#writeComplete()
	 */
	public void writeComplete() {
		transitionState(State.READING);
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#getReadType()
	 */
	public ReadType getReadType() {
		return readType;
	}

	/**
	 * Set the read type of this operation.
	 */
	protected void setReadType(ReadType to) {
		readType=to;
	}

	/**
	 * Set some arguments for an operation into the given byte buffer.
	 */
	protected void setArguments(ByteBuffer bb, Object... args) {
		boolean wasFirst=true;
		for(Object o : args) {
			if(wasFirst) {
				wasFirst=false;
			} else {
				bb.put((byte)' ');
			}
			bb.put(String.valueOf(o).getBytes());
		}
		bb.put(CRLF);
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#initialize()
	 */
	public abstract void initialize();

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#readFromBuffer(java.nio.ByteBuffer)
	 */
	public final void readFromBuffer(ByteBuffer data) throws IOException {
		// Loop while there's data remaining to get it all drained.
		while(state != State.COMPLETE && data.remaining() > 0) {
			if(readType == ReadType.DATA) {
				handleRead(data);
			} else {
				int offset=-1;
				for(int i=0; data.remaining() > 0; i++) {
					byte b=data.get();
					if(b == '\r') {
						foundCr=true;
					} else if(b == '\n') {
						assert foundCr: "got a \\n without a \\r";
						offset=i;
						foundCr=false;
						break;
					} else {
						assert !foundCr : "got a \\r without a \\n";
						currentLine.append((char)b);
					}
				}
				if(offset >= 0) {
					String line=currentLine.toString();
					currentLine.delete(0, currentLine.length());
					assert currentLine.length() == 0;
					ErrorType eType=classifyError(line);
					if(eType != null) {
						handleError(eType, line);
					} else {
						handleLine(line);
					}
				}
			}
		}
	}

	private void handleError(ErrorType eType, String line) throws IOException {
		getLogger().error("Error:  %s", line);
		switch(eType) {
			case GENERAL:
				exception=new OperationException();
				break;
			case SERVER:
				exception=new OperationException(eType, line);
				break;
			case CLIENT:
				exception=new OperationException(eType, line);
				break;
			default: assert false;
		}
		transitionState(State.COMPLETE);
		throw exception;
	}

	private ErrorType classifyError(String line) {
		ErrorType rv=null;
		if(line.startsWith("ERROR")) {
			rv=ErrorType.GENERAL;
		} else if(line.startsWith("CLIENT_ERROR")) {
			rv=ErrorType.CLIENT;
		} else if(line.startsWith("SERVER_ERROR")) {
			rv=ErrorType.SERVER;
		}
		return rv;
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#handleRead(java.nio.ByteBuffer)
	 */
	public void handleRead(ByteBuffer data) {
		assert false;
	}

	/* (non-Javadoc)
	 * @see net.spy.memcached.protocol.ascii.Operation#handleLine(java.lang.String)
	 */
	public abstract void handleLine(String line);
}

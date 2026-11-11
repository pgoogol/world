package com.pgoogol.npcworld.brain;

/** The brains server answered with something the contract does not allow. */
public class BrainProtocolException extends RuntimeException {

    public BrainProtocolException(String message) {
        super(message);
    }
}

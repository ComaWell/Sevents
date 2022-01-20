package me.connor.sevents;

public class EventDispatchException extends RuntimeException {

	private static final long serialVersionUID = 787694083970915543L;
	
	private final Event<?> event;
	
	EventDispatchException(Event<?> event, Exception cause) {
		super(cause);
		this.event = event;
	}
	
	public Event<?> event() {
		return event;
	}

}

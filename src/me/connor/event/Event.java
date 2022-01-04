package me.connor.event;

import java.util.function.*;

public abstract class Event<T> {
	
	//The only Event with a null parent. Every single other Event is a descendant of the root
	public static final Blank ROOT = new Blank("Event", null);
	
	private final String name;
	
	private Event(String name) {
		if (name == null)
			throw new NullPointerException();
		if (name.isBlank() || name.lines().count() != 1)
			throw new IllegalArgumentException("An Event name must be a non-blank, single-line String");
		this.name = name;
	}
	
	public String name() {
		return name;
	}
	
	@Override
	public String toString() {
		return name();
	}
	
	public abstract boolean isBlank();
	
	public abstract boolean isProxy();
	
	public abstract Event<?> parent();
	
	public Proxy<T> proxy() {
		return isProxy() ? (Proxy<T>) this : new Proxy<>(this);
	}
	
	public static Blank blank(String name) {
		return ROOT.child(name);
	}
	
	public static <T> Valued<T> valued(String name) {
		return ROOT.valuedChild(name);
	}
	
	public static final class Blank extends Event<Void> {
		
		private final Blank parent;
		
		private Blank(String name, Blank parent) {
			super(name);
			this.parent = parent;
		}
		
		@Override
		public boolean isBlank() {
			return true;
		}
		
		@Override
		public boolean isProxy() {
			return false;
		}
		
		public boolean isRoot() {
			return parent == null;
		}
		
		@Override
		public Blank parent() {
			return isRoot() ? this : parent;
		}
		
		public <T> Valued<T> valuedChild(String name) {
			return new Valued<>(name, this, (t) -> null);
		}
		
		public Blank child(String name) {
			return new Blank(name, this);
		}
		
		public void dispatch() {
			EventChannel.dispatch(this);
		}
		
	}
	
	public static final class Valued<T> extends Event<T> {
		
		private final Event<?> parent;
		private final Function<T, ?> parentConverter;
		
		private <P> Valued(String name, Event<P> parent, Function<T, P> parentConverter) {
			super(name);
			if (parent == null || parentConverter == null)
				throw new NullPointerException();
			this.parent = parent;
			this.parentConverter = parentConverter;
		}
		
		@Override
		public boolean isBlank() {
			return false;
		}
		
		@Override
		public boolean isProxy() {
			return false;
		}
		
		@Override
		public Event<?> parent() {
			return parent;
		}
		
		Object convert(T input) {
			if (input == null)
				throw new NullPointerException();
			return parentConverter.apply(input);
		}
		
		public <C> Valued<C> child(String name, Function<C, T> converter) {
			return new Valued<>(name, this, converter);
		}
		
		public <C extends T> Valued<T> child(String name) {
			return child(name, (t) -> t);
		}
		
		public T dispatch(T value) {
			return EventChannel.dispatch(this, value);
		}
		
	}
	
	public static final class Proxy<T> extends Event<T> {
		
		private final Event<T> parent;
		
		private Proxy(Event<T> parent) {
			super(parent.name());
			this.parent = parent;
		}
		
		@Override
		public boolean isBlank() {
			return parent.isBlank();
		}
		
		@Override
		public boolean isProxy() {
			return true;
		}

		Event<T> proxied() {
			return parent;
		}

		@Override
		public Event<?> parent() {
			return parent.parent();
		}
		
	}
	
}

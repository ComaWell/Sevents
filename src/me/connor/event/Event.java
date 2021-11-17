package me.connor.event;

import java.util.function.*;

import me.connor.util.*;

public abstract class Event<T> {
	
	//The only event with a blank parent. Every single other Event is a descendant of the root
	public static final Blank ROOT = new Blank("Event", null);
	
	private final String name;
	
	private Event(@Nonnull String name) {
		Assert.notNull(name);
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
	
	public abstract Event<?> parent();
	
	//I don't think there's a need to make these public. Events should be declared, stored, and referenced statically
	//as their valued or blank Class, so there shouldn't really be a situation where casting is needed publicly
	Blank castBlank() {
		if (!isBlank()) throw new UnsupportedOperationException("This Event is valued");
		return (Blank) this;
	}
	
	Valued<T> castValued() {
		if (isBlank()) throw new UnsupportedOperationException("This Event is blank");
		return (Valued<T>) this;
	}
	
	public static Blank blank(@Nonnull String name) {
		return ROOT.child(name);
	}
	
	public static <T> Valued<T> valued(@Nonnull String name) {
		return ROOT.valuedChild(name);
	}
	
	public static final class Blank extends Event<Void> {
		
		private final Blank parent;
		
		private Blank(@Nonnull String name, @Nullable Blank parent) {
			super(name);
			this.parent = parent;
		}
		
		@Override
		public boolean isBlank() {
			return true;
		}
		
		public boolean isRoot() {
			return parent == null;
		}
		
		@Override
		public Blank parent() {
			return isRoot() ? this : parent;
		}
		
		public <T> Valued<T> valuedChild(@Nonnull String name) {
			return new Valued<>(name, this, (t) -> null);
		}
		
		public Blank child(@Nonnull String name) {
			return new Blank(name, this);
		}
		
		public void dispatch() {
			EventChannel.dispatch(this);
		}
		
	}
	
	public static final class Valued<T> extends Event<T> {
		
		private final Event<?> parent;
		private final Function<T, ?> parentConverter;
		
		private <P> Valued(@Nonnull String name, @Nonnull Event<P> parent, @Nonnull Function<T, P> parentConverter) {
			super(name);
			Assert.allNotNull(parent, parentConverter);
			this.parent = parent;
			this.parentConverter = parentConverter;
		}
		
		@Override
		public boolean isBlank() {
			return false;
		}
		
		@Override
		public Event<?> parent() {
			return parent;
		}
		
		Object convert(@Nonnull T input) {
			Assert.notNull(input);
			return parentConverter.apply(input);
		}
		
		public <C> Valued<C> child(@Nonnull String name, @Nonnull Function<C, T> converter) {
			return new Valued<>(name, this, converter);
		}
		
		public <C extends T> Valued<T> child(@Nonnull String name) {
			return child(name, (t) -> t);
		}
		
		public T dispatch(@Nonnull T value) {
			return EventChannel.dispatch(this, value);
		}
		
	}
	
	
	
}

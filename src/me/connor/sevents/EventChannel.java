package me.connor.sevents;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.Collectors;

public abstract class EventChannel {
	
	public static enum ListenerType {
		
		OBSERVER,
		MUTATOR,
		MONITOR
		
	}
	
	private int id;
	
	protected EventChannel(int id) {
		this.id = id;
	}
	
	public final int id() {
		return id;
	}
	
	public final boolean isActive() {
		return isActive(this);
	}
	
	protected final void assertActive() {
		assertActive(this);
	}
	
	public abstract <T> void observe(Event<T> event, BiConsumer<Event<?>, ? super T> observer);
	
	public <T> void observe(Event<T> event, Consumer<? super T> observer) {
		if (observer == null)
			throw new NullPointerException();
		observe(event, (e, v) -> observer.accept(v));
	}
	
	public void observe(Event<?> event, Runnable observer) {
		if (observer == null)
			throw new NullPointerException();
		observe(event, (e, v) -> observer.run());
	}
	
	public abstract <T> void mutate(Event<T> event, BiConsumer<Event<?>, ? super T> mutator);
	
	public <T> void mutate(Event<T> event, Consumer<? super T> mutator) {
		if (mutator == null)
			throw new NullPointerException();
		mutate(event, (e, v) -> mutator.accept(v));
	}
	
	public void mutate(Event<?> event, Runnable mutator) {
		if (mutator == null)
			throw new NullPointerException();
		mutate(event, (e, v) -> mutator.run());
	}
	
	public abstract <T> void monitor(Event<T> event, BiConsumer<Event<?>, ? super T> monitor);
	
	public <T> void monitor(Event<T> event, Consumer<? super T> monitor) {
		if (monitor == null)
			throw new NullPointerException();
		monitor(event, (e, v) -> monitor.accept(v));
	}
	
	public void monitor(Event<?> event, Runnable monitor) {
		if (monitor == null)
			throw new NullPointerException();
		monitor(event, (e, v) -> monitor.run());
	}
	
	abstract <T> List<EventDispatchException> accept(Event<T> event, T value, ListenerType type);
	
	@Override
	public final int hashCode() {
		return id;
	}
	
	@Override
	public final boolean equals(Object obj) {
		return obj instanceof EventChannel && ((EventChannel) obj).id == id;
	}
	
	public static final class Default extends EventChannel {
		
		private final boolean async;
		
		private final EventMap observers = new EventMap();
		private final EventMap mutators = new EventMap();
		private final EventMap monitors = new EventMap();

		protected Default(int id, boolean async) {
			super(id);
			this.async = async;
		}

		@Override
		public <T> void observe(Event<T> event, BiConsumer<Event<?>, ? super T> observer) {
			if (event == null || observer == null)
				throw new NullPointerException();
			assertActive();
			synchronized(observers) {
				observers.add(event, observer);
			}
		}

		@Override
		public <T> void mutate(Event<T> event, BiConsumer<Event<?>, ? super T> mutator) {
			if (event == null || mutator == null)
				throw new NullPointerException();
			assertActive();
			synchronized(mutators) {
				mutators.add(event, mutator);
			}
		}

		@Override
		public <T> void monitor(Event<T> event, BiConsumer<Event<?>, ? super T> monitor) {
			if (event == null || monitor == null)
				throw new NullPointerException();
			assertActive();
			synchronized(monitors) {
				monitors.add(event, monitor);
			}
		}
		
		private <T> List<EventDispatchException> accept(Event<T> event, T value, EventMap map, boolean async) {
			BiConsumer<Event<?>, T>[] baked = map.get(event);
			if (baked == null || baked.length == 0)
				return List.of();
			if (async) try {
				return THREAD_POOL.submit(() -> Arrays.stream(baked)
						.parallel()
						.map((l) -> {
							try {
								l.accept(event, value);
								return null;
							} catch (Exception e) {
								return new EventDispatchException(event, e);
							}
						})
						.filter((e) -> e != null).collect(Collectors.toUnmodifiableList()
						)).get();
			} catch (InterruptedException | ExecutionException e) {
				return List.of(new EventDispatchException(event, e));
			}
			else {
				List<EventDispatchException> failed = new ArrayList<>();
				for (BiConsumer<Event<?>, T> listener : baked) {
					try {
						listener.accept(event, value);
					} catch (Exception e) {
						failed.add(new EventDispatchException(event, e));
					}
				}
				return failed;
			}
		}

		@Override
		<T> List<EventDispatchException> accept(Event<T> event, T value, ListenerType type) {
			assertActive();
			EventMap map;
			boolean async;
			switch (type) {
				case OBSERVER: {
					map = observers;
					async = this.async;
					break;
				}
				case MUTATOR: {
					map = mutators;
					async = false;
					break;
				}
				case MONITOR: {
					map = monitors;
					async = this.async;
					break;
				}
				default: {
					throw new InternalError("ListenerType " + type.name() + " not implemented for EventChannel.Default");
				}
			}
			return accept(event, value, map, async);
		}
		
	}
	
	private static final ForkJoinPool THREAD_POOL = new ForkJoinPool();
	
	private static final Set<EventChannel> CHANNELS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
	
	private static boolean isActive(EventChannel channel) {
		return CHANNELS.contains(channel);
	}
	
	private static void assertActive(EventChannel channel) {
		if (!isActive(channel)) throw new IllegalStateException("This EventChannel is no longer active");
	}
	
	/*
	 * Note: Due to the way this is programmed, there is a limit to the number of EventChannels
	 * that can be created in a given runtime (Integer.MAX_VALUE). This shouldn't be an issue however,
	 * generally EventChannels should only be added for situations where groups of listeners can be
	 * loaded/unloaded dynamically (such as with a plugin system), so it isn't realisitic for
	 * more than a few (maybe a few dozen/hundred in extreme cases) channels to be created anyways.
	 */
	public static EventChannel create(IntFunction<EventChannel> generator) {
		int id = ID_GENERATOR.getAndIncrement();
		if (id < 0)
			throw new ArithmeticException("The maximum number of EventChannels have been created");
		EventChannel channel = generator.apply(id);
		if (channel.id() != id)
			throw new IllegalArgumentException("The id of this EventChannel does not match the id it was assigned"
					+ "(expected " + id + ", received " + channel.id() + ")");
		synchronized (CHANNELS) {
			if (!CHANNELS.add(channel))
				//Only a sanity check, this should never happen
				throw new InternalError("Failed to insert EventChannel " + id);
		}
		return channel;
	}
	
	public static void kill(EventChannel channel) {
		if (channel == null)
			throw new NullPointerException();
		if (!CHANNELS.contains(channel))
			return;
		synchronized (CHANNELS) {
			CHANNELS.remove(channel);
		}
	}
	
	public static EventChannel newSync() {
		return create((i) -> new Default(i, false));
	}
	
	public static EventChannel newAsync() {
		return create((i) -> new Default(i, true));
	}
	
	//TODO: Reevaluate the try/catch blocks here
	private static <T> void dispatchAll(Event<T> event, T value) {
		if (event == null)
			throw new NullPointerException();
		if (event.isProxy())
			throw new IllegalArgumentException("Cannot dispatch a proxy Event");
		//copied for async dispatch safety
		Set<EventChannel> channels = Set.copyOf(CHANNELS);
		List<EventDispatchException> failed = new CopyOnWriteArrayList<>();
		try {
			THREAD_POOL.submit(() -> channels.parallelStream()
					.forEach((c) -> failed.addAll(c.accept(event, value, ListenerType.OBSERVER))))
					.get();
		} catch (InterruptedException | ExecutionException e) {
			failed.add(new EventDispatchException(event, e));
		}
		for (EventChannel channel : channels) {
			failed.addAll(channel.accept(event, value, ListenerType.MUTATOR));
		}
		try {
			THREAD_POOL.submit(() -> channels.parallelStream()
					.forEach((c) -> failed.addAll(c.accept(event, value, ListenerType.MONITOR))))
					.get();
		} catch (InterruptedException | ExecutionException e) {
			failed.add(new EventDispatchException(event, e));
		}
		//Prevents endless dispatch loop if an Event.ERR listener throws an exception
		if (event == Event.ERR || event.isDescendantOf(Event.ERR)) {
			for (EventDispatchException e : failed) {
				System.err.println("An unmanaged exception was thrown while dispatching "
						+ event.name() + ":");
				e.printStackTrace();
			}
		}
		else for (EventDispatchException e : failed)
			dispatch(Event.ERR, e);
	}
	
	public static <T> T dispatch(Event<T> event, T value) {
		if (value == null)
			throw new NullPointerException();
		dispatchAll(event, value);
		return value;
	}
	
	public static void dispatch(Event<Void> event) {
		dispatchAll(event, null);
	}

}

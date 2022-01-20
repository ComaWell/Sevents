package me.connor.sevents;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;
import java.util.stream.*;

public final class EventChannel {
	
	private final int id;
	private final boolean async;
	
	private final EventMap observers = new EventMap();
	private final EventMap mutators = new EventMap();
	private final EventMap monitors = new EventMap();
	
	private EventChannel(int id, boolean async) {
		this.id = id;
		this.async = async;
	}
	
	public int id() {
		return id;
	}
	
	public boolean isActive() {
		return isActive(this);
	}
	
	private void checkActive() {
		checkActive(this);
	}
	
	public <T> void observe(Event<T> event, BiConsumer<Event<?>, ? super T> observer) {
		if (event == null || observer == null)
			throw new NullPointerException();
		checkActive();
		synchronized(observers) {
			observers.add(event, observer);
		}
	}
	
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
	
	public <T> void mutate(Event<T> event, BiConsumer<Event<?>, ? super T> mutator) {
		if (event == null || mutator == null)
			throw new NullPointerException();
		checkActive();
		synchronized(mutators) {
			mutators.add(event, mutator);
		}
	}
	
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
	
	public <T> void monitor(Event<T> event, BiConsumer<Event<?>, ? super T> monitor) {
		if (event == null || monitor == null)
			throw new NullPointerException();
		checkActive();
		synchronized(monitors) {
			monitors.add(event, monitor);
		}
	}
	
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
	
	@SuppressWarnings("rawtypes")
	private List<EventDispatchException> accept(Event event, Object value, EventMap map, boolean async) {
		BiConsumer[] baked = map.get(event);
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
			for (BiConsumer listener : baked) {
				try {
					listener.accept(event, value);
				} catch (Exception e) {
					failed.add(new EventDispatchException(event, e));
				}
			}
			return failed;
		}
	}
	
	@SuppressWarnings("rawtypes")
	private List<EventDispatchException> observersAccept(Event event, Object value) {
		return accept(event, value, observers, async);
	}
	
	@SuppressWarnings("rawtypes")
	private List<EventDispatchException> mutatorsAccept(Event event, Object value) {
		return accept(event, value, mutators, false);
	}
	
	@SuppressWarnings("rawtypes")
	private List<EventDispatchException> monitorsAccept(Event event, Object value) {
		return accept(event, value, monitors, async);
	}
	
	private static final ForkJoinPool THREAD_POOL = new ForkJoinPool();
	
	private static final Set<EventChannel> CHANNELS = Collections.newSetFromMap(new WeakHashMap<>());
	private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);
	
	private static boolean isActive(EventChannel channel) {
		if (channel == null)
			throw new NullPointerException();
		for (EventChannel c : Set.copyOf(CHANNELS)) {
			if (c == channel) return true;
		}
		return false;
	}
	
	private static void checkActive(EventChannel channel) {
		if (!isActive(channel)) throw new IllegalStateException("This EventChannel is no longer active");
	}
	
	/*
	 * Note: Due to the way this is programmed, there is a limit to the number of EventChannels
	 * that can be created in a given runtime (Integer.MAX_VALUE). This shouldn't be an issue however,
	 * generally EventChannels should only be added for situations where groups of listeners can be
	 * loaded/unloaded dynamically (such as with a plugin system), so it isn't realisitic for
	 * more than a few (maybe a few dozen/hundred in extreme cases) channels to be created anyways.
	 */
	private static EventChannel newChannel(boolean async) {
		int id = ID_GENERATOR.getAndIncrement();
		if (id < 0)
			throw new ArithmeticException("The maximum number of EventChannels have been created");
		EventChannel channel = new EventChannel(id, async);
		synchronized (CHANNELS) {
			if (!CHANNELS.add(channel))
				//Only a sanity check, this should never happen
				throw new InternalError("Failed to insert EventChannel " + id);
		}
		return channel;
	}
	
	//Note: A synchronous channel's non-mutating listeners are only guaranteed sequential execution with respect to
	//the other listeners of that channel
	public static EventChannel sync() {
		return newChannel(false);
	}
	
	//Note: An asynchronous channel will still invoke mutator listeners
	//sequentially
	public static EventChannel async() {
		return newChannel(true);
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
					.forEach((c) -> failed.addAll(c.observersAccept(event, value)))
					).get();
		} catch (InterruptedException | ExecutionException e) {
			failed.add(new EventDispatchException(event, e));
		}
		for (EventChannel channel : channels) {
			failed.addAll(channel.mutatorsAccept(event, value));
		}
		try {
			THREAD_POOL.submit(() -> channels.parallelStream()
					.forEach((c) -> failed.addAll(c.monitorsAccept(event, value)))
					).get();
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

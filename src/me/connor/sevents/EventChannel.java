package me.connor.sevents;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

import me.connor.sevents.EventMap.Listener;

public abstract class EventChannel {
	
	public static enum ListenerType {
		
		OBSERVER(true),
		MUTATOR(false),
		MONITOR(true);
		
		private final boolean async;
		
		private ListenerType(boolean async) {
			this.async = async;
		}
		
		public boolean allowsAsync() {
			return async;
		}
		
	}
	
	protected EventChannel() {
		
	}
	
	public final boolean isActive() {
		return isActive(this);
	}
	
	public abstract <T> void listenTo(Event<T> event, ListenerType type, BiConsumer<Event<?>, ? super T> listener);
	
	public <T> void observe(Event<T> event, BiConsumer<Event<?>, ? super T> listener) {
		if (listener == null)
			throw new NullPointerException();
		listenTo(event, ListenerType.OBSERVER, listener);
	}
	
	public <T> void observe(Event<T> event, Consumer<? super T> listener) {
		if (listener == null)
			throw new NullPointerException();
		observe(event, (e, v) -> listener.accept(v));
	}
	
	public <T> void observe(Event<T> event, Runnable listener) {
		if (listener == null)
			throw new NullPointerException();
		observe(event, (e, v) -> listener.run());
	}
	
	public <T> void mutate(Event<T> event, BiConsumer<Event<?>, ? super T> listener) {
		if (listener == null)
			throw new NullPointerException();
		listenTo(event, ListenerType.MUTATOR, listener);
	}
	
	public <T> void mutate(Event<T> event, Consumer<? super T> listener) {
		if (listener == null)
			throw new NullPointerException();
		mutate(event, (e, v) -> listener.accept(v));
	}
	
	public <T> void mutate(Event<T> event, Runnable listener) {
		if (listener == null)
			throw new NullPointerException();
		mutate(event, (e, v) -> listener.run());
	}
	
	public <T> void monitor(Event<T> event, BiConsumer<Event<?>, ? super T> listener) {
		listenTo(event, ListenerType.MONITOR, listener);
	}
	
	public <T> void monitor(Event<T> event, Consumer<? super T> listener) {
		if (listener == null)
			throw new NullPointerException();
		monitor(event, (e, v) -> listener.accept(v));
	}
	
	public <T> void monitor(Event<T> event, Runnable listener) {
		if (listener == null)
			throw new NullPointerException();
		monitor(event, (e, v) -> listener.run());
	}
	
	protected abstract <T> List<EventDispatchException> accept(Event<T> event, T value, ListenerType type);
	
	public static final class Default extends EventChannel {
		
		private final boolean async;
		
		private final EventMap[] maps = new EventMap[ListenerType.values().length];

		protected Default(boolean async) {
			this.async = async;
			for (int i = 0; i < ListenerType.values().length; i++) {
				maps[i] = new EventMap();
			}
		}

		@Override
		public <T> void listenTo(Event<T> event, ListenerType type, BiConsumer<Event<?>, ? super T> listener) {
			if (event == null || type == null || listener == null)
				throw new NullPointerException();
			maps[type.ordinal()].add(event, listener);
		}
		
		@SuppressWarnings("rawtypes")
		private Object[] buildConversions(Event event, Object value) {
			Event[] lineage = EventMap.lineageBetween(event, Event.ROOT);
			int size = 0;
			while (!lineage[++size].isBlank()) { }
			Object[] conversions = new Object[size];
			conversions[0] = value;
			for (int i = 1; i < conversions.length; i++) {
				conversions[i] = ((Event.Valued) lineage[i - 1]).convert(conversions[i - 1]);
			}
			return conversions;
		}
		
		private static final Object[] EMPTY_CONVERSIONS = new Object[0];
		
		@SuppressWarnings("rawtypes")
		private List<EventDispatchException> acceptSync(Event event, Object value, EventMap map) {
			Listener[][] baked = map.get(event);
			if (baked.length == 0)
				return List.of();
			List<EventDispatchException> failed = new ArrayList<>();
			Object[] conversions = event.isBlank() ? EMPTY_CONVERSIONS : buildConversions(event, value);
			//Loop for all valued events
			for (int i = 0; i < conversions.length; i++) {
				Listener[] listeners = baked[i];
				Object val = conversions[i];
				for (Listener l : listeners) {
					try {
						l.accept(event, val);
					} catch (Exception e) {
						failed.add(new EventDispatchException(event, e));
					}
				}
			}
			//Loop for remaining blank events
			for (int i = conversions.length; i < baked.length; i++) {
				for (Listener l : baked[i]) {
					try {
						l.accept(event, null);
					} catch (Exception e) {
						failed.add(new EventDispatchException(event, e));
					}
				}
			}
			return failed;
		}
		
		@SuppressWarnings("rawtypes")
		private int acceptAsync(Event event, Object value, EventMap map, CompletionService<EventDispatchException> completer) {
			Listener[][] baked = map.get(event);
			if (baked.length == 0)
				return 0;
			Object[] conversions = event.isBlank() ? EMPTY_CONVERSIONS : buildConversions(event, value);
System.out.println("CONVERSIONS: " + Arrays.toString(conversions));
			int numTasks = 0;
			//Loop for all valued events
			for (int i = 0; i < conversions.length; i++) {
				Listener[] listeners = baked[i];
				Object val = conversions[i];
				for (Listener l : listeners) {
					numTasks++;
					completer.submit(() -> {
						try {
							l.accept(event, val);
							return null;
						} catch (Exception e) {
							return new EventDispatchException(event, e);
						}
					});
				}
			}
			//Loop for remaining blank events
			for (int i = conversions.length; i < baked.length; i++) {
				for (Listener l : baked[i]) {
					numTasks++;
					completer.submit(() -> {
						try {
							l.accept(event, null);
							return null;
						} catch (Exception e) {
							return new EventDispatchException(event, e);
						}
					});
				}
			}
			return numTasks;
		}
		
		@Override
		protected <T> List<EventDispatchException> accept(Event<T> event, T value, ListenerType type) {
			boolean async = type.allowsAsync() ? this.async : false;
			EventMap map = maps[type.ordinal()];
			List<EventDispatchException> failed;
			if (async) {
				failed = new ArrayList<>();
				CompletionService<EventDispatchException> completer = new ExecutorCompletionService<>(THREAD_POOL);
				int numTasks = acceptAsync(event, value, map, completer);
				for (int i = 0; i < numTasks; i++) {
					try {
						EventDispatchException exception = completer.take().get();
						if (exception != null)
							failed.add(exception);
					//TODO: I somewhat don't like this, since it's an issue with the dispatch itself, not a listener
					} catch (InterruptedException | ExecutionException e) {
						failed.add(new EventDispatchException(event, e));
					}
				}
			}
			else {
				failed = acceptSync(event, value, map);
			}
			return failed;
		}
		
	}
	
	private static final ForkJoinPool THREAD_POOL = new ForkJoinPool();
	
	private static final Set<EventChannel> CHANNELS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
	
	private static boolean isActive(EventChannel channel) {
		if (channel == null)
			throw new NullPointerException();
		return CHANNELS.contains(channel);
	}
	
	public static EventChannel create(Supplier<? extends EventChannel> generator) {
		if (generator == null)
			throw new NullPointerException();
		EventChannel channel = generator.get();
		if (channel == null)
			throw new NullPointerException();
		if (!CHANNELS.add(channel))
			throw new InternalError("Failed to insert EventChannel");
		return channel;
	}
	
	public static void kill(EventChannel channel) {
		if (channel == null)
			throw new NullPointerException();
		CHANNELS.remove(channel);
	}
	
	public static EventChannel newSync() {
		return create(() -> new Default(false));
	}
	
	public static EventChannel newAsync() {
		return create(() -> new Default(true));
	}
	
	private static <T> void dispatchAll(Event<T> event, T value) {
		if (event == null)
			throw new NullPointerException();
		if (event.isProxy())
			throw new IllegalArgumentException("Cannot dispatch a proxy Event");
		EventChannel[] channels = CHANNELS.toArray(EventChannel[]::new);
		List<EventDispatchException> failed = new ArrayList<>();
		for (ListenerType type : ListenerType.values()) {
			if (type.allowsAsync()) {
				CompletionService<List<EventDispatchException>> completer = new ExecutorCompletionService<>(THREAD_POOL);
				for (EventChannel channel : channels) {
					completer.submit(() -> channel.accept(event, value, type));
				}
				for (int i = 0; i < channels.length; i++) {
					try {
						List<EventDispatchException> exceptions = completer.take().get();
						if (exceptions != null)
							failed.addAll(exceptions);
					//TODO: I somewhat don't like this, since it's an issue with the dispatch itself, not a listener
					} catch (Exception e) {
						failed.add(new EventDispatchException(event, e));
					}
				}
			}
			else for (EventChannel channel : channels) {
				try {
					failed.addAll(channel.accept(event, value, type));
				//TODO: I somewhat don't like this, since it's an issue with the dispatch itself, not a listener
				} catch(Exception e) {
					failed.add(new EventDispatchException(event, e));
				}
			}
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

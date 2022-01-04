package me.connor.event;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import java.util.stream.*;

public class EventChannel {
	
	private final int id;
	
	@SuppressWarnings("rawtypes")
	private final HashMap<Event, List> listeners = new HashMap<>();
	
	@SuppressWarnings("rawtypes")
	private Map<Event, BiConsumer[]> baked;
	
	private EventChannel(int id) {
		this.id = id;
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
	
	private boolean isBaked() {
		return baked != null;
	}
	
	private void invalidateBaked() {
		baked = null;
	}
	
	synchronized
	private <T> BiConsumer<Event<?>, T>[] getBaked(Event<T> event) {
		if (event == null)
			throw new NullPointerException();
		if (!isBaked()) bake();
		return baked.get(event);
	}
	
	@SuppressWarnings("rawtypes")
	private void bake() {
		HashMap<Event, BiConsumer[]> baked = new HashMap<>();
		for (Event e : Set.copyOf(listeners.keySet())) {
			baked.put(e, bake(e));
		}
		this.baked = Map.copyOf(baked);
	}
	
	private <T> BiConsumer<Event<?>, T>[] bake(Event<T> event) {
		return (event.isBlank() ? bakeBlank((Event.Blank) event) : bakeValued((Event.Valued<T>) event)).toArray(BiConsumer[]::new);
	}
	
	private List<BiConsumer<Event<?>, Void>> bakeBlank(Event.Blank event) {
		if (event == null)
			throw new NullPointerException();
		List<BiConsumer<Event<?>, Void>> listeners = new ArrayList<>(getListeners(event));
		if (!event.isRoot()) listeners.addAll(bakeBlank(event.parent()));
		return listeners;
	}
	
	private <T> List<BiConsumer<Event<?>, T>> bakeValued(Event.Valued<T> event) {
		if (event == null)
			throw new NullPointerException();
		List<BiConsumer<Event<?>, T>> listeners = new ArrayList<>(getListeners(event));
		listeners.addAll(bakeForDescendant(event.parent(), event));
		return listeners;
	}
	
	private <T, P> List<BiConsumer<Event<?>, T>> bakeForDescendant(Event<P> event, Event.Valued<T> descendant) {
		if (event == null || descendant == null)
			throw new NullPointerException();
		if (event.isBlank()) return convertListeners(bakeBlank((Event.Blank) event), (Event.Blank) event, descendant);
		List<BiConsumer<Event<?>, T>> listeners = new ArrayList<>(convertListeners(getListeners(event), event, descendant));
		listeners.addAll(bakeForDescendant(event.parent(), descendant));
		return listeners;
	}
	
	private <T, P> List<BiConsumer<Event<?>, T>> convertListeners(List<BiConsumer<Event<?>, P>> listeners, Event<P> from, Event<T> to) {
		if (listeners == null || from == null || to == null)
			throw new NullPointerException();
		return listeners.stream().map((l) -> convertListener(l, from, to)).collect(Collectors.toList());
	}
	
	//TODO: There's one million percent a better/cleaner/faster way to do this
	@SuppressWarnings("rawtypes")
	private <T, P> BiConsumer<Event<?>, T> convertListener(BiConsumer<Event<?>, P> listener, Event<P> from, Event<T> to) {
		if (listeners == null || from == null || to == null)
			throw new NullPointerException();
		return (e, t) -> {
			Object val = t;
			Event current = from;
			do {
				if (current.isBlank()) break;
				else val = ((Event.Valued) current).convert(val);
			} while((current = current.parent()) != to); 
			listener.accept(e, (P) val);
		};
	}
	
	private <T> List<BiConsumer<Event<?>, T>> getListeners(Event<T> event) {
		if (event == null)
			throw new NullPointerException();
		if (!listeners.containsKey(event)) synchronized(listeners) {
			listeners.putIfAbsent(event, new CopyOnWriteArrayList<BiConsumer<Event<?>, ? super T>>());
		}
		return listeners.get(event);
	}
	
	private <T> void addListener(Event<T> event, BiConsumer<Event<?>, ? super T> listener) {
		if (event == null || listener == null)
			throw new NullPointerException();
		checkActive();
		getListeners(event.isProxy() ? ((Event.Proxy<T>) event).proxied() : event).add((BiConsumer<Event<?>, T>) listener);
		invalidateBaked();
	}
	
	public <T> void listenTo(Event<T> event, BiConsumer<Event<?>, ? super T> listener) {
		addListener(event, listener);
	}
	
	public <T> void listenTo(Event<T> event, Consumer<? super T> listener) {
		addListener(event, (e, v) -> listener.accept(v));
	}
	
	public void listenTo(Event<?> event, Runnable listener) {
		addListener(event, (e, v) -> listener.run());
	}
	
	@SuppressWarnings("rawtypes")
	private void accept(Event dispatcher, Event event, Object value) {
		if (dispatcher == null || event == null)
			throw new NullPointerException();
		if (event.isProxy())
			throw new IllegalArgumentException("Cannot accept for a proxy Event");
		checkActive();
		if (!event.isBlank() && value == null)
			throw new NullPointerException();
		BiConsumer[] baked = getBaked(event);
		if (baked == null) {
			if (event.isBlank() && ((Event.Blank) event).isRoot()) return; 
			else accept(dispatcher, event.parent(), event.isBlank() ? null : ((Event.Valued) event).convert(value));
		}
		else for (BiConsumer listener : baked) {
			try {
				listener.accept(dispatcher, value);
			} catch (Exception e) {
				System.err.println("Unmanaged Exception thrown from listener of " + event.name());
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public int hashCode() {
		return Integer.hashCode(id);
	}
	
	@Override
	public boolean equals(Object obj) {
		return obj == this || obj instanceof EventChannel && ((EventChannel) obj).id == id;
	}
	
	private static final Set<EventChannel> CHANNELS = Collections.newSetFromMap(new WeakHashMap<>());
	
	private static boolean hasChannel(int id) {
		return CHANNELS.stream().anyMatch((c) -> c.id == id);
	}
	
	private static boolean isActive(EventChannel channel) {
		if (channel == null)
			throw new NullPointerException();
		return CHANNELS.stream().anyMatch((c) -> c == channel);
	}
	
	private static void checkActive(EventChannel channel) {
		if (!isActive(channel)) throw new IllegalStateException("This EventChannel is no longer active");
	}
	
	synchronized
	public static EventChannel newChannel() {
		int id = -1;
		while (hasChannel(++id)) { }
		EventChannel channel = new EventChannel(id);
		if (!CHANNELS.add(channel))
			throw new InternalError("Failed to insert EventChannel " + id);
		return channel;
	}
	
	synchronized
	public static void killChannel(EventChannel channel) {
		if (channel == null)
			throw new NullPointerException();
		CHANNELS.remove(channel);
	}
	
	private static <T> void dispatchTo(Event<T> event, T value) {
		if (event == null)
			throw new NullPointerException();
		if (event.isProxy())
			throw new IllegalArgumentException("Cannot dispatch a proxy Event");
		for (EventChannel channel : CHANNELS) 
			channel.accept(event, event, value);
	}
	
	public static <T> T dispatch(Event<T> event, T value) {
		if (value == null)
			throw new NullPointerException();
		dispatchTo(event, value);
		return value;
	}
	
	public static void dispatch(Event<Void> event) {
		dispatchTo(event, null);
	}

}

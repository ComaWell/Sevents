package me.connor.event;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import java.util.stream.*;

import me.connor.util.*;

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
	private <T> BiConsumer<Event<?>, T>[] getBaked(@Nonnull Event<T> event) {
		Assert.notNull(event);
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
	
	private <T> BiConsumer<Event<?>, T>[] bake(@Nonnull Event<T> event) {
		return (event.isBlank() ? bakeBlank(event.castBlank()) : bakeValued(event.castValued())).toArray(BiConsumer[]::new);
	}
	
	private List<BiConsumer<Event<?>, Void>> bakeBlank(@Nonnull Event.Blank event) {
		Assert.notNull(event);
		List<BiConsumer<Event<?>, Void>> listeners = new ArrayList<>(getListeners(event));
		if (!event.isRoot()) listeners.addAll(bakeBlank(event.parent()));
		return listeners;
	}
	
	private <T> List<BiConsumer<Event<?>, T>> bakeValued(@Nonnull Event.Valued<T> event) {
		Assert.notNull(event);
		List<BiConsumer<Event<?>, T>> listeners = new ArrayList<>(getListeners(event));
		listeners.addAll(bakeForDescendant(event.parent(), event));
		return listeners;
	}
	
	private <T, P> List<BiConsumer<Event<?>, T>> bakeForDescendant(@Nonnull Event<P> event, @Nonnull Event.Valued<T> descendant) {
		Assert.allNotNull(event, descendant);
		if (event.isBlank()) return convertListeners(bakeBlank(event.castBlank()), event.castBlank(), descendant);
		List<BiConsumer<Event<?>, T>> listeners = new ArrayList<>(convertListeners(getListeners(event), event, descendant));
		listeners.addAll(bakeForDescendant(event.parent(), descendant));
		return listeners;
	}
	
	private <T, P> List<BiConsumer<Event<?>, T>> convertListeners(@Nonnull List<BiConsumer<Event<?>, P>> listeners, @Nonnull Event<P> from, @Nonnull Event<T> to) {
		Assert.allNotNull(listeners, from, to);
		return listeners.stream().map((l) -> convertListener(l, from, to)).collect(Collectors.toList());
	}
	
	//TODO: There's one million percent a better/cleaner/faster way to do this
	@SuppressWarnings("rawtypes")
	private <T, P> BiConsumer<Event<?>, T> convertListener(@Nonnull BiConsumer<Event<?>, P> listener, @Nonnull Event<P> from, @Nonnull Event<T> to) {
		Assert.allNotNull(listener, from, to);
		return (e, t) -> {
			Object val = t;
			Event current = from;
			do {
				if (current.isBlank()) break;
				else val = current.castValued().convert(val);
			} while((current = current.parent()) != to); 
			listener.accept(e, (P) val);
		};
	}
	
	private <T> List<BiConsumer<Event<?>, T>> getListeners(@Nonnull Event<T> event) {
		Assert.notNull(event);
		if (!listeners.containsKey(event)) synchronized(listeners) {
			listeners.putIfAbsent(event, new CopyOnWriteArrayList<BiConsumer<Event<?>, ? super T>>());
		}
		return listeners.get(event);
	}
	
	private <T> void addListener(@Nonnull Event<T> event, @Nonnull BiConsumer<Event<?>, ? super T> listener) {
		Assert.allNotNull(event, listener);
		checkActive();
		getListeners(event).add((BiConsumer<Event<?>, T>) listener);
		invalidateBaked();
	}
	
	public <T> void listenTo(@Nonnull Event<T> event, @Nonnull BiConsumer<Event<?>, ? super T> listener) {
		addListener(event, listener);
	}
	
	public <T> void listenTo(@Nonnull Event<T> event, @Nonnull Consumer<? super T> listener) {
		addListener(event, (e, v) -> listener.accept(v));
	}
	
	public void listenTo(@Nonnull Event<?> event, @Nonnull Runnable listener) {
		addListener(event, (e, v) -> listener.run());
	}
	
	@SuppressWarnings("rawtypes")
	private void accept(@Nonnull Event dispatcher, @Nonnull Event event, @Nullable Object value) {
		Assert.allNotNull(dispatcher, event);
		checkActive();
		if (!event.isBlank()) Assert.notNull(value);
		BiConsumer[] baked = getBaked(event);
		if (baked == null) {
			if (event.isBlank() && event.castBlank().isRoot()) return; 
			else accept(dispatcher, event.parent(), event.isBlank() ? null : event.castValued().convert(value));
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
	
	private static boolean isActive(@Nonnull EventChannel channel) {
		Assert.notNull(channel);
		return CHANNELS.stream().anyMatch((c) -> c == channel);
	}
	
	private static void checkActive(@Nonnull EventChannel channel) {
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
	public static void killChannel(@Nonnull EventChannel channel) {
		Assert.notNull(channel);
		CHANNELS.remove(channel);
	}
	
	private static <T> void dispatchTo(@Nonnull Event<T> event, @Nullable T value) {
		Assert.notNull(event);
		for (EventChannel channel : CHANNELS) 
			channel.accept(event, event, value);
	}
	
	public static <T> T dispatch(@Nonnull Event<T> event, @Nonnull T value) {
		Assert.notNull(value);
		dispatchTo(event, value);
		return value;
	}
	
	public static void dispatch(@Nonnull Event<Void> event) {
		dispatchTo(event, null);
	}

}

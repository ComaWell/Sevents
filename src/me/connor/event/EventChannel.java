package me.connor.event;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.*;
import java.util.stream.*;

import me.connor.util.*;

public class EventChannel {
	
	public static enum Type {
		
		SEQUENTIAL(BaseStream::sequential),
		PARALLEL(BaseStream::parallel);
		
		@SuppressWarnings("rawtypes")
		private final Function<BaseStream, BaseStream> converter;
		
		@SuppressWarnings("rawtypes")
		private Type(@Nonnull Function<BaseStream, BaseStream> converter) {
			Assert.notNull(converter);
			this.converter = converter;
		}
		
		private <T> Stream<T> applyType(@Nonnull Stream<T> stream) {
			Assert.notNull(stream);
			stream.parallel();
			return (Stream<T>) converter.apply(stream);
		}
	}
	
	private final int id;
	private final Type type;
	
	@SuppressWarnings("rawtypes")
	private final HashMap<Event, List> listeners = new HashMap<>();
	
	@SuppressWarnings("rawtypes")
	private Map<Event, Consumer[]> baked;
	
	private EventChannel(int id, @Nonnull Type type) {
		Assert.notNull(type);
		this.id = id;
		this.type = type;
	}
	
	public int id() {
		return id;
	}
	
	public Type type() {
		return type;
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
	private <T> Consumer<T>[] getBaked(@Nonnull Event<T> event) {
		Assert.notNull(event);
		if (!isBaked()) bake();
		return baked.get(event);
	}
	
	private void bake() {
		baked = listeners.keySet()
				.parallelStream()
				.collect(Collectors.toUnmodifiableMap((e) -> e, (e) -> bake(e)));
	}
	
	@SuppressWarnings("rawtypes")
	private Consumer[] bake(@Nonnull Event<?> event) {
		return (event.isBlank() ? bake(event.castBlank()) : bake(event.castValued())).toArray(Consumer[]::new);
	}
	
	private <T> List<Consumer<T>> bake(@Nonnull Event.Valued<T> event) {
		Assert.notNull(event);
		List<Consumer<T>> listeners = new ArrayList<>(getListeners(event));
		listeners.addAll(bake(event.parent(), event));
		return listeners;
	}
	
	private <T, A> List<Consumer<T>> bake(@Nonnull Event<A> event, @Nonnull Event<T> origin) {
		Assert.allNotNull(origin);
		if (event.isBlank()) return convertListeners(bake(event.castBlank()), event.castBlank(), origin);
		List<Consumer<T>> listeners = new ArrayList<>(convertListeners(getListeners(event), event, origin));
		listeners.addAll(bake(event.parent(), origin));
		return listeners;
	}
	
	private List<Consumer<Void>> bake(@Nonnull Event.Blank event) {
		Assert.notNull(event);
		List<Consumer<Void>> listeners = new ArrayList<>(getListeners(event));
		if (!event.isRoot()) listeners.addAll(bake(event.parent()));
		return listeners;
	}
	
	private <T, A> List<Consumer<T>> convertListeners(@Nonnull List<Consumer<A>> listeners, @Nonnull Event<A> from, @Nonnull Event<T> to) {
		Assert.allNotNull(listeners, from, to);
		return listeners.stream().map((l) -> convertListener(l, from, to)).collect(Collectors.toUnmodifiableList());
	}
	
	@SuppressWarnings("rawtypes")
	private <T, A> Consumer<T> convertListener(@Nonnull Consumer<A> listener, @Nonnull Event<A> from, @Nonnull Event<T> to) {
		Assert.allNotNull(listener, from, to);
		return (t) -> {
			Object val = t;
			Event current = from;
			do {
				if (current.isBlank()) break;
				else val = current.castValued().convert(val);
			} while((current = current.parent()) != to); 
			listener.accept((A) val);
		};
	}
	
	private <T> List<Consumer<T>> getListeners(@Nonnull Event<T> event) {
		Assert.notNull(event);
		if (!listeners.containsKey(event)) synchronized(this) {
			listeners.putIfAbsent(event, new CopyOnWriteArrayList<Consumer<T>>());
		}
		return listeners.get(event);
	}
	
	public <T> void addListener(@Nonnull Event<T> event, @Nonnull Consumer<? super T> listener) {
		Assert.allNotNull(event, listener);
		checkActive();
		getListeners(event).add((Consumer<T>) listener);
		invalidateBaked();
	}
	
	public void addListener(@Nonnull Event<Void> event, Runnable listener) {
		Assert.allNotNull(event, listener);
		checkActive();
		getListeners(event).add((v) -> listener.run());
		invalidateBaked();
	}
	
	private <T> void accept(@Nonnull Event<T> event, @Nullable T value) {
		Assert.notNull(event);
		checkActive();
		if (!event.isBlank()) Assert.notNull(value);
		Consumer<T>[] listeners = getBaked(event);
		if (listeners != null && listeners.length != 0) {
			type.applyType(Arrays.stream(listeners))
					.forEach((c) -> {
						try {
							c.accept(value);
						} catch (Throwable t) {
							t.printStackTrace();
							//TODO: Log somewhere probably
						}
					});
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
	private static EventChannel newChannel(@Nonnull Type type) {
		Assert.notNull(type);
		int id = -1;
		while (hasChannel(++id)) { }
		EventChannel channel = new EventChannel(id, type);
		if (!CHANNELS.add(channel))
			throw new InternalError("Failed to insert EventChannel " + id);
		return channel;
	}
	
	public static EventChannel newSequential() {
		return newChannel(Type.SEQUENTIAL);
	}
	
	public static EventChannel newParallel() {
		return newChannel(Type.PARALLEL);
	}
	
	synchronized
	public static void killChannel(@Nonnull EventChannel channel) {
		Assert.notNull(channel);
		CHANNELS.remove(channel);
	}
	
	private static <T> void dispatchSequential(@Nonnull Event<T> event, @Nullable T value) {
		CHANNELS.stream()
		.sequential()
		.filter((c) -> c.type().equals(Type.SEQUENTIAL))
		.forEach((c) -> c.accept(event, value));
	}
	
	private static <T> void dispatchParallel(@Nonnull Event<T> event, @Nullable T value) {
		CHANNELS.stream()
		.parallel()
		.filter((c) -> c.type().equals(Type.PARALLEL))
		.forEach((c) -> c.accept(event, value));
	}
	
	public static <T> T dispatch(@Nonnull Event<T> event, @Nonnull T value) {
		dispatchSequential(event, value);
		dispatchParallel(event, value);
		return value;
	}
	
	public static void dispatch(@Nonnull Event<Void> event) {
		dispatchSequential(event, null);
		dispatchParallel(event, null);
	}

}

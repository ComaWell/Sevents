package me.connor.sevents;

import java.util.function.*;

/**
 * <p>An Event is an Object used primarily as a namespace for code to dispatch and listen to. An Event can either be
 * {@link Valued}, meaning that there is data associated with the Event when it is dispatched, or {@link Blank},
 * meaning the act of dispatching it is in and of itself the data.
 * </p>
 * 
 * <p>While the Event class is abstract, extending the Event class is NOT supported by the rest of the library.</p>
 * 
 * <p>Events do not by themselves hold, create, or dispatch data, nor are Events instatiated as part of the process of dispatching.
 * Rather, they should generally be instantiated, referenced, and treated as a static constant. Note that because Events need to be referenced
 * in order to be listened to or dispatched, they should be declared publicly. (In situations where having control over who/what can dispatch an Event
 * is desired, please see {@linkplain Event.Proxy}.) Events follow a tree-like hierarchy of lineage, similar 
 * to interface or class inheritance. By instantiating a new Event, it effectively creates a new branch for dispatching and
 * listening to occur. Child Events can then be instantiated from that Event, adding to its lineage. When an Event is dispatched, it will trigger all
 * listeners that are registered to it as well as all of the ancestors of said Event (passing along the Object that was used for dispatch if applicable).
 * </p>
 * 
 * @author Connor Wellington
 *
 * @param <T> the type of Object that is dispatched for this Event, or {@code Void} if no other data is involved in dispatch.
 * 
 * @see Event.Valued
 * @see Event.Blank
 * @see Event.Proxy
 * @see Event#ROOT
 * @see EventChannel
 * 
 * @implSpec The various implementations of Event contain context-specific methods that Event.class does not. As such, it is
 * strongly recommended that Events are declared and referenced as the implementation they correspond to. For example:
 * 
 * <p>{@code public static final Event.Valued<String> EVENT_X = Event.valued("EventX");}</p>
 * <p>should be used instead of:</p>
 * <p>{@code public static final Event<String> EVENT_X = Event.valued("EventX");})</p>
 */
public abstract class Event<T> {
	
	/**
	 * <p>The root Event from which all other Events are descended. By listening
	 * to the root, said listener will be invoked every time any Event is dispatched.
	 * For performance reasons, it is heavily recommended to avoid listening to the
	 * root at all, or in cases where it is a necessity, using very few, very inexpensive, 
	 * preferably async listeners wherever possible. Note that dispatching this Event
	 * will only invoke listeners that are directly listening to the root.
	 * </p>
	 * 
	 * <p>Note that the root is a {@link Blank} Event instance.</p>
	 */
	public static final Blank ROOT = new Blank("Event", null);
	
	private final String name;
	
	private Event(String name) {
		if (name == null)
			throw new NullPointerException();
		if (name.isBlank() || name.lines().count() != 1)
			throw new IllegalArgumentException("An Event name must be a non-blank, single-line String");
		this.name = name;
	}
	
	/**
	 * @return the name given to this Event instance. Note that names
	 * are not strictly required (although strongly recommended) to be
	 * unique.
	 */
	public String name() {
		return name;
	}
	
	/**
	 * @return the value of this Event instance's {@link Event#name()} method.
	 */
	@Override
	public String toString() {
		return name();
	}
	
	/**
	 * @return true if this Event is {@link Blank}, false if it is {@link Valued}.
	 * 
	 * @see Event.Blank
	 * @see Event.Valued
	 */
	public abstract boolean isBlank();
	
	/**
	 * @return true if this Event is a {@link Proxy} of another Event, false if it is the direct Event instance.
	 * 
	 *  @see Event.Proxy
	 */
	public abstract boolean isProxy();
	
	/**
	 * @return the direct parent of this Event, i.e. the immediate Event that will also be dispatched
	 * when this Event is dispatched. The only exception with this Method is the {@link Event#ROOT root}
	 * Event, which has no parent and will instead return itself. Note that Events are not aware of, nor 
	 * do they hold references to their children, so there is no corresponding Event::children method.
	 * 
	 * @see Event#ROOT
	 */
	public abstract Event<?> parent();
	
	/**
	 * @return a {@link Proxy}-equivalent instance of this Event, or itself if it is already a proxy.
	 * For more information on what proxies are and the purpose they serve, please see {@link Event.Proxy}.
	 * 
	 * @see Event.Proxy
	 */
	public Proxy<T> proxy() {
		return isProxy() ? (Proxy<T>) this : new Proxy<>(this);
	}
	
	/**
	 * Creates a new {@link Blank} Event instance that is a direct child of the {@link Event#ROOT root}.
	 * 
	 * @param name a non-blank, case-sensitive, single-line {@link String}. Note that this name is not required
	 * (but is strongly recommended) to be unique.
	 * 
	 * @return a new {@link Blank} Event instance.
	 */
	public static Blank blank(String name) {
		return ROOT.child(name);
	}
	
	/**
	 * Creates a new {@link Valued} Event instance that is a direct child of the {@link Event#ROOT root}.
	 * 
	 * @param <T> the type of Object that is dispatched for this Event.
	 * @param name a non-blank, case-sensitive, single-line {@link String}. Note that this name is not required
		 * (but is strongly recommended) to be unique.
	 * 
	 * @return a new {@link Valued} Event instance.
	 */
	public static <T> Valued<T> valued(String name) {
		return ROOT.valuedChild(name);
	}
	
	/**
	 * <p>Blank Events do not have additional data associated with them during dispatch.</p>
	 * 
	 * @author Connor Wellington
	 * 
	 * @see Event
	 * @see Event#isBlank()
	 * @see Event.Valued
	 * @see Event.Proxy
	 * @see EventChannel
	 */
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
		
		/**
		 * @return true if this Blank Event is the {@link Event#ROOT root} instance, false otherwise.
		 */
		public boolean isRoot() {
			return parent == null;
		}
		
		@Override
		public Blank parent() {
			return isRoot() ? this : parent;
		}
		
		/**
		 * Creates a new {@link Valued} Event instance that is a direct child of this Event.
		 * 
		 * @param <T> the type of Object that is dispatched for the child.
		 * @param name a non-blank, case-sensitive, single-line {@link String}. Note that this name is not required
		 * (but is strongly recommended) to be unique.
		 * 
		 * @return a new {@link Valued} Event instance.
		 */
		public <T> Valued<T> valuedChild(String name) {
			return new Valued<>(name, this, (t) -> null);
		}
		
		/**
		 * Creates a new {@link Blank} Event instance that is a direct child of this Event.
		 * 
		 * @param name a non-blank, case-sensitive, single-line {@link String}. Note that this name is not required
		 * (but is strongly recommended) to be unique.
		 * 
		 * @return a new {@link Blank} Event instance.
		 */
		public Blank child(String name) {
			return new Blank(name, this);
		}
		
		/**
		 * <p>Triggers a dispatch for this Event to all {@link EventChannel EventChannels}. Dispatching this Event
		 * will invoke all listeners for this Event, as well as the listeners of this Event's ancestors.
		 * Because this Event is Blank, no value is needed or used for dispatch. 
		 * </p>
		 * 
		 * <p>Note that this method will not return until all listeners have completed executing (or been invoked
		 * in the case of asynchronous listeners).
		 * </p>
		 */
		public void dispatch() {
			EventChannel.dispatch(this);
		}
		
	}
	
	/**
	 * <p>Valued Events dispatch Objects as data to be consumed by their listeners. It is recommended but
	 * not required that data be immutable wherever possible, as well as for Events to clearly document when
	 * asynchronous listeners are or aren't permitted. 
	 * </p>
	 * 
	 * <p>Because Events trigger the listeners of their ancestors, any child of a Valued Event will need to
	 * dispatch data that can be converted into data that its direct parent can also dispatch. For example, 
	 * a Valued Event of type {@link String} can have a child that is also of type {@link String} without issue,
	 * however in order for it to have a child of type {@link Integer}, a converter {@link Function} will
	 * have to be provided when the child is instantiated, which is used to convert {@link Integer} values
	 * dispatched for that child to {@link String} values that can also be dispatched for its parent.
	 * </p>
	 * 
	 * @author Connor Wellington
	 *
	 * @param <T> the type of Object that is dispatched for this Event.
	 * 
	 * @see Event
	 * @see Event#isBlank()
	 * @see Event.Blank
	 * @see Event.Proxy
	 * @see EventChannel
	 */
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
		
		/**
		 * Creates a new {@link Valued} Event instance that is a direct child of this Event.
		 * 
		 * @param <C> the type of Object that is dispatched for the child. Because C is extended from T,
		 * no converter is necessary.
		 * 
		 * @param name a non-blank, case-sensitive, single-line {@link String}. Note that this name is not required
		 * (but is strongly recommended) to be unique.
		 * @param converter a {@link Function} used to convert dispatch values for the child Event to dispatch values for
		 * its parent (this Event instance).
		 * 
		 * @return a new {@link Valued} Event instance.
		 * 
		 * @see Valued#child(String)
		 */
		public <C> Valued<C> child(String name, Function<C, T> converter) {
			return new Valued<>(name, this, converter);
		}
		
		/**
		 * Creates a new {@link Valued} Event instance that is a direct child of this Event.
		 * 
		 * @param <C> the type of Object that is dispatched for the child. Because C is extended from T,
		 * no converter is necessary for the child.
		 * 
		 * @param name a non-blank, case-sensitive, single-line {@link String}. Note that this name is not required
		 * (but is strongly recommended) to be unique.
		 * @return a new {@link Valued} Event instance.
		 * 
		 * @see Valued#child(String, Function)
		 */
		public <C extends T> Valued<C> child(String name) {
			return child(name, (t) -> t);
		}
		
		/**
		 * <p>Dispatches the given value for this Event to all {@link EventChannel EventChannels}. Dispatching this Event
		 * will invoke all listeners for this Event, as well as the listeners of this Event's ancestors. If applicable,
		 * the converter {@link Function} this instance was created with will be used to convert the value to a data type
		 * its parent can use for dispatch.
		 * </p>
		 * 
		 * <p>Note that this method will not return until all listeners have completed executing (or been invoked
		 * in the case of asynchronous listeners).
		 * </p>
		 * 
		 * @param value the nonnull value to be dispatched.
		 * 
		 * @return the value instance that was supplied to this method.
		 */
		public T dispatch(T value) {
			return EventChannel.dispatch(this, value);
		}
		
	}
	
	
	/**
	 * <p>Valued Events dispatch Objects as data to be consumed by their listeners. It is recommended but
	 * not required that data be immutable wherever possible, as well as for Events to clearly document when
	 * asynchronous listeners are or aren't permitted. 
	 * </p>
	 * 
	 * <p>Because Events trigger the listeners of their ancestors, any child of a Valued Event will need to
	 * dispatch data that can be converted into data that its direct parent can also dispatch. For example, 
	 * a Valued Event of type {@link String} can have a child that is also of type {@link String} without issue,
	 * however in order for it to have a child of type {@link Integer}, a converter {@link Function} will
	 * have to be provided when the child is instantiated, which is used to convert {@link Integer} values
	 * dispatched for that child to {@link String} values that can also be dispatched for its parent.
	 * </p>
	 */
	
	/**
	 * <p>Proxy Events can be used when it is desired to control who or what has access to dispatch or create children for
	 * an Event, without imposing the same restrictions for registering listeners to said Event. For all methods specified
	 * in Event.class, a Proxy will return the same value as the Event it is proxying. Additionally, using a proxy instance
	 * to register a listener will register the listener to the Event that it is proxying (Proxy Events themselves cannot
	 * have listeners). However, Proxies do not have a dispatch method, and attempting to dispatch a Proxy event directly
	 * via {@link EventChannel#dispatch(Event)} or {@link EventChannel#dispatch(Event, Object)} will result in an
	 * {@link IllegalArgumentException} being thrown.
	 * <p>
	 * 
	 * <p>Note that while Proxies cannot have children and cannot be used to invoke an Event directly, the Event they are
	 * proxying can still be dispatched by any Object that can reference it directly, or as a result of its children being
	 * dispatched.
	 * </p>
	 * 
	 * <p>The recommended way to achieve proxying is to have two static declarations for an Event, in a way similar to the
	 * following:
	 * </p>
	 * 
	 * <p>{@code private static final Event.Valued<String> EVENT_X = Event.valued("EventX");}</p>
	 * <p>{@code public static final Event.Proxy<String> EVENT_X_PROXY = EVENT_X.proxy();}</p>
	 * 
	 * @author Connor Wellington
	 *
	 * @param <T> the type of Object that is dispatched for the proxied Event.
	 * 
	 * @see Event
	 * @see Event#proxy()
	 * @see Event#isProxy()
	 * @see Event.Blank
	 * @see Event.Valued
	 * @see EventChannel
	 * 
	 * @implSpec it is recommended that it be conveyed either in the field/method name or documentation of a Proxy Event that
	 * it is such, to prevent accidental attempts at dispatching them.
	 */
	public static final class Proxy<T> extends Event<T> {
		
		private final Event<T> proxied;
		
		private Proxy(Event<T> proxied) {
			super(proxied.name());
			this.proxied = proxied;
		}
		
		@Override
		public boolean isBlank() {
			return proxied.isBlank();
		}
		
		@Override
		public boolean isProxy() {
			return true;
		}

		Event<T> proxied() {
			return proxied;
		}

		@Override
		public Event<?> parent() {
			return proxied.parent();
		}
		
	}
	
}

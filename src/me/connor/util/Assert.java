package me.connor.util;

public class Assert {

	public static void notNull(@Nonnull Object obj, @Nullable String msg) throws NullPointerException {
		if (obj == null) throw (msg == null ? new NullPointerException() : new NullPointerException(msg));
	}
	
	public static void notNull(@Nonnull Object obj) throws NullPointerException {
		notNull(obj, null);
	}
	
	public static void allNotNull(Object...objs) throws NullPointerException {
		notNull(objs);
	}
	
	public static void notNull(Object[] array) throws NullPointerException {
		notNull((Object) array);
		for (int i = 0; i < array.length; i++) {
			notNull(array[i], "Thrown for argument " + (i + 1));
		}
	}
	
	public static void validIndex(int index) throws IndexOutOfBoundsException {
		if (index < 0) throw new IndexOutOfBoundsException(index);
	}
	
	public static void validIndex(int index, @Nonnull Object[] array) throws IndexOutOfBoundsException, NullPointerException {
		notNull(array);
		if (index < 0 || index >= array.length) throw new IndexOutOfBoundsException(index);
	}
	
	public static void isInstance(@Nonnull Class<?> type, @Nonnull Object instance) throws ClassCastException, NullPointerException {
		allNotNull(type, instance);
		if (!type.isInstance(instance)) throw new ClassCastException("Object is not an instance of Class " + type.getCanonicalName());
	}
	
	public static void isTrue(boolean condition, @Nonnull String msg) throws IllegalArgumentException {
		notNull(msg);
		if (!condition) throw new IllegalArgumentException(msg);
	}
	
	public static void isTrue(boolean condition) throws IllegalArgumentException {
		isTrue(condition, "Condition is not true");
	}
	
	public static void isFalse(boolean condition, @Nonnull String msg) throws IllegalArgumentException {
		notNull(msg);
		if (condition) throw new IllegalArgumentException(msg);
	}
	
	public static void isFalse(boolean condition) throws IllegalArgumentException {
		isFalse(condition, "Condition is not false");
	}
}

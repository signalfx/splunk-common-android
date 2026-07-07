/*
Copyright 2026 Splunk Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.splunk.rum.common.utils;

import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class ArrayListObserver<E> extends ArrayList<E> {

	private ArrayList<E> list;
	private final Observer<E> observer;

	public ArrayListObserver(@NonNull ArrayList<E> list, @NonNull Observer<E> observer) {
		super(0);
		this.list = list;
		this.observer = observer;
	}

	@NonNull
	public ArrayList<E> getWrappedList() {
		return list;
	}

	public void setWrappedList(@NonNull ArrayList<E> list) {
		this.list = list;
	}

	@Override
	public void trimToSize() {
		list.trimToSize();
	}

	@Override
	public void ensureCapacity(int minCapacity) {
		list.ensureCapacity(minCapacity);
	}

	@Override
	public int size() {
		return list.size();
	}

	@Override
	public boolean isEmpty() {
		return list.isEmpty();
	}

	@Override
	public boolean contains(@Nullable Object o) {
		return list.contains(o);
	}

	@Override
	public int indexOf(@Nullable Object o) {
		return list.indexOf(o);
	}

	@Override
	public int lastIndexOf(@Nullable Object o) {
		return list.lastIndexOf(o);
	}

	@NonNull
	@Override
	public Object clone() {
		return list.clone();
	}

	@NonNull
	@Override
	public Object[] toArray() {
		return list.toArray();
	}

	@NonNull
	@Override
	public <T> T[] toArray(@NonNull T[] a) {
		return list.toArray(a);
	}

	@Override
	public E get(int index) {
		return list.get(index);
	}

	@Override
	public E set(int index, E element) {
		final E removed = list.set(index, element);
		observer.onRemoved(removed);
		observer.onAdded(element);
		return removed;
	}

	@Override
	public boolean add(E e) {
		final boolean added = list.add(e);
		observer.onAdded(e);
		return added;
	}

	@Override
	public void add(int index, E element) {
		list.add(index, element);
		observer.onAdded(element);
	}

	@Override
	public E remove(int index) {
		final E removed = list.remove(index);
		observer.onRemoved(removed);
		return removed;
	}

	@Override
	public boolean remove(@Nullable Object o) {
		if (list.remove(o)) {
			observer.onRemoved((E) o);
			return true;
		} else
			return false;
	}

	@Override
	public void clear() {
		final ArrayList<E> copy = new ArrayList<>(list);
		list.clear();

		for (int i = 0; i < copy.size(); i++)
			observer.onRemoved(copy.get(i));
	}

	@Override
	public boolean addAll(@NonNull Collection<? extends E> c) {
		for (E element : c)
			add(element);

		return c.size() > 0;
	}

	@Override
	public boolean addAll(int index, @NonNull Collection<? extends E> c) {
		ArrayList<E> copy = new ArrayList<>(c);
		Collections.reverse(copy);

		for (int i = 0; i < copy.size(); i++)
			add(index, copy.get(i));

		return copy.size() > 0;
	}

	@Override
	protected void removeRange(int fromIndex, int toIndex) {
		for (int i = fromIndex; i < toIndex; i++)
			remove(fromIndex);
	}

	@Override
	public boolean removeAll(@NonNull Collection<?> c) {
		boolean removed = false;

		for (Object element : c)
			if (remove(element)) {
				observer.onRemoved((E) element);
				removed = true;
			}

		return removed;
	}

	@Override
	public boolean retainAll(@NonNull Collection<?> c) {
		boolean removed = false;
		final Iterator<E> iterator = list.iterator();

		while (iterator.hasNext()) {
			final E element = iterator.next();

			if (!c.contains(element)) {
				iterator.remove();
				observer.onRemoved(element);
				removed = true;
			}
		}

		return removed;
	}

	@NonNull
	@Override
	public ListIterator<E> listIterator(int i) {
		return new ListIterator<E>() {

			private int index = i;

			@Override
			public boolean hasNext() {
				return index + 1 != list.size();
			}

			@Override
			public E next() {
				return list.get(++index);
			}

			@Override
			public boolean hasPrevious() {
				return index - 1 >= 0;
			}

			@Override
			public E previous() {
				return list.get(--index);
			}

			@Override
			public int nextIndex() {
				return index + 1;
			}

			@Override
			public int previousIndex() {
				return index - 1;
			}

			@Override
			public void remove() {
				list.remove(index);
			}

			@Override
			public void set(E e) {
				list.set(index, e);
			}

			@Override
			public void add(E e) {
				list.add(index, e);
			}
		};
	}

	@NonNull
	@Override
	public ListIterator<E> listIterator() {
		return listIterator(0);
	}

	@NonNull
	@Override
	public Iterator<E> iterator() {
		return new Iterator<E>() {

			private int index = 0;

			@Override
			public boolean hasNext() {
				return index != list.size();
			}

			@Override
			public E next() {
				return list.get(index++);
			}

			@Override
			public void remove() {
				list.remove(--index);
			}
		};
	}

	@NonNull
	@Override
	public List<E> subList(int fromIndex, int toIndex) {
		ArrayList<E> subList = new ArrayList<>(toIndex - fromIndex);

		for (int i = fromIndex; i < toIndex; i++)
			subList.add(list.get(i));

		return subList;
	}

	@Override
	@RequiresApi(api = Build.VERSION_CODES.N)
	public void forEach(@NonNull Consumer<? super E> action) {
		for (int i = 0; i < list.size(); i++)
			action.accept(list.get(i));
	}

	@NonNull
	@Override
	@RequiresApi(api = Build.VERSION_CODES.N)
	public Spliterator<E> spliterator() {
		return list.spliterator();
	}

	@Override
	@RequiresApi(api = Build.VERSION_CODES.N)
	public boolean removeIf(@NonNull Predicate<? super E> filter) {
		boolean removed = false;

		for (int i = list.size() - 1; i >= 0; i--)
			if (filter.test(list.get(i))) {
				list.remove(i);
				removed = true;
			}

		return removed;
	}

	@Override
	@RequiresApi(api = Build.VERSION_CODES.N)
	public void replaceAll(@NonNull UnaryOperator<E> operator) {
		for (int i = 0; i < list.size(); i++)
			list.set(i, operator.apply(list.get(i)));
	}

	@Override
	public void sort(@Nullable Comparator<? super E> c) {
		Collections.sort(list, c);
	}

	@Override
	public boolean equals(@Nullable Object o) {
		return list.equals(o);
	}

	@Override
	public int hashCode() {
		return list.hashCode();
	}

	@Override
	public boolean containsAll(@NonNull Collection<?> c) {
		return list.containsAll(c);
	}

	@NonNull
	@Override
	public String toString() {
		return list.toString();
	}

	@NonNull
	@Override
	@RequiresApi(api = Build.VERSION_CODES.N)
	public Stream<E> stream() {
		return list.stream();
	}

	@NonNull
	@Override
	@RequiresApi(api = Build.VERSION_CODES.N)
	public Stream<E> parallelStream() {
		return list.parallelStream();
	}

	interface Observer<E> {
		void onAdded(E element);

		void onRemoved(E element);
	}
}

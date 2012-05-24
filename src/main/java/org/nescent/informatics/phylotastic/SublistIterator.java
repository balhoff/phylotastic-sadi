package org.nescent.informatics.phylotastic;

import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.NotImplementedException;

public class SublistIterator<T> implements Iterator<List<T>> {
	
	private final List<T> collection;
	private final int size;
	private int nextIndex = 0;
	
	public SublistIterator(List<T> collection, int size) {
		this.collection = collection;
		this.size = size;
	}

	@Override
	public boolean hasNext() {
		return this.nextIndex < this.collection.size();
	}

	@Override
	public List<T> next() {
		final int possibleEnd = this.nextIndex + this.size;
		final int end = (possibleEnd > this.collection.size()) ? this.collection.size() : possibleEnd;
		final List<T> sublist = collection.subList(nextIndex, end);
		this.nextIndex = possibleEnd;
		return sublist;
	}

	@Override
	public void remove() {
		throw new NotImplementedException();
	}
	
}

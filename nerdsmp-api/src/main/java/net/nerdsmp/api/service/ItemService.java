package net.nerdsmp.api.service;

import net.nerdsmp.api.model.ItemId;

import java.util.Optional;

public interface ItemService<T> {
    Optional<ItemDescriptor> describe(T item);

    T create(ItemId itemId);

    boolean isAuthentic(T item);

    record ItemDescriptor(ItemId itemId, int schemaVersion, String origin) {
    }
}


package com.payflow.catalog;

import com.payflow.catalog.dto.CreateProductRequest;
import com.payflow.catalog.dto.ProductResponse;
import com.payflow.common.api.PageResponse;
import com.payflow.common.ds.LruCache;
import com.payflow.common.error.ConflictException;
import com.payflow.common.error.NotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Two caches, two jobs:
// - Redis (@Cacheable "product"): shared by every inventory-service instance, survives restarts, has a TTL.
// - The hand-written LRU: per-instance, in memory, for autocomplete results, which are cheap to recompute.
@Service
public class ProductService {

    public static final String PRODUCT_CACHE = "product";

    private final ProductRepository products;
    private final LruCache<String, List<String>> autocompleteCache;
    private volatile ProductNameTrie trie = new ProductNameTrie();

    public ProductService(ProductRepository products, CacheProperties properties) {
        this.products = products;
        this.autocompleteCache = new LruCache<>(properties.autocompleteCapacity());
    }

    // Builds a fresh trie and swaps it in with one volatile write, so readers never see a half-built index.
    @EventListener(ApplicationReadyEvent.class)
    public void rebuildIndex() {
        var fresh = new ProductNameTrie();
        products.findAllNames().forEach(fresh::add);
        trie = fresh;
        autocompleteCache.clear();
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> page(Pageable pageable) {
        return PageResponse.from(products.findAll(pageable), ProductResponse::from);
    }

    // Concept: @Cacheable. The first call loads from Postgres and stores the result in Redis under "product::<id>";
    // later calls (from any instance) are served from Redis until the entry expires or is evicted.
    @Cacheable(cacheNames = PRODUCT_CACHE, key = "#id")
    @Transactional(readOnly = true)
    public ProductResponse byId(UUID id) {
        return products.findById(id).map(ProductResponse::from).orElseThrow(() -> new NotFoundException("Product"));
    }

    @Transactional(readOnly = true)
    public List<ProductResponse> byIds(Collection<UUID> ids) {
        return products.findAllById(ids).stream().map(ProductResponse::from).toList();
    }

    public List<String> autocomplete(String prefix, int limit) {
        int capped = Math.min(Math.max(limit, 1), 20);
        String key = prefix.trim().toLowerCase(Locale.ROOT) + "|" + capped;
        return autocompleteCache.computeIfAbsent(key, k -> trie.complete(prefix, capped));
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        if (products.existsBySku(request.sku())) {
            throw new ConflictException("SKU_TAKEN", "A product with this SKU already exists");
        }
        Product saved = products.save(new Product(request.sku(), request.name().trim(), request.priceMinor(), request.stock()));
        trie.add(saved.getName());
        autocompleteCache.clear();
        return ProductResponse.from(saved);
    }

    // Concept: @CacheEvict. After a write the cached copy would be stale, so it is removed from Redis.
    @CacheEvict(cacheNames = PRODUCT_CACHE, key = "#id")
    @Transactional
    public ProductResponse setStock(UUID id, int stock) {
        Product product = products.findById(id).orElseThrow(() -> new NotFoundException("Product"));
        product.setStock(stock);
        return ProductResponse.from(product);
    }

    public LruCache<String, List<String>> autocompleteCache() {
        return autocompleteCache;
    }
}

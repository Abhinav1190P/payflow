package com.payflow.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public class ProductCatalogLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogLoader.class);

    private final ProductRepository products;

    public ProductCatalogLoader(ProductRepository products) {
        this.products = products;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (products.count() > 0) {
            return;
        }
        List<Product> seed = read(new ClassPathResource("products.csv"));
        products.saveAll(seed);
        log.info("seeded {} products", seed.size());
    }

    // Concept: try-with-resources. The reader (and the stream under it) is closed automatically.
    static List<Product> read(ClassPathResource resource) {
        try (var reader = new BufferedReader(new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines()
                    .skip(1)
                    .filter(Predicate.not(String::isBlank))
                    .map(ProductCatalogLoader::parse)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read product seed file", e);
        }
    }

    private static Product parse(String line) {
        String[] cols = line.split(",", -1);
        if (cols.length != 4) {
            throw new IllegalArgumentException("bad product line: " + line);
        }
        return new Product(cols[0].trim(), cols[1].trim(), Long.parseLong(cols[2].trim()), Integer.parseInt(cols[3].trim()));
    }
}

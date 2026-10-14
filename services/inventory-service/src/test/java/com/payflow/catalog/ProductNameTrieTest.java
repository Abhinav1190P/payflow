package com.payflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProductNameTrieTest {

    private ProductNameTrie trie() {
        var trie = new ProductNameTrie();
        trie.add("Wireless Mouse");
        trie.add("Mouse Pad XL");
        trie.add("Monitor 27 inch");
        trie.add("Monitor Arm");
        trie.add("Mechanical Keyboard");
        return trie;
    }

    @Test
    void matchesAnyWordOfTheNameByPrefix() {
        assertThat(trie().complete("mou", 10)).containsExactlyInAnyOrder("Wireless Mouse", "Mouse Pad XL");
        assertThat(trie().complete("arm", 10)).containsExactly("Monitor Arm");
    }

    @Test
    void isCaseInsensitiveAndReturnsAlphabeticalWordOrder() {
        assertThat(trie().complete("MO", 10)).containsExactly("Monitor 27 inch", "Monitor Arm", "Mouse Pad XL", "Wireless Mouse");
    }

    @Test
    void respectsLimitAndHandlesMisses() {
        assertThat(trie().complete("m", 2)).hasSize(2);
        assertThat(trie().complete("zebra", 5)).isEmpty();
        assertThat(trie().complete(" ", 5)).isEmpty();
    }

    @Test
    void duplicateNamesAreNotRepeated() {
        var trie = new ProductNameTrie();
        trie.add("USB Hub");
        trie.add("USB Hub");
        assertThat(trie.complete("usb", 5)).containsExactly("USB Hub");
        assertThat(trie.indexedWords()).isEqualTo(2);
    }
}

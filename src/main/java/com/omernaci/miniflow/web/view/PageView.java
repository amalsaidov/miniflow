package com.omernaci.miniflow.web.view;

import org.springframework.data.domain.Page;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PageView<T>(List<T> items, int page, int size, long total, String basePath, Map<String, String> params) {

    public static final List<Integer> SIZES = List.of(10, 25, 50, 100);

    public static int normalizeSize(int size, int defaultSize) {
        return SIZES.contains(size) ? size : defaultSize;
    }

    public static <T> PageView<T> slice(List<T> all, int page, int size, String basePath, Map<String, String> params) {
        int pages = Math.max(1, (all.size() + size - 1) / size);
        int p = Math.min(Math.max(page, 1), pages);
        int from = (p - 1) * size;
        List<T> items = all.subList(Math.min(from, all.size()), Math.min(from + size, all.size()));
        return new PageView<>(List.copyOf(items), p, size, all.size(), basePath, params);
    }

    public static <T> PageView<T> of(Page<T> page, String basePath, Map<String, String> params) {
        return new PageView<>(page.getContent(), page.getNumber() + 1, page.getSize(), page.getTotalElements(),
                basePath, params);
    }

    public List<Integer> sizes() {
        return SIZES;
    }

    public int totalPages() {
        return (int) Math.max(1, (total + size - 1) / size);
    }

    public boolean hasPrev() {
        return page > 1;
    }

    public boolean hasNext() {
        return page < totalPages();
    }

    public long from() {
        return total == 0 ? 0 : (long) (page - 1) * size + 1;
    }

    public long to() {
        return Math.min(total, (long) page * size);
    }

    public String url(int n) {
        return build(Math.min(Math.max(n, 1), totalPages()), size);
    }

    public String sizeUrl(int newSize) {
        return build(1, newSize);
    }

    public List<Integer> window() {
        int last = totalPages();
        List<Integer> out = new ArrayList<>();
        for (int n = 1; n <= last; n++) {
            boolean edge = n == 1 || n == last;
            boolean near = Math.abs(n - page) <= 1;
            if (edge || near) {
                out.add(n);
            } else if (!out.isEmpty() && out.get(out.size() - 1) != 0) {
                out.add(0);
            }
        }
        return out;
    }

    private String build(int n, int pageSize) {
        UriComponentsBuilder b = UriComponentsBuilder.fromPath(basePath);
        Map<String, String> all = new LinkedHashMap<>(params == null ? Map.of() : params);
        all.forEach((k, v) -> {
            if (v != null && !v.isBlank()) {
                b.queryParam(k, v);
            }
        });
        if (n > 1) {
            b.queryParam("page", n);
        }
        b.queryParam("size", pageSize);
        return b.encode().build().toUriString();
    }
}

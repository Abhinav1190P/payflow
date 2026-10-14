package com.payflow.order.admin;

import com.payflow.common.api.ApiResponse;
import com.payflow.common.api.PageResponse;
import com.payflow.order.OrderRepository;
import com.payflow.order.OrderStatus;
import com.payflow.order.dto.OrderView;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/orders")
@PreAuthorize("hasRole('ADMIN')")
public class AdminOrderController {

    private final OrderRepository orders;

    public AdminOrderController(OrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<PageResponse<OrderView>> list(@RequestParam(required = false) OrderStatus status,
                                                     @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        var page = status == null ? orders.findAll(pageable) : orders.findByStatus(status, pageable);
        return ApiResponse.of(PageResponse.from(page, OrderView::summary));
    }
}

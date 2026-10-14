package com.payflow.order.admin;

import com.payflow.order.Order;
import com.payflow.order.OrderRepository;
import com.payflow.order.OrderStatus;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

// Concept: Spring MVC with server-side views. @Controller (not @RestController) returns a VIEW NAME;
// Thymeleaf renders templates/orders.html with the data put into the Model.
@Controller
public class AdminUiController {

    private final OrderRepository orders;

    public AdminUiController(OrderRepository orders) {
        this.orders = orders;
    }

    @GetMapping("/admin-ui/orders")
    @Transactional(readOnly = true)
    public String orders(@RequestParam(required = false) OrderStatus status, Model model) {
        var page = PageRequest.of(0, 200, Sort.by(Sort.Direction.DESC, "createdAt"));
        List<Order> recent = orders.findAll(page).getContent();
        Map<OrderStatus, Long> counts = recent.stream()
                .collect(Collectors.groupingBy(Order::getStatus, () -> new EnumMap<>(OrderStatus.class), Collectors.counting()));
        List<Order> shown = status == null ? recent : recent.stream().filter(o -> o.getStatus() == status).toList();
        model.addAttribute("orders", shown);
        model.addAttribute("counts", counts);
        model.addAttribute("statuses", OrderStatus.values());
        model.addAttribute("selected", status);
        return "orders";
    }
}

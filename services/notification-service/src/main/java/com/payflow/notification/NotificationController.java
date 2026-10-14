package com.payflow.notification;

import com.payflow.common.api.ApiResponse;
import com.payflow.common.api.PageResponse;
import com.payflow.common.security.AuthenticatedUser;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationRepository notifications;

    public NotificationController(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public record NotificationView(UUID id, UUID orderId, String type, String message, Instant createdAt) {
        static NotificationView from(Notification n) {
            return new NotificationView(n.getId(), n.getOrderId(), n.getType(), n.getMessage(), n.getCreatedAt());
        }
    }

    @GetMapping("/me")
    public ApiResponse<PageResponse<NotificationView>> mine(@AuthenticationPrincipal AuthenticatedUser user,
                                                            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.of(PageResponse.from(notifications.findByUserId(user.id(), pageable), NotificationView::from));
    }
}

package com.eduhub.notification.repository;

import com.eduhub.notification.domain.Notification;
import com.eduhub.notification.domain.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

	long countByEmployeeIdAndType(Long employeeId, NotificationType type);
}

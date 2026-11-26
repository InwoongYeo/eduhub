package com.eduhub.notification.domain;

import java.time.Instant;

import com.eduhub.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 임직원에게 보낼 알림의 기록 (FR-09). 1단계에서는 DB 기록만 하고 실제 발송은 하지 않는다.
 */
@Entity
@Table(name = "notification",
		indexes = @Index(name = "idx_notification_employee_created", columnList = "employee_id, created_at"))
public class Notification {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "employee_id", nullable = false)
	private User employee;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Column(nullable = false, length = 255)
	private String message;

	@Column(nullable = false)
	private Instant createdAt;

	protected Notification() {
	}

	private Notification(User employee, NotificationType type, String message, Instant createdAt) {
		this.employee = employee;
		this.type = type;
		this.message = message;
		this.createdAt = createdAt;
	}

	public static Notification create(User employee, NotificationType type, String message, Instant createdAt) {
		return new Notification(employee, type, message, createdAt);
	}

	public Long getId() {
		return id;
	}

	public User getEmployee() {
		return employee;
	}

	public NotificationType getType() {
		return type;
	}

	public String getMessage() {
		return message;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}

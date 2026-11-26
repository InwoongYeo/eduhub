-- 신청 확정·승급 알림 기록 (FR-09, D-42). MySQL 8.4와 H2(MySQL 모드) 양쪽에서 실행된다.
CREATE TABLE notification (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    employee_id BIGINT       NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    message     VARCHAR(255) NOT NULL,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_employee FOREIGN KEY (employee_id) REFERENCES users (id)
);

CREATE INDEX idx_notification_employee_created ON notification (employee_id, created_at);

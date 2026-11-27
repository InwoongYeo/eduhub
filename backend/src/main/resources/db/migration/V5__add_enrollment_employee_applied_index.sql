-- 내 신청 내역 최신순 정렬을 인덱스로 (FR-07, D-43). MySQL 8.4와 H2(MySQL 모드) 양쪽에서 실행된다.
CREATE INDEX idx_enrollment_employee_applied ON enrollment (employee_id, applied_at);

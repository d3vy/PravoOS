CREATE UNIQUE INDEX uq_applications_pending_email
    ON lawyer_applications (email)
    WHERE status = 'PENDING';

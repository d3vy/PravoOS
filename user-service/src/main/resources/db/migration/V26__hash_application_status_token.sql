UPDATE lawyer_applications
SET status_token = encode(sha256(status_token::bytea), 'hex')
WHERE status_token IS NOT NULL;

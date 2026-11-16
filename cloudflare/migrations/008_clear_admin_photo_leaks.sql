-- Safety cleanup: non-admin accounts must never reuse an admin profile photo.
-- This only clears exact admin-photo copies and leaves user-owned photos intact.
UPDATE users
SET photo_url = '',
    updated_at = CAST(strftime('%s', 'now') AS INTEGER) * 1000
WHERE COALESCE(photo_url, '') <> ''
  AND lower(COALESCE(role, 'user')) <> 'admin'
  AND photo_url IN (
    SELECT photo_url
    FROM users
    WHERE lower(COALESCE(role, '')) = 'admin'
      AND COALESCE(photo_url, '') <> ''
  );
